package com.javaorm.core.config;

/**
 * 驼峰转大写下划线命名转换器（如 Oracle / DB2 常用）。
 * 例如：studentName -> STUDENT_NAME
 */
public class UpperUnderlinedNameConversion implements NameConversion {

    private final UnderlinedNameConversion underlined = new UnderlinedNameConversion();

    @Override
    public String convert(String propertyName) {
        String result = underlined.convert(propertyName);
        return result != null ? result.toUpperCase() : null;
    }

    @Override
    public String convertTableName(String entityClassName) {
        String result = underlined.convertTableName(entityClassName);
        return result != null ? result.toUpperCase() : null;
    }
}
