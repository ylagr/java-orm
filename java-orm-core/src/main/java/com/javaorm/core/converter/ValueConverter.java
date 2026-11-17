package com.javaorm.core.converter;

import com.javaorm.core.metadata.ColumnMetadata;

/**
 * 字段值转换器核心接口。
 * 定义实体属性（TProperty）与底层 JDBC/数据库存储值（TProvider）之间的双向转换。
 *
 * @param <TProperty> Java 实体属性类型
 * @param <TProvider> 数据库驱动/存储值类型（如 String, Integer, byte[] 等）
 */
public interface ValueConverter<TProperty, TProvider> {

    /**
     * 写入数据库前调用：将 Java 实体属性对象转换为数据库物理存储格式。
     *
     * @param value    Java 属性值（可能为 null）
     * @param metadata 当前列的元数据信息
     * @return 适合存入数据库的值
     */
    TProvider serialize(TProperty value, ColumnMetadata metadata);

    /**
     * 从数据库读取后调用：将数据库物理列值还原为 Java 实体属性对象。
     *
     * @param dbValue  数据库读取出来的原始物理值（可能为 null）
     * @param metadata 当前列的元数据信息（可获取 genericType 处理泛型）
     * @return 还原后的 Java 属性对象
     */
    TProperty deserialize(TProvider dbValue, ColumnMetadata metadata);
}
