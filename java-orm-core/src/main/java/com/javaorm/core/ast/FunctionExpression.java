package com.javaorm.core.ast;

import com.javaorm.core.function.SqlFunction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FunctionExpression<T> implements SqlExpression<T> {

    private final SqlFunction function;
    private final List<Object> arguments = new ArrayList<>();
    private String alias;
    private boolean distinct;

    public FunctionExpression(SqlFunction function, Object... args) {
        if (function == null) {
            throw new IllegalArgumentException("SqlFunction cannot be null");
        }
        this.function = function;
        if (args != null && args.length > 0) {
            this.arguments.addAll(Arrays.asList(args));
        }
    }

    public FunctionExpression(String functionName, Object... args) {
        this(SqlFunction.of(functionName), args);
    }

    public SqlFunction getFunction() {
        return function;
    }

    public FunctionExpression<T> distinct() {
        this.distinct = true;
        return this;
    }

    public FunctionExpression<T> distinct(boolean distinct) {
        this.distinct = distinct;
        return this;
    }

    public boolean isDistinct() {
        return distinct;
    }

    @Override
    public String getAlias() {
        return alias;
    }

    @Override
    public FunctionExpression<T> as(String alias) {
        this.alias = alias;
        return this;
    }

    public String getFunctionName() {
        return function.getName();
    }

    public List<Object> getArguments() {
        return arguments;
    }
}
