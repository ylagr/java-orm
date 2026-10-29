package com.javaorm.core.ast;

public class OrderBy {
    private String tableAlias;
    private String columnName;
    private SqlExpression<?> expression;
    private boolean ascending = true;

    public OrderBy(String tableAlias, String columnName, boolean ascending) {
        this.tableAlias = tableAlias;
        this.columnName = columnName;
        this.ascending = ascending;
    }

    public OrderBy(SqlExpression<?> expression, boolean ascending) {
        this.expression = expression;
        this.ascending = ascending;
    }

    public String getTableAlias() { return tableAlias; }
    public String getColumnName() { return columnName; }
    public SqlExpression<?> getExpression() { return expression; }
    public boolean isAscending() { return ascending; }
}
