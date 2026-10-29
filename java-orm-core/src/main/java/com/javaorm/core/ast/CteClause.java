package com.javaorm.core.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 通用表表达式（CTE）AST 节点。
 */
public class CteClause {

    private String name;
    private Object query;
    private boolean recursive;
    private List<String> columnNames;
    private Class<?> entityClass;

    public CteClause(String name, Object query) {
        this(name, query, false, null, null);
    }

    public CteClause(String name, Object query, boolean recursive) {
        this(name, query, recursive, null, null);
    }

    public CteClause(String name, Object query, boolean recursive, List<String> columnNames, Class<?> entityClass) {
        this.name = name;
        this.query = query;
        this.recursive = recursive;
        this.columnNames = columnNames != null ? Collections.unmodifiableList(new ArrayList<>(columnNames)) : null;
        this.entityClass = entityClass;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }

    public List<String> getColumnNames() {
        return columnNames;
    }

    public void setColumnNames(List<String> columnNames) {
        this.columnNames = columnNames != null ? Collections.unmodifiableList(new ArrayList<>(columnNames)) : null;
    }

    public Class<?> getEntityClass() {
        return entityClass;
    }

    public void setEntityClass(Class<?> entityClass) {
        this.entityClass = entityClass;
    }
}
