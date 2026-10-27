package com.javaorm.core.query;

import com.javaorm.core.ast.OrderBy;

public class OrderByBuilder<T> {
    private final Query<T> query;
    private final String tableAlias;
    private final String columnName;

    public OrderByBuilder(Query<T> query, String tableAlias, String columnName) {
        this.query = query;
        this.tableAlias = tableAlias;
        this.columnName = columnName;
    }

    public Query<T> asc() {
        query.getStatement().addOrderBy(new OrderBy(tableAlias, columnName, true));
        return query;
    }

    public Query<T> desc() {
        query.getStatement().addOrderBy(new OrderBy(tableAlias, columnName, false));
        return query;
    }
}
