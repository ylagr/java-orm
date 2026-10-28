package com.javaorm.core.query;

import com.javaorm.core.ast.OrderBy;
import com.javaorm.core.ast.SelectStatement;
import com.javaorm.core.ast.SqlExpression;

public class OrderByBuilder<TQuery> {
    private final TQuery query;
    private final SelectStatement statement;
    private String tableAlias;
    private String columnName;
    private SqlExpression expression;

    public OrderByBuilder(TQuery query, SelectStatement statement, String tableAlias, String columnName) {
        this.query = query;
        this.statement = statement;
        this.tableAlias = tableAlias;
        this.columnName = columnName;
    }

    public OrderByBuilder(TQuery query, SelectStatement statement, SqlExpression expression) {
        this.query = query;
        this.statement = statement;
        this.expression = expression;
    }

    public TQuery asc() {
        if (expression != null) {
            statement.addOrderBy(new OrderBy(expression, true));
        } else {
            statement.addOrderBy(new OrderBy(tableAlias, columnName, true));
        }
        return query;
    }

    public TQuery desc() {
        if (expression != null) {
            statement.addOrderBy(new OrderBy(expression, false));
        } else {
            statement.addOrderBy(new OrderBy(tableAlias, columnName, false));
        }
        return query;
    }
}
