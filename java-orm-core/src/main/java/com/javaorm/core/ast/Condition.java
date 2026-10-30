package com.javaorm.core.ast;

public interface Condition {
    LogicalOperator getLogicalOperator();

    void setLogicalOperator(LogicalOperator logicalOperator);
}
