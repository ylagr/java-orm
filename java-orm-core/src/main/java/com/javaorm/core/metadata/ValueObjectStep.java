package com.javaorm.core.metadata;

import com.javaorm.core.fastbean.FastBeanCreator;
import com.javaorm.core.fastbean.FastPropertyGetter;
import com.javaorm.core.fastbean.FastPropertySetter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * ValueObject 在实体调用链中的单级跳转步骤（如 user -> address，或 address -> area）。
 */
public class ValueObjectStep {

    private final String propertyName;
    private final Class<?> valueObjectClass;
    private final Field field;
    private final Method getter;
    private final Method setter;
    private final FastPropertyGetter fastGetter;
    private final FastPropertySetter fastSetter;
    private final FastBeanCreator<?> creator;

    public ValueObjectStep(String propertyName, Class<?> valueObjectClass, Field field,
                           Method getter, Method setter,
                           FastPropertyGetter fastGetter, FastPropertySetter fastSetter,
                           FastBeanCreator<?> creator) {
        this.propertyName = propertyName;
        this.valueObjectClass = valueObjectClass;
        this.field = field;
        this.getter = getter;
        this.setter = setter;
        this.fastGetter = fastGetter;
        this.fastSetter = fastSetter;
        this.creator = creator;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public Class<?> getValueObjectClass() {
        return valueObjectClass;
    }

    public Object getValue(Object target) {
        if (target == null) return null;
        if (fastGetter != null) {
            try {
                return fastGetter.get(target);
            } catch (Exception ignored) {
            }
        }
        if (getter != null) {
            try {
                return getter.invoke(target);
            } catch (Exception ignored) {
            }
        }
        if (field != null) {
            try {
                return field.get(target);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public void setValue(Object target, Object value) {
        if (target == null) return;
        if (fastSetter != null) {
            try {
                fastSetter.set(target, value);
                return;
            } catch (Exception ignored) {
            }
        }
        if (setter != null) {
            try {
                setter.invoke(target, value);
                return;
            } catch (Exception ignored) {
            }
        }
        if (field != null) {
            try {
                field.set(target, value);
            } catch (Exception ignored) {
            }
        }
    }

    public Object newInstance() {
        if (creator != null) {
            try {
                return creator.create();
            } catch (Exception ignored) {
            }
        }
        try {
            return valueObjectClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate ValueObject: " + valueObjectClass.getName(), e);
        }
    }
}
