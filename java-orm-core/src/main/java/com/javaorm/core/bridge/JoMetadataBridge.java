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
        for (Annotation ann : entityClass.getAnnotations()) {
            if (ann instanceof JoTable) {
                JoTable t = (JoTable) ann;
                String name = !t.value().isEmpty() ? t.value() : t.name();
                return new TableInfo(name, t.schema(), t.comment());
            }
            String annName = ann.annotationType().getName();
            if (annName.endsWith(".JoTable") || annName.endsWith(".Table")) {
                String name = getAnnotationString(ann, "value");
                if (name == null || name.isEmpty()) {
                    name = getAnnotationString(ann, "name");
                }
                String schema = getAnnotationString(ann, "schema");
                String comment = getAnnotationString(ann, "comment");
                return new TableInfo(name, schema, comment);
            }
        }
        return null;
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
