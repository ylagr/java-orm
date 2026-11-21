package com.javaorm.core.hydration;

import com.javaorm.core.annotation.RelationType;
import com.javaorm.core.ast.BinaryCondition;
import com.javaorm.core.ast.ColumnExpression;
import com.javaorm.core.ast.SelectStatement;
import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.dialect.Dialect;
import com.javaorm.core.metadata.ColumnMetadata;
import com.javaorm.core.metadata.EntityMetadata;
import com.javaorm.core.metadata.EntityMetadataManager;
import com.javaorm.core.metadata.RelationMetadata;
import com.javaorm.core.operator.SqlOperator;
import com.javaorm.core.query.SelectBuilder;
import com.javaorm.core.sql.SqlEnv;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.*;

/**
 * EasyQuery 风格的 selectAutoInclude 自动关联装配与 DTO 投影处理器（无反射高性能架构）。
 * <p>
 * 核心设计：
 * 1. 严格约束：该功能只能在 DTO/VO 类上使用，严禁在 Entity 实体类上直接使用；
 * 2. 彻底杜绝反射：基于 DtoMetadata、FastBean、LambdaFactory 字节码调用器，消除所有运行期反射（Method.invoke / Field.get / set）；
 * 3. 列裁剪投影（Selective Projection）：SQL 主查询只查 DTO 声明的标量字段 + 关联装配所需的 Key 列；
 * 4. 杜绝笛卡尔积（Split/Batch IN Query）：自动识别嵌套 DTO 对象或 DTO 集合属性，分批批量查询；
 * 5. 递归级联装配：子 DTO 若仍包含嵌套关系，递归执行 selectAutoInclude 装配，支持无限层级；
 * 6. 多框架注解桥接：关系注解通过 MetadataBridgeRegistry 统一解析，无缝兼容 EasyQuery、JPA 与原生注解。
 */
public class AutoIncludeProcessor {

    private static final int CHUNK_SIZE = 500;
    private static final ThreadLocal<Set<Class<?>>> VISITING_DTO_CLASSES = ThreadLocal.withInitial(HashSet::new);

    /**
     * 校验传入的 class 是否为合法 DTO/VO 类。
     * 若传入的是实体类（带有 @JoTable、JPA @Table、已注册为 Entity，或等于根实体），强制抛出异常。
     */
    public static void validateDtoClass(Class<?> dtoClass, Class<?> rootEntityClass) {
        if (dtoClass == null) {
            throw new IllegalArgumentException("dtoClass must not be null");
        }
        if (isEntityClass(dtoClass) || dtoClass.equals(rootEntityClass)) {
            throw new IllegalArgumentException("selectAutoInclude only supports DTO/VO class, cannot be used on Entity class: " + dtoClass.getName());
        }
    }

    /**
     * 判断指定类是否为实体类。
     */
    public static boolean isEntityClass(Class<?> clazz) {
        if (clazz == null || clazz.isPrimitive() || clazz.getName().startsWith("java.")) {
            return false;
        }
        return EntityMetadataManager.hasEntityAnnotation(clazz);
    }

