package com.javaorm.core.bridge;

import com.javaorm.core.annotation.*;
import com.javaorm.core.converter.*;
import com.javaorm.core.sharding.JoShardingKey;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * JavaORM 内置核心注解桥接器（优先级 0，最高）。
 * 负责解析 @JoXxx 系列注解及标准 JPA 兼容注解。
 */
public class JoMetadataBridge implements MetadataBridge {

    @Override
    public int getOrder() {
        return 0; // 最高优先级
    }

    @Override
    public TableInfo resolveTable(Class<?> entityClass) {
        if (entityClass == null) return null;
        TableInfo tableInfo = null;
        for (Annotation ann : entityClass.getAnnotations()) {
            if (ann instanceof JoTable) {
                JoTable t = (JoTable) ann;
                String name = !t.value().isEmpty() ? t.value() : t.name();
                tableInfo = new TableInfo(name, t.schema(), t.comment());
                break;
            }
            String annName = ann.annotationType().getName();
            if (annName.endsWith(".JoTable") || annName.endsWith(".Table")) {
                String name = getAnnotationString(ann, "value");
                if (name == null || name.isEmpty()) {
                    name = getAnnotationString(ann, "name");
                }
                String schema = getAnnotationString(ann, "schema");
                String comment = getAnnotationString(ann, "comment");
                tableInfo = new TableInfo(name, schema, comment);
                break;
            }
        }
        if (tableInfo == null) {
            tableInfo = new TableInfo();
        }

        // 解析类级别 @JoIndex 与 @JoIndexes
        for (com.javaorm.core.annotation.JoIndex idx : entityClass.getAnnotationsByType(com.javaorm.core.annotation.JoIndex.class)) {
            tableInfo.addIndex(new com.javaorm.core.metadata.IndexMetadata(idx.name(), java.util.Arrays.asList(idx.columns()), idx.unique()));
        }

        // 解析第三方/通用 @Index 与 @Indexes 注解（如 EasyQuery、MyBatis 衍生或自定义 @Index）
        for (Annotation ann : entityClass.getAnnotations()) {
            String sName = ann.annotationType().getSimpleName();
            if ("Index".equals(sName) && !(ann instanceof com.javaorm.core.annotation.JoIndex)) {
                com.javaorm.core.metadata.IndexMetadata im = parseGenericIndex(ann);
                if (im != null) tableInfo.addIndex(im);
            } else if ("Indexes".equals(sName) && !(ann instanceof com.javaorm.core.annotation.JoIndexes)) {
                try {
                    Method valM = ann.annotationType().getMethod("value");
                    Object arr = valM.invoke(ann);
                    if (arr instanceof Annotation[]) {
                        for (Annotation sub : (Annotation[]) arr) {
                            com.javaorm.core.metadata.IndexMetadata im = parseGenericIndex(sub);
                            if (im != null) tableInfo.addIndex(im);
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
        }

        return (tableInfo.getTableName() != null || !tableInfo.getIndexes().isEmpty()) ? tableInfo : null;
    }

    private com.javaorm.core.metadata.IndexMetadata parseGenericIndex(Annotation ann) {
        try {
            String name = getAnnotationString(ann, "name");
            Boolean unique = getAnnotationBoolean(ann, "unique");
            boolean isUnique = unique != null && unique;

            java.util.List<String> cols = new java.util.ArrayList<>();
            Object colObj = getAnnotationObject(ann, "columns");
            if (colObj == null) colObj = getAnnotationObject(ann, "columnNames");
            if (colObj == null) colObj = getAnnotationObject(ann, "columnList");
            if (colObj instanceof String[]) {
                java.util.Collections.addAll(cols, (String[]) colObj);
            } else if (colObj instanceof String) {
                for (String part : ((String) colObj).split(",")) {
                    String c = part.trim().split("\\s+")[0].trim();
                    if (!c.isEmpty()) cols.add(c);
                }
            }
            if (!cols.isEmpty() || (name != null && !name.isEmpty())) {
                return new com.javaorm.core.metadata.IndexMetadata(name, cols, isUnique);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Object getAnnotationObject(Annotation ann, String methodName) {
        try {
            Method m = ann.annotationType().getMethod(methodName);
            return m.invoke(ann);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public ColumnInfo resolveColumn(Class<?> entityClass, Field field, Method getter) {
        Annotation[] annotations = collectAnnotations(field, getter);
        ColumnInfo info = null;

        for (Annotation ann : annotations) {
            String name = ann.annotationType().getName();

            // 1. @JoColumn
            if (ann instanceof JoColumn) {
                if (info == null) info = new ColumnInfo();
                JoColumn c = (JoColumn) ann;
                String colName = !c.value().isEmpty() ? c.value() : c.name();
                if (!colName.isEmpty()) info.setColumnName(colName);
                if (c.primaryKey()) info.setPrimaryKey(true);
                if (c.ignore()) info.setIgnored(true);
                info.setNullable(c.nullable());
                info.setLength(c.length());
                info.setPrecision(c.precision());
                info.setScale(c.scale());
                if (!c.columnDefinition().isEmpty()) info.setColumnDefinition(c.columnDefinition());
                if (!c.comment().isEmpty()) info.setComment(c.comment());
                if (!c.defaultValue().isEmpty()) info.setDefaultValue(c.defaultValue());
            } else if (name.endsWith(".JoColumn") || name.endsWith(".Column")) {
                if (info == null) info = new ColumnInfo();
                String colName = getAnnotationString(ann, "value");
                if (colName == null || colName.isEmpty()) {
                    colName = getAnnotationString(ann, "name");
                }
                if (colName != null && !colName.isEmpty()) info.setColumnName(colName);
                Boolean nullable = getAnnotationBoolean(ann, "nullable");
                if (nullable != null) info.setNullable(nullable);
                Integer length = getAnnotationInt(ann, "length");
                if (length != null) info.setLength(length);
                Integer precision = getAnnotationInt(ann, "precision");
                if (precision != null) info.setPrecision(precision);
                Integer scale = getAnnotationInt(ann, "scale");
                if (scale != null) info.setScale(scale);
                String def = getAnnotationString(ann, "columnDefinition");
                if (def != null && !def.isEmpty()) info.setColumnDefinition(def);
                String cmt = getAnnotationString(ann, "comment");
                if (cmt != null && !cmt.isEmpty()) info.setComment(cmt);
            }

            // 2. @JoId / @Id
            if (ann instanceof JoId || name.endsWith(".JoId") || name.endsWith(".Id")) {
                if (info == null) info = new ColumnInfo();
                info.setPrimaryKey(true);
            }

            // 3. @JoGeneratedValue / @GeneratedValue
            if (ann instanceof JoGeneratedValue) {
                if (info == null) info = new ColumnInfo();
                info.setGenerated(true);
                info.setGenerationType(((JoGeneratedValue) ann).strategy());
            } else if (name.endsWith(".JoGeneratedValue") || name.endsWith(".GeneratedValue")) {
                if (info == null) info = new ColumnInfo();
                info.setGenerated(true);
            }

            // 4. @JoVersion / @Version (JPA)
            if (ann instanceof JoVersion || name.endsWith(".JoVersion") || name.endsWith(".Version")) {
                if (info == null) info = new ColumnInfo();
                info.setVersion(true);
            }

            // 5. @JoLogicDelete
            if (ann instanceof JoLogicDelete) {
                if (info == null) info = new ColumnInfo();
                JoLogicDelete ld = (JoLogicDelete) ann;
                info.setLogicDelete(true);
                String norm = !ld.value().isEmpty() ? ld.value() : ld.normalValue();
                String del = !ld.delval().isEmpty() ? ld.delval() : ld.deletedValue();
                info.setLogicDeleteNormalValue(norm);
                info.setLogicDeleteDeletedValue(del);
            }

            // 6. @JoTenantId
            if (ann instanceof JoTenantId || name.endsWith(".JoTenantId") || name.endsWith(".TenantId")) {
                if (info == null) info = new ColumnInfo();
                info.setTenant(true);
            }

            // 7. @JoAutoFill
            if (ann instanceof JoAutoFill) {
                if (info == null) info = new ColumnInfo();
                JoAutoFill af = (JoAutoFill) ann;
                info.setAutoFill(true);
                AutoFillType fillType = af.fillType() != AutoFillType.INSERT ? af.fillType() : af.value();
                info.setAutoFillType(fillType);
            }

            // 8. @JoShardingKey
            if (ann instanceof JoShardingKey || name.endsWith(".JoShardingKey") || name.endsWith(".ShardingKey")) {
                if (info == null) info = new ColumnInfo();
                info.setShardingKey(true);
            }

            // 9. 转换器、JSON、加密、枚举
            if (ann instanceof JoConverter) {
                if (info == null) info = new ColumnInfo();
                Class<? extends ValueConverter<?, ?>> convCls = ((JoConverter) ann).value();
                try {
                    info.setConverter(convCls.getDeclaredConstructor().newInstance());
                } catch (Exception e) {
                    throw new RuntimeException("Failed to instantiate ValueConverter: " + convCls.getName(), e);
                }
            } else if (ann instanceof JoEncrypt) {
                if (info == null) info = new ColumnInfo();
                Class<? extends EncryptionStrategy> stratCls = ((JoEncrypt) ann).value();
                try {
                    EncryptionStrategy strat = stratCls.getDeclaredConstructor().newInstance();
                    info.setConverter(new EncryptionValueConverter(strat));
                } catch (Exception e) {
                    throw new RuntimeException("Failed to instantiate EncryptionStrategy: " + stratCls.getName(), e);
                }
            } else if (ann instanceof JoJson) {
                if (info == null) info = new ColumnInfo();
                JoJson jj = (JoJson) ann;
                info.setConverter(new JsonValueConverter(jj.provider()));
            } else if (ann instanceof JoEnum) {
                if (info == null) info = new ColumnInfo();
                JoEnum je = (JoEnum) ann;
                if (field.getType().isEnum()) {
                    EnumMetadata em = EnumMetadataManager.getMetadata(field.getType(), je);
                    info.setConverter(new EnumConverter(em));
                }
            }
        }

        return info;
    }

    @Override
    public Boolean resolveValueObject(Class<?> entityClass, Field field, Method getter) {
        Annotation[] annotations = collectAnnotations(field, getter);
        for (Annotation ann : annotations) {
            if (ann instanceof JoValueObject) return true;
            String name = ann.annotationType().getName();
            if (name.endsWith(".JoValueObject") || name.endsWith(".ValueObject") || name.endsWith(".Embedded")) {
                return true;
            }
        }
        return null;
    }

    @Override
    public RelationInfo resolveRelation(Class<?> entityClass, Field field, Method getter) {
        Annotation[] annotations = collectAnnotations(field, getter);
        boolean isRelation = false;
        RelationType relType = RelationType.MANY_TO_ONE;
        String selfProp = "";
        String targetProp = "";
        Class<?> targetEntityClass = null;
        Class<?> mappingClass = null;
        String selfMappingProp = "";
        String targetMappingProp = "";
        String extraFilter = null;

        Class<?> propType = field != null ? field.getType() : (getter != null ? getter.getReturnType() : null);
        boolean isCollection = propType != null && java.util.Collection.class.isAssignableFrom(propType);

        for (Annotation ann : annotations) {
            String annName = ann.annotationType().getName();

            // 1. JavaORM 专有注解 @JoNavigate
            if (ann instanceof JoNavigate) {
                JoNavigate nav = (JoNavigate) ann;
                isRelation = true;
                relType = nav.value();
                selfProp = nav.selfProperty();
                targetProp = nav.targetProperty();
                break;
            }

            // 2. EasyQuery 专有注解 @Navigate (支持同名与全限定名)
            if (annName.endsWith("Navigate")) {
                isRelation = true;
                Object valObj = getAnnotationObject(ann, "value");
                if (valObj != null) {
                    String enumName = valObj.toString();
                    if (enumName.contains("OneToOne")) {
                        relType = RelationType.ONE_TO_ONE;
                    } else if (enumName.contains("OneToMany")) {
                        relType = RelationType.ONE_TO_MANY;
                    } else if (enumName.contains("ManyToMany")) {
                        relType = RelationType.MANY_TO_MANY;
                    } else {
                        relType = RelationType.MANY_TO_ONE;
                    }
                }
                String sp = getAnnotationString(ann, "selfProperty");
                if (sp != null && !sp.isEmpty()) selfProp = sp;
                String tp = getAnnotationString(ann, "targetProperty");
                if (tp != null && !tp.isEmpty()) targetProp = tp;

                Object mc = getAnnotationObject(ann, "mappingClass");
                if (mc instanceof Class && mc != void.class && mc != Object.class) {
                    mappingClass = (Class<?>) mc;
                }
                String smp = getAnnotationString(ann, "selfMappingProperty");
                if (smp != null && !smp.isEmpty()) selfMappingProp = smp;
                String tmp = getAnnotationString(ann, "targetMappingProperty");
                if (tmp != null && !tmp.isEmpty()) targetMappingProp = tmp;
                String extra = getAnnotationString(ann, "extraFilter");
                if (extra != null && !extra.isEmpty()) extraFilter = extra;
                break;
            }

            // 3. JPA 关系注解 (@ManyToOne, @OneToMany, @OneToOne, @ManyToMany)
            if (annName.endsWith("ManyToOne")) {
                isRelation = true;
                relType = RelationType.MANY_TO_ONE;
                Object te = getAnnotationObject(ann, "targetEntity");
                if (te instanceof Class && te != void.class) targetEntityClass = (Class<?>) te;
            } else if (annName.endsWith("OneToMany")) {
                isRelation = true;
                relType = RelationType.ONE_TO_MANY;
                String mappedBy = getAnnotationString(ann, "mappedBy");
                if (mappedBy != null && !mappedBy.isEmpty()) targetProp = mappedBy;
                Object te = getAnnotationObject(ann, "targetEntity");
                if (te instanceof Class && te != void.class) targetEntityClass = (Class<?>) te;
            } else if (annName.endsWith("OneToOne")) {
                isRelation = true;
                relType = RelationType.ONE_TO_ONE;
                String mappedBy = getAnnotationString(ann, "mappedBy");
                if (mappedBy != null && !mappedBy.isEmpty()) targetProp = mappedBy;
                Object te = getAnnotationObject(ann, "targetEntity");
                if (te instanceof Class && te != void.class) targetEntityClass = (Class<?>) te;
            } else if (annName.endsWith("ManyToMany")) {
                isRelation = true;
                relType = RelationType.MANY_TO_MANY;
                Object te = getAnnotationObject(ann, "targetEntity");
                if (te instanceof Class && te != void.class) targetEntityClass = (Class<?>) te;
            }

            // 4. JPA @JoinColumn
            if (annName.endsWith("JoinColumn")) {
                String joinCol = getAnnotationString(ann, "name");
                String refCol = getAnnotationString(ann, "referencedColumnName");
                if (joinCol != null && !joinCol.isEmpty() && selfProp.isEmpty()) {
                    selfProp = joinCol;
                }
                if (refCol != null && !refCol.isEmpty() && targetProp.isEmpty()) {
                    targetProp = refCol;
                }
            }
        }

        if (!isRelation) {
            return null;
        }

        if (targetEntityClass == null) {
            Class<?> rawTarget = isCollection ? getGenericType(field, getter) : propType;
            if (rawTarget != null) {
                if (com.javaorm.core.hydration.AutoIncludeProcessor.isEntityClass(rawTarget)) {
                    targetEntityClass = rawTarget;
                } else {
                    Class<?> inferred = com.javaorm.core.hydration.AutoIncludeProcessor.inferRootEntityClass(rawTarget, null);
                    targetEntityClass = inferred != null ? inferred : rawTarget;
                }
            }
        }

        String propName = field != null ? field.getName() : (getter != null ? getter.getName() : "");

        if (relType == RelationType.MANY_TO_ONE || relType == RelationType.ONE_TO_ONE) {
            if (selfProp.isEmpty()) {
                selfProp = propName + "Id";
            }
            if (targetProp.isEmpty()) {
                targetProp = "id";
            }
        } else if (relType == RelationType.ONE_TO_MANY) {
            if (selfProp.isEmpty()) {
                selfProp = "id";
            }
            if (targetProp.isEmpty() && entityClass != null) {
                String entitySimple = entityClass.getSimpleName();
                targetProp = Character.toLowerCase(entitySimple.charAt(0)) + entitySimple.substring(1) + "Id";
            }
        }

        RelationInfo info = new RelationInfo(relType, targetEntityClass, selfProp, targetProp);
        info.setMappingClass(mappingClass);
        info.setSelfMappingProperty(selfMappingProp);
        info.setTargetMappingProperty(targetMappingProp);
        info.setExtraFilter(extraFilter);
        return info;
    }

    private static Class<?> getGenericType(Field field, Method getter) {
        java.lang.reflect.Type genericType = null;
        if (field != null) {
            genericType = field.getGenericType();
        } else if (getter != null) {
            genericType = getter.getGenericReturnType();
        }
        if (genericType instanceof java.lang.reflect.ParameterizedType) {
            java.lang.reflect.ParameterizedType pt = (java.lang.reflect.ParameterizedType) genericType;
            java.lang.reflect.Type[] args = pt.getActualTypeArguments();
            if (args.length > 0 && args[0] instanceof Class) {
                return (Class<?>) args[0];
            }
        }
        return null;
    }

    private static Annotation[] collectAnnotations(Field field, Method getter) {
        Annotation[] fa = field != null ? field.getAnnotations() : new Annotation[0];
        Annotation[] ga = getter != null ? getter.getAnnotations() : new Annotation[0];
        if (fa.length == 0) return ga;
        if (ga.length == 0) return fa;
        Annotation[] result = new Annotation[fa.length + ga.length];
        System.arraycopy(fa, 0, result, 0, fa.length);
        System.arraycopy(ga, 0, result, fa.length, ga.length);
        return result;
    }

    private static String getAnnotationString(Annotation ann, String methodName) {
        try {
            Method m = ann.annotationType().getMethod(methodName);
            Object v = m.invoke(ann);
            return v != null ? v.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static Boolean getAnnotationBoolean(Annotation ann, String methodName) {
        try {
            Method m = ann.annotationType().getMethod(methodName);
            Object v = m.invoke(ann);
            if (v instanceof Boolean) return (Boolean) v;
            return v != null ? Boolean.valueOf(v.toString()) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer getAnnotationInt(Annotation ann, String methodName) {
        try {
            Method m = ann.annotationType().getMethod(methodName);
            Object v = m.invoke(ann);
            if (v instanceof Number) return ((Number) v).intValue();
            return v != null ? Integer.valueOf(v.toString()) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
