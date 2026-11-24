package com.javaorm.bridge.mp;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.IEnum;
import com.javaorm.bridge.mybatis.MyBatisTypeHandlerConverter;
import com.javaorm.core.converter.ValueConverter;
import com.javaorm.core.metadata.ColumnMetadata;
import org.apache.ibatis.type.TypeHandler;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * MyBatis-Plus 枚举转换器。
 * <p>
 * 支持以下四级递进的枚举值映射策略：
 * 1. 显式实现 {@link IEnum} 接口（通过 {@code getValue()} 提取值）；
 * 2. 字段或方法标注 {@link EnumValue} 注解；
 * 3. 约定属性匹配：自动识别属性 {@code code} / 方法 {@code getCode()}，或属性 {@code value} / 方法 {@code getValue()}；
 * 4. 接入 MyBatis-Plus 官方 {@code MybatisEnumTypeHandler} 处理。
 * </p>
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
        if (valueGetter != null) {
            valueGetter.setAccessible(true);
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

    public static ValueConverter<?, Object> tryCreate(Class<?> clazz) {
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

        // 3. 约定属性匹配：检查是否有 code / getCode() 属性或方法
        try {
            Method m = enumClass.getMethod("getCode");
            return new MyBatisPlusEnumConverter(enumClass, m, null);
        } catch (NoSuchMethodException ignored) {
        }
        try {
            Field f = enumClass.getDeclaredField("code");
            return new MyBatisPlusEnumConverter(enumClass, null, f);
        } catch (NoSuchFieldException ignored) {
        }

        // 检查是否有 value / getValue() 属性或方法
        try {
            Method m = enumClass.getMethod("getValue");
            return new MyBatisPlusEnumConverter(enumClass, m, null);
        } catch (NoSuchMethodException ignored) {
        }
        try {
            Field f = enumClass.getDeclaredField("value");
            return new MyBatisPlusEnumConverter(enumClass, null, f);
        } catch (NoSuchFieldException ignored) {
        }

        // 4. 尝试利用 MyBatis-Plus 官方 MybatisEnumTypeHandler
        try {
            Class<?> mpHandlerClass = Class.forName("com.baomidou.mybatisplus.core.handlers.MybatisEnumTypeHandler");
            Constructor<?> ctor = mpHandlerClass.getConstructor(Class.class);
            @SuppressWarnings("unchecked")
            TypeHandler<Enum<?>> handler = (TypeHandler<Enum<?>>) ctor.newInstance(enumClass);
            return new MyBatisTypeHandlerConverter<>(handler, enumClass);
        } catch (Throwable ignored) {
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
