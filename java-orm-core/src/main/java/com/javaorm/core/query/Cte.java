package com.javaorm.core.query;

import com.javaorm.core.ast.CteClause;
import com.javaorm.core.proxy.ProxyFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 强类型通用表表达式（Common Table Expression, CTE）。
 * 绑定数据契约类型 T 与底层定义子查询，可在主查询中像操作普通实体一样进行强类型查询、过滤与连接。
 *
 * @param <T> CTE 对应的实体或 DTO 契约类型
 */
public class Cte<T> {

    private final String name;
    private final Class<T> entityClass;
    private Object query;
    private final boolean recursive;
    private final List<String> columnNames;

    public Cte(String name, Class<T> entityClass, Object query) {
        this(name, entityClass, query, false, null);
    }

    public Cte(String name, Class<T> entityClass, Object query, boolean recursive) {
        this(name, entityClass, query, recursive, null);
    }

    public Cte(String name, Class<T> entityClass, Object query, boolean recursive, List<String> columnNames) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("CTE name must not be null or empty");
        }
        if (entityClass == null) {
            throw new IllegalArgumentException("CTE entityClass must not be null");
        }
        this.name = name.trim();
        this.entityClass = entityClass;
        this.query = query;
        this.recursive = recursive;
        this.columnNames = columnNames != null ? Collections.unmodifiableList(new ArrayList<>(columnNames)) : null;
    }

    public String getName() {
        return name;
    }

    public Class<T> getEntityClass() {
        return entityClass;
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
     * 声明当前 CTE 为递归 CTE
     */
    public Cte<T> recursive() {
        return new Cte<>(name, entityClass, query, true, columnNames);
    }

    /**
     * 显式指定 CTE 外部列名投影列表：WITH cte_name (col1, col2) AS (...)
     */
    public Cte<T> columns(String... columns) {
        return new Cte<>(name, entityClass, query, recursive, Arrays.asList(columns));
    }

    public Cte<T> columns(List<String> columns) {
        return new Cte<>(name, entityClass, query, recursive, columns);
    }

    /**
     * 获取当前 CTE 绑定的强类型录制代理单例对象
     */
    public T proxy() {
        return ProxyFactory.getProxy(entityClass);
    }

    /**
     * 获取带指定别名的强类型录制代理对象
     */
    public T proxy(String alias) {
        return ProxyFactory.createProxy(entityClass, alias);
    }

    /**
     * 转换为 AST 节点
     */
    public CteClause toClause() {
        return new CteClause(name, query, recursive, columnNames, entityClass);
    }

    @Override
    public String toString() {
        return "Cte[" + name + " -> " + entityClass.getSimpleName() + "]";
    }
}
