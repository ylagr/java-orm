package com.javaorm.bridge.mp;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.IEnum;
import com.javaorm.core.converter.ValueConverter;
import com.javaorm.core.metadata.ColumnMetadata;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * MyBatis-Plus 枚举转换器。
 * 支持两种模式：
 * 1. 实现 IEnum 接口（通过 getValue() 获取值）
 * 2. 字段标注 @EnumValue
 */
public class MyBatisPlusEnumConverter implements ValueConverter<Enum<?>, Object> {

    private final Class<? extends Enum<?>> enumClass;
    private final Method valueGetter;
    private final Field valueField;
    private final Map<Object, Enum<?>> dbToEnumMap = new HashMap<>();

    public MyBatisPlusEnumConverter(Class<? extends Enum<?>> enumClass, Method valueGetter, Field valueField) {
        this.enumClass = enumClass;
        this.valueGetter = valueGetter;
        this.valueField = valueField;
        if (valueField != null) {
            valueField.setAccessible(true);
        }

        // 预热缓存枚举映射表
        Enum<?>[] constants = enumClass.getEnumConstants();
        if (constants != null) {
            for (Enum<?> constant : constants) {
                Object dbVal = extractDbValue(constant);
                if (dbVal != null) {
                    dbToEnumMap.put(dbVal, constant);
                    dbToEnumMap.put(String.valueOf(dbVal), constant);
                }
            }
        }
    }

    public static MyBatisPlusEnumConverter tryCreate(Class<?> clazz) {
        if (clazz == null || !clazz.isEnum()) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Class<? extends Enum<?>> enumClass = (Class<? extends Enum<?>>) clazz;

        // 1. 检查是否实现 IEnum 接口
        if (IEnum.class.isAssignableFrom(enumClass)) {
            try {
                Method m = enumClass.getMethod("getValue");
                return new MyBatisPlusEnumConverter(enumClass, m, null);
            } catch (NoSuchMethodException ignored) {
            }
        }

        // 2. 检查是否有 @EnumValue 字段或方法
        for (Field f : enumClass.getDeclaredFields()) {
            if (f.isAnnotationPresent(EnumValue.class)) {
                return new MyBatisPlusEnumConverter(enumClass, null, f);
            }
        }
        for (Method m : enumClass.getDeclaredMethods()) {
            if (m.isAnnotationPresent(EnumValue.class)) {
                return new MyBatisPlusEnumConverter(enumClass, m, null);
            }
        }

        return null;
    }

    private Object extractDbValue(Enum<?> enumVal) {
        if (enumVal == null) return null;
        try {
            if (valueGetter != null) {
                return valueGetter.invoke(enumVal);
            }
            if (valueField != null) {
                return valueField.get(enumVal);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract DB value from enum: " + enumVal, e);
        }
        return enumVal.name();
    }

    @Override
    public Object serialize(Enum<?> value, ColumnMetadata metadata) {
        if (value == null) return null;
        return extractDbValue(value);
    }

    @Override
    public Enum<?> deserialize(Object dbValue, ColumnMetadata metadata) {
        if (dbValue == null) return null;
        Enum<?> match = dbToEnumMap.get(dbValue);
        if (match != null) return match;
        return dbToEnumMap.get(String.valueOf(dbValue));
    }
}
