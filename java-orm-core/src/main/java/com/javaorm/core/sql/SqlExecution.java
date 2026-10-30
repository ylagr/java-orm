package com.javaorm.core.sql;

import com.javaorm.core.dialect.SqlAndParams;
import com.javaorm.core.metadata.ColumnMetadata;
import com.javaorm.core.mutation.DeleteBuilder;
import com.javaorm.core.mutation.UpdateBuilder;
import com.javaorm.core.query.Query;

import java.util.List;
import java.util.Map;

/**
 * SqlEnv 统合执行门面。
 * 统一承载读操作（SELECT：toList, listMaps, firstOrNull, count）与写操作（UPDATE/DELETE：exec, execute）。
 *
 * @param <T> 查询结果类型强类型约束
 */
public class SqlExecution<T> {

    private final SqlEnv env;
    private final Object operation;
    private final Class<T> resultClass;
    private Boolean printSqlOverride = null;

    public SqlExecution(SqlEnv env, Object operation) {
        this(env, operation, null);
    }

    public SqlExecution(SqlEnv env, Object operation, Class<T> resultClass) {
        this.env = env;
        this.operation = operation;
        this.resultClass = resultClass;
    }

    public Object getOperation() {
        return operation;
    }

    public Class<T> getResultClass() {
        return resultClass;
    }

    /**
     * 链式配置：本次执行按照全局配置决定是否打印 SQL
     */
    public SqlExecution<T> printSql() {
        this.printSqlOverride = true;
        applyPrintSql();
        return this;
    }

    /**
     * 链式配置：本次执行指定是否打印 SQL
     */
    public SqlExecution<T> printSql(boolean print) {
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
        } else if (operation instanceof Query<?>) {
            ((Query<?>) operation).printSql(printSqlOverride);
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
        if (operation instanceof UpdateBuilder<?>) {
            return ((UpdateBuilder<?>) operation).execute();
        }
        if (operation instanceof DeleteBuilder<?>) {
            return ((DeleteBuilder<?>) operation).execute();
        }
        if (operation instanceof SqlSelectStream || operation instanceof Query<?>) {
            throw new IllegalStateException("Cannot call exec() / execute() on a SELECT query. Please call .toList(), .listMaps(), or .firstOrNull() instead.");
        }
        throw new IllegalStateException("No valid SQL operation configured in SqlEnv. Please call e.select(), e.update(), or e.delete().");
    }

    /**
     * 执行查询操作并返回强类型实体/DTO列表
     */
    @SuppressWarnings("unchecked")
    public <R> List<R> toList() {
        if (operation instanceof SqlSelectStream) {
            SqlSelectStream selectStream = (SqlSelectStream) operation;
            if (resultClass != null) {
                return (List<R>) selectStream.toList(resultClass);
            }
            return selectStream.toList();
        }
        if (operation instanceof Query<?>) {
            Query<?> q = (Query<?>) operation;
            if (resultClass != null) {
                if (ColumnMetadata.isScalarType(resultClass)) {
                    return (List<R>) env.executeSelectScalarList(null, q.getStatement(), resultClass);
                }
                if (Map.class.isAssignableFrom(resultClass)) {
                    return (List<R>) env.executeSelectMaps(null, q.getStatement());
                }
                return (List<R>) env.executeSelectDtoList(null, q.getStatement(), resultClass);
            }
            return (List<R>) q.toList();
        }
        throw new IllegalStateException("Cannot call toList() on an UPDATE or DELETE operation. Please call .exec() instead.");
    }

