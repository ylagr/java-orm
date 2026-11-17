package com.javaorm.core.annotation;

/**
 * 枚举持久化映射模式。
 */
public enum EnumMappingType {
    /**
     * 自动探测（默认）：优先检查枚举中是否存在 @JoEnumValue，若存在则按对应值存储；否则按枚举名 name() 存储。
     */
    AUTO,

    /**
     * 按枚举名称字符串（Enum.name()）存储。
     */
    NAME,

    /**
     * 按枚举序号整数（Enum.ordinal()）存储。
     */
    ORDINAL,

    /**
     * 按枚举指定属性（配合 @JoEnum(property = "...")）存储。
     */
    PROPERTY
}
