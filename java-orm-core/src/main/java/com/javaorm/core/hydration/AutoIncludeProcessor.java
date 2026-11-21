package com.javaorm.core.hydration;

import com.javaorm.core.annotation.JoNavigate;
import com.javaorm.core.annotation.JoTable;
import com.javaorm.core.annotation.RelationType;
import com.javaorm.core.ast.BinaryCondition;
import com.javaorm.core.ast.ColumnExpression;
import com.javaorm.core.ast.SelectStatement;
import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.dialect.Dialect;
import com.javaorm.core.fastbean.FastBean;
import com.javaorm.core.fastbean.FastPropertySetter;
import com.javaorm.core.fastbean.LambdaFactory;
import com.javaorm.core.metadata.ColumnMetadata;
import com.javaorm.core.metadata.EntityMetadata;
import com.javaorm.core.metadata.EntityMetadataManager;
import com.javaorm.core.metadata.RelationMetadata;
import com.javaorm.core.operator.SqlOperator;
import com.javaorm.core.query.SelectBuilder;
import com.javaorm.core.sql.SqlEnv;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.util.*;

/**
 * EasyQuery 风格的 selectAutoInclude 自动关联装配与 DTO 投影处理器。
 * <p>
 * 核心机制：
 * 1. 严格约束：该功能只能在 DTO/VO 类上使用，严禁在 Entity 实体类上直接使用；
 * 2. 列裁剪投影（Selective Projection）：SQL 主查询只查 DTO 声明的标量字段 + 关联装配所需的 Key 列；
 * 3. 杜绝笛卡尔积（Split/Batch IN Query）：自动识别嵌套 DTO 对象或 DTO 集合属性，分批批量查询；
 * 4. 递归级联装配：子 DTO 若仍包含嵌套关系，递归执行 selectAutoInclude 装配，支持无限层级。
 */
public class AutoIncludeProcessor {

    private static final int CHUNK_SIZE = 500;

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
     * 4. 属性签名最佳匹配度探测。
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

