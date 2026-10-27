package com.javaorm.core.config;

/**
 * 驼峰转下划线命名转换器（默认）。
 * 例如：studentName -> student_name, Student -> student
 */
public class UnderlinedNameConversion implements NameConversion {

    private final String tablePrefix;

    public UnderlinedNameConversion() {
        this("");
    }

    public UnderlinedNameConversion(String tablePrefix) {
        this.tablePrefix = tablePrefix == null ? "" : tablePrefix;
    }

    @Override
    public String convert(String propertyName) {
        if (propertyName == null || propertyName.isEmpty()) {
            return propertyName;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < propertyName.length(); i++) {
            char c = propertyName.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    @Override
    public String convertTableName(String entityClassName) {
        return tablePrefix + convert(entityClassName);
    }
}
