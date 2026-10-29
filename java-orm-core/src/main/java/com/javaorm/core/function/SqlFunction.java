package com.javaorm.core.function;

/**
 * SQL 函数规范逻辑标识符接口。
 * 解耦物理数据库函数名与 AST 逻辑表达，支持内置枚举与用户自定义实现，
 * 支持不同方言自由替换与注册扩展。
 */
public interface SqlFunction {

    /**
     * 获取函数逻辑标识符或规范名称（通常全大写，如 DATE_FORMAT, IFNULL）。
     */
    String getName();

    /**
     * 根据字符串名称获取或创建 SqlFunction。
     * 若名称匹配 BuiltinSqlFunction 内置枚举则返回对应枚举，否则返回 SimpleSqlFunction。
     */
    static SqlFunction of(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Function name cannot be null or empty");
        }
        String upper = name.trim().toUpperCase();
        for (BuiltinSqlFunction builtin : BuiltinSqlFunction.values()) {
            if (builtin.name().equals(upper)) {
                return builtin;
            }
        }
        return new SimpleSqlFunction(upper);
    }
}
