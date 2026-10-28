package com.javaorm.core.ast;

/**
 * 自增/自减/倍乘等原地更新表达式。
 * 例如：SET age = age + 1, stock = stock - 1
 */
public class IncrementExpression implements SqlExpression {

    private final Number step;
    private final String operator;
    private String alias;

    public IncrementExpression(Number step, String operator) {
        this.step = step;
        this.operator = operator != null ? operator : "+";
    }

    public Number getStep() {
        return step;
    }

    public String getOperator() {
        return operator;
    }

    @Override
    public SqlExpression as(String alias) {
        this.alias = alias;
        return this;
    }

    @Override
    public String getAlias() {
        return alias;
    }
}
