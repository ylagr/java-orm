package com.javaorm.core.converter;

import com.javaorm.core.annotation.EnumMappingType;
import com.javaorm.core.annotation.JoEnum;
import com.javaorm.core.annotation.JoEnumValue;
import com.javaorm.core.fastbean.FastPropertyGetter;
import com.javaorm.core.fastbean.LambdaFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 枚举高性能元数据管理器。
 * 负责解析枚举类注解、动态生成无反射调用器并缓存查找表。
 */
public class EnumMetadataManager {

    private static final Map<String, EnumMetadata> ENUM_META_CACHE = new ConcurrentHashMap<>();

    /**
     * 获取指定枚举类型及其特定映射配置的元数据。
     */
    @SuppressWarnings("unchecked")
    public static EnumMetadata getMetadata(Class<?> enumClass, JoEnum joEnum) {
        if (enumClass == null || !enumClass.isEnum()) {
            return null;
        }

        Class<? extends Enum<?>> actualEnumClass = (Class<? extends Enum<?>>) enumClass;
        EnumMappingType mode = joEnum != null ? joEnum.mode() : EnumMappingType.AUTO;
        String prop = joEnum != null ? joEnum.property().trim() : "";
        String fallback = joEnum != null ? joEnum.fallback().trim() : "";

        String cacheKey = actualEnumClass.getName() + "#" + mode + "#" + prop + "#" + fallback;
        return ENUM_META_CACHE.computeIfAbsent(cacheKey, k -> buildMetadata(actualEnumClass, mode, prop, fallback));
    }

    private static EnumMetadata buildMetadata(Class<? extends Enum<?>> enumClass,
                                              EnumMappingType mode,
                                              String specifiedProp,
                                              String fallbackName) {
        Enum<?>[] constants = enumClass.getEnumConstants();
        if (constants == null) {
            constants = new Enum<?>[0];
        }

        // 解析 fallback 枚举实例
        Enum<?> fallbackEnum = null;
        if (!fallbackName.isEmpty()) {
            for (Enum<?> c : constants) {
                if (c.name().equalsIgnoreCase(fallbackName)) {
                    fallbackEnum = c;
                    break;
                }
            }
        }

        // 1. 若显式指定为 NAME 或 ORDINAL
        if (mode == EnumMappingType.NAME) {
            Map<Object, Enum<?>> lookup = new HashMap<>();
            for (Enum<?> c : constants) {
                lookup.put(c.name(), c);
            }
            return new EnumMetadata(enumClass, EnumMappingType.NAME, null, String.class, Collections.unmodifiableMap(lookup), null, fallbackEnum);
        }
        if (mode == EnumMappingType.ORDINAL) {
            Map<Object, Enum<?>> lookup = new HashMap<>();
            Enum<?>[] array = new Enum<?>[constants.length];
            for (int i = 0; i < constants.length; i++) {
                Enum<?> c = constants[i];
                lookup.put(c.ordinal(), c);
                array[i] = c;
            }
            return new EnumMetadata(enumClass, EnumMappingType.ORDINAL, null, Integer.class, Collections.unmodifiableMap(lookup), array, fallbackEnum);
        }

        // 2. 寻找目标属性或 Getter
        Field targetField = null;
        Method targetMethod = null;
        Class<?> valueType = null;

        // 2.1 若显式指定了 property
        if (!specifiedProp.isEmpty()) {
            targetField = findField(enumClass, specifiedProp);
            targetMethod = findGetter(enumClass, specifiedProp);
        } else {
            // 2.2 自动寻找标注了 @JoEnumValue 的字段或方法
            for (Field f : enumClass.getDeclaredFields()) {
                if (f.isAnnotationPresent(JoEnumValue.class)) {
                    targetField = f;
                    break;
                }
            }
            if (targetField == null) {
                for (Method m : enumClass.getDeclaredMethods()) {
                    if (m.isAnnotationPresent(JoEnumValue.class) && m.getParameterTypes().length == 0 && m.getReturnType() != void.class) {
                        targetMethod = m;
                        break;
                    }
                }
            }
        }

        // 2.3 若未找到任何属性，且 mode 为 AUTO，则默认退化为 NAME 模式
        if (targetField == null && targetMethod == null) {
            Map<Object, Enum<?>> lookup = new HashMap<>();
            for (Enum<?> c : constants) {
                lookup.put(c.name(), c);
            }
            return new EnumMetadata(enumClass, EnumMappingType.NAME, null, String.class, Collections.unmodifiableMap(lookup), null, fallbackEnum);
        }

        if (targetField != null) {
            valueType = targetField.getType();
            if (targetMethod == null) {
                targetMethod = findGetter(enumClass, targetField.getName());
            }
        } else {
            valueType = targetMethod.getReturnType();
        }

        // 3. 利用 LambdaFactory 生成零反射的高性能 Getter
        FastPropertyGetter fastGetter = LambdaFactory.createGetter(enumClass, targetMethod, targetField);

        // 4. 构建 O(1) 查找表与非负小整数快速数组
        Map<Object, Enum<?>> lookup = new HashMap<>();
        boolean canUseArray = true;
        int maxInt = -1;

        for (Enum<?> c : constants) {
            Object val = fastGetter.get(c);
            if (val != null) {
                lookup.put(val, c);
                if (canUseArray) {
                    if (val instanceof Number) {
                        long num = ((Number) val).longValue();
                        if (num >= 0 && num <= 128) {
                            if (num > maxInt) maxInt = (int) num;
                        } else {
                            canUseArray = false;
                        }
                    } else {
                        canUseArray = false;
                    }
                }
            }
        }

        Enum<?>[] arrayLookup = null;
        if (canUseArray && maxInt >= 0) {
            arrayLookup = new Enum<?>[maxInt + 1];
            for (Enum<?> c : constants) {
                Object val = fastGetter.get(c);
                if (val instanceof Number) {
                    int idx = ((Number) val).intValue();
                    if (idx >= 0 && idx <= maxInt) {
                        arrayLookup[idx] = c;
                    }
                }
            }
        }

        return new EnumMetadata(enumClass, EnumMappingType.PROPERTY, fastGetter, valueType, Collections.unmodifiableMap(lookup), arrayLookup, fallbackEnum);
    }

    private static Field findField(Class<?> clazz, String propName) {
        for (Field f : clazz.getDeclaredFields()) {
            if (f.getName().equals(propName) && !Modifier.isStatic(f.getModifiers())) {
                return f;
            }
        }
        return null;
    }

    private static Method findGetter(Class<?> clazz, String propName) {
        String capitalized = Character.toUpperCase(propName.charAt(0)) + propName.substring(1);
        String getMethodName = "get" + capitalized;
        String isMethodName = "is" + capitalized;
        for (Method m : clazz.getDeclaredMethods()) {
            if ((m.getName().equals(getMethodName) || m.getName().equals(isMethodName) || m.getName().equals(propName))
                    && m.getParameterTypes().length == 0 && m.getReturnType() != void.class) {
                return m;
            }
        }
        return null;
    }
}
