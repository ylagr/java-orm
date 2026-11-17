package com.javaorm.core.converter;

import com.javaorm.core.metadata.ColumnMetadata;

/**
 * 加密策略到 ValueConverter 的桥接实现。
 * 统一纳入 ValueConverter 转换管线。
 */
public class EncryptionValueConverter implements ValueConverter<Object, Object> {

    private final EncryptionStrategy strategy;

    public EncryptionValueConverter(EncryptionStrategy strategy) {
        this.strategy = strategy;
    }

    public EncryptionStrategy getStrategy() {
        return strategy;
    }

    @Override
    public Object serialize(Object value, ColumnMetadata metadata) {
        if (value == null) {
            return null;
        }
        String propName = metadata != null ? metadata.getPropertyName() : "";
        return strategy.encrypt(propName, value);
    }

    @Override
    public Object deserialize(Object dbValue, ColumnMetadata metadata) {
        if (dbValue == null) {
            return null;
        }
        String propName = metadata != null ? metadata.getPropertyName() : "";
        Object decrypted = strategy.decrypt(propName, dbValue);
        if (decrypted != null && metadata != null && metadata.getPropertyType() != null) {
            return ColumnMetadata.convertValue(decrypted, metadata.getPropertyType());
        }
        return decrypted;
    }
}
