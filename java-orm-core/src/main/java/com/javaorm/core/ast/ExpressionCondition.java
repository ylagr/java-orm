package com.javaorm.core.ast;

import com.javaorm.core.operator.SqlOperator;

public class ExpressionCondition implements Condition {

    private LogicalOperator logicalOperator = LogicalOperator.AND;
    private final SqlExpression<?> expression;
    private final SqlOperator operator;
    private final Object[] values;

    public ExpressionCondition(SqlExpression<?> expression, SqlOperator operator, Object... values) {
        this.expression = expression;
        this.operator = operator;
        this.values = values;
    }

    @Override
    public LogicalOperator getLogicalOperator() { return logicalOperator; }
    @Override
    public void setLogicalOperator(LogicalOperator logicalOperator) { this.logicalOperator = logicalOperator; }

    public SqlExpression<?> getExpression() { return expression; }
    public SqlOperator getOperator() { return operator; }
    public Object[] getValues() { return values; }
}
