package com.javaorm.core.converter;

import com.javaorm.core.metadata.ColumnMetadata;

import java.lang.reflect.Type;

/**
 * 通用 JSON 字段值转换器。
 * 借助 JsonProvider 序列化为 JSON 字符串入库，出库时保留完整泛型类型进行反序列化。
 */
public class JsonValueConverter implements ValueConverter<Object, String> {

    private final Class<? extends JsonProvider> providerClass;

    public JsonValueConverter() {
        this(null);
    }

    public JsonValueConverter(Class<? extends JsonProvider> providerClass) {
        this.providerClass = providerClass;
    }

    public JsonProvider getProvider() {
        return JsonProviderManager.getProvider(providerClass);
    }

    @Override
    public String serialize(Object value, ColumnMetadata metadata) {
        if (value == null) {
            return null;
        }
        return getProvider().toJson(value);
    }

    @Override
    public Object deserialize(String dbValue, ColumnMetadata metadata) {
        if (dbValue == null || dbValue.trim().isEmpty()) {
            return null;
        }
        Type targetType = (metadata != null && metadata.getGenericType() != null)
                ? metadata.getGenericType()
                : (metadata != null ? metadata.getPropertyType() : Object.class);
        return getProvider().fromJson(dbValue, targetType);
    }
}
