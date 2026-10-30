package com.javaorm.core.metadata;

import com.javaorm.core.annotation.*;
import com.javaorm.core.config.NameConversion;
import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.fastbean.*;

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
    private static final Map<Class<?>, Map<String, ValueObjectMetadata>> VALUE_OBJECTS_CACHE = new ConcurrentHashMap<>();

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
                rel.setFastSetter(LambdaFactory.createSetter(clazz, setter, field));
                rel.setFastGetter(LambdaFactory.createGetter(clazz, getter, field));
                relations.put(propName, rel);
            }
        }
        return Collections.unmodifiableMap(relations);
    }

    public static ValueObjectMetadata getValueObject(Class<?> entityClass, String propertyName) {
        if (entityClass == null || propertyName == null) return null;
        Map<String, ValueObjectMetadata> map = VALUE_OBJECTS_CACHE.computeIfAbsent(entityClass, EntityMetadataManager::parseClassValueObjects);
        return map.get(propertyName);
    }

    public static boolean isValueObject(Class<?> entityClass, String propertyName) {
        return getValueObject(entityClass, propertyName) != null;
    }

    public static Map<String, ValueObjectMetadata> parseClassValueObjects(Class<?> clazz) {
        Map<String, ValueObjectMetadata> map = new LinkedHashMap<>();
        Map<String, Field> fields = getAllFields(clazz);
        for (Map.Entry<String, Field> entry : fields.entrySet()) {
            String propName = entry.getKey();
            Field field = entry.getValue();
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)) {
                continue;
            }
            if (hasValueObjectAnnotation(field, clazz, propName)) {
                Method getter = findGetter(clazz, propName, field.getType());
                Method setter = findSetter(clazz, propName, field.getType());
                FastPropertyGetter fg = LambdaFactory.createGetter(clazz, getter, field);
                FastPropertySetter fs = LambdaFactory.createSetter(clazz, setter, field);
                FastBeanCreator<?> creator = LambdaFactory.createConstructor(field.getType());
                ValueObjectMetadata vo = new ValueObjectMetadata(propName, propName, field.getType(), clazz, field, getter, setter, fg, fs, creator);
                map.put(propName, vo);
            }
        }
        return Collections.unmodifiableMap(map);
    }

    private static boolean hasValueObjectAnnotation(Field field, Class<?> clazz, String propName) {
        Method getter = findGetter(clazz, propName, field.getType());
        Annotation[] annotations = collectAnnotations(field, getter);
        for (Annotation ann : annotations) {
            if (ann instanceof JoValueObject) return true;
            String name = ann.annotationType().getName();
            if (name.endsWith(".JoValueObject") || name.endsWith(".ValueObject") || name.endsWith(".Embedded")) {
                return true;
            }
        }
        return false;
    }

    public static void clearCache() {
        METADATA_CACHE.clear();
        RELATIONS_CACHE.clear();
        VALUE_OBJECTS_CACHE.clear();
    }

    public static EntityMetadata getTableMetadata(Class<?> entityClass) {
        return get(entityClass);
    }

    private static EntityMetadata parseMetadata(Class<?> clazz, OrmConfig config) {
        EntityMetadata metadata = new EntityMetadata(clazz);
        metadata.setBeanCreator(LambdaFactory.createConstructor(clazz));
        NameConversion nameConversion = config.getNameConversion();

        // 1. 解析表名（优先 @JoTable，亦兼容 JPA 的 @Table）
        String tableName = null;
        String schema = null;
        for (Annotation ann : clazz.getAnnotations()) {
            String annName = ann.annotationType().getName();
            if (ann instanceof JoTable) {
                JoTable t = (JoTable) ann;
                if (!t.value().isEmpty()) tableName = t.value();
                else if (!t.name().isEmpty()) tableName = t.name();
                if (!t.schema().isEmpty()) schema = t.schema();
            } else if (annName.endsWith(".JoTable") || annName.endsWith(".Table")) {
                tableName = getAnnotationString(ann, "value");
                if (tableName == null || tableName.isEmpty()) {
                    tableName = getAnnotationString(ann, "name");
                }
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

        // 3. 填充值对象（独立静态解析）
        Map<String, ValueObjectMetadata> classVos = parseClassValueObjects(clazz);
        for (ValueObjectMetadata vo : classVos.values()) {
            metadata.addValueObject(vo);
        }

        // 4. 遍历字段与 Getter/Setter 解析普通列及 ValueObject 平铺列
        Map<String, Field> fields = getAllFields(clazz);
        for (Map.Entry<String, Field> entry : fields.entrySet()) {
            String propName = entry.getKey();
            Field field = entry.getValue();

            // 过滤静态与 transient 字段以及关联属性
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || classRelations.containsKey(propName)) {
                continue;
            }

            // 处理 ValueObject 平铺
            if (classVos.containsKey(propName)) {
                ValueObjectMetadata vo = classVos.get(propName);
                parseValueObjectColumns(metadata, vo, new ArrayList<>(), nameConversion, config);
                continue;
            }

            Method getter = findGetter(clazz, propName, field.getType());
            Method setter = findSetter(clazz, propName, field.getType());

            // 解析普通列
            ColumnMetadata colMeta = parseColumn(clazz, field, getter, setter, nameConversion, config);
            if (colMeta != null && !colMeta.isIgnored()) {
                metadata.addColumn(colMeta);
            }
        }

        return metadata;
    }

    private static void parseValueObjectColumns(EntityMetadata metadata, ValueObjectMetadata vo,
                                                List<ValueObjectStep> parentSteps,
                                                NameConversion nameConversion, OrmConfig config) {
        ValueObjectStep step = new ValueObjectStep(vo.getPropertyName(), vo.getValueObjectClass(),
                vo.getField(), vo.getGetter(), vo.getSetter(), vo.getFastGetter(), vo.getFastSetter(), vo.getCreator());
        List<ValueObjectStep> currentSteps = new ArrayList<>(parentSteps);
        currentSteps.add(step);

        Class<?> voClass = vo.getValueObjectClass();
        Map<String, Field> fields = getAllFields(voClass);
        for (Map.Entry<String, Field> entry : fields.entrySet()) {
            String propName = entry.getKey();
            Field field = entry.getValue();
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)) {
                continue;
            }

            Method getter = findGetter(voClass, propName, field.getType());
            Method setter = findSetter(voClass, propName, field.getType());

            if (hasValueObjectAnnotation(field, voClass, propName)) {
                // 嵌套 ValueObject
                String fullNestedProp = vo.getFullPropertyName() + "." + propName;
                FastPropertyGetter fg = LambdaFactory.createGetter(voClass, getter, field);
                FastPropertySetter fs = LambdaFactory.createSetter(voClass, setter, field);
                FastBeanCreator<?> creator = LambdaFactory.createConstructor(field.getType());
                ValueObjectMetadata nestedVo = new ValueObjectMetadata(propName, fullNestedProp, field.getType(), voClass, field, getter, setter, fg, fs, creator);
                vo.addNestedValueObject(nestedVo);
                metadata.addValueObject(nestedVo);
                parseValueObjectColumns(metadata, nestedVo, currentSteps, nameConversion, config);
            } else {
                // 普通叶子列
                ColumnMetadata colMeta = parseColumn(voClass, field, getter, setter, nameConversion, config);
                if (colMeta != null && !colMeta.isIgnored()) {
                    String fullProp = vo.getFullPropertyName() + "." + propName;
                    colMeta.setPropertyName(fullProp);
                    colMeta.setValueObjectSteps(currentSteps);
                    metadata.addColumn(colMeta);
                    vo.addColumn(colMeta);
                }
            }
        }
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
            if (ann instanceof JoNavigate) {
                JoNavigate nav = (JoNavigate) ann;
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

    private static ColumnMetadata parseColumn(Class<?> clazz, Field field, Method getter, Method setter, NameConversion nameConversion, OrmConfig config) {
        Annotation[] annotations = collectAnnotations(field, getter);

        ColumnMetadata col = new ColumnMetadata();
        col.setPropertyName(field.getName());
        col.setPropertyType(field.getType());
        col.setField(field);
        col.setGetter(getter);
        col.setSetter(setter);
        col.setFastSetter(LambdaFactory.createSetter(clazz, setter, field));
        col.setFastGetter(LambdaFactory.createGetter(clazz, getter, field));

        String colName = null;
        boolean isPk = "id".equalsIgnoreCase(field.getName());

        for (Annotation ann : annotations) {
            String name = ann.annotationType().getName();
            if (ann instanceof JoColumn) {
                JoColumn c = (JoColumn) ann;
                if (!c.value().isEmpty()) colName = c.value();
                else if (!c.name().isEmpty()) colName = c.name();
                if (c.primaryKey()) isPk = true;
                if (c.ignore()) col.setIgnored(true);
            } else if (name.endsWith(".JoColumn") || name.endsWith(".Column")) {
                String n = getAnnotationString(ann, "value");
                if (n == null || n.isEmpty()) {
                    n = getAnnotationString(ann, "name");
                }
                if (n != null && !n.isEmpty()) colName = n;
            } else if (ann instanceof JoId || name.endsWith(".JoId") || name.endsWith(".Id")) {
                isPk = true;
            } else if (ann instanceof JoGeneratedValue) {
                JoGeneratedValue gv = (JoGeneratedValue) ann;
                col.setGenerated(true);
                col.setGenerationType(gv.strategy());
            } else if (name.endsWith(".JoGeneratedValue") || name.endsWith(".GeneratedValue")) {
                col.setGenerated(true);
            } else if (ann instanceof JoLogicDelete) {
                JoLogicDelete ld = (JoLogicDelete) ann;
                col.setLogicDelete(true);
                String norm = !ld.value().isEmpty() ? ld.value() : ld.normalValue();
                String del = !ld.delval().isEmpty() ? ld.delval() : ld.deletedValue();
                col.setLogicDeleteNormalValue(norm);
                col.setLogicDeleteDeletedValue(del);
            } else if (ann instanceof JoTenantId) {
                col.setTenant(true);
            } else if (ann instanceof JoAutoFill) {
                JoAutoFill af = (JoAutoFill) ann;
                col.setAutoFill(true);
                AutoFillType fillType = af.fillType() != AutoFillType.INSERT ? af.fillType() : af.value();
                col.setAutoFillType(fillType);
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

    public static boolean hasEntityAnnotation(Class<?> clazz) {
        if (clazz == null || clazz.isPrimitive() || clazz.getName().startsWith("java.")) return false;
        for (Annotation ann : clazz.getAnnotations()) {
            String name = ann.annotationType().getName();
            if (name.endsWith(".Entity") || name.endsWith(".Table") || name.endsWith(".JoTable") || ann instanceof JoTable) {
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
