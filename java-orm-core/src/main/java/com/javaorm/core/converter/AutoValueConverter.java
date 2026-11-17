package com.javaorm.core.converter;

import com.javaorm.core.metadata.ColumnMetadata;

/**
 * 全局自动匹配转换器接口。
 * 命中条件的实体字段无需在字段上单独配置注解，系统自动匹配并执行双向转换。
 */
public interface AutoValueConverter<TProperty, TProvider> extends ValueConverter<TProperty, TProvider> {

    /**
     * 判断当前转换器是否适用于指定的列。
     *
     * @param entityClass  实体类
     * @param propertyName 属性名称
     * @param metadata     列元数据（包含 propertyType, genericType 等）
     * @return true 表示自动应用此转换器
     */
    boolean matches(Class<?> entityClass, String propertyName, ColumnMetadata metadata);
}
