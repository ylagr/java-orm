package com.javaorm.core.operator;

import com.javaorm.core.ast.ColumnExpression;
import com.javaorm.core.ast.SqlExpression;
import com.javaorm.core.proxy.PropertyPath;
import com.javaorm.core.proxy.RecordingContext;
import com.javaorm.core.query.Query;
import com.javaorm.core.query.WhereBuilder;

import java.util.Collection;

/**
 * 强类型字段/表达式断言操作符构造器。
 * 直接接在 .and(...) 后面链式调用，例如 .gt(18), .in(subQuery)。
 *
 * @param <TQuery> 所在上下文查询或构造器类型
 * @param <R>      被断言字段或表达式所映射的 Java 强类型
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

    public TQuery op(SqlOperator op, Object... values) {
        if (values != null && values.length == 1) {
            Object operand = resolveOperand(values[0]);
            appender.append(op, operand);
        } else {
            appender.append(op, values);
        }
        return query;
    }

    public TQuery eq(R value) {
        Object operand = resolveOperand(value);
        if (operand == null) {
            return isNull();
        }
        appender.append(SqlOperator.EQ, operand);
        return query;
    }

    public TQuery eq(Number value, Object... dummy) {
        appender.append(SqlOperator.EQ, value);
        return query;
    }

    public TQuery eq(SqlExpression<? extends R> expr, Object... dummy) {
        appender.append(SqlOperator.EQ, expr);
        return query;
    }

    public TQuery eq(Query<?> subQuery, Object... dummy) {
        appender.append(SqlOperator.EQ, subQuery);
        return query;
    }

    public TQuery eq(WhereBuilder<?> subQuery, Object... dummy) {
        appender.append(SqlOperator.EQ, subQuery);
        return query;
    }

    public TQuery ne(R value) {
        Object operand = resolveOperand(value);
        if (operand == null) {
            return isNotNull();
        }
        appender.append(SqlOperator.NE, operand);
        return query;
    }

    public TQuery ne(Number value, Object... dummy) {
        appender.append(SqlOperator.NE, value);
        return query;
    }

    public TQuery ne(SqlExpression<? extends R> expr, Object... dummy) {
        appender.append(SqlOperator.NE, expr);
        return query;
    }

    public TQuery ne(Query<?> subQuery, Object... dummy) {
        appender.append(SqlOperator.NE, subQuery);
        return query;
    }

    public TQuery ne(WhereBuilder<?> subQuery, Object... dummy) {
        appender.append(SqlOperator.NE, subQuery);
        return query;
    }

    public TQuery gt(R value) {
        Object operand = resolveOperand(value);
        if (operand == null) {
            throw new IllegalArgumentException("Comparison operator > does not support null value");
        }
        appender.append(SqlOperator.GT, operand);
        return query;
    }

    public TQuery gt(Number value, Object... dummy) {
        appender.append(SqlOperator.GT, value);
        return query;
    }

    public TQuery gt(SqlExpression<? extends R> expr, Object... dummy) {
        appender.append(SqlOperator.GT, expr);
        return query;
    }

    public TQuery gt(Query<?> subQuery, Object... dummy) {
        appender.append(SqlOperator.GT, subQuery);
        return query;
    }

    public TQuery gt(WhereBuilder<?> subQuery, Object... dummy) {
        appender.append(SqlOperator.GT, subQuery);
        return query;
    }

    public TQuery ge(R value) {
        Object operand = resolveOperand(value);
        if (operand == null) {
            throw new IllegalArgumentException("Comparison operator >= does not support null value");
        }
        appender.append(SqlOperator.GE, operand);
        return query;
    }

    public TQuery ge(Number value, Object... dummy) {
        appender.append(SqlOperator.GE, value);
        return query;
    }

    public TQuery ge(SqlExpression<? extends R> expr, Object... dummy) {
        appender.append(SqlOperator.GE, expr);
        return query;
    }

    public TQuery ge(Query<?> subQuery, Object... dummy) {
        appender.append(SqlOperator.GE, subQuery);
        return query;
    }

    public TQuery ge(WhereBuilder<?> subQuery, Object... dummy) {
        appender.append(SqlOperator.GE, subQuery);
        return query;
    }

    public TQuery lt(R value) {
        Object operand = resolveOperand(value);
        if (operand == null) {
            throw new IllegalArgumentException("Comparison operator < does not support null value");
        }
        appender.append(SqlOperator.LT, operand);
        return query;
    }

    public TQuery lt(Number value, Object... dummy) {
        appender.append(SqlOperator.LT, value);
        return query;
    }

    public TQuery lt(SqlExpression<? extends R> expr, Object... dummy) {
        appender.append(SqlOperator.LT, expr);
        return query;
    }

    public TQuery lt(Query<?> subQuery, Object... dummy) {
        appender.append(SqlOperator.LT, subQuery);
        return query;
    }

    public TQuery lt(WhereBuilder<?> subQuery, Object... dummy) {
        appender.append(SqlOperator.LT, subQuery);
        return query;
    }

    public TQuery le(R value) {
        Object operand = resolveOperand(value);
        if (operand == null) {
            throw new IllegalArgumentException("Comparison operator <= does not support null value");
        }
        appender.append(SqlOperator.LE, operand);
        return query;
    }

    public TQuery le(Number value, Object... dummy) {
        appender.append(SqlOperator.LE, value);
        return query;
    }

    public TQuery le(SqlExpression<? extends R> expr, Object... dummy) {
        appender.append(SqlOperator.LE, expr);
        return query;
    }

    public TQuery le(Query<?> subQuery, Object... dummy) {
        appender.append(SqlOperator.LE, subQuery);
        return query;
    }

    public TQuery le(WhereBuilder<?> subQuery, Object... dummy) {
        appender.append(SqlOperator.LE, subQuery);
        return query;
    }

    private Object resolveOperand(Object value) {
        if (value instanceof SqlExpression) {
            return value;
        }
        PropertyPath rightPath = RecordingContext.finish();
        if (rightPath != null) {
            return new ColumnExpression<>(rightPath);
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

    public TQuery in(Collection<? extends R> values) {
        appender.append(SqlOperator.IN, values);
        return query;
    }

    @SafeVarargs
    public final TQuery in(R... values) {
        if (values != null && values.length == 1 && values[0] instanceof Collection) {
            return in((Collection<? extends R>) values[0]);
        }
        appender.append(SqlOperator.IN, (Object[]) values);
        return query;
    }

    public TQuery in(Query<?> subQuery) {
        appender.append(SqlOperator.IN, subQuery);
        return query;
    }

    public TQuery in(WhereBuilder<?> subQuery) {
        appender.append(SqlOperator.IN, subQuery);
        return query;
    }

    public TQuery in(SqlExpression<?> subQuery, Object... dummy) {
        appender.append(SqlOperator.IN, subQuery);
        return query;
    }

    public TQuery notIn(Collection<? extends R> values) {
        appender.append(SqlOperator.NOT_IN, values);
        return query;
    }

    @SafeVarargs
    public final TQuery notIn(R... values) {
        if (values != null && values.length == 1 && values[0] instanceof Collection) {
            return notIn((Collection<? extends R>) values[0]);
        }
        appender.append(SqlOperator.NOT_IN, (Object[]) values);
        return query;
    }

    public TQuery notIn(Query<?> subQuery) {
        appender.append(SqlOperator.NOT_IN, subQuery);
        return query;
    }

    public TQuery notIn(WhereBuilder<?> subQuery) {
        appender.append(SqlOperator.NOT_IN, subQuery);
        return query;
    }

    public TQuery notIn(SqlExpression<?> subQuery, Object... dummy) {
        appender.append(SqlOperator.NOT_IN, subQuery);
        return query;
    }

    public TQuery between(R min, R max) {
        appender.append(SqlOperator.BETWEEN, min, max);
        return query;
    }

    public TQuery between(SqlExpression<? extends R> min, SqlExpression<? extends R> max) {
        appender.append(SqlOperator.BETWEEN, min, max);
        return query;
    }

    public TQuery between(SqlExpression<? extends R> min, R max) {
        appender.append(SqlOperator.BETWEEN, min, max);
        return query;
    }

    public TQuery between(R min, SqlExpression<? extends R> max) {
        appender.append(SqlOperator.BETWEEN, min, max);
        return query;
    }
}
