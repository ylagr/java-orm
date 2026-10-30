package com.javaorm.core.metadata;

import com.javaorm.core.annotation.RelationType;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class RelationMetadata {
    private String propertyName;
    private RelationType relationType = RelationType.MANY_TO_ONE;
    private Class<?> targetEntityClass;
    private String selfProperty;
    private String targetProperty;
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
            throw new RuntimeException("Failed to get relation value for " + propertyName, e);
        }
        return null;
    }

    public void setValue(Object entity, Object value) {
        if (entity == null) {
            return;
        }
        try {
            if (setter != null) {
                setter.invoke(entity, value);
                return;
            }
            if (field != null) {
                field.setAccessible(true);
                field.set(entity, value);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to set relation value for " + propertyName, e);
        }
    }

    public String getPropertyName() {
        return propertyName;
    }

    public void setPropertyName(String propertyName) {
        this.propertyName = propertyName;
    }

    public RelationType getRelationType() {
        return relationType;
    }

    public void setRelationType(RelationType relationType) {
        this.relationType = relationType;
    }

    public Class<?> getTargetEntityClass() {
        return targetEntityClass;
    }

    public void setTargetEntityClass(Class<?> targetEntityClass) {
        this.targetEntityClass = targetEntityClass;
    }

    public String getSelfProperty() {
        return selfProperty;
    }

    public void setSelfProperty(String selfProperty) {
        this.selfProperty = selfProperty;
    }

    public String getTargetProperty() {
        return targetProperty;
    }

    public void setTargetProperty(String targetProperty) {
        this.targetProperty = targetProperty;
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
