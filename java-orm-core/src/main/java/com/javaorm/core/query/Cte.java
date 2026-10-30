package com.javaorm.core.query;

import com.javaorm.core.ast.ColumnExpression;
import com.javaorm.core.ast.CteClause;
import com.javaorm.core.proxy.ProxyFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 通用表表达式（Common Table Expression, CTE）。
 * 既支持强类型契约绑定（绑定实体或 DTO 契约类型 T），亦支持纯动态临时表（无需定义任何实体类，完全按需投影和过滤）。
 * 可在主查询中像操作普通实体或临时表一样进行强类型/动态查询、过滤与连接。
 *
 * @param <T> CTE 对应的实体或 DTO 契约类型，动态 CTE 时为 Object 或 Map
 */
public class Cte<T> {

    private final String name;
    private final Class<T> entityClass;
    private Object query;
    private final boolean recursive;
    private final List<String> columnNames;
    private final String alias;

    public Cte(String name, Object query) {
        this(name, null, query, false, null, null);
    }

    public Cte(String name, Object query, boolean recursive) {
        this(name, null, query, recursive, null, null);
    }

    public Cte(String name, Object query, boolean recursive, List<String> columnNames) {
        this(name, null, query, recursive, columnNames, null);
    }

    public Cte(String name, Class<T> entityClass, Object query) {
        this(name, entityClass, query, false, null, null);
    }

    public Cte(String name, Class<T> entityClass, Object query, boolean recursive) {
        this(name, entityClass, query, recursive, null, null);
    }

    public Cte(String name, Class<T> entityClass, Object query, boolean recursive, List<String> columnNames) {
        this(name, entityClass, query, recursive, columnNames, null);
    }

    public Cte(String name, Class<T> entityClass, Object query, boolean recursive, List<String> columnNames, String alias) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("CTE name must not be null or empty");
        }
        this.name = name.trim();
        this.entityClass = entityClass;
        this.query = query;
        this.recursive = recursive;
        this.columnNames = columnNames != null ? Collections.unmodifiableList(new ArrayList<>(columnNames)) : null;
        this.alias = alias != null && !alias.trim().isEmpty() ? alias.trim() : null;
    }

    public String getName() {
        return name;
    }

    public Class<T> getEntityClass() {
        return entityClass;
    }

    public boolean isDynamic() {
        return entityClass == null;
    }

    public String getAlias() {
        return alias;
    }

    public String getEffectiveAlias() {
        return alias != null ? alias : name;
    }

    public Object getQuery() {
        return query;
    }

    public void setQuery(Object query) {
        this.query = query;
    }

    public boolean isRecursive() {
        return recursive;
    }

    public List<String> getColumnNames() {
        return columnNames;
    }

    /**
     * 为当前 CTE 指定查询中的表别名，如 temp.as("t1")
     */
    public Cte<T> as(String alias) {
        return new Cte<>(name, entityClass, query, recursive, columnNames, alias);
    }

    /**
     * 声明当前 CTE 为递归 CTE
     */
    public Cte<T> recursive() {
        return new Cte<>(name, entityClass, query, true, columnNames, alias);
    }

    /**
     * 显式指定 CTE 外部列名投影列表：WITH cte_name (col1, col2) AS (...)
     */
    public Cte<T> columns(String... columns) {
        return new Cte<>(name, entityClass, query, recursive, Arrays.asList(columns), alias);
    }

    public Cte<T> columns(List<String> columns) {
        return new Cte<>(name, entityClass, query, recursive, columns, alias);
    }

    /**
     * 动态引用 CTE 结果集中的列（支持字段别名，如 "cnt as total" 或链式 .as("total")）
     */
    public ColumnExpression<Object> col(String colName) {
        return column(colName);
    }

    /**
     * 动态引用 CTE 结果集中的列（支持字段别名，如 "cnt as total" 或链式 .as("total")）
     */
    public ColumnExpression<Object> column(String colName) {
        if (colName == null) {
            throw new IllegalArgumentException("Column name must not be null");
        }
        String str = colName.trim();
        String colAlias = null;
        String[] parts = str.split(" (?i)as ");
        if (parts.length == 2) {
            str = parts[0].trim();
            colAlias = parts[1].trim();
        }
        String prefix = alias != null && !alias.isEmpty() ? alias : name;
        ColumnExpression<Object> ce = new ColumnExpression<>(prefix, str);
        if (colAlias != null) {
            ce.as(colAlias);
        }
        return ce;
    }

    @SuppressWarnings("unchecked")
    public <R> ColumnExpression<R> col(String colName, Class<R> type) {
        return (ColumnExpression<R>) column(colName);
    }

    @SuppressWarnings("unchecked")
    public <R> ColumnExpression<R> column(String colName, Class<R> type) {
        return (ColumnExpression<R>) column(colName);
    }

    /**
     * 获取当前 CTE 绑定的强类型录制代理单例对象（若为动态 CTE 则返回 null）
     */
    public T proxy() {
        return entityClass != null ? ProxyFactory.getProxy(entityClass) : null;
    }

    /**
     * 获取带指定别名的强类型录制代理对象（若为动态 CTE 则返回 null）
     */
    public T proxy(String alias) {
        return entityClass != null ? ProxyFactory.createProxy(entityClass, alias) : null;
    }

    /**
     * 转换为 AST 节点
     */
    public CteClause toClause() {
        return new CteClause(name, query, recursive, columnNames, entityClass);
    }

    @Override
    public String toString() {
        return "Cte[" + name + (entityClass != null ? " -> " + entityClass.getSimpleName() : " (dynamic)") + (alias != null ? " as " + alias : "") + "]";
    }
}
