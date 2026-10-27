package com.javaorm.core.config;

/**
 * 默认原样命名转换器（不做转换）
 */
public class DefaultNameConversion implements NameConversion {

    @Override
    public String convert(String propertyName) {
        return propertyName;
    }

    @Override
    public String convertTableName(String entityClassName) {
        return entityClassName;
    }
}
