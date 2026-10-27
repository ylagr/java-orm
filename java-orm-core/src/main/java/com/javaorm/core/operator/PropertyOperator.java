package com.javaorm.core.operator;

import com.javaorm.core.proxy.PropertyPath;

import java.util.Arrays;
import java.util.Collection;

/**
 * 强类型字段断言操作符构造器。
 * 直接接在 .and(s -> s.getAge()) 后面链式调用，例如 .gt(18)。
 */
public class PropertyOperator<TQuery, R> {

    private final TQuery query;
    private final PropertyPath path;
    private final ConditionAppender<TQuery> appender;

    public PropertyOperator(TQuery query, PropertyPath path, ConditionAppender<TQuery> appender) {
        this.query = query;
        this.path = path;
        this.appender = appender;
    }

    public TQuery eq(R value) {
        appender.append(path, SqlOperator.EQ, value);
        return query;
    }

    public TQuery ne(R value) {
        appender.append(path, SqlOperator.NE, value);
        return query;
    }

    public TQuery gt(R value) {
        appender.append(path, SqlOperator.GT, value);
        return query;
    }

    public TQuery ge(R value) {
        appender.append(path, SqlOperator.GE, value);
        return query;
    }

    public TQuery lt(R value) {
        appender.append(path, SqlOperator.LT, value);
        return query;
    }

    public TQuery le(R value) {
        appender.append(path, SqlOperator.LE, value);
        return query;
    }

    public TQuery like(String pattern) {
        appender.append(path, SqlOperator.LIKE, pattern);
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
        appender.append(path, SqlOperator.NOT_LIKE, pattern);
        return query;
    }

    public TQuery isNull() {
        appender.append(path, SqlOperator.IS_NULL);
        return query;
    }

    public TQuery isNotNull() {
        appender.append(path, SqlOperator.IS_NOT_NULL);
        return query;
    }

    public TQuery in(Collection<R> values) {
        appender.append(path, SqlOperator.IN, values);
        return query;
    }

    @SuppressWarnings("unchecked")
    public TQuery in(R... values) {
        return in(Arrays.asList(values));
    }

    public TQuery notIn(Collection<R> values) {
        appender.append(path, SqlOperator.NOT_IN, values);
        return query;
    }

    @SuppressWarnings("unchecked")
    public TQuery notIn(R... values) {
        return notIn(Arrays.asList(values));
    }

    public TQuery between(R min, R max) {
        appender.append(path, SqlOperator.BETWEEN, min, max);
        return query;
    }
}
