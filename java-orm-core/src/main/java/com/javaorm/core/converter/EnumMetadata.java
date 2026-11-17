package com.javaorm.core.converter;

import com.javaorm.core.annotation.EnumMappingType;
import com.javaorm.core.fastbean.FastPropertyGetter;

import java.util.Map;

/**
 * 枚举高性能元数据与零反射运行时调度器。
 * 针对枚举在启动期/初次使用时构建 Lambda 快速访问器与 O(1) 紧凑查找表（小整数支持数组直接寻址）。
 */
public class EnumMetadata {

    private final Class<? extends Enum<?>> enumClass;
    private final EnumMappingType mappingType;
    private final FastPropertyGetter fastGetter;
    private final Class<?> valueType;
    private final Map<Object, Enum<?>> lookupTable;
    private final Enum<?>[] arrayLookup;
    private final Enum<?> fallbackEnum;

    public EnumMetadata(Class<? extends Enum<?>> enumClass,
                        EnumMappingType mappingType,
                        FastPropertyGetter fastGetter,
                        Class<?> valueType,
                        Map<Object, Enum<?>> lookupTable,
                        Enum<?>[] arrayLookup,
                        Enum<?> fallbackEnum) {
        this.enumClass = enumClass;
        this.mappingType = mappingType;
        this.fastGetter = fastGetter;
        this.valueType = valueType;
        this.lookupTable = lookupTable;
        this.arrayLookup = arrayLookup;
        this.fallbackEnum = fallbackEnum;
    }

    public Class<? extends Enum<?>> getEnumClass() {
        return enumClass;
    }

    public EnumMappingType getMappingType() {
        return mappingType;
    }

    public Class<?> getValueType() {
        return valueType;
    }

    public Enum<?> getFallbackEnum() {
        return fallbackEnum;
    }

    /**
     * 极速序列化：将枚举实例转换为数据库物理值（零反射）。
     */
    public Object toDbValue(Enum<?> enumValue) {
        if (enumValue == null) {
            return null;
        }
        if (mappingType == EnumMappingType.NAME) {
            return enumValue.name();
        }
        if (mappingType == EnumMappingType.ORDINAL) {
            return enumValue.ordinal();
        }
        if (fastGetter != null) {
            return fastGetter.get(enumValue);
        }
        return enumValue.name();
    }

    /**
     * 极速反序列化：根据数据库物理值反查枚举实例（O(1) 数组下标或 Map 查找，零反射遍历）。
     */
    public Enum<?> toEnum(Object dbValue) {
        if (dbValue == null) {
            return null;
        }

        // 1. 若建立了稠密非负小整数直接寻址数组，直接数组下标获取
        if (arrayLookup != null && dbValue instanceof Number) {
            int idx = ((Number) dbValue).intValue();
            if (idx >= 0 && idx < arrayLookup.length) {
                Enum<?> val = arrayLookup[idx];
                if (val != null) {
                    return val;
                }
            }
        }

        // 2. Map 查找
        Enum<?> result = lookupTable.get(dbValue);
        if (result != null) {
            return result;
        }

        // 3. 数字类型容错匹配（如 Long vs Integer）
        if (dbValue instanceof Number) {
            long l = ((Number) dbValue).longValue();
            result = lookupTable.get(l);
            if (result != null) return result;
            result = lookupTable.get((int) l);
            if (result != null) return result;
        } else if (dbValue instanceof String) {
            // 字符串数字容错（如 "1" -> 1）
            String s = ((String) dbValue).trim();
            try {
                int intVal = Integer.parseInt(s);
                result = lookupTable.get(intVal);
                if (result != null) return result;
            } catch (NumberFormatException ignored) {
            }
        }

        // 4. 若未命中且配置了 fallback 兜底枚举
        return result != null ? result : fallbackEnum;
    }
}
