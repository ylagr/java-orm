package com.javaorm.core.sql;

import com.javaorm.core.dialect.SqlAndParams;

import java.util.List;
import java.util.Map;

/**
 * SqlEnv 统合执行门面。
 * 统一承载读操作（SELECT：toList, listMaps, firstOrNull, count）与写操作（UPDATE/DELETE：exec, execute）。
 */
public class SqlExecution {

    private final SqlEnv env;
    private final Object operation;
    private Boolean printSqlOverride = null;

    public SqlExecution(SqlEnv env, Object operation) {
        this.env = env;
        this.operation = operation;
    }

    public Object getOperation() {
        return operation;
    }

    /**
     * 链式配置：本次执行按照全局配置决定是否打印 SQL
     */
    public SqlExecution printSql() {
        this.printSqlOverride = true;
        applyPrintSql();
        return this;
    }

    /**
     * 链式配置：本次执行指定是否打印 SQL
     */
    public SqlExecution printSql(boolean print) {
        this.printSqlOverride = print;
        applyPrintSql();
        return this;
    }

    private void applyPrintSql() {
        if (operation instanceof SqlSelectStream) {
            ((SqlSelectStream) operation).printSql(printSqlOverride);
        } else if (operation instanceof SqlUpdateStream) {
            ((SqlUpdateStream) operation).printSql(printSqlOverride);
        } else if (operation instanceof SqlDeleteStream) {
            ((SqlDeleteStream) operation).printSql(printSqlOverride);
        }
    }

    /**
     * 执行写操作（UPDATE / DELETE），返回受影响行数
     */
    public int exec() {
        return execute();
    }

    /**
     * 执行写操作（UPDATE / DELETE），返回受影响行数
     */
    public int execute() {
        if (operation instanceof SqlUpdateStream) {
            return ((SqlUpdateStream) operation).execute();
        }
        if (operation instanceof SqlDeleteStream) {
            return ((SqlDeleteStream) operation).execute();
        }
        if (operation instanceof SqlSelectStream) {
            throw new IllegalStateException("Cannot call exec() / execute() on a SELECT query. Please call .toList(), .listMaps(), or .firstOrNull() instead.");
        }
        throw new IllegalStateException("No valid SQL operation configured in SqlEnv. Please call e.select(), e.update(), or e.delete().");
    }

    /**
     * 执行查询操作并返回实体列表
     */
    @SuppressWarnings("unchecked")
    public <R> List<R> toList() {
        if (operation instanceof SqlSelectStream) {
            return ((SqlSelectStream) operation).toList();
        }
        throw new IllegalStateException("Cannot call toList() on an UPDATE or DELETE operation. Please call .exec() instead.");
    }

    /**
     * 执行查询操作并映射为指定目标类型列表（如 DTO）
     */
    public <R> List<R> toList(Class<R> targetClass) {
        if (operation instanceof SqlSelectStream) {
            return ((SqlSelectStream) operation).toList(targetClass);
        }
        throw new IllegalStateException("Cannot call toList(Class) on an UPDATE or DELETE operation. Please call .exec() instead.");
    }

    /**
     * 执行查询操作并返回 Map 字典列表
     */
    public List<Map<String, Object>> listMaps() {
        if (operation instanceof SqlSelectStream) {
            return ((SqlSelectStream) operation).listMaps();
        }
        throw new IllegalStateException("Cannot call listMaps() on an UPDATE or DELETE operation. Please call .exec() instead.");
    }

    /**
     * 执行查询操作并返回第一条记录，若无结果返回 null
     */
    @SuppressWarnings("unchecked")
    public <R> R firstOrNull() {
        if (operation instanceof SqlSelectStream) {
            return ((SqlSelectStream) operation).firstOrNull();
        }
        throw new IllegalStateException("Cannot call firstOrNull() on an UPDATE or DELETE operation. Please call .exec() instead.");
    }

    /**
     * 执行查询操作并返回第一条记录，映射为指定类型，若无结果返回 null
     */
    public <R> R firstOrNull(Class<R> targetClass) {
        if (operation instanceof SqlSelectStream) {
            return ((SqlSelectStream) operation).firstOrNull(targetClass);
        }
        throw new IllegalStateException("Cannot call firstOrNull(Class) on an UPDATE or DELETE operation. Please call .exec() instead.");
    }

    /**
     * 执行计数查询
     */
    public long count() {
        if (operation instanceof SqlSelectStream) {
            return ((SqlSelectStream) operation).count();
        }
        throw new IllegalStateException("Cannot call count() on an UPDATE or DELETE operation. Please call .exec() instead.");
    }

    /**
     * 获取生成的完整 SQL 语句字符串
     */
    public String toSql() {
        if (operation instanceof SqlSelectStream) {
            return ((SqlSelectStream) operation).toSql();
        }
        if (operation instanceof SqlUpdateStream) {
            return ((SqlUpdateStream) operation).toSql();
        }
        if (operation instanceof SqlDeleteStream) {
            return ((SqlDeleteStream) operation).toSql();
        }
        throw new IllegalStateException("No valid SQL operation configured in SqlEnv.");
    }

    /**
     * 获取生成的完整 SQL 语句与绑定参数列表
     */
    public SqlAndParams toSqlAndParams() {
        if (operation instanceof SqlSelectStream) {
            return ((SqlSelectStream) operation).toSqlAndParams();
        }
        if (operation instanceof SqlUpdateStream) {
            return ((SqlUpdateStream) operation).toSqlAndParams();
        }
        if (operation instanceof SqlDeleteStream) {
            return ((SqlDeleteStream) operation).toSqlAndParams();
        }
        throw new IllegalStateException("No valid SQL operation configured in SqlEnv.");
    }

    @Override
    public String toString() {
        return toSql();
    }
}
