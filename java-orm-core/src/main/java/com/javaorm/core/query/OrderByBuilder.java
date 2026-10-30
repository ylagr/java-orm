package com.javaorm.core.query;

import com.javaorm.core.ast.OrderBy;
import com.javaorm.core.ast.OrderDirection;
import com.javaorm.core.ast.SelectStatement;
import com.javaorm.core.ast.SqlExpression;

import com.javaorm.core.sql.SqlEnv;

/**
 * ORDER BY 链式排序构造器。
 * 支持静态及动态指定排序方向（ASC / DESC），以及条件过滤排序（ascIf / descIf / sort(condition, isAsc)）。
 *
 * @param <TQuery> 所在上下文查询或构造器类型
 */
public class OrderByBuilder<TQuery> {
    private final SqlEnv env;
    private final TQuery query;
    private final SelectStatement statement;
    private String tableAlias;
    private String columnName;
    private SqlExpression<?> expression;

    public OrderByBuilder(TQuery query, SelectStatement statement, String tableAlias, String columnName) {
        this(null, query, statement, tableAlias, columnName);
    }

    public OrderByBuilder(SqlEnv env, TQuery query, SelectStatement statement, String tableAlias, String columnName) {
        this.env = env;
        this.query = query;
        this.statement = statement;
        this.tableAlias = tableAlias;
        this.columnName = columnName;
    }

    public OrderByBuilder(TQuery query, SelectStatement statement, SqlExpression<?> expression) {
        this(null, query, statement, expression);
    }

    public OrderByBuilder(SqlEnv env, TQuery query, SelectStatement statement, SqlExpression<?> expression) {
        this.env = env;
        this.query = query;
        this.statement = statement;
        this.expression = expression;
    }

    public SqlEnv getEnv() {
        return env;
    }

    public SelectStatement getStatement() {
        return statement;
    }

    public TQuery getQuery() {
        return query;
    }

    /**
     * 升序排序 ASC
     */
    public TQuery asc() {
        if (expression != null) {
            statement.addOrderBy(new OrderBy(expression, true));
        } else {
            statement.addOrderBy(new OrderBy(tableAlias, columnName, true));
        }
        return query;
    }

    /**
     * 降序排序 DESC
     */
    public TQuery desc() {
        if (expression != null) {
            statement.addOrderBy(new OrderBy(expression, false));
        } else {
            statement.addOrderBy(new OrderBy(tableAlias, columnName, false));
        }
        return query;
    }

    /**
     * 动态指定升降序：isAsc 为 true 则升序，为 false 则降序。
     */
    public TQuery asc(boolean isAsc) {
        return isAsc ? asc() : desc();
    }

    /**
     * 动态指定升降序（支持包装类，为 null 时默认升序）：isAsc 为 true 则升序，为 false 则降序。
     */
    public TQuery asc(Boolean isAsc) {
        if (isAsc == null || isAsc) {
            return asc();
        } else {
            return desc();
        }
    }

    /**
     * 动态指定降升序：isDesc 为 true 则降序，为 false 则升序。
     */
    public TQuery desc(boolean isDesc) {
        return isDesc ? desc() : asc();
    }

    /**
     * 动态指定降升序（支持包装类，为 null 时默认降序）：isDesc 为 true 则降序，为 false 则升序。
     */
    public TQuery desc(Boolean isDesc) {
        if (isDesc == null || isDesc) {
            return desc();
        } else {
            return asc();
        }
    }

    /**
     * 动态指定排序方向：isAsc 为 true 则升序，为 false 则降序。
     */
    public TQuery sort(boolean isAsc) {
        return isAsc ? asc() : desc();
    }

    /**
     * 动态指定排序方向：支持 OrderDirection 枚举（ASC / DESC）。
     */
    public TQuery sort(OrderDirection direction) {
        return (direction != null && !direction.isAscending()) ? desc() : asc();
    }

    /**
     * 动态指定排序方向字符串："asc" / "desc"（忽略大小写，默认 asc）。
     */
    public TQuery sort(String direction) {
        if ("desc".equalsIgnoreCase(direction != null ? direction.trim() : null)) {
            return desc();
        }
        return asc();
    }

    public TQuery direction(boolean isAsc) {
        return sort(isAsc);
    }

    public TQuery direction(OrderDirection direction) {
        return sort(direction);
    }

    public TQuery direction(String direction) {
        return sort(direction);
    }

    public TQuery ascOrDesc(boolean isAsc) {
        return sort(isAsc);
    }

    public TQuery ascOrDesc(String direction) {
        return sort(direction);
    }

    /**
     * 条件升序：当 condition 为 true 时才添加 ASC 排序，否则跳过排序。
     */
    public TQuery ascIf(boolean condition) {
        if (condition) {
            return asc();
        }
        return query;
    }

    /**
     * 条件降序：当 condition 为 true 时才添加 DESC 排序，否则跳过排序。
     */
    public TQuery descIf(boolean condition) {
        if (condition) {
            return desc();
        }
        return query;
    }

    /**
     * 条件动态排序：当 condition 为 true 时才根据 isAsc 决定升降序，否则跳过排序。
     */
    public TQuery sort(boolean condition, boolean isAsc) {
        if (condition) {
            return isAsc ? asc() : desc();
        }
        return query;
    }

    /**
     * 条件动态排序：当 condition 为 true 时才根据 direction 决定升降序，否则跳过排序。
     */
    public TQuery sort(boolean condition, OrderDirection direction) {
        if (condition) {
            return sort(direction);
        }
        return query;
    }

    /**
     * 条件动态排序：当 condition 为 true 时才根据 direction 字符串决定升降序，否则跳过排序。
     */
    public TQuery sort(boolean condition, String direction) {
        if (condition) {
            return sort(direction);
        }
        return query;
    }

    /**
     * 条件动态排序别名：当 condition 为 true 时才根据 isAsc 决定升降序，否则跳过排序。
     */
    public TQuery asc(boolean condition, boolean isAsc) {
        return sort(condition, isAsc);
    }

    public com.javaorm.core.dialect.SqlAndParams toSqlAndParams() {
        asc();
        if (query instanceof SelectBuilder) {
            return ((SelectBuilder<?>) query).toSqlAndParams();
        } else if (query instanceof WhereBuilder) {
            return ((WhereBuilder<?>) query).toSqlAndParams();
        } else if (query instanceof DtoQuery) {
            return ((DtoQuery<?, ?>) query).toSqlAndParams();
        } else if (query instanceof HavingBuilder) {
            return ((HavingBuilder<?>) query).toSqlAndParams();
        }
        throw new UnsupportedOperationException("toSqlAndParams() not supported on " + query.getClass());
    }

    public String toSql() {
        return toSqlAndParams().getSql();
    }

    @Override
    public String toString() {
        try {
            return toSql();
        } catch (Exception e) {
            return super.toString();
        }
    }
}
