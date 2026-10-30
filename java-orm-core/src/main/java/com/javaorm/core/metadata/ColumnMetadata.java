package com.javaorm.core.metadata;

import com.javaorm.core.annotation.GenerationType;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ColumnMetadata {
    private String propertyName;
    private String columnName;
    private Class<?> propertyType;
    private boolean primaryKey;
    private boolean generated;
    private GenerationType generationType = GenerationType.IDENTITY;
    private boolean logicDelete;
    private Object logicDeleteNormalValue;
    private Object logicDeleteDeletedValue;
    private boolean tenant;
    private boolean autoFill;
    private com.javaorm.core.annotation.AutoFillType autoFillType;
    private boolean ignored;
    private Field field;
    private Method getter;
    private Method setter;
    private com.javaorm.core.fastbean.FastPropertySetter fastSetter;
    private com.javaorm.core.fastbean.FastPropertyGetter fastGetter;
    private java.util.List<ValueObjectStep> valueObjectSteps;

    public java.util.List<ValueObjectStep> getValueObjectSteps() {
        return valueObjectSteps;
    }

    public void setValueObjectSteps(java.util.List<ValueObjectStep> valueObjectSteps) {
        this.valueObjectSteps = valueObjectSteps;
    }

    public boolean isValueObjectProperty() {
        return valueObjectSteps != null && !valueObjectSteps.isEmpty();
    }

    public Object getValue(Object entity) {
        if (entity == null) {
            return null;
        }
        if (valueObjectSteps != null && !valueObjectSteps.isEmpty()) {
            Object current = entity;
            for (ValueObjectStep step : valueObjectSteps) {
                current = step.getValue(current);
                if (current == null) {
                    return null;
                }
            }
            return getLeafValue(current);
        }
        return getLeafValue(entity);
    }

    private Object getLeafValue(Object target) {
        if (target == null) return null;
        if (fastGetter != null) {
            try {
                return fastGetter.get(target);
            } catch (Exception ignored) {
            }
        }
        try {
            if (getter != null) {
                return getter.invoke(target);
            }
            if (field != null) {
                field.setAccessible(true);
                return field.get(target);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to get value for property " + propertyName, e);
        }
        return null;
    }

    public void setValue(Object entity, Object value) {
        if (entity == null) {
            return;
        }
        if (valueObjectSteps != null && !valueObjectSteps.isEmpty()) {
            // DDD 规则：如果从数据库查出值为 null，且宿主尚未创建该 VO，则不创建空壳对象
            if (value == null) {
                Object current = entity;
                for (int i = 0; i < valueObjectSteps.size(); i++) {
                    current = valueObjectSteps.get(i).getValue(current);
                    if (current == null) {
                        return;
                    }
                }
                setLeafValue(current, null);
                return;
            }

            // 值为非空：逐级确保父级 VO 实例化并挂载到宿主上
            Object current = entity;
            for (ValueObjectStep step : valueObjectSteps) {
                Object next = step.getValue(current);
                if (next == null) {
                    next = step.newInstance();
                    step.setValue(current, next);
                }
                current = next;
            }
            setLeafValue(current, value);
            return;
        }
        setLeafValue(entity, value);
    }

    private void setLeafValue(Object target, Object value) {
        if (target == null) return;
        Object converted = convertValue(value, propertyType);
        if (fastSetter != null) {
            try {
                fastSetter.set(target, converted);
                return;
            } catch (Exception ignored) {
            }
        }
        try {
            if (setter != null) {
                setter.invoke(target, converted);
                return;
            }
            if (field != null) {
                field.setAccessible(true);
                field.set(target, converted);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to set value for property " + propertyName + " with value " + value, e);
        }
    }

    public com.javaorm.core.fastbean.FastPropertySetter getFastSetter() {
        return fastSetter;
    }

    public void setFastSetter(com.javaorm.core.fastbean.FastPropertySetter fastSetter) {
        this.fastSetter = fastSetter;
    }

    public com.javaorm.core.fastbean.FastPropertyGetter getFastGetter() {
        return fastGetter;
    }

    public void setFastGetter(com.javaorm.core.fastbean.FastPropertyGetter fastGetter) {
        this.fastGetter = fastGetter;
    }

    public static Object convertValue(Class<?> targetType, Object value) {
        return convertValue(value, targetType);
    }

    public static Object convertValue(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }
        Class<?> valClass = value.getClass();
        if (targetType == valClass || targetType.isInstance(value)) {
            return value;
        }
        if (targetType == int.class && valClass == Integer.class) {
            return value;
        }
        if (targetType == long.class && valClass == Long.class) {
            return value;
        }
        if (targetType == double.class && valClass == Double.class) {
            return value;
        }
        if (targetType == float.class && valClass == Float.class) {
            return value;
        }
        if (targetType == boolean.class && valClass == Boolean.class) {
            return value;
        }
        if (targetType == Long.class || targetType == long.class) {
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            return Long.parseLong(value.toString());
        }
        if (targetType == Integer.class || targetType == int.class) {
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            return Integer.parseInt(value.toString());
        }
        if (targetType == Double.class || targetType == double.class) {
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
            return Double.parseDouble(value.toString());
        }
        if (targetType == Float.class || targetType == float.class) {
            if (value instanceof Number) {
                return ((Number) value).floatValue();
            }
            return Float.parseFloat(value.toString());
        }
        if (targetType == Boolean.class || targetType == boolean.class) {
            if (value instanceof Number) {
                return ((Number) value).intValue() != 0;
            }
            return Boolean.parseBoolean(value.toString());
        }
        if (targetType == String.class) {
            return value.toString();
        }
        if (targetType == java.time.LocalDateTime.class) {
            if (value instanceof java.sql.Timestamp) {
                return ((java.sql.Timestamp) value).toLocalDateTime();
            }
            if (value instanceof java.time.LocalDateTime) {
                return value;
            }
            if (value instanceof Number) {
                return java.time.LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(((Number) value).longValue()),
                        java.time.ZoneId.systemDefault());
            }
            String str = value.toString();
            if (str.contains("T")) {
                return java.time.LocalDateTime.parse(str);
            }
            return java.time.LocalDateTime.parse(str.replace(' ', 'T'));
        }
        if (targetType == java.time.LocalDate.class) {
            if (value instanceof java.sql.Date) {
                return ((java.sql.Date) value).toLocalDate();
            }
            if (value instanceof java.time.LocalDate) {
                return value;
            }
            return java.time.LocalDate.parse(value.toString());
        }
        if (targetType == java.util.Date.class) {
            if (value instanceof java.util.Date) {
                return value;
            }
            if (value instanceof Number) {
                return new java.util.Date(((Number) value).longValue());
            }
        }
        if (targetType == java.math.BigDecimal.class) {
            if (value instanceof java.math.BigDecimal) {
                return value;
            }
            return new java.math.BigDecimal(value.toString());
        }
        return value;
    }

    public static boolean isScalarType(Class<?> targetType) {
        if (targetType == null) return false;
        return targetType.isPrimitive()
                || Number.class.isAssignableFrom(targetType)
                || targetType == String.class
                || targetType == Boolean.class
                || targetType == Character.class
                || java.util.Date.class.isAssignableFrom(targetType)
                || java.time.temporal.Temporal.class.isAssignableFrom(targetType)
                || targetType.isEnum()
                || targetType == byte[].class
                || targetType == Byte[].class;
    }

    // Getters and Setters
    public String getPropertyName() {
        return propertyName;
    }

    public void setPropertyName(String propertyName) {
        this.propertyName = propertyName;
    }

    public String getColumnName() {
        return columnName;
    }

    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }

    public Class<?> getPropertyType() {
        return propertyType;
    }

    public void setPropertyType(Class<?> propertyType) {
        this.propertyType = propertyType;
    }

    public boolean isPrimaryKey() {
        return primaryKey;
    }

    public void setPrimaryKey(boolean primaryKey) {
        this.primaryKey = primaryKey;
    }

    public boolean isGenerated() {
        return generated;
    }

    public void setGenerated(boolean generated) {
        this.generated = generated;
    }

    public GenerationType getGenerationType() {
        return generationType;
    }

    public void setGenerationType(GenerationType generationType) {
        this.generationType = generationType;
    }

    public boolean isLogicDelete() {
        return logicDelete;
    }

    public void setLogicDelete(boolean logicDelete) {
        this.logicDelete = logicDelete;
    }

    public Object getLogicDeleteNormalValue() {
        return logicDeleteNormalValue;
    }

    public void setLogicDeleteNormalValue(Object logicDeleteNormalValue) {
        this.logicDeleteNormalValue = logicDeleteNormalValue;
    }

    public Object getLogicDeleteDeletedValue() {
        return logicDeleteDeletedValue;
    }

    public void setLogicDeleteDeletedValue(Object logicDeleteDeletedValue) {
        this.logicDeleteDeletedValue = logicDeleteDeletedValue;
    }

    public boolean isTenant() {
        return tenant;
    }

    public void setTenant(boolean tenant) {
        this.tenant = tenant;
    }

    public boolean isAutoFill() {
        return autoFill;
    }

    public void setAutoFill(boolean autoFill) {
        this.autoFill = autoFill;
    }

    public com.javaorm.core.annotation.AutoFillType getAutoFillType() {
        return autoFillType;
    }

    public void setAutoFillType(com.javaorm.core.annotation.AutoFillType autoFillType) {
        this.autoFillType = autoFillType;
    }

    public boolean isIgnored() {
        return ignored;
    }

    public void setIgnored(boolean ignored) {
        this.ignored = ignored;
    }

    public Field getField() {
        return field;
    }

    public void setField(Field field) {
        this.field = field;
    }

    public Method getGetter() {
        return getter;
    }

    public void setGetter(Method getter) {
        this.getter = getter;
    }

    public Method getSetter() {
        return setter;
    }

    public void setSetter(Method setter) {
        this.setter = setter;
    }
}
