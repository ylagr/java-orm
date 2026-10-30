package com.javaorm.core.ast;

import java.util.List;

/**
 * 抽象语法树（AST）顶层语句抽象定义。
 * 纯粹承载 SQL 结构元数据与条件分支，不持有运行时环境或执行逻辑。
 */
public interface Statement {

    /**
     * 获取语句操作的目标根实体类型（若为非实体表或原生 SQL 表可为 null）
     */
    Class<?> getEntityClass();

    /**
     * 获取语句操作的目标表名
     */
    String getTableName();

    /**
     * 获取系统内置级条件（逻辑删除、多租户隔离等）
     */
    default List<Condition> getSystemConditions() {
        return java.util.Collections.emptyList();
    }

    /**
     * 获取用户通过 WHERE 子句构建的业务过滤条件
     */
    default List<Condition> getWhereConditions() {
        return java.util.Collections.emptyList();
    }

    /**
     * 添加系统条件
     */
    default void addSystemCondition(Condition condition) {
    }

    /**
     * 添加业务 WHERE 条件
     */
    default void addWhereCondition(Condition condition) {
    }
}