    /**
     * 执行查询操作并映射为指定目标类型列表（如 DTO / 实体 / 单标量值 / Map）
     */
    @SuppressWarnings("unchecked")
    public <R> List<R> toList(Class<R> targetClass) {
        if (operation instanceof SqlSelectStream) {
            return ((SqlSelectStream) operation).toList(targetClass);
        }
        if (operation instanceof Query<?>) {
            Query<?> q = (Query<?>) operation;
            if (ColumnMetadata.isScalarType(targetClass)) {
                return env.executeSelectScalarList(null, q.getStatement(), targetClass);
            }
            if (Map.class.isAssignableFrom(targetClass)) {
                return (List<R>) env.executeSelectMaps(null, q.getStatement());
            }
            return env.executeSelectDtoList(null, q.getStatement(), targetClass);
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
        if (operation instanceof Query<?>) {
            return ((Query<?>) operation).listMaps();
        }
        throw new IllegalStateException("Cannot call listMaps() on an UPDATE or DELETE operation. Please call .exec() instead.");
    }

    /**
     * 执行查询操作并返回第一条记录，若无结果返回 null
     */
    @SuppressWarnings("unchecked")
    public <R> R firstOrNull() {
        if (operation instanceof SqlSelectStream) {
            SqlSelectStream selectStream = (SqlSelectStream) operation;
            if (resultClass != null) {
                return (R) selectStream.firstOrNull(resultClass);
            }
            return selectStream.firstOrNull();
        }
        if (operation instanceof Query<?>) {
            Query<?> q = (Query<?>) operation;
            if (resultClass != null) {
                if (ColumnMetadata.isScalarType(resultClass)) {
                    return (R) env.executeSelectScalar(null, q.getStatement(), resultClass);
                }
                if (Map.class.isAssignableFrom(resultClass)) {
                    List<Map<String, Object>> maps = env.executeSelectMaps(null, q.getStatement());
                    return (maps != null && !maps.isEmpty()) ? (R) maps.get(0) : null;
                }
                List<R> list = toList((Class<R>) resultClass);
                return (list != null && !list.isEmpty()) ? list.get(0) : null;
            }
            return (R) q.firstOrNull();
        }
        throw new IllegalStateException("Cannot call firstOrNull() on an UPDATE or DELETE operation. Please call .exec() instead.");
    }

    /**
     * 执行查询操作并返回第一条记录，映射为指定类型，若无结果返回 null
     */
    @SuppressWarnings("unchecked")
    public <R> R firstOrNull(Class<R> targetClass) {
        if (operation instanceof SqlSelectStream) {
            return ((SqlSelectStream) operation).firstOrNull(targetClass);
        }
        if (operation instanceof Query<?>) {
            Query<?> q = (Query<?>) operation;
            if (ColumnMetadata.isScalarType(targetClass)) {
                return env.executeSelectScalar(null, q.getStatement(), targetClass);
            }
            if (Map.class.isAssignableFrom(targetClass)) {
                List<Map<String, Object>> maps = env.executeSelectMaps(null, q.getStatement());
                return (maps != null && !maps.isEmpty()) ? (R) maps.get(0) : null;
            }
            List<R> list = toList(targetClass);
            return (list != null && !list.isEmpty()) ? list.get(0) : null;
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
        if (operation instanceof Query<?>) {
            return ((Query<?>) operation).count();
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
        if (operation instanceof Query<?>) {
            return ((Query<?>) operation).toSql();
        }
        if (operation instanceof UpdateBuilder<?>) {
            return ((UpdateBuilder<?>) operation).toSql();
        }
        if (operation instanceof DeleteBuilder<?>) {
            return ((DeleteBuilder<?>) operation).toSql();
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
        if (operation instanceof Query<?>) {
            return ((Query<?>) operation).toSqlAndParams();
        }
        if (operation instanceof UpdateBuilder<?>) {
            return ((UpdateBuilder<?>) operation).toSqlAndParams();
        }
        if (operation instanceof DeleteBuilder<?>) {
            return ((DeleteBuilder<?>) operation).toSqlAndParams();
        }
        throw new IllegalStateException("No valid SQL operation configured in SqlEnv.");
    }

    @Override
    public String toString() {
        return toSql();
    }
}
