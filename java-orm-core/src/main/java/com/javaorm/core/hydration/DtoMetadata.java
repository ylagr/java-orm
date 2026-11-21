package com.javaorm.core.hydration;

import com.javaorm.core.bridge.MetadataBridgeRegistry;
import com.javaorm.core.bridge.RelationInfo;
import com.javaorm.core.fastbean.FastBean;
import com.javaorm.core.fastbean.FastPropertyGetter;
import com.javaorm.core.fastbean.FastPropertySetter;
import com.javaorm.core.fastbean.LambdaFactory;
import com.javaorm.core.metadata.ColumnMetadata;
import com.javaorm.core.metadata.EntityMetadata;
import com.javaorm.core.metadata.RelationMetadata;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DTO 高性能无反射元模型（基于 LambdaMetafactory / FastBean）。
 * 针对 selectAutoInclude 的高频装配场景，在首次访问时将 DTO 的所有属性读取、写入与关联关系预编译为直接指令。
 */
public class DtoMetadata {

    private static final Map<String, DtoMetadata> DTO_CACHE = new ConcurrentHashMap<>();

    private final Class<?> dtoClass;
    private final FastBean<?> fastBean;
    private final Map<String, DtoProperty> propertyMap = new HashMap<>();
    private final List<DtoProperty> allProperties = new ArrayList<>();
    private final List<DtoProperty> scalarProperties = new ArrayList<>();
    private final List<DtoProperty> relationProperties = new ArrayList<>();

    public static DtoMetadata get(Class<?> dtoClass, EntityMetadata rootMetadata) {
        String key = dtoClass.getName() + "@" + (rootMetadata != null ? rootMetadata.getEntityClass().getName() : "NONE");
        return DTO_CACHE.computeIfAbsent(key, k -> new DtoMetadata(dtoClass, rootMetadata));
    }

    @SuppressWarnings("unchecked")
    private DtoMetadata(Class<?> dtoClass, EntityMetadata rootMetadata) {
        this.dtoClass = dtoClass;
        this.fastBean = LambdaFactory.getFastBean((Class<Object>) dtoClass);

        Map<String, Field> fields = new LinkedHashMap<>();
        Class<?> curr = dtoClass;
        while (curr != null && curr != Object.class) {
            for (Field f : curr.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || Modifier.isTransient(f.getModifiers())) {
                    continue;
                }
                fields.putIfAbsent(f.getName(), f);
            }
            curr = curr.getSuperclass();
        }