        // 4. 属性签名最佳匹配度推断
        if (allMeta != null && !allMeta.isEmpty()) {
            List<Field> fields = getAllDeclaredFields(dtoClass);
            Class<?> bestEntity = null;
            int maxMatch = 0;
            for (Map.Entry<Class<?>, EntityMetadata> entry : allMeta.entrySet()) {
                Class<?> entityClass = entry.getKey();
                EntityMetadata meta = entry.getValue();
                int match = 0;
                for (Field f : fields) {
                    String propName = f.getName();
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

        // 收集 DTO 中的所有字段
        List<Field> allFields = getAllDeclaredFields(dtoClass);

        for (Field field : allFields) {
            String propName = field.getName();

            // 检查是否是关系导航字段
            RelationMetadata relation = findRelation(rootMetadata, field);
            if (relation != null) {
                // 如果是关联字段，必须投影关联所需的键列
                if (relation.getRelationType() == RelationType.MANY_TO_ONE || relation.getRelationType() == RelationType.ONE_TO_ONE) {
                    // 需要自身外键列
                    if (rootMetadata != null) {
                        ColumnMetadata selfCol = rootMetadata.getColumnByProperty(relation.getSelfProperty());
                        if (selfCol != null) {
                            targetColumns.add(selfCol.getColumnName());
                        } else if (config != null && config.getNameConversion() != null) {
                            targetColumns.add(config.getNameConversion().convert(relation.getSelfProperty()));
                        }
                    }
                } else if (relation.getRelationType() == RelationType.ONE_TO_MANY) {
                    // 需要自身主键列
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
                continue;
            }

            // 普通标量字段，如果在实体中存在对应列，则加入投影
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

        // 添加外部显式要求的列（例如子查询中父查询分组所需的外键）
        if (extraRequiredColumns != null) {
            targetColumns.addAll(extraRequiredColumns);
        }

        // 如果没有投影任何列（极端空 DTO），兜底投影主键列
        if (targetColumns.isEmpty() && rootMetadata != null && rootMetadata.getIdColumn() != null) {
            targetColumns.add(rootMetadata.getIdColumn().getColumnName());
        }

        // 重新设置投影
        statement.getProjectExpressions().clear();
        for (String colName : targetColumns) {
            statement.addProjectExpression(new ColumnExpression(rootAlias, colName));
        }
    }

    /**
     * 执行查询并递归装配关联 DTO。
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

        // 2. 将行映射水合为 DTO 实例列表，并登记上下文行映射
        FastBean<DTO> fastBean = LambdaFactory.getFastBean(dtoClass);
        List<DTO> dtoList = new ArrayList<>(rows.size());
        Map<Object, Map<String, Object>> currentBatchContext = new IdentityHashMap<>();

        for (Map<String, Object> row : rows) {
            DTO dto = fastBean.create();
            hydrateDtoScalarProperties(dto, row, fastBean, dtoClass);
            dtoList.add(dto);
            currentBatchContext.put(dto, row);
            AutoIncludeContext.registerRow(dto, row);
        }

        // 3. 自动识别 DTO 上的关联关系，并递归执行关联批量装配
        List<Field> allFields = getAllDeclaredFields(dtoClass);
        for (Field field : allFields) {
            RelationMetadata relation = findRelation(rootMetadata, field);
            if (relation == null) {
                continue;
            }

            RelationType relType = relation.getRelationType();
            if (relType == RelationType.MANY_TO_ONE || relType == RelationType.ONE_TO_ONE) {
                processManyToOne(conn, dtoList, field, relation, rootMetadata, currentBatchContext, dialect, config, dataSource);
            } else if (relType == RelationType.ONE_TO_MANY) {
                processOneToMany(conn, dtoList, field, relation, rootMetadata, currentBatchContext, dialect, config, dataSource);
            }
        }

        return dtoList;
    }

    /**
     * 处理 ManyToOne / OneToOne 关联回填。
     */
    private static <DTO> void processManyToOne(Connection conn,
                                               List<DTO> dtoList,
                                               Field field,
                                               RelationMetadata relation,
                                               EntityMetadata rootMetadata,
                                               Map<Object, Map<String, Object>> batchContext,
                                               Dialect dialect,
                                               OrmConfig config,
                                               DataSource dataSource) throws Exception {
        Class<?> childClass = field.getType();
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
            Object fkVal = extractKeyValue(dto, selfProp, selfColName, row);
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

        for (int i = 0; i < keyList.size(); i += CHUNK_SIZE) {
            List<Object> chunk = keyList.subList(i, Math.min(i + CHUNK_SIZE, keyList.size()));
            SelectBuilder<?> childQuery = new SelectBuilder(targetEntityClass, dataSource, dialect, config);

            // 若子类型是 DTO（非 Entity），递归应用 selectAutoInclude
            if (!isEntityClass(childClass)) {
                // 确保子查询投影包含目标键（主键）
                childQuery.addExtraRequiredColumn(targetColName);
                childQuery.selectAutoInclude(childClass);
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
        Map<Object, Object> childMap = new HashMap<>();
        for (Object child : allChildren) {
            Map<String, Object> childRow = AutoIncludeContext.getRow(child);
            Object pkVal = extractKeyValue(child, relation.getTargetProperty(), targetColName, childRow);
            if (pkVal != null) {
                childMap.put(normalizeKeyValue(pkVal), child);
            }
        }

        // 装配回父 DTO
        for (DTO dto : dtoList) {
            Object fk = dtoToFkMap.get(dto);
            if (fk != null) {
                Object matchedChild = childMap.get(normalizeKeyValue(fk));
                setFieldValue(dto, field, matchedChild);
            }
        }
    }

    /**
     * 处理 OneToMany 关联集合回填。
     */
    private static <DTO> void processOneToMany(Connection conn,
                                               List<DTO> dtoList,
                                               Field field,
                                               RelationMetadata relation,
                                               EntityMetadata rootMetadata,
                                               Map<Object, Map<String, Object>> batchContext,
                                               Dialect dialect,
                                               OrmConfig config,
                                               DataSource dataSource) throws Exception {
        Class<?> elemClass = getCollectionElementType(field);
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
            Object pkVal = extractKeyValue(dto, selfProp, selfColName, row);
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

        for (int i = 0; i < keyList.size(); i += CHUNK_SIZE) {
            List<Object> chunk = keyList.subList(i, Math.min(i + CHUNK_SIZE, keyList.size()));
            SelectBuilder<?> childQuery = new SelectBuilder(targetEntityClass, dataSource, dialect, config);

            // 若集合元素类型是 DTO（非 Entity），递归应用 selectAutoInclude，并强制投影子表外键
            if (!isEntityClass(elemClass)) {
                childQuery.addExtraRequiredColumn(childFkColName);
                childQuery.selectAutoInclude(elemClass);
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
        Map<Object, List<Object>> groupedChildren = new HashMap<>();
        for (Object child : allChildren) {
            Map<String, Object> childRow = AutoIncludeContext.getRow(child);
            Object fkVal = extractKeyValue(child, relation.getTargetProperty(), childFkColName, childRow);
            if (fkVal != null) {
                groupedChildren.computeIfAbsent(normalizeKeyValue(fkVal), k -> new ArrayList<>()).add(child);
            }
        }

        // 装配回父 DTO
        for (DTO dto : dtoList) {
            Object pk = dtoToPkMap.get(dto);
            List<Object> children = (pk != null) ? groupedChildren.get(normalizeKeyValue(pk)) : null;
            if (children == null) {
                children = new ArrayList<>();
            }
            setCollectionFieldValue(dto, field, children);
        }
    }

    /**
     * 将行数据填充到 DTO 的标量属性中。
     */
    private static <DTO> void hydrateDtoScalarProperties(DTO dto, Map<String, Object> row, FastBean<DTO> fastBean, Class<DTO> dtoClass) {
        if (row == null || row.isEmpty()) {
            return;
        }

        List<Field> fields = getAllDeclaredFields(dtoClass);
        for (Field field : fields) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                continue;
            }
            String name = field.getName();
            Object rawVal = null;
            if (row.containsKey(name)) {
                rawVal = row.get(name);
            } else if (row.containsKey(name.toLowerCase())) {
                rawVal = row.get(name.toLowerCase());
            } else if (row.containsKey(name.toUpperCase())) {
                rawVal = row.get(name.toUpperCase());
            } else {
                String underscore = toUnderscore(name);
                if (row.containsKey(underscore)) {
                    rawVal = row.get(underscore);
                } else if (row.containsKey(underscore.toLowerCase())) {
                    rawVal = row.get(underscore.toLowerCase());
                } else if (row.containsKey(underscore.toUpperCase())) {
                    rawVal = row.get(underscore.toUpperCase());
                }
            }

            if (rawVal != null) {
                Object converted = ColumnMetadata.convertValue(field.getType(), rawVal);
                FastPropertySetter setter = fastBean.getSetter(name.toLowerCase());
                if (setter != null) {
                    try {
                        setter.set(dto, converted);
                        continue;
                    } catch (Exception ignored) {
                    }
                }
                setFieldValue(dto, field, converted);
            }
        }
    }

    /**
     * 查找字段对应的实体关联关系。
     */
    private static RelationMetadata findRelation(EntityMetadata rootMetadata, Field field) {
        if (field == null) {
            return null;
        }
        String name = field.getName();

        // 1. 优先通过 MetadataBridgeRegistry 解析 DTO 字段本身标注的关系注解（支持 @JoNavigate, EasyQuery @Navigate, JPA 等）
        Class<?> declaringClass = field.getDeclaringClass();
        com.javaorm.core.bridge.RelationInfo bridgeInfo = com.javaorm.core.bridge.MetadataBridgeRegistry.resolveRelation(declaringClass, field, null);
        if (bridgeInfo != null) {
            RelationMetadata rm = new RelationMetadata();
            rm.setPropertyName(name);
            rm.setRelationType(bridgeInfo.getRelationType());
            rm.setSelfProperty(bridgeInfo.getSelfProperty());
            rm.setTargetProperty(bridgeInfo.getTargetProperty());
            rm.setMappingClass(bridgeInfo.getMappingClass());
            rm.setSelfMappingProperty(bridgeInfo.getSelfMappingProperty());
            rm.setTargetMappingProperty(bridgeInfo.getTargetMappingProperty());
            rm.setExtraFilter(bridgeInfo.getExtraFilter());
            Class<?> targetEntity = bridgeInfo.getTargetEntityClass();
            if (targetEntity == null) {
                if (Collection.class.isAssignableFrom(field.getType())) {
                    targetEntity = getCollectionElementType(field);
                } else {
                    targetEntity = field.getType();
                }
            }
            rm.setTargetEntityClass(targetEntity);
            return rm;
        }

        if (rootMetadata == null) {
            return null;
        }

        // 2. 匹配 rootMetadata 中同名属性的关系
        RelationMetadata rel = rootMetadata.getRelation(name);
        if (rel != null) {
            return rel;
        }

        // 3. 忽略大小写匹配
        Collection<RelationMetadata> allRelations = rootMetadata.getRelations();
        if (allRelations != null) {
            for (RelationMetadata r : allRelations) {
                if (r.getPropertyName() != null && r.getPropertyName().equalsIgnoreCase(name)) {
                    return r;
                }
            }
        }

        return null;
    }

    /**
     * 从对象或 Row Map 中提取键值。
     */
    private static Object extractKeyValue(Object obj, String propName, String colName, Map<String, Object> row) {
        // 1. 尝试从对象本身的属性读取
        if (obj != null && propName != null) {
            try {
                Field f = findField(obj.getClass(), propName);
                if (f != null) {
                    f.setAccessible(true);
                    Object val = f.get(obj);
                    if (val != null) {
                        return val;
                    }
                }
            } catch (Exception ignored) {
            }
            try {
                String getterName = "get" + Character.toUpperCase(propName.charAt(0)) + propName.substring(1);
                Method m = obj.getClass().getMethod(getterName);
                Object val = m.invoke(obj);
                if (val != null) {
                    return val;
                }
            } catch (Exception ignored) {
            }
        }

        // 2. 从行数据 Map 中查找
        if (row != null) {
            if (colName != null) {
                if (row.containsKey(colName)) return row.get(colName);
                if (row.containsKey(colName.toLowerCase())) return row.get(colName.toLowerCase());
                if (row.containsKey(colName.toUpperCase())) return row.get(colName.toUpperCase());
            }
            if (propName != null) {
                if (row.containsKey(propName)) return row.get(propName);
                if (row.containsKey(propName.toLowerCase())) return row.get(propName.toLowerCase());
                if (row.containsKey(propName.toUpperCase())) return row.get(propName.toUpperCase());
                String und = toUnderscore(propName);
                if (row.containsKey(und)) return row.get(und);
                if (row.containsKey(und.toLowerCase())) return row.get(und.toLowerCase());
                if (row.containsKey(und.toUpperCase())) return row.get(und.toUpperCase());
            }
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

    private static void setFieldValue(Object target, Field field, Object value) {
        if (target == null || field == null) {
            return;
        }
        try {
            String setterName = "set" + Character.toUpperCase(field.getName().charAt(0)) + field.getName().substring(1);
            Method setter = null;
            for (Method m : target.getClass().getMethods()) {
                if (m.getName().equals(setterName) && m.getParameterCount() == 1) {
                    setter = m;
                    break;
                }
            }
            if (setter != null) {
                setter.invoke(target, value);
                return;
            }
        } catch (Exception ignored) {
        }
        try {
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception ignored) {
        }
    }

    private static void setCollectionFieldValue(Object target, Field field, List<Object> list) {
        if (target == null || field == null) {
            return;
        }
        Object collectionVal = list;
        if (Set.class.isAssignableFrom(field.getType())) {
            collectionVal = new LinkedHashSet<>(list);
        }
        setFieldValue(target, field, collectionVal);
    }

    private static List<Field> getAllDeclaredFields(Class<?> clazz) {
        List<Field> list = new ArrayList<>();
        Class<?> curr = clazz;
        while (curr != null && curr != Object.class) {
            Field[] declared = curr.getDeclaredFields();
            list.addAll(Arrays.asList(declared));
            curr = curr.getSuperclass();
        }
        return list;
    }

    private static Field findField(Class<?> clazz, String fieldName) {
        Class<?> curr = clazz;
        while (curr != null && curr != Object.class) {
            try {
                return curr.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
            }
            curr = curr.getSuperclass();
        }
        return null;
    }

    private static Class<?> getCollectionElementType(Field field) {
        Type genericType = field.getGenericType();
        if (genericType instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) genericType;
            Type[] args = pt.getActualTypeArguments();
            if (args != null && args.length > 0 && args[0] instanceof Class) {
                return (Class<?>) args[0];
            }
        }
        return null;
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