    /**
     * 尝试根据 DTO/VO 类智能推断其对应的根实体（Root Entity）类。
     * <p>
     * 支持推断策略：
     * 1. 命名规范推断：去除 DTO/VO 后缀，在同包、上级包、.entity / .model / .domain 等子包中查找同名实体；
     * 2. 静态内部类包含关系推断（如单测或统一命名空间内的实体）；
     * 3. 已注册实体元数据中心按类名检索；
     * 4. 属性签名最佳匹配度探测（基于 DtoMetadata 零反射）。
     *
     * @param dtoClass 目标 DTO 类
     * @param config   ORM 全局配置
     * @return 推断出的根实体 Class，若无法推断则返回 null
     */
    public static Class<?> inferRootEntityClass(Class<?> dtoClass, OrmConfig config) {
        if (dtoClass == null) {
            return null;
        }
        String simpleName = dtoClass.getSimpleName();
        String candidateName = null;
        if (simpleName.endsWith("Dto") && simpleName.length() > 3) {
            candidateName = simpleName.substring(0, simpleName.length() - 3);
        } else if (simpleName.endsWith("DTO") && simpleName.length() > 3) {
            candidateName = simpleName.substring(0, simpleName.length() - 3);
        } else if (simpleName.endsWith("VO") && simpleName.length() > 2) {
            candidateName = simpleName.substring(0, simpleName.length() - 2);
        } else if (simpleName.endsWith("Vo") && simpleName.length() > 2) {
            candidateName = simpleName.substring(0, simpleName.length() - 2);
        }

        // 1. 检查包含类（静态内部类场景）
        Class<?> enclosing = dtoClass.getEnclosingClass();
        if (enclosing != null && candidateName != null) {
            for (Class<?> nested : enclosing.getDeclaredClasses()) {
                if (nested.getSimpleName().equals(candidateName) && isEntityClass(nested)) {
                    return nested;
                }
            }
        }

        // 2. 检查相同包与常见领域包
        if (candidateName != null) {
            Package pkg = dtoClass.getPackage();
            if (pkg != null) {
                String pkgName = pkg.getName();
                try {
                    Class<?> candidate = Class.forName(pkgName + "." + candidateName);
                    if (isEntityClass(candidate)) {
                        return candidate;
                    }
                } catch (ClassNotFoundException ignored) {
                }
                int lastDot = pkgName.lastIndexOf('.');
                if (lastDot > 0) {
                    String parentPkg = pkgName.substring(0, lastDot);
                    for (String sub : Arrays.asList("", ".entity", ".model", ".domain", ".table")) {
                        try {
                            Class<?> candidate = Class.forName(parentPkg + sub + "." + candidateName);
                            if (isEntityClass(candidate)) {
                                return candidate;
                            }
                        } catch (ClassNotFoundException ignored) {
                        }
                    }
                }
            }
        }

        // 3. 从已缓存的所有实体元数据中按类名匹配
        Map<Class<?>, EntityMetadata> allMeta = EntityMetadataManager.getAllCachedMetadata();
        if (allMeta != null && candidateName != null) {
            for (Class<?> entityClass : allMeta.keySet()) {
                if (entityClass.getSimpleName().equalsIgnoreCase(candidateName) && isEntityClass(entityClass)) {
                    return entityClass;
                }
            }
        }

        // 4. 属性签名最佳匹配度推断（基于 DtoMetadata 零反射）
        if (allMeta != null && !allMeta.isEmpty()) {
            DtoMetadata dtoMeta = DtoMetadata.get(dtoClass, null);
            List<DtoMetadata.DtoProperty> props = new ArrayList<>(dtoMeta.getScalarProperties());
            props.addAll(dtoMeta.getRelationProperties());

            Class<?> bestEntity = null;
            int maxMatch = 0;
            for (Map.Entry<Class<?>, EntityMetadata> entry : allMeta.entrySet()) {
                Class<?> entityClass = entry.getKey();
                EntityMetadata meta = entry.getValue();
                int match = 0;
                for (DtoMetadata.DtoProperty p : props) {
                    String propName = p.getPropertyName();
                    if (meta.getColumnByProperty(propName) != null || meta.getRelation(propName) != null) {
                        match++;
                    }
                }
                if (match > maxMatch && match >= 2) {
                    maxMatch = match;
                    bestEntity = entityClass;
                }
            }
            if (bestEntity != null) {
                return bestEntity;
            }
        }

        return null;
    }