        for (Map.Entry<String, Field> entry : fields.entrySet()) {
            String propName = entry.getKey();
            Field field = entry.getValue();
            Class<?> propType = field.getType();

            FastPropertySetter setter = fastBean.getSetter(propName);
            FastPropertyGetter getter = fastBean.getGetter(propName);

            // 识别关联关系：优先从 DTO 自身标注的桥接注解识别，其次从根实体元数据同名关系匹配
            RelationMetadata relation = null;
            RelationInfo bridgeInfo = MetadataBridgeRegistry.resolveRelation(dtoClass, field, null);
            if (bridgeInfo != null) {
                relation = new RelationMetadata();
                relation.setPropertyName(propName);
                relation.setRelationType(bridgeInfo.getRelationType());
                relation.setSelfProperty(bridgeInfo.getSelfProperty());
                relation.setTargetProperty(bridgeInfo.getTargetProperty());
                relation.setMappingClass(bridgeInfo.getMappingClass());
                relation.setSelfMappingProperty(bridgeInfo.getSelfMappingProperty());
                relation.setTargetMappingProperty(bridgeInfo.getTargetMappingProperty());
                relation.setExtraFilter(bridgeInfo.getExtraFilter());
                Class<?> targetEntity = bridgeInfo.getTargetEntityClass();
                if (targetEntity == null) {
                    if (Collection.class.isAssignableFrom(propType)) {
                        targetEntity = getCollectionElementType(field);
                    } else {
                        targetEntity = propType;
                    }
                }
                relation.setTargetEntityClass(targetEntity);
            } else if (rootMetadata != null) {
                relation = rootMetadata.getRelation(propName);
                if (relation == null) {
                    for (RelationMetadata r : rootMetadata.getRelations()) {
                        if (r.getPropertyName() != null && r.getPropertyName().equalsIgnoreCase(propName)) {
                            relation = r;
                            break;
                        }
                    }
                }
            }

            boolean isCollection = Collection.class.isAssignableFrom(propType);
            Class<?> collectionElementType = isCollection ? getCollectionElementType(field) : null;

            DtoProperty prop = new DtoProperty(
                    propName,
                    propType,
                    field,
                    setter,
                    getter,
                    relation,
                    isCollection,
                    collectionElementType
            );

            allProperties.add(prop);
            propertyMap.put(propName, prop);
            propertyMap.put(propName.toLowerCase(), prop);
            String underscore = toUnderscore(propName);
            if (!underscore.equals(propName)) {
                propertyMap.put(underscore, prop);
                propertyMap.put(underscore.toLowerCase(), prop);
            }

            if (prop.isRelation()) {
                relationProperties.add(prop);
            } else {
                scalarProperties.add(prop);
            }
        }
    }

    public Class<?> getDtoClass() {
        return dtoClass;
    }

    public FastBean<?> getFastBean() {
        return fastBean;
    }

    public Object createInstance() {
        return fastBean.create();
    }

    public DtoProperty getProperty(String name) {
        if (name == null) return null;
        DtoProperty prop = propertyMap.get(name);
        if (prop != null) return prop;
        prop = propertyMap.get(name.toLowerCase());
        if (prop != null) return prop;
        String und = toUnderscore(name);
        prop = propertyMap.get(und);
        if (prop != null) return prop;
        return propertyMap.get(und.toLowerCase());
    }

    public List<DtoProperty> getAllProperties() {
        return allProperties;
    }

    public List<DtoProperty> getScalarProperties() {
        return scalarProperties;
    }

    public List<DtoProperty> getRelationProperties() {
        return relationProperties;
    }

    private static Class<?> getCollectionElementType(Field field) {
        Type genericType = field.getGenericType();
        if (genericType instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) genericType;
            Type[] args = pt.getActualTypeArguments();
            if (args.length > 0 && args[0] instanceof Class) {
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

    /**
     * DTO 属性元信息（持有直接字节码调用器 FastPropertySetter 与 FastPropertyGetter）。
     */
    public static class DtoProperty {
        private final String propertyName;
        private final Class<?> propertyType;
        private final Field field;
        private final FastPropertySetter fastSetter;
        private final FastPropertyGetter fastGetter;
        private final RelationMetadata relation;
        private final boolean isCollection;
        private final Class<?> collectionElementType;

        public DtoProperty(String propertyName,
                           Class<?> propertyType,
                           Field field,
                           FastPropertySetter fastSetter,
                           FastPropertyGetter fastGetter,
                           RelationMetadata relation,
                           boolean isCollection,
                           Class<?> collectionElementType) {
            this.propertyName = propertyName;
            this.propertyType = propertyType;
            this.field = field;
            this.fastSetter = fastSetter;
            this.fastGetter = fastGetter;
            this.relation = relation;
            this.isCollection = isCollection;
            this.collectionElementType = collectionElementType;
        }

        public String getPropertyName() {
            return propertyName;
        }

        public Class<?> getPropertyType() {
            return propertyType;
        }

        public Field getField() {
            return field;
        }

        public FastPropertySetter getFastSetter() {
            return fastSetter;
        }

        public FastPropertyGetter getFastGetter() {
            return fastGetter;
        }

        public RelationMetadata getRelation() {
            return relation;
        }

        public boolean isRelation() {
            return relation != null;
        }

        public boolean isCollection() {
            return isCollection;
        }

        public Class<?> getCollectionElementType() {
            return collectionElementType;
        }

        public void setValue(Object target, Object value) {
            if (target == null) return;
            if (value != null && isCollection && value instanceof List) {
                if (Set.class.isAssignableFrom(propertyType)) {
                    value = new LinkedHashSet<>((List<?>) value);
                }
            }
            if (fastSetter != null) {
                fastSetter.set(target, ColumnMetadata.convertValue(propertyType, value));
                return;
            }
            // 极端兜底（私有字段无 setter）
            try {
                field.setAccessible(true);
                field.set(target, ColumnMetadata.convertValue(propertyType, value));
            } catch (Exception ignored) {
            }
        }

        public Object getValue(Object target) {
            if (target == null) return null;
            if (fastGetter != null) {
                return fastGetter.get(target);
            }
            // 极端兜底
            try {
                field.setAccessible(true);
                return field.get(target);
            } catch (Exception ignored) {
                return null;
            }
        }
    }
}
