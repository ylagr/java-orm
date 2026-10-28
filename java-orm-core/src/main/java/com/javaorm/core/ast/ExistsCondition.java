package com.javaorm.core.ast;

public class ExistsCondition implements Condition {

    private LogicalOperator logicalOperator = LogicalOperator.AND;
    private final boolean notExists;
    private final Object subQuery; // Query<?>

    public ExistsCondition(boolean notExists, Object subQuery) {
        this.notExists = notExists;
        this.subQuery = subQuery;
    }

    public ExistsCondition(Object subQuery, boolean notExists) {
        this.notExists = notExists;
        this.subQuery = subQuery;
    }

    @Override
    public LogicalOperator getLogicalOperator() { return logicalOperator; }
    @Override
    public void setLogicalOperator(LogicalOperator logicalOperator) { this.logicalOperator = logicalOperator; }

    public boolean isNotExists() { return notExists; }
    public Object getSubQuery() { return subQuery; }
}