    /**
     * 配置 selectAutoInclude 的 SQL 投影列（Selective Projection）。
     * 仅投影 DTO 中声明的对应实体列，以及关联装配所必需的主键/外键列。
     */
    public static void configureAutoIncludeProjection(SelectStatement statement,
                                                      EntityMetadata rootMetadata,
                                                      Class<?> dtoClass,
                                                      OrmConfig config,
                                                      Set<String> extraRequiredColumns) {
        if (statement == null) {
            return;
        }
        String rootAlias = statement.getRootTableAlias();
        Set<String> targetColumns = new LinkedHashSet<>();

        DtoMetadata dtoMeta = DtoMetadata.get(dtoClass, rootMetadata);

        // 1. 按照 DTO 字段声明顺序遍历属性，确保生成的投影 SQL 列顺序与 DTO 保持完全一致
        for (DtoMetadata.DtoProperty prop : dtoMeta.getAllProperties()) {
            if (prop.isRelation()) {
                RelationMetadata relation = prop.getRelation();
                if (relation != null) {
                    RelationType relType = relation.getRelationType();
                    if (relType == RelationType.MANY_TO_ONE || relType == RelationType.ONE_TO_ONE) {
                        if (rootMetadata != null) {
                            ColumnMetadata selfCol = rootMetadata.getColumnByProperty(relation.getSelfProperty());
                            if (selfCol != null) {
                                targetColumns.add(selfCol.getColumnName());
                            } else if (config != null && config.getNameConversion() != null) {
                                targetColumns.add(config.getNameConversion().convert(relation.getSelfProperty()));
                            }
                        }
                    } else if (relType == RelationType.ONE_TO_MANY) {
                        if (rootMetadata != null) {
                            ColumnMetadata selfCol = rootMetadata.getColumnByProperty(relation.getSelfProperty());
                            if (selfCol != null) {
                                targetColumns.add(selfCol.getColumnName());
                            } else {
                                ColumnMetadata idCol = rootMetadata.getIdColumn();
                                if (idCol != null) {
                                    targetColumns.add(idCol.getColumnName());
                                }
                            }
                        }
                    }
                }
            } else {
                String propName = prop.getPropertyName();
                if (rootMetadata != null) {
                    ColumnMetadata col = rootMetadata.getColumnByProperty(propName);
                    if (col == null) {
                        col = rootMetadata.getColumnByDbName(propName);
                    }
                    if (col == null && config != null && config.getNameConversion() != null) {
                        String dbCol = config.getNameConversion().convert(propName);
                        col = rootMetadata.getColumnByDbName(dbCol);
                    }
                    if (col != null) {
                        targetColumns.add(col.getColumnName());
                    }
                }
            }
        }

        // 3. 添加外部显式要求的列（例如子查询中父查询分组所需的外键）
        if (extraRequiredColumns != null) {
            targetColumns.addAll(extraRequiredColumns);
        }

        // 4. 如果没有投影任何列（极端空 DTO），兜底投影主键列
        if (targetColumns.isEmpty() && rootMetadata != null && rootMetadata.getIdColumn() != null) {
            targetColumns.add(rootMetadata.getIdColumn().getColumnName());
        }

        // 5. 重新配置查询投影表达式
        statement.getProjectExpressions().clear();
        for (String colName : targetColumns) {
            statement.addProjectExpression(new ColumnExpression(rootAlias, colName));
        }
    }

    /**
     * 执行查询并递归装配关联 DTO（彻底杜绝反射的高性能装配）。
     */
    public static <DTO> List<DTO> executeAndHydrate(Connection conn,
                                                    SelectStatement statement,
                                                    Class<DTO> dtoClass,
                                                    EntityMetadata rootMetadata,
                                                    Dialect dialect,
                                                    OrmConfig config,
                                                    DataSource dataSource,
                                                    SqlEnv env) throws Exception {
        // 1. 执行主查询，获取行映射数据
        List<Map<String, Object>> rows = env.executeSelectMaps(conn, statement);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 将行映射水合为 DTO 实例列表（基于 FastBean 与编译好的 FastPropertySetter）
        DtoMetadata dtoMeta = DtoMetadata.get(dtoClass, rootMetadata);
        List<DTO> dtoList = new ArrayList<>(rows.size());
        Map<Object, Map<String, Object>> currentBatchContext = new IdentityHashMap<>();
        List<DtoMetadata.DtoProperty> scalarProps = dtoMeta.getScalarProperties();

        for (Map<String, Object> row : rows) {
            @SuppressWarnings("unchecked")
            DTO dto = (DTO) dtoMeta.createInstance();
            for (DtoMetadata.DtoProperty prop : scalarProps) {
                Object rawVal = extractFromRow(row, prop.getPropertyName());
                if (rawVal != null) {
                    prop.setValue(dto, rawVal);
                }
            }
            dtoList.add(dto);
            currentBatchContext.put(dto, row);
            AutoIncludeContext.registerRow(dto, row);
        }

        boolean isRoot = VISITING_DTO_CLASSES.get().isEmpty();
        VISITING_DTO_CLASSES.get().add(dtoClass);
        try {
            // 3. 自动识别 DTO 上的关联关系，并递归执行关联批量装配
            for (DtoMetadata.DtoProperty relProp : dtoMeta.getRelationProperties()) {
                RelationMetadata relation = relProp.getRelation();
                if (relation == null) {
                    continue;
                }

                RelationType relType = relation.getRelationType();
                if (relType == RelationType.MANY_TO_ONE || relType == RelationType.ONE_TO_ONE) {
                    processManyToOne(conn, dtoList, dtoMeta, relProp, relation, rootMetadata, currentBatchContext, dialect, config, dataSource);
                } else if (relType == RelationType.ONE_TO_MANY) {
                    processOneToMany(conn, dtoList, dtoMeta, relProp, relation, rootMetadata, currentBatchContext, dialect, config, dataSource);
                }
            }

            return dtoList;
        } finally {
            VISITING_DTO_CLASSES.get().remove(dtoClass);
            if (isRoot) {
                VISITING_DTO_CLASSES.remove();
            }
        }
    }

