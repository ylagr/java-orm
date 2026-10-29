package com.javaorm.core.ast;

import com.javaorm.core.operator.SqlOperator;
import com.javaorm.core.proxy.PropertyPath;
import com.javaorm.core.proxy.RecordingContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * CASE WHEN 条件分支表达式构建器。
 * 支持 ANSI SQL 标准 CASE WHEN ... THEN ... ELSE ... END
 */
public class CaseWhenBuilder implements SqlExpression {

    public static class WhenBranch {
        private final Condition condition;
        private final Object thenVal;

        public WhenBranch(Condition condition, Object thenVal) {
            this.condition = condition;
            this.thenVal = thenVal;
        }

        public Condition getCondition() {
            return condition;
        }

        public Object getThenVal() {
            return thenVal;
        }
    }

    private final List<WhenBranch> branches = new ArrayList<>();
    private Object elseVal;
    private String alias;

    public CaseWhenBuilder() {
    }

    public CaseWhenBuilder(Condition condition, Object thenVal) {
        when(condition, thenVal);
    }

    public CaseWhenBuilder(Object prop, SqlOperator op, Object val, Object thenVal) {
        when(prop, op, val, thenVal);
    }

    public CaseWhenBuilder when(Condition condition, Object thenVal) {
        branches.add(new WhenBranch(condition, thenVal));
        return this;
    }

    public CaseWhenBuilder when(Object prop, SqlOperator op, Object val, Object thenVal) {
        PropertyPath path = RecordingContext.finish();
        Condition condition;
        if (path != null) {
            condition = new BinaryCondition(path, op, val);
        } else if (prop instanceof ColumnExpression) {
            ColumnExpression ce = (ColumnExpression) prop;
            condition = new BinaryCondition(ce.getTableAlias(), ce.getColumnName(), op, val);
        } else if (prop instanceof SqlExpression) {
            condition = new ExpressionCondition((SqlExpression) prop, op, val);
        } else if (prop instanceof String) {
            condition = new BinaryCondition(null, (String) prop, op, val);
        } else {
            condition = new BinaryCondition(null, String.valueOf(prop), op, val);
        }
        branches.add(new WhenBranch(condition, thenVal));
        return this;
    }

    public CaseWhenBuilder elseVal(Object elseVal) {
        this.elseVal = elseVal;
        return this;
    }

    @Override
    public CaseWhenBuilder as(String alias) {
        this.alias = alias;
        return this;
    }

    @Override
    public String getAlias() {
        return alias;
    }

    public List<WhenBranch> getBranches() {
        return Collections.unmodifiableList(branches);
    }

    public Object getElseVal() {
        return elseVal;
    }
}
