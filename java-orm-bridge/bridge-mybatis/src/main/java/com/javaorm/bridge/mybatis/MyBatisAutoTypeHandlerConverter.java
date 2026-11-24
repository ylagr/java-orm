package com.javaorm.bridge.mybatis;

import com.javaorm.core.converter.AutoValueConverter;
import com.javaorm.core.metadata.ColumnMetadata;
import org.apache.ibatis.type.TypeHandler;

/**
 * 支持全局自动按 JavaType 匹配的 MyBatis 类型转换器。
 * <p>
 * 当实体的某个属性类型与该转换器声明的 javaType 一致（或为其子类）时，
 * JavaORM 将自动为该属性应用此转换器，无需任何显式注解标注。
 * </p>
 *
 * @param <T> Java 实体属性类型
 */
public class MyBatisAutoTypeHandlerConverter<T> extends MyBatisTypeHandlerConverter<T> implements AutoValueConverter<T, Object> {

    public MyBatisAutoTypeHandlerConverter(TypeHandler<T> typeHandler, Class<?> javaType) {
        super(typeHandler, javaType);
    }

    @Override
    public boolean matches(Class<?> entityClass, String propertyName, ColumnMetadata metadata) {
        if (metadata == null || metadata.getPropertyType() == null || javaType == null) {
            return false;
        }
        return javaType.isAssignableFrom(metadata.getPropertyType());
    }
}