    /**
     * 处理 ManyToOne / OneToOne 关联回填（无反射）。
     */
    private static <DTO> void processManyToOne(Connection conn,
                                               List<DTO> dtoList,
                                               DtoMetadata parentDtoMeta,
                                               DtoMetadata.DtoProperty relProp,
                                               RelationMetadata relation,
                                               EntityMetadata rootMetadata,
                                               Map<Object, Map<String, Object>> batchContext,
                                               Dialect dialect,
                                               OrmConfig config,
                                               DataSource dataSource) throws Exception {
        Class<?> childClass = relProp.getPropertyType();
        Class<?> targetEntityClass = relation.getTargetEntityClass();
        if (targetEntityClass != null && !isEntityClass(targetEntityClass)) {
            Class<?> inferred = inferRootEntityClass(targetEntityClass, config);
            if (inferred != null) {
                targetEntityClass = inferred;
            }
        }
        EntityMetadata targetMeta = EntityMetadataManager.get(targetEntityClass, config);

        String selfProp = relation.getSelfProperty();
        ColumnMetadata selfCol = rootMetadata != null ? rootMetadata.getColumnByProperty(selfProp) : null;
        String selfColName = selfCol != null ? selfCol.getColumnName() : selfProp;

        // 收集所有父 DTO 的外键值
        Map<Object, Object> dtoToFkMap = new IdentityHashMap<>();
        Set<Object> fkValues = new LinkedHashSet<>();

        for (DTO dto : dtoList) {
            Map<String, Object> row = batchContext.get(dto);
            Object fkVal = extractKeyValue(dto, parentDtoMeta, selfProp, selfColName, row, rootMetadata);
            if (fkVal != null) {
                fkValues.add(fkVal);
                dtoToFkMap.put(dto, fkVal);
            }
        }

        if (fkValues.isEmpty()) {
            return;
        }

        ColumnMetadata targetCol = targetMeta != null ? targetMeta.getColumnByProperty(relation.getTargetProperty()) : null;
        String targetColName = targetCol != null ? targetCol.getColumnName() : relation.getTargetProperty();

        // 分批切片查询关联子对象
        List<Object> allChildren = new ArrayList<>();
        List<Object> keyList = new ArrayList<>(fkValues);
        boolean isChildDto = !isEntityClass(childClass);

        for (int i = 0; i < keyList.size(); i += CHUNK_SIZE) {
            List<Object> chunk = keyList.subList(i, Math.min(i + CHUNK_SIZE, keyList.size()));
            SelectBuilder<?> childQuery = new SelectBuilder(targetEntityClass, dataSource, dialect, config);

            // 若子类型是 DTO（非 Entity），递归应用 selectAutoInclude（防止双向循环引用）
            if (isChildDto) {
                // 确保子查询投影包含目标键（主键）
                childQuery.addExtraRequiredColumn(targetColName);
                if (!VISITING_DTO_CLASSES.get().contains(childClass)) {
                    childQuery.selectAutoInclude(childClass);
                } else {
                    childQuery.select(childClass);
                }
            }

            childQuery.getStatement().addWhereCondition(
                    new BinaryCondition(childQuery.getStatement().getRootTableAlias(), targetColName, SqlOperator.IN, chunk)
            );

            List<?> chunkResults = childQuery.list(conn);
            if (chunkResults != null) {
                allChildren.addAll(chunkResults);
            }
        }

        // 建立 targetKey -> childObject 映射
        DtoMetadata childDtoMeta = isChildDto ? DtoMetadata.get(childClass, targetMeta) : null;
        Map<Object, Object> childMap = new HashMap<>();
        for (Object child : allChildren) {
            Map<String, Object> childRow = AutoIncludeContext.getRow(child);
            Object pkVal = extractKeyValue(child, childDtoMeta, relation.getTargetProperty(), targetColName, childRow, targetMeta);
            if (pkVal != null) {
                childMap.put(normalizeKeyValue(pkVal), child);
            }
        }

        // 无反射装配回父 DTO
        for (DTO dto : dtoList) {
            Object fk = dtoToFkMap.get(dto);
            if (fk != null) {
                Object matchedChild = childMap.get(normalizeKeyValue(fk));
                relProp.setValue(dto, matchedChild);
            }
        }
    }

