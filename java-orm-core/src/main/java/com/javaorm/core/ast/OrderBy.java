package com.javaorm.core.ast;

public class OrderBy {
    private String tableAlias;
    private String columnName;
    private boolean ascending = true;

    public OrderBy(String tableAlias, String columnName, boolean ascending) {
        this.tableAlias = tableAlias;
        this.columnName = columnName;
        this.ascending = ascending;
    }

    public String getTableAlias() { return tableAlias; }
    public String getColumnName() { return columnName; }
    public boolean isAscending() { return ascending; }
}
