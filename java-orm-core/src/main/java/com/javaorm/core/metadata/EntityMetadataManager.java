package com.javaorm.core.metadata;

import com.javaorm.core.annotation.*;
import com.javaorm.core.config.NameConversion;
import com.javaorm.core.config.OrmConfig;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实体元数据管理器（线程安全单例与缓存中心）。
 * 自动识别 JPA (javax/jakarta) 注解、JavaOrm 专有注解，以及零注解默认约定。
 */
public class EntityMetadataManager {

    private static final Map<MetadataCacheKey, EntityMetadata> METADATA_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<String, RelationMetadata>> RELATIONS_CACHE = new ConcurrentHashMap<>();

    public static final class MetadataCacheKey {
        private final Class<?> entityClass;
        private final Class<?> nameConversionClass;
        private final String defaultLogicDeleteProperty;
        private final Object defaultLogicDeleteNormalValue;
        private final Object defaultLogicDeleteDeletedValue;

        public MetadataCacheKey(Class<?> entityClass, OrmConfig config) {
            this.entityClass = entityClass;
            if (config != null) {
                this.nameConversionClass = config.getNameConversion() != null ? config.getNameConversion().getClass() : null;
                this.defaultLogicDeleteProperty = config.getDefaultLogicDeleteProperty();
                this.defaultLogicDeleteNormalValue = config.getDefaultLogicDeleteNormalValue();
                this.defaultLogicDeleteDeletedValue = config.getDefaultLogicDeleteDeletedValue();
            } else {
                this.nameConversionClass = null;
                this.defaultLogicDeleteProperty = null;
                this.defaultLogicDeleteNormalValue = null;
                this.defaultLogicDeleteDeletedValue = null;
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MetadataCacheKey that = (MetadataCacheKey) o;
            return Objects.equals(entityClass, that.entityClass) &&
                    Objects.equals(nameConversionClass, that.nameConversionClass) &&
                    Objects.equals(defaultLogicDeleteProperty, that.defaultLogicDeleteProperty) &&
                    Objects.equals(defaultLogicDeleteNormalValue, that.defaultLogicDeleteNormalValue) &&
                    Objects.equals(defaultLogicDeleteDeletedValue, that.defaultLogicDeleteDeletedValue);
        }

        @Override
        public int hashCode() {
            return Objects.hash(entityClass, nameConversionClass, defaultLogicDeleteProperty,
                    defaultLogicDeleteNormalValue, defaultLogicDeleteDeletedValue);
        }
    }

    public static EntityMetadata get(Class<?> entityClass, OrmConfig config) {
        if (config == null) {
            config = new OrmConfig();
        }
        MetadataCacheKey key = new MetadataCacheKey(entityClass, config);
        final OrmConfig cfg = config;
        return METADATA_CACHE.computeIfAbsent(key, k -> parseMetadata(entityClass, cfg));
    }

    public static EntityMetadata get(Class<?> entityClass) {
        return get(entityClass, new OrmConfig());
    }

    public static RelationMetadata getRelation(Class<?> entityClass, String propertyName) {
        if (entityClass == null || propertyName == null) return null;
        Map<String, RelationMetadata> map = RELATIONS_CACHE.computeIfAbsent(entityClass, EntityMetadataManager::parseClassRelations);
        return map.get(propertyName);
    }

