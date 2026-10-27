package com.javaorm.core.ast;

import java.util.ArrayList;
import java.util.List;

public class GroupCondition implements Condition {
    private LogicalOperator logicalOperator = LogicalOperator.AND;
    private final List<Condition> children = new ArrayList<>();

    public GroupCondition(LogicalOperator logicalOperator) {
        this.logicalOperator = logicalOperator;
    }

    public void addCondition(Condition condition) {
        children.add(condition);
    }

    public List<Condition> getChildren() {
        return children;
    }

    @Override
    public LogicalOperator getLogicalOperator() { return logicalOperator; }
    @Override
    public void setLogicalOperator(LogicalOperator logicalOperator) { this.logicalOperator = logicalOperator; }
}