    /**
     * 处理 OneToMany 关联集合回填（无反射）。
     */
    private static <DTO> void processOneToMany(Connection conn,
                                               List<DTO> dtoList,
                                               DtoMetadata parentDtoMeta,
                                               DtoMetadata.DtoProperty relProp,
                                               RelationMetadata relation,
                                               EntityMetadata rootMetadata,
                                               Map<Object, Map<String, Object>> batchContext,
                                               Dialect dialect,
                                               OrmConfig config,
                                               DataSource dataSource) throws Exception {
        Class<?> elemClass = relProp.getCollectionElementType();
        if (elemClass == null) {
            return;
        }

        Class<?> targetEntityClass = relation.getTargetEntityClass();
        if (targetEntityClass != null && !isEntityClass(targetEntityClass)) {
            Class<?> inferred = inferRootEntityClass(targetEntityClass, config);
            if (inferred != null) {
                targetEntityClass = inferred;
            }
        }
        EntityMetadata targetMeta = EntityMetadataManager.get(targetEntityClass, config);

        String selfProp = relation.getSelfProperty();
        ColumnMetadata selfCol = rootMetadata != null ? rootMetadata.getColumnByProperty(selfProp) : null;
        if (selfCol == null && rootMetadata != null) {
            selfCol = rootMetadata.getIdColumn();
        }
        String selfColName = selfCol != null ? selfCol.getColumnName() : selfProp;

        // 收集所有父 DTO 的主键值
        Map<Object, Object> dtoToPkMap = new IdentityHashMap<>();
        Set<Object> pkValues = new LinkedHashSet<>();

        for (DTO dto : dtoList) {
            Map<String, Object> row = batchContext.get(dto);
            Object pkVal = extractKeyValue(dto, parentDtoMeta, selfProp, selfColName, row, rootMetadata);
            if (pkVal != null) {
                pkValues.add(pkVal);
                dtoToPkMap.put(dto, pkVal);
            }
        }

        if (pkValues.isEmpty()) {
            return;
        }

        ColumnMetadata childFkCol = targetMeta != null ? targetMeta.getColumnByProperty(relation.getTargetProperty()) : null;
        String childFkColName = childFkCol != null ? childFkCol.getColumnName() : relation.getTargetProperty();

        // 分批切片查询关联子对象
        List<Object> allChildren = new ArrayList<>();
        List<Object> keyList = new ArrayList<>(pkValues);
        boolean isChildDto = !isEntityClass(elemClass);

        for (int i = 0; i < keyList.size(); i += CHUNK_SIZE) {
            List<Object> chunk = keyList.subList(i, Math.min(i + CHUNK_SIZE, keyList.size()));
            SelectBuilder<?> childQuery = new SelectBuilder(targetEntityClass, dataSource, dialect, config);

            // 若集合元素类型是 DTO（非 Entity），递归应用 selectAutoInclude，并强制投影子表外键
            if (isChildDto) {
                childQuery.addExtraRequiredColumn(childFkColName);
                if (!VISITING_DTO_CLASSES.get().contains(elemClass)) {
                    childQuery.selectAutoInclude(elemClass);
                } else {
                    childQuery.select(elemClass);
                }
            }

            childQuery.getStatement().addWhereCondition(
                    new BinaryCondition(childQuery.getStatement().getRootTableAlias(), childFkColName, SqlOperator.IN, chunk)
            );

            List<?> chunkResults = childQuery.list(conn);
            if (chunkResults != null) {
                allChildren.addAll(chunkResults);
            }
        }

        // 按照外键分组
        DtoMetadata childDtoMeta = isChildDto ? DtoMetadata.get(elemClass, targetMeta) : null;
        Map<Object, List<Object>> groupedChildren = new HashMap<>();
        for (Object child : allChildren) {
            Map<String, Object> childRow = AutoIncludeContext.getRow(child);
            Object fkVal = extractKeyValue(child, childDtoMeta, relation.getTargetProperty(), childFkColName, childRow, targetMeta);
            if (fkVal != null) {
                groupedChildren.computeIfAbsent(normalizeKeyValue(fkVal), k -> new ArrayList<>()).add(child);
            }
        }

        // 无反射装配回父 DTO
        for (DTO dto : dtoList) {
            Object pk = dtoToPkMap.get(dto);
            List<Object> children = (pk != null) ? groupedChildren.get(normalizeKeyValue(pk)) : null;
            if (children == null) {
                children = new ArrayList<>();
            }
            relProp.setValue(dto, children);
        }
    }

