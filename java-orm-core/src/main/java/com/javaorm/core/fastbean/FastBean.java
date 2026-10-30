package com.javaorm.core.fastbean;

import com.javaorm.core.metadata.ColumnMetadata;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 封装类级别的高性能快速访问入口（实例创建器、属性 Setters、属性 Getters）。
 *
 * @param <T> 实体/DTO 类型
 */
public class FastBean<T> {

    private final Class<T> beanClass;
    private final FastBeanCreator<T> creator;
    private final Map<String, FastPropertySetter> setters;
    private final Map<String, FastPropertyGetter> getters;
    private final Map<String, Class<?>> propertyTypes;

    public FastBean(Class<T> beanClass,
                    FastBeanCreator<T> creator,
                    Map<String, FastPropertySetter> setters,
                    Map<String, FastPropertyGetter> getters,
                    Map<String, Class<?>> propertyTypes) {
        this.beanClass = beanClass;
        this.creator = creator;
        this.setters = Collections.unmodifiableMap(new HashMap<>(setters));
        this.getters = Collections.unmodifiableMap(new HashMap<>(getters));
        this.propertyTypes = Collections.unmodifiableMap(new HashMap<>(propertyTypes));
    }

    public Class<T> getBeanClass() {
        return beanClass;
    }

    public FastBeanCreator<T> getCreator() {
        return creator;
    }

    public T create() {
        return creator != null ? creator.create() : null;
    }

    public FastPropertySetter getSetter(String name) {
        if (name == null) return null;
        return setters.get(name.toLowerCase());
    }

    public FastPropertyGetter getGetter(String name) {
        if (name == null) return null;
        return getters.get(name.toLowerCase());
    }

    public Class<?> getPropertyType(String name) {
        if (name == null) return null;
        return propertyTypes.get(name.toLowerCase());
    }

    public void setProperty(Object bean, String name, Object value) {
        if (bean == null || name == null) return;
        String key = name.toLowerCase();
        FastPropertySetter setter = setters.get(key);
        if (setter != null) {
            Class<?> type = propertyTypes.get(key);
            setter.set(bean, ColumnMetadata.convertValue(value, type));
        }
    }

    public Object getProperty(Object bean, String name) {
        if (bean == null || name == null) return null;
        FastPropertyGetter getter = getters.get(name.toLowerCase());
        return getter != null ? getter.get(bean) : null;
    }
}
