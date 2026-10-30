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

    private boolean checkCondition(boolean condition) {
        if (!condition) {
            if (query instanceof WhereBuilder) {
                ((WhereBuilder<?>) query).cancelCurrentCondition();
            } else {
                RecordingContext.finish();
            }
            return false;
        }
        return true;
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

    public TQuery eq(boolean condition, R value) {
        if (!checkCondition(condition)) {
            return query;
        }
        return eq(value);
    }

    public TQuery eq(R value) {
        Object operand = resolveOperand(value);
        if (operand == null) {
            return isNull();
        }
        appender.append(SqlOperator.EQ, operand);
        return query;
    }

    public TQuery eq(boolean condition, Number value, Object... dummy) {
        if (!checkCondition(condition)) {
            return query;
        }
        return eq(value, dummy);
    }

    public TQuery eq(Number value, Object... dummy) {
        Object operand = resolveOperand(value);
        if (operand == null) {
            return isNull();
        }
        appender.append(SqlOperator.EQ, operand);
        return query;
    }

    public TQuery eq(boolean condition, SqlExpression<? extends R> expr, Object... dummy) {
        if (!checkCondition(condition)) {
            return query;
        }
        return eq(expr, dummy);
    }

    public TQuery eq(SqlExpression<? extends R> expr, Object... dummy) {
        appender.append(SqlOperator.EQ, expr);
        return query;
    }

    public TQuery eq(boolean condition, Query<?> subQuery, Object... dummy) {
        if (!checkCondition(condition)) {
            return query;
        }
        return eq(subQuery, dummy);
    }

    public TQuery eq(Query<?> subQuery, Object... dummy) {
        appender.append(SqlOperator.EQ, subQuery);
        return query;
    }

    public TQuery eq(boolean condition, WhereBuilder<?> subQuery, Object... dummy) {
        if (!checkCondition(condition)) {
            return query;
        }
        return eq(subQuery, dummy);
    }

    public TQuery eq(WhereBuilder<?> subQuery, Object... dummy) {
        appender.append(SqlOperator.EQ, subQuery);
        return query;
    }

    public TQuery ne(boolean condition, R value) {
        if (!checkCondition(condition)) {
            return query;
        }
        return ne(value);
    }

    public TQuery ne(R value) {
        Object operand = resolveOperand(value);
        if (operand == null) {
            return isNotNull();
        }
        appender.append(SqlOperator.NE, operand);
        return query;
    }

    public TQuery ne(boolean condition, Number value, Object... dummy) {
        if (!checkCondition(condition)) {
            return query;
        }
        return ne(value, dummy);
    }

    public TQuery ne(Number value, Object... dummy) {
        Object operand = resolveOperand(value);
        if (operand == null) {
            return isNotNull();
        }
        appender.append(SqlOperator.NE, operand);
        return query;
    }

    public TQuery ne(boolean condition, SqlExpression<? extends R> expr, Object... dummy) {
        if (!checkCondition(condition)) {
            return query;
        }
        return ne(expr, dummy);
    }

    public TQuery ne(SqlExpression<? extends R> expr, Object... dummy) {
        appender.append(SqlOperator.NE, expr);
        return query;
    }

    public TQuery ne(boolean condition, Query<?> subQuery, Object... dummy) {
        if (!checkCondition(condition)) {
            return query;
        }
        return ne(subQuery, dummy);
    }

    public TQuery ne(Query<?> subQuery, Object... dummy) {
        appender.append(SqlOperator.NE, subQuery);
        return query;
    }

    public TQuery ne(boolean condition, WhereBuilder<?> subQuery, Object... dummy) {
        if (!checkCondition(condition)) {
            return query;
        }
        return ne(subQuery, dummy);
    }

    public TQuery ne(WhereBuilder<?> subQuery, Object... dummy) {
        appender.append(SqlOperator.NE, subQuery);
        return query;
    }

    public TQuery gt(boolean condition, R value) {
        if (!checkCondition(condition)) {
            return query;
        }
        return gt(value);
    }

    public TQuery gt(R value) {
        Object operand = resolveOperand(value);
        if (operand == null) {
            throw new IllegalArgumentException("Comparison operator > does not support null value");
        }
        appender.append(SqlOperator.GT, operand);
        return query;
    }

    public TQuery gt(boolean condition, Number value, Object... dummy) {
        if (!checkCondition(condition)) {
            return query;
        }
        return gt(value, dummy);
    }

    public TQuery gt(Number value, Object... dummy) {
        Object operand = resolveOperand(value);
        if (operand == null) {
            throw new IllegalArgumentException("Comparison operator > does not support null value");
        }
        appender.append(SqlOperator.GT, operand);
        return query;
    }

    public TQuery gt(boolean condition, SqlExpression<? extends R> expr, Object... dummy) {
        if (!checkCondition(condition)) {
            return query;
        }
        return gt(expr, dummy);
    }

    public TQuery gt(SqlExpression<? extends R> expr, Object... dummy) {
        appender.append(SqlOperator.GT, expr);
        return query;
    }

    public TQuery gt(boolean condition, Query<?> subQuery, Object... dummy) {
        if (!checkCondition(condition)) {
            return query;
        }
        return gt(subQuery, dummy);
    }

    public TQuery gt(Query<?> subQuery, Object... dummy) {
        appender.append(SqlOperator.GT, subQuery);
        return query;
    }

    public TQuery gt(boolean condition, WhereBuilder<?> subQuery, Object... dummy) {
        if (!checkCondition(condition)) {
            return query;
        }
        return gt(subQuery, dummy);
    }

    public TQuery gt(WhereBuilder<?> subQuery, Object... dummy) {
        appender.append(SqlOperator.GT, subQuery);
        return query;
    }

    public TQuery ge(boolean condition, R value) {
        if (!checkCondition(condition)) {
            return query;
        }
        return ge(value);
    }

    public TQuery ge(R value) {
        Object operand = resolveOperand(value);
        if (operand == null) {
            throw new IllegalArgumentException("Comparison operator >= does not support null value");
        }
        appender.append(SqlOperator.GE, operand);
        return query;
    }

    public TQuery ge(boolean condition, Number value, Object... dummy) {
        if (!checkCondition(condition)) {
            return query;
        }
        return ge(value, dummy);
    }

    public TQuery ge(Number value, Object... dummy) {
        Object operand = resolveOperand(value);
        if (operand == null) {
            throw new IllegalArgumentException("Comparison operator >= does not support null value");
        }
        appender.append(SqlOperator.GE, operand);
        return query;
    }

    public TQuery ge(boolean condition, SqlExpression<? extends R> expr, Object... dummy) {
        if (!checkCondition(condition)) {
            return query;
        }
        return ge(expr, dummy);
    }

    public TQuery ge(SqlExpression<? extends R> expr, Object... dummy) {
        appender.append(SqlOperator.GE, expr);
        return query;
    }

    public TQuery ge(boolean condition, Query<?> subQuery, Object... dummy) {
        if (!checkCondition(condition)) {
            return query;
        }
        return ge(subQuery, dummy);
    }

    public TQuery ge(Query<?> subQuery, Object... dummy) {
        appender.append(SqlOperator.GE, subQuery);
        return query;
    }

    public TQuery ge(boolean condition, WhereBuilder<?> subQuery, Object... dummy) {
        if (!checkCondition(condition)) {
            return query;
        }
        return ge(subQuery, dummy);
    }

    public TQuery ge(WhereBuilder<?> subQuery, Object... dummy) {
        appender.append(SqlOperator.GE, subQuery);
        return query;
    }

    public TQuery lt(boolean condition, R value) {
        if (!checkCondition(condition)) {
            return query;
        }
        return lt(value);
    }

    public TQuery lt(R value) {
        Object operand = resolveOperand(value);
        if (operand == null) {
            throw new IllegalArgumentException("Comparison operator < does not support null value");
        }
        appender.append(SqlOperator.LT, operand);
        return query;
    }

    public TQuery lt(boolean condition, Number value, Object... dummy) {
        if (!checkCondition(condition)) {
            return query;
        }
        return lt(value, dummy);
    }

    public TQuery lt(Number value, Object... dummy) {
        Object operand = resolveOperand(value);
        if (operand == null) {
            throw new IllegalArgumentException("Comparison operator < does not support null value");
        }
        appender.append(SqlOperator.LT, operand);
        return query;
    }

    public TQuery lt(boolean condition, SqlExpression<? extends R> expr, Object... dummy) {
        if (!checkCondition(condition)) {
            return query;
        }
        return lt(expr, dummy);
    }

    public TQuery lt(SqlExpression<? extends R> expr, Object... dummy) {
        appender.append(SqlOperator.LT, expr);
        return query;
    }

    public TQuery lt(boolean condition, Query<?> subQuery, Object... dummy) {
        if (!checkCondition(condition)) {
            return query;
        }
        return lt(subQuery, dummy);
    }

    public TQuery lt(Query<?> subQuery, Object... dummy) {
        appender.append(SqlOperator.LT, subQuery);
        return query;
    }

    public TQuery lt(boolean condition, WhereBuilder<?> subQuery, Object... dummy) {
        if (!checkCondition(condition)) {
            return query;
        }
        return lt(subQuery, dummy);
    }

    public TQuery lt(WhereBuilder<?> subQuery, Object... dummy) {
        appender.append(SqlOperator.LT, subQuery);
        return query;
    }

    public TQuery le(boolean condition, R value) {
        if (!checkCondition(condition)) {
            return query;
        }
        return le(value);
    }

    public TQuery le(R value) {
        Object operand = resolveOperand(value);
        if (operand == null) {
            throw new IllegalArgumentException("Comparison operator <= does not support null value");
        }
        appender.append(SqlOperator.LE, operand);
        return query;
    }

    public TQuery le(boolean condition, Number value, Object... dummy) {
        if (!checkCondition(condition)) {
            return query;
        }
        return le(value, dummy);
    }

    public TQuery le(Number value, Object... dummy) {
        Object operand = resolveOperand(value);
        if (operand == null) {
            throw new IllegalArgumentException("Comparison operator <= does not support null value");
        }
        appender.append(SqlOperator.LE, operand);
        return query;
    }

    public TQuery le(boolean condition, SqlExpression<? extends R> expr, Object... dummy) {
        if (!checkCondition(condition)) {
            return query;
        }
        return le(expr, dummy);
    }

    public TQuery le(SqlExpression<? extends R> expr, Object... dummy) {
        appender.append(SqlOperator.LE, expr);
        return query;
    }

    public TQuery le(boolean condition, Query<?> subQuery, Object... dummy) {
        if (!checkCondition(condition)) {
            return query;
        }
        return le(subQuery, dummy);
    }

    public TQuery le(Query<?> subQuery, Object... dummy) {
        appender.append(SqlOperator.LE, subQuery);
        return query;
    }

    public TQuery le(boolean condition, WhereBuilder<?> subQuery, Object... dummy) {
        if (!checkCondition(condition)) {
            return query;
        }
        return le(subQuery, dummy);
    }

    public TQuery le(WhereBuilder<?> subQuery, Object... dummy) {
        appender.append(SqlOperator.LE, subQuery);
        return query;
    }

    private Object resolveOperand(Object value) {
        if (value instanceof SqlExpression) {
            return value;
        }
        PropertyPath rightPath = RecordingContext.finishCurrent();
        if (rightPath != null) {
            return new ColumnExpression<>(rightPath);
        }
        return value;
    }

    public TQuery like(boolean condition, String pattern) {
        if (!checkCondition(condition)) {
            return query;
        }
        return like(pattern);
    }

    public TQuery like(String pattern) {
        appender.append(SqlOperator.LIKE, pattern);
        return query;
    }

    public TQuery likeLeft(boolean condition, String value) {
        if (!checkCondition(condition)) {
            return query;
        }
        return likeLeft(value);
    }

    public TQuery likeLeft(String value) {
        return like("%" + value);
    }

    public TQuery likeRight(boolean condition, String value) {
        if (!checkCondition(condition)) {
            return query;
        }
        return likeRight(value);
    }

    public TQuery likeRight(String value) {
        return like(value + "%");
    }

    public TQuery likeAll(boolean condition, String value) {
        if (!checkCondition(condition)) {
            return query;
        }
        return likeAll(value);
    }

    public TQuery likeAll(String value) {
        return like("%" + value + "%");
    }

    public TQuery notLike(boolean condition, String pattern) {
        if (!checkCondition(condition)) {
            return query;
        }
        return notLike(pattern);
    }

    public TQuery notLike(String pattern) {
        appender.append(SqlOperator.NOT_LIKE, pattern);
        return query;
    }

    public TQuery likeMatchLeft(boolean condition, String value) {
        if (!checkCondition(condition)) {
            return query;
        }
        return likeMatchLeft(value);
    }

    public TQuery likeMatchLeft(String value) {
        return like(value + "%");
    }

    public TQuery likeMatchRight(boolean condition, String value) {
        if (!checkCondition(condition)) {
            return query;
        }
        return likeMatchRight(value);
    }

    public TQuery likeMatchRight(String value) {
        return like("%" + value);
    }

    public TQuery notLikeMatchLeft(boolean condition, String value) {
        if (!checkCondition(condition)) {
            return query;
        }
        return notLikeMatchLeft(value);
    }

    public TQuery notLikeMatchLeft(String value) {
        return notLike(value + "%");
    }

    public TQuery notLikeMatchRight(boolean condition, String value) {
        if (!checkCondition(condition)) {
            return query;
        }
        return notLikeMatchRight(value);
    }

    public TQuery notLikeMatchRight(String value) {
        return notLike("%" + value);
    }

    public TQuery isEmpty(boolean condition) {
        if (!checkCondition(condition)) {
            return query;
        }
        return isEmpty();
    }

    public TQuery isEmpty() {
        appender.append(SqlOperator.IS_EMPTY);
        return query;
    }

    public TQuery isNotEmpty(boolean condition) {
        if (!checkCondition(condition)) {
            return query;
        }
        return isNotEmpty();
    }

    public TQuery isNotEmpty() {
        appender.append(SqlOperator.IS_NOT_EMPTY);
        return query;
    }

    public TQuery isBlank(boolean condition) {
        if (!checkCondition(condition)) {
            return query;
        }
        return isBlank();
    }

    public TQuery isBlank() {
        appender.append(SqlOperator.IS_BLANK);
        return query;
    }

    public TQuery isNotBlank(boolean condition) {
        if (!checkCondition(condition)) {
            return query;
        }
        return isNotBlank();
    }

    public TQuery isNotBlank() {
        appender.append(SqlOperator.IS_NOT_BLANK);
        return query;
    }

    public TQuery rangeClosed(boolean condition, boolean condLeft, R left, boolean condRight, R right) {
        if (!checkCondition(condition)) {
            return query;
        }
        boolean hasLeft = condLeft && left != null;
        boolean hasRight = condRight && right != null;
        if (hasLeft && hasRight) {
            appender.append(SqlOperator.RANGE_CLOSED, left, right);
        } else if (hasLeft) {
            ge(left);
        } else if (hasRight) {
            le(right);
        }
        return query;
    }

    public TQuery rangeClosed(boolean condLeft, R left, boolean condRight, R right) {
        return rangeClosed(true, condLeft, left, condRight, right);
    }

    public TQuery rangeClosed(boolean condition, R left, R right) {
        return rangeClosed(condition, true, left, true, right);
    }

    public TQuery rangeClosed(R left, R right) {
        return rangeClosed(true, true, left, true, right);
    }

    public TQuery rangeOpen(boolean condition, boolean condLeft, R left, boolean condRight, R right) {
        if (!checkCondition(condition)) {
            return query;
        }
        boolean hasLeft = condLeft && left != null;
        boolean hasRight = condRight && right != null;
        if (hasLeft && hasRight) {
            appender.append(SqlOperator.RANGE_OPEN, left, right);
        } else if (hasLeft) {
            gt(left);
        } else if (hasRight) {
            lt(right);
        }
        return query;
    }

    public TQuery rangeOpen(boolean condLeft, R left, boolean condRight, R right) {
        return rangeOpen(true, condLeft, left, condRight, right);
    }

    public TQuery rangeOpen(boolean condition, R left, R right) {
        return rangeOpen(condition, true, left, true, right);
    }

    public TQuery rangeOpen(R left, R right) {
        return rangeOpen(true, true, left, true, right);
    }

    public TQuery rangeClosedOpen(boolean condition, boolean condLeft, R left, boolean condRight, R right) {
        if (!checkCondition(condition)) {
            return query;
        }
        boolean hasLeft = condLeft && left != null;
        boolean hasRight = condRight && right != null;
        if (hasLeft && hasRight) {
            appender.append(SqlOperator.RANGE_CLOSED_OPEN, left, right);
        } else if (hasLeft) {
            ge(left);
        } else if (hasRight) {
            lt(right);
        }
        return query;
    }

    public TQuery rangeClosedOpen(boolean condLeft, R left, boolean condRight, R right) {
        return rangeClosedOpen(true, condLeft, left, condRight, right);
    }

    public TQuery rangeClosedOpen(boolean condition, R left, R right) {
        return rangeClosedOpen(condition, true, left, true, right);
    }

    public TQuery rangeClosedOpen(R left, R right) {
        return rangeClosedOpen(true, true, left, true, right);
    }

    public TQuery rangeOpenClosed(boolean condition, boolean condLeft, R left, boolean condRight, R right) {
        if (!checkCondition(condition)) {
            return query;
        }
        boolean hasLeft = condLeft && left != null;
        boolean hasRight = condRight && right != null;
        if (hasLeft && hasRight) {
            appender.append(SqlOperator.RANGE_OPEN_CLOSED, left, right);
        } else if (hasLeft) {
            gt(left);
        } else if (hasRight) {
            le(right);
        }
        return query;
    }

    public TQuery rangeOpenClosed(boolean condLeft, R left, boolean condRight, R right) {
        return rangeOpenClosed(true, condLeft, left, condRight, right);
    }

    public TQuery rangeOpenClosed(boolean condition, R left, R right) {
        return rangeOpenClosed(condition, true, left, true, right);
    }

    public TQuery rangeOpenClosed(R left, R right) {
        return rangeOpenClosed(true, true, left, true, right);
    }

    public TQuery isNull(boolean condition) {
        if (!checkCondition(condition)) {
            return query;
        }
        return isNull();
    }

    public TQuery isNull() {
        appender.append(SqlOperator.IS_NULL);
        return query;
    }

    public TQuery isNotNull(boolean condition) {
        if (!checkCondition(condition)) {
            return query;
        }
        return isNotNull();
    }

    public TQuery isNotNull() {
        appender.append(SqlOperator.IS_NOT_NULL);
        return query;
    }

    public TQuery in(boolean condition, Collection<? extends R> values) {
        if (!checkCondition(condition)) {
            return query;
        }
        return in(values);
    }

    public TQuery in(Collection<? extends R> values) {
        appender.append(SqlOperator.IN, values);
        return query;
    }

    @SafeVarargs
    public final TQuery in(boolean condition, R... values) {
        if (!checkCondition(condition)) {
            return query;
        }
        return in(values);
    }

    @SafeVarargs
    public final TQuery in(R... values) {
        if (values != null && values.length == 1 && values[0] instanceof Collection) {
            return in((Collection<? extends R>) values[0]);
        }
        appender.append(SqlOperator.IN, (Object[]) values);
        return query;
    }

    public TQuery in(boolean condition, Query<?> subQuery) {
        if (!checkCondition(condition)) {
            return query;
        }
        return in(subQuery);
    }

    public TQuery in(Query<?> subQuery) {
        appender.append(SqlOperator.IN, subQuery);
        return query;
    }

    public TQuery in(boolean condition, WhereBuilder<?> subQuery) {
        if (!checkCondition(condition)) {
            return query;
        }
        return in(subQuery);
    }

    public TQuery in(WhereBuilder<?> subQuery) {
        appender.append(SqlOperator.IN, subQuery);
        return query;
    }

    public TQuery in(boolean condition, SqlExpression<?> subQuery, Object... dummy) {
        if (!checkCondition(condition)) {
            return query;
        }
        return in(subQuery, dummy);
    }

    public TQuery in(SqlExpression<?> subQuery, Object... dummy) {
        appender.append(SqlOperator.IN, subQuery);
        return query;
    }

    public TQuery notIn(boolean condition, Collection<? extends R> values) {
        if (!checkCondition(condition)) {
            return query;
        }
        return notIn(values);
    }

    public TQuery notIn(Collection<? extends R> values) {
        appender.append(SqlOperator.NOT_IN, values);
        return query;
    }

    @SafeVarargs
    public final TQuery notIn(boolean condition, R... values) {
        if (!checkCondition(condition)) {
            return query;
        }
        return notIn(values);
    }

    @SafeVarargs
    public final TQuery notIn(R... values) {
        if (values != null && values.length == 1 && values[0] instanceof Collection) {
            return notIn((Collection<? extends R>) values[0]);
        }
        appender.append(SqlOperator.NOT_IN, (Object[]) values);
        return query;
    }

    public TQuery notIn(boolean condition, Query<?> subQuery) {
        if (!checkCondition(condition)) {
            return query;
        }
        return notIn(subQuery);
    }

    public TQuery notIn(Query<?> subQuery) {
        appender.append(SqlOperator.NOT_IN, subQuery);
        return query;
    }

    public TQuery notIn(boolean condition, WhereBuilder<?> subQuery) {
        if (!checkCondition(condition)) {
            return query;
        }
        return notIn(subQuery);
    }

    public TQuery notIn(WhereBuilder<?> subQuery) {
        appender.append(SqlOperator.NOT_IN, subQuery);
        return query;
    }

    public TQuery notIn(boolean condition, SqlExpression<?> subQuery, Object... dummy) {
        if (!checkCondition(condition)) {
            return query;
        }
        return notIn(subQuery, dummy);
    }

    public TQuery notIn(SqlExpression<?> subQuery, Object... dummy) {
        appender.append(SqlOperator.NOT_IN, subQuery);
        return query;
    }

    public TQuery between(boolean condition, R min, R max) {
        if (!checkCondition(condition)) {
            return query;
        }
        return between(min, max);
    }

    public TQuery between(R min, R max) {
        appender.append(SqlOperator.BETWEEN, min, max);
        return query;
    }

    public TQuery between(boolean condition, SqlExpression<? extends R> min, SqlExpression<? extends R> max) {
        if (!checkCondition(condition)) {
            return query;
        }
        return between(min, max);
    }

    public TQuery between(SqlExpression<? extends R> min, SqlExpression<? extends R> max) {
        appender.append(SqlOperator.BETWEEN, min, max);
        return query;
    }

    public TQuery between(boolean condition, SqlExpression<? extends R> min, R max) {
        if (!checkCondition(condition)) {
            return query;
        }
        return between(min, max);
    }

    public TQuery between(SqlExpression<? extends R> min, R max) {
        appender.append(SqlOperator.BETWEEN, min, max);
        return query;
    }

    public TQuery between(boolean condition, R min, SqlExpression<? extends R> max) {
        if (!checkCondition(condition)) {
            return query;
        }
        return between(min, max);
    }

    public TQuery between(R min, SqlExpression<? extends R> max) {
        appender.append(SqlOperator.BETWEEN, min, max);
        return query;
    }
}
