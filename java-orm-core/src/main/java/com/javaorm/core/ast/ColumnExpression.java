package com.javaorm.core.ast;

import com.javaorm.core.proxy.PropertyPath;

public class ColumnExpression implements SqlExpression {

    private PropertyPath path;
    private String tableAlias;
    private String columnName;
    private String alias;

    public ColumnExpression(PropertyPath path) {
        this.path = path;
    }

    public ColumnExpression(String tableAlias, String columnName) {
        this.tableAlias = tableAlias;
        this.columnName = columnName;
    }

    @Override
    public String getAlias() {
        return alias;
    }

    @Override
    public ColumnExpression as(String alias) {
        this.alias = alias;
        return this;
    }

    public PropertyPath getPath() { return path; }
    public void setPath(PropertyPath path) { this.path = path; }

    public String getTableAlias() { return tableAlias; }
    public void setTableAlias(String tableAlias) { this.tableAlias = tableAlias; }

    public String getColumnName() { return columnName; }
    public void setColumnName(String columnName) { this.columnName = columnName; }
}
