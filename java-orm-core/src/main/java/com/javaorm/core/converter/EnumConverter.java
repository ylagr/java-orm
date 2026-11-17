package com.javaorm.core.converter;

import com.javaorm.core.metadata.ColumnMetadata;

/**
 * 通用高性能枚举转换器实现。
 * 依托 EnumMetadataManager，零反射读写数据库物理值。
 */
public class EnumConverter implements ValueConverter<Enum<?>, Object> {

    private final EnumMetadata enumMetadata;

    public EnumConverter(EnumMetadata enumMetadata) {
        this.enumMetadata = enumMetadata;
    }

    public EnumMetadata getEnumMetadata() {
        return enumMetadata;
    }

    @Override
    public Object serialize(Enum<?> value, ColumnMetadata metadata) {
        if (value == null) {
            return null;
        }
        return enumMetadata.toDbValue(value);
    }

    @Override
    public Enum<?> deserialize(Object dbValue, ColumnMetadata metadata) {
        if (dbValue == null) {
            return null;
        }
        return enumMetadata.toEnum(dbValue);
    }
}
