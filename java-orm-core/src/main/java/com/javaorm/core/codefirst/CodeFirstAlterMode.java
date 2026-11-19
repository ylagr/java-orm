package com.javaorm.core.codefirst;

/**
 * Code-First 已有列变更控制模式（位掩码枚举）。
 * 用于对已有数据表的字段变更（长度扩容/缩短、默认值、可空性、注释、数据类型）进行细粒度安全控制。
 * 支持单个枚举、预设组合枚举以及基于按位或（|）运算的自定义多选组合。
 */
public enum CodeFirstAlterMode {

    /**
     * 禁止修改任何已有字段的属性（默认安全模式）
     */
    NONE(0),

    /**
     * 允许字段长度/精度扩容（如 VARCHAR(50) -> VARCHAR(150)，DECIMAL(10,2) -> DECIMAL(12,2)）
     */
    LENGTH_EXPAND(1 << 0), // 1

    /**
     * 允许字段长度缩减（如 VARCHAR(100) -> VARCHAR(50)，高危：可能导致既有数据截断）
     */
    LENGTH_SHRINK(1 << 1), // 2

    /**
     * 允许修改字段默认值（SET DEFAULT / DROP DEFAULT）
     */
    DEFAULT_VALUE(1 << 2), // 4

    /**
     * 允许修改字段可空性约束（SET NOT NULL / DROP NOT NULL）
     */
    NULLABLE(1 << 3), // 8

    /**
     * 允许修改字段注释（COMMENT）
     */
    COMMENT(1 << 4), // 16

    /**
     * 允许修改字段类型定义（如 VARCHAR -> TEXT）
     */
    DATA_TYPE(1 << 5), // 32

    /**
     * 允许为缺失的索引自动创建索引（CREATE INDEX / CREATE UNIQUE INDEX）
     */
    INDEX(1 << 6), // 64

    /* ========================================================================= */
    /* 常用组合预设枚举 (Presets)                                                 */
    /* ========================================================================= */

    /**
     * 允许所有长度变更（包含扩容与缩减）：LENGTH_EXPAND | LENGTH_SHRINK
     */
    LENGTH(1 | 2),

    /**
     * 允许长度扩容与默认值变更：LENGTH_EXPAND | DEFAULT_VALUE
     */
    LENGTH_EXPAND_AND_DEFAULT(1 | 4),

    /**
     * 安全演进模式（推荐开发环境使用）：仅允许长度扩容 + 默认值变更 + 注释更新 + 自动补齐索引，禁止缩减字段与改类型
     */
    EXPAND_ONLY(1 | 4 | 16 | 64),

    /**
     * 与 EXPAND_ONLY 等效的安全演进模式别名
     */
    SAFE(1 | 4 | 16 | 64),

    /**
     * 允许所有列属性与索引变更（完全放开，包含高危操作）
     */
    ALL(1 | 2 | 4 | 8 | 16 | 32 | 64);

    private final int mask;

    CodeFirstAlterMode(int mask) {
        this.mask = mask;
    }

    /**
     * 获取当前枚举对应的位掩码值
     */
    public int getMask() {
        return mask;
    }

    /**
     * 与另一个模式进行按位或（OR）合并
     */
    public int or(CodeFirstAlterMode other) {
        return this.mask | (other != null ? other.mask : 0);
    }

    /**
     * 当前模式中是否包含目标模式
     */
    public boolean has(CodeFirstAlterMode target) {
        if (target == null || target.mask == 0) return false;
        return (this.mask & target.mask) == target.mask;
    }

    /**
     * 将多个模式通过按位或（|）合并为一个综合位掩码
     */
    public static int combine(CodeFirstAlterMode... modes) {
        if (modes == null || modes.length == 0) return 0;
        int res = 0;
        for (CodeFirstAlterMode m : modes) {
            if (m != null) {
                res |= m.mask;
            }
        }
        return res;
    }

    /**
     * 判断指定组合掩码中是否完全包含目标模式的所有位
     */
    public static boolean isEnabled(int combinedMask, CodeFirstAlterMode target) {
        if (target == null || target.mask == 0) return false;
        return (combinedMask & target.mask) == target.mask;
    }

    /**
     * 判断指定组合掩码中是否包含目标模式中的任意一位（用于组合枚举匹配）
     */
    public static boolean isAnyEnabled(int combinedMask, CodeFirstAlterMode target) {
        if (target == null || target.mask == 0) return false;
        return (combinedMask & target.mask) != 0;
    }
}
