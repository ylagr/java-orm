package com.javaorm.core.ast;

import java.util.ArrayList;
import java.util.List;

public class InsertStatement implements Statement {
    private Class<?> entityClass;
    private String tableName;
    private final List<String> columns = new ArrayList<>();
    private final List<Object> values = new ArrayList<>();

    public InsertStatement(Class<?> entityClass, String tableName) {
        this.entityClass = entityClass;
        this.tableName = tableName;
    }

    public void addColumnValue(String column, Object value) {
        columns.add(column);
        values.add(value);
    }

    public Class<?> getEntityClass() { return entityClass; }
    public String getTableName() { return tableName; }
    public List<String> getColumns() { return columns; }
    public List<Object> getValues() { return values; }
}
