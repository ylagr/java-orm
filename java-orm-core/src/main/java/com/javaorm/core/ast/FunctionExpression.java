package com.javaorm.core.ast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FunctionExpression implements SqlExpression {

    private final String functionName;
    private final List<Object> arguments = new ArrayList<>();
    private String alias;

    public FunctionExpression(String functionName, Object... args) {
        this.functionName = functionName;
        if (args != null && args.length > 0) {
            this.arguments.addAll(Arrays.asList(args));
        }
    }

    @Override
    public String getAlias() {
        return alias;
    }

    @Override
    public FunctionExpression as(String alias) {
        this.alias = alias;
        return this;
    }

    public String getFunctionName() {
        return functionName;
    }

    public List<Object> getArguments() {
        return arguments;
    }
}
