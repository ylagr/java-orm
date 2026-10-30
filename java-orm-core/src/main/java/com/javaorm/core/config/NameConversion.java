package com.javaorm.core.config;

/**
 * 属性名与数据库列名/表名的命名转换策略接口（参考 Easy-Query）。
 */
public interface NameConversion {

    /**
     * 将实体属性名转换为数据库列名
     *
     * @param propertyName 实体类属性名（如 studentName）
     * @return 数据库列名（如 student_name）
     */
    String convert(String propertyName);

    /**
     * 将实体类名转换为数据库表名
     *
     * @param entityClassName 实体类简单名称（如 Student）
     * @return 数据库表名（如 student 或 t_student）
     */
    String convertTableName(String entityClassName);
}
