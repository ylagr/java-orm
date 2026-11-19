package com.javaorm.core.codefirst;

/**
 * Code-First 数据库架构同步模式（参考 JPA / EasyQuery）。
 */
public enum CodeFirstMode {
    /**
     * 关闭 Code-First 同步，不执行任何 DDL 检测与变更（默认）
     */
    NONE,

    /**
     * 只校验模式：检查实体定义的表和字段是否存在，若缺失则抛出异常，不执行任何 DDL 变更
     */
    VALIDATE,

    /**
     * 仅建表模式：检测数据库中未创建的表并自动执行 CREATE TABLE，若表已存在则不执行任何字段追加
     */
    CREATE_ONLY,

    /**
     * 增量更新模式（核心推荐）：
     * 1. 检测不存在的表并自动执行 CREATE TABLE；
     * 2. 检测已有表中未创建的字段，自动生成并执行 ALTER TABLE ... ADD COLUMN ... 增量补齐。
     * （遵循安全原则：默认不删除废弃字段）
     */
    UPDATE,

    /**
     * 开发重置模式：先执行 DROP TABLE IF EXISTS，再执行 CREATE TABLE（仅建议在测试环境下使用）
     */
    DROP_CREATE
}