    public static Map<String, RelationMetadata> parseClassRelations(Class<?> clazz) {
        Map<String, RelationMetadata> relations = new LinkedHashMap<>();
        Map<String, Field> fields = getAllFields(clazz);
        for (Map.Entry<String, Field> entry : fields.entrySet()) {
            String propName = entry.getKey();
            Field field = entry.getValue();
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)) {
                continue;
            }
            Method getter = findGetter(clazz, propName, field.getType());
            RelationMetadata rel = parseRelation(field, getter, clazz);
            if (rel != null) {
                Method setter = findSetter(clazz, propName, field.getType());
                rel.setGetter(getter);
                rel.setSetter(setter);
                relations.put(propName, rel);
            }
        }
        return Collections.unmodifiableMap(relations);
    }

    public static void clearCache() {
        METADATA_CACHE.clear();
        RELATIONS_CACHE.clear();
    }

    private static EntityMetadata parseMetadata(Class<?> clazz, OrmConfig config) {
        EntityMetadata metadata = new EntityMetadata(clazz);
        NameConversion nameConversion = config.getNameConversion();

        // 1. 解析表名（检查 @Table 或兼容 JPA 的 @Table）
        String tableName = null;
        String schema = null;
        for (Annotation ann : clazz.getAnnotations()) {
            String annName = ann.annotationType().getName();
            if (ann instanceof Table) {
                Table t = (Table) ann;
                if (!t.name().isEmpty()) tableName = t.name();
                if (!t.schema().isEmpty()) schema = t.schema();
            } else if (annName.endsWith(".Table")) {
                tableName = getAnnotationString(ann, "name");
                schema = getAnnotationString(ann, "schema");
            }
        }
        if (tableName == null || tableName.isEmpty()) {
            tableName = nameConversion.convertTableName(clazz.getSimpleName());
        }
        metadata.setTableName(tableName);
        metadata.setSchema(schema);

        // 2. 填充关联关系（独立静态解析）
        Map<String, RelationMetadata> classRelations = parseClassRelations(clazz);
        for (RelationMetadata rel : classRelations.values()) {
            metadata.addRelation(rel);
        }

        // 3. 遍历字段与 Getter/Setter 解析列
        Map<String, Field> fields = getAllFields(clazz);
        for (Map.Entry<String, Field> entry : fields.entrySet()) {
            String propName = entry.getKey();
            Field field = entry.getValue();

            // 过滤静态与 transient 字段以及关联属性
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || classRelations.containsKey(propName)) {
                continue;
            }

            Method getter = findGetter(clazz, propName, field.getType());
            Method setter = findSetter(clazz, propName, field.getType());

            // 解析普通列
            ColumnMetadata colMeta = parseColumn(field, getter, setter, nameConversion, config);
            if (colMeta != null && !colMeta.isIgnored()) {
                metadata.addColumn(colMeta);
            }
        }

        return metadata;
    }

    private static RelationMetadata parseRelation(Field field, Method getter, Class<?> currentEntity) {
        Annotation[] annotations = collectAnnotations(field, getter);
        boolean isRelation = false;
        RelationType relType = RelationType.MANY_TO_ONE;
        String selfProp = "";
        String targetProp = "";
        String joinColName = "";

        for (Annotation ann : annotations) {
            String name = ann.annotationType().getName();
            if (ann instanceof Navigate) {
                Navigate nav = (Navigate) ann;
                isRelation = true;
                relType = nav.value();
                selfProp = nav.selfProperty();
                targetProp = nav.targetProperty();
            } else if (name.endsWith(".ManyToOne")) {
                isRelation = true;
                relType = RelationType.MANY_TO_ONE;
            } else if (name.endsWith(".OneToMany")) {
                isRelation = true;
                relType = RelationType.ONE_TO_MANY;
            } else if (name.endsWith(".OneToOne")) {
                isRelation = true;
                relType = RelationType.ONE_TO_ONE;
            } else if (name.endsWith(".JoinColumn")) {
                joinColName = getAnnotationString(ann, "name");
            }
        }

        // 如果字段类型本身是带有 @Table 或 @Entity 注解的类，或者 Collection 包含泛型实体
        Class<?> targetType = field.getType();
        if (Collection.class.isAssignableFrom(targetType)) {
            Type genericType = field.getGenericType();
            if (genericType instanceof ParameterizedType) {
                Type[] args = ((ParameterizedType) genericType).getActualTypeArguments();
                if (args.length > 0 && args[0] instanceof Class) {
                    targetType = (Class<?>) args[0];
                    if (isRelation || hasEntityAnnotation(targetType)) {
                        isRelation = true;
                        relType = RelationType.ONE_TO_MANY;
                    }
                }
            }
        } else if (hasEntityAnnotation(targetType)) {
            isRelation = true;
        }

        if (!isRelation) {
            return null;
        }

        RelationMetadata rel = new RelationMetadata();
        rel.setPropertyName(field.getName());
        rel.setRelationType(relType);
        rel.setTargetEntityClass(targetType);
        rel.setField(field);

        // 默认外键属性推导规则
        if (selfProp == null || selfProp.isEmpty()) {
            if (relType == RelationType.MANY_TO_ONE || relType == RelationType.ONE_TO_ONE) {
                // 例如 teacher -> teacherId
                selfProp = field.getName() + "Id";
            }
        }
        if (targetProp == null || targetProp.isEmpty()) {
            if (relType == RelationType.MANY_TO_ONE || relType == RelationType.ONE_TO_ONE) {
                targetProp = "id";
            } else if (relType == RelationType.ONE_TO_MANY) {
                // 一对多默认外键在对方表：例如 Student 里的 teacherId
                String currentSimpleName = currentEntity.getSimpleName();
                targetProp = Character.toLowerCase(currentSimpleName.charAt(0)) + currentSimpleName.substring(1) + "Id";
            }
        }
        rel.setSelfProperty(selfProp);
        rel.setTargetProperty(targetProp);

        return rel;
    }

    private static ColumnMetadata parseColumn(Field field, Method getter, Method setter, NameConversion nameConversion, OrmConfig config) {
        Annotation[] annotations = collectAnnotations(field, getter);

        ColumnMetadata col = new ColumnMetadata();
        col.setPropertyName(field.getName());
        col.setPropertyType(field.getType());
        col.setField(field);
        col.setGetter(getter);
        col.setSetter(setter);

        String colName = null;
        boolean isPk = "id".equalsIgnoreCase(field.getName());

        for (Annotation ann : annotations) {
            String name = ann.annotationType().getName();
            if (ann instanceof Column) {
                Column c = (Column) ann;
                if (!c.name().isEmpty()) colName = c.name();
                if (c.primaryKey()) isPk = true;
                if (c.ignore()) col.setIgnored(true);
            } else if (name.endsWith(".Column")) {
                String n = getAnnotationString(ann, "name");
                if (n != null && !n.isEmpty()) colName = n;
            } else if (ann instanceof Id || name.endsWith(".Id")) {
                isPk = true;
            } else if (ann instanceof GeneratedValue || name.endsWith(".GeneratedValue")) {
                col.setGenerated(true);
            } else if (ann instanceof LogicDelete) {
                LogicDelete ld = (LogicDelete) ann;
                col.setLogicDelete(true);
                col.setLogicDeleteNormalValue(ld.normalValue());
                col.setLogicDeleteDeletedValue(ld.deletedValue());
            } else if (ann instanceof TenantId) {
                col.setTenant(true);
            }
        }

        col.setPrimaryKey(isPk);
        if (isPk) {
            Class<?> type = field.getType();
            if (Number.class.isAssignableFrom(type) || type == long.class || type == int.class) {
                col.setGenerated(true);
            }
        }
        if (colName == null || colName.isEmpty()) {
            colName = nameConversion.convert(field.getName());
        }
        col.setColumnName(colName);

        // 全局逻辑删除属性推断（若未注解但属性名匹配配置）
        if (!col.isLogicDelete() && config.getDefaultLogicDeleteProperty() != null) {
            if (config.getDefaultLogicDeleteProperty().equalsIgnoreCase(field.getName())
                    || config.getDefaultLogicDeleteProperty().equalsIgnoreCase(colName)) {
                col.setLogicDelete(true);
                col.setLogicDeleteNormalValue(config.getDefaultLogicDeleteNormalValue());
                col.setLogicDeleteDeletedValue(config.getDefaultLogicDeleteDeletedValue());
            }
        }

        return col;
    }

    private static boolean hasEntityAnnotation(Class<?> clazz) {
        if (clazz == null || clazz.isPrimitive() || clazz.getName().startsWith("java.")) return false;
        for (Annotation ann : clazz.getAnnotations()) {
            String name = ann.annotationType().getName();
            if (name.endsWith(".Entity") || name.endsWith(".Table") || ann instanceof Table) {
                return true;
            }
        }
        return false;
    }

    private static Annotation[] collectAnnotations(Field field, Method getter) {
        List<Annotation> list = new ArrayList<>();
        if (field != null) {
            Collections.addAll(list, field.getAnnotations());
        }
        if (getter != null) {
            Collections.addAll(list, getter.getAnnotations());
        }
        return list.toArray(new Annotation[0]);
    }

    private static String getAnnotationString(Annotation ann, String methodName) {
        try {
            Method m = ann.annotationType().getMethod(methodName);
            Object result = m.invoke(ann);
            return result != null ? result.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static Map<String, Field> getAllFields(Class<?> clazz) {
        Map<String, Field> map = new LinkedHashMap<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field f : current.getDeclaredFields()) {
                if (!map.containsKey(f.getName())) {
                    map.put(f.getName(), f);
                }
            }
            current = current.getSuperclass();
        }
        return map;
    }

    private static Method findGetter(Class<?> clazz, String propName, Class<?> propType) {
        String capitalized = Character.toUpperCase(propName.charAt(0)) + propName.substring(1);
        String[] prefixes = (propType == boolean.class || propType == Boolean.class)
                ? new String[]{"is", "get"}
                : new String[]{"get"};

        for (String prefix : prefixes) {
            try {
                return clazz.getMethod(prefix + capitalized);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private static Method findSetter(Class<?> clazz, String propName, Class<?> propType) {
        String capitalized = Character.toUpperCase(propName.charAt(0)) + propName.substring(1);
        try {
            return clazz.getMethod("set" + capitalized, propType);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }
}
