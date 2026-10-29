package com.javaorm.core.function;

/**
 * 框架默认内置的常用 SQL 函数逻辑标识符枚举。
 * 覆盖聚合、开窗、时间日期、字符串、数学、逻辑分支及类型转换等。
 */
public enum BuiltinSqlFunction implements SqlFunction {

    // ==================== 聚合函数 ====================
    COUNT,
    SUM,
    AVG,
    MIN,
    MAX,
    GROUP_CONCAT,
    STDDEV,
    VARIANCE,

    // ==================== 开窗分析函数 ====================
    ROW_NUMBER,
    RANK,
    DENSE_RANK,
    PERCENT_RANK,
    CUME_DIST,
    NTILE,
    LEAD,
    LAG,
    FIRST_VALUE,
    LAST_VALUE,
    NTH_VALUE,

    // ==================== 日期与时间函数 ====================
    DATE_FORMAT,
    STR_TO_DATE,
    DATE_ADD,
    DATE_SUB,
    DATEDIFF,
    DAYOFWEEK,
    DAYOFYEAR,
    YEAR,
    MONTH,
    DAY,
    HOUR,
    MINUTE,
    SECOND,
    LAST_DAY,
    NOW,
    CURDATE,
    CURTIME,

    // ==================== 字符串函数 ====================
    LENGTH,
    CHAR_LENGTH,
    LOWER,
    UPPER,
    TRIM,
    LTRIM,
    RTRIM,
    CONCAT,
    CONCAT_WS,
    SUBSTRING,
    REPLACE,
    LEFT,
    RIGHT,
    INSTR,
    LOCATE,
    LPAD,
    RPAD,
    REVERSE,
    REPEAT,
    ASCII,
    MD5,

    // ==================== 数学与数值函数 ====================
    ABS,
    ROUND,
    CEIL,
    CEILING,
    FLOOR,
    MOD,
    SQRT,
    POWER,
    SIGN,
    TRUNCATE,
    TRUNC,
    EXP,
    LN,
    LOG,
    PI,
    SIN,
    COS,
    TAN,
    ASIN,
    ACOS,
    ATAN,
    DEGREES,
    RADIANS,

    // ==================== 条件与空值处理 ====================
    IFNULL,
    NVL,
    COALESCE,
    NULLIF,
    GREATEST,
    LEAST,
    IIF,
    IF,

    // ==================== 类型转换 ====================
    CAST;

    @Override
    public String getName() {
        return name();
    }
}
