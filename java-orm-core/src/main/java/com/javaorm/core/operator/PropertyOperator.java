package com.javaorm.core.operator;

import com.javaorm.core.proxy.PropertyPath;

import java.util.Collection;

/**
 * 强类型字段/表达式断言操作符构造器。
 * 直接接在 .and(...) 后面链式调用，例如 .gt(18), .in(subQuery)。
 */
public class PropertyOperator<TQuery, R> {

    private final TQuery query;
    private final ConditionAppender<TQuery> appender;

    public PropertyOperator(TQuery query, ConditionAppender<TQuery> appender) {
        this.query = query;
        this.appender = appender;
    }

    public PropertyOperator(TQuery query, PropertyPath path, PathConditionAppender<TQuery> appender) {
        this.query = query;
        this.appender = (op, values) -> appender.append(path, op, values);
    }

    public TQuery eq(Object value) {
        Object operand = resolveOperand(value);
        if (operand == null) {
            return isNull();
        }
        appender.append(SqlOperator.EQ, operand);
        return query;
    }

    public TQuery ne(Object value) {
        Object operand = resolveOperand(value);
        if (operand == null) {
            return isNotNull();
        }
        appender.append(SqlOperator.NE, operand);
        return query;
    }

    public TQuery gt(Object value) {
        Object operand = resolveOperand(value);
        if (operand == null) {
            throw new IllegalArgumentException("Comparison operator > does not support null value");
        }
        appender.append(SqlOperator.GT, operand);
        return query;
    }

    public TQuery ge(Object value) {
        Object operand = resolveOperand(value);
        if (operand == null) {
            throw new IllegalArgumentException("Comparison operator >= does not support null value");
        }
        appender.append(SqlOperator.GE, operand);
        return query;
    }

    public TQuery lt(Object value) {
        Object operand = resolveOperand(value);
        if (operand == null) {
            throw new IllegalArgumentException("Comparison operator < does not support null value");
        }
        appender.append(SqlOperator.LT, operand);
        return query;
    }

    public TQuery le(Object value) {
        Object operand = resolveOperand(value);
        if (operand == null) {
            throw new IllegalArgumentException("Comparison operator <= does not support null value");
        }
        appender.append(SqlOperator.LE, operand);
        return query;
    }

    private Object resolveOperand(Object value) {
        if (value instanceof com.javaorm.core.ast.SqlExpression) {
            return value;
        }
        PropertyPath rightPath = com.javaorm.core.proxy.RecordingContext.finish();
        if (rightPath != null) {
            return new com.javaorm.core.ast.ColumnExpression(rightPath);
        }
        return value;
    }

    public TQuery like(String pattern) {
        appender.append(SqlOperator.LIKE, pattern);
        return query;
    }

    public TQuery likeLeft(String value) {
        return like("%" + value);
    }

    public TQuery likeRight(String value) {
        return like(value + "%");
    }

    public TQuery likeAll(String value) {
        return like("%" + value + "%");
    }

    public TQuery notLike(String pattern) {
        appender.append(SqlOperator.NOT_LIKE, pattern);
        return query;
    }

    public TQuery isNull() {
        appender.append(SqlOperator.IS_NULL);
        return query;
    }

    public TQuery isNotNull() {
        appender.append(SqlOperator.IS_NOT_NULL);
        return query;
    }

    public TQuery in(Collection<?> values) {
        appender.append(SqlOperator.IN, values);
        return query;
    }

    public TQuery in(Object... values) {
        if (values != null && values.length == 1 && values[0] instanceof Collection) {
            return in((Collection<?>) values[0]);
        }
        appender.append(SqlOperator.IN, (Object[]) values);
        return query;
    }

    public TQuery notIn(Collection<?> values) {
        appender.append(SqlOperator.NOT_IN, values);
        return query;
    }

    public TQuery notIn(Object... values) {
        if (values != null && values.length == 1 && values[0] instanceof Collection) {
            return notIn((Collection<?>) values[0]);
        }
        appender.append(SqlOperator.NOT_IN, (Object[]) values);
        return query;
    }

    public TQuery between(Object min, Object max) {
        appender.append(SqlOperator.BETWEEN, min, max);
        return query;
    }
}