    /**
     * 从对象或 Row Map 中提取键值（基于 DtoProperty / FastPropertyGetter 零反射）。
     */
    private static Object extractKeyValue(Object obj,
                                         DtoMetadata dtoMeta,
                                         String propName,
                                         String colName,
                                         Map<String, Object> row,
                                         EntityMetadata entityMeta) {
        if (obj != null) {
            // 1. 优先尝试从 DTO 元数据获取（基于 FastPropertyGetter 字节码直接读取）
            if (dtoMeta != null && propName != null) {
                DtoMetadata.DtoProperty prop = dtoMeta.getProperty(propName);
                if (prop != null) {
                    Object val = prop.getValue(obj);
                    if (val != null) {
                        return val;
                    }
                }
            } else if (entityMeta != null && propName != null) {
                // 2. 从实体元数据直接通过 fastGetter 读取
                ColumnMetadata col = entityMeta.getColumnByProperty(propName);
                if (col != null) {
                    Object val = col.getValue(obj);
                    if (val != null) {
                        return val;
                    }
                }
            }
        }

        // 3. 从行数据 Map 中查找（列裁剪投影已确保外键列必然存在）
        if (row != null) {
            Object val = extractFromRow(row, colName);
            if (val != null) return val;
            val = extractFromRow(row, propName);
            if (val != null) return val;
        }

        return null;
    }

    /**
     * 从行映射中兼容大小写与驼峰下划线提取值。
     */
    private static Object extractFromRow(Map<String, Object> row, String name) {
        if (row == null || name == null) {
            return null;
        }
        if (row.containsKey(name)) {
            return row.get(name);
        }
        String lower = name.toLowerCase();
        if (row.containsKey(lower)) {
            return row.get(lower);
        }
        String upper = name.toUpperCase();
        if (row.containsKey(upper)) {
            return row.get(upper);
        }
        String und = toUnderscore(name);
        if (row.containsKey(und)) {
            return row.get(und);
        }
        if (row.containsKey(und.toLowerCase())) {
            return row.get(und.toLowerCase());
        }
        if (row.containsKey(und.toUpperCase())) {
            return row.get(und.toUpperCase());
        }
        return null;
    }

    private static Object normalizeKeyValue(Object val) {
        if (val == null) {
            return null;
        }
        if (val instanceof Number) {
            return ((Number) val).longValue();
        }
        return val.toString();
    }

    private static String toUnderscore(String name) {
        if (name == null) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                sb.append('_');
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }
}
