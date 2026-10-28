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
    private boolean ignored;
    private Field field;
    private Method getter;
    private Method setter;

    public Object getValue(Object entity) {
        if (entity == null) {
            return null;
        }
        try {
            if (getter != null) {
                return getter.invoke(entity);
            }
            if (field != null) {
                field.setAccessible(true);
                return field.get(entity);
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
        try {
            if (setter != null) {
                setter.invoke(entity, convertValue(value, propertyType));
                return;
            }
            if (field != null) {
                field.setAccessible(true);
                field.set(entity, convertValue(value, propertyType));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to set value for property " + propertyName + " with value " + value, e);
        }
    }

    private Object convertValue(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }
        if (targetType.isInstance(value)) {
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

    // Getters and Setters
    public String getPropertyName() { return propertyName; }
    public void setPropertyName(String propertyName) { this.propertyName = propertyName; }

    public String getColumnName() { return columnName; }
    public void setColumnName(String columnName) { this.columnName = columnName; }

    public Class<?> getPropertyType() { return propertyType; }
    public void setPropertyType(Class<?> propertyType) { this.propertyType = propertyType; }

    public boolean isPrimaryKey() { return primaryKey; }
    public void setPrimaryKey(boolean primaryKey) { this.primaryKey = primaryKey; }

    public boolean isGenerated() { return generated; }
    public void setGenerated(boolean generated) { this.generated = generated; }

    public GenerationType getGenerationType() { return generationType; }
    public void setGenerationType(GenerationType generationType) { this.generationType = generationType; }

    public boolean isLogicDelete() { return logicDelete; }
    public void setLogicDelete(boolean logicDelete) { this.logicDelete = logicDelete; }

    public Object getLogicDeleteNormalValue() { return logicDeleteNormalValue; }
    public void setLogicDeleteNormalValue(Object logicDeleteNormalValue) { this.logicDeleteNormalValue = logicDeleteNormalValue; }

    public Object getLogicDeleteDeletedValue() { return logicDeleteDeletedValue; }
    public void setLogicDeleteDeletedValue(Object logicDeleteDeletedValue) { this.logicDeleteDeletedValue = logicDeleteDeletedValue; }

    public boolean isTenant() { return tenant; }
    public void setTenant(boolean tenant) { this.tenant = tenant; }

    public boolean isIgnored() { return ignored; }
    public void setIgnored(boolean ignored) { this.ignored = ignored; }

    public Field getField() { return field; }
    public void setField(Field field) { this.field = field; }

    public Method getGetter() { return getter; }
    public void setGetter(Method getter) { this.getter = getter; }

    public Method getSetter() { return setter; }
    public void setSetter(Method setter) { this.setter = setter; }
}
