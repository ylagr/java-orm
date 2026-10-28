package com.javaorm.core.query;

import com.javaorm.core.ast.BinaryCondition;
import com.javaorm.core.ast.Condition;
import com.javaorm.core.ast.GroupCondition;
import com.javaorm.core.ast.LogicalOperator;
import com.javaorm.core.operator.ConditionAppender;
import com.javaorm.core.operator.PropertyOperator;
import com.javaorm.core.operator.SqlOperator;
import com.javaorm.core.proxy.PropertyPath;
import com.javaorm.core.proxy.ProxyFactory;
import com.javaorm.core.proxy.RecordingContext;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * WHERE 条件链式构造器。
 * 支持两种断言方式：
 * 1. 代理值直接调用：w.and(s.getAge()).gt(18)
 * 2. Lambda 表达式调用：w.and(s -> s.getAge()).gt(18)
 */
public class WhereBuilder<T> {

    @FunctionalInterface
    public interface BiWhereConsumer<T> {
        void accept(WhereBuilder<T> w, T s);
    }

    private final Class<T> entityClass;
    private final Query<?> rootQuery;
    private final String scopeAlias;
    private final Consumer<Condition> conditionReceiver;

    @SuppressWarnings("unchecked")
    public WhereBuilder(Query<T> query) {
        this.entityClass = (Class<T>) query.getStatement().getRootEntityClass();
        this.rootQuery = query;
        this.scopeAlias = query.getStatement().getRootTableAlias();
        this.conditionReceiver = query.getStatement()::addWhereCondition;
    }

    public WhereBuilder(Class<T> entityClass, Query<?> rootQuery, String scopeAlias) {
        this.entityClass = entityClass;
        this.rootQuery = rootQuery;
        this.scopeAlias = scopeAlias;
        this.conditionReceiver = rootQuery.getStatement()::addWhereCondition;
    }

    public WhereBuilder(Class<T> entityClass, Query<?> rootQuery, String scopeAlias, Consumer<Condition> conditionReceiver) {
        this.entityClass = entityClass;
        this.rootQuery = rootQuery;
        this.scopeAlias = scopeAlias;
        this.conditionReceiver = conditionReceiver;
    }

    /**
     * 获取当前实体类型的录制代理对象
     */
    public T proxy() {
        return ProxyFactory.getProxy(entityClass);
    }

    /**
     * 允许语法糖调用 .where()
     */
    public WhereBuilder<T> where() {
        return this;
    }

    /**
     * 方式 1：直接传入代理 getter 调用结果，如 w.and(s.getAge()).gt(18)
     */
    public <R> PropertyOperator<WhereBuilder<T>, R> and(R val) {
        PropertyPath path = RecordingContext.finish();
        if (path == null) {
            throw new IllegalStateException("No proxy property accessed before calling and(...)! Did you call s.getFoo()?");
        }
        return new PropertyOperator<>(this, path, (p, op, vals) -> appendCondition(LogicalOperator.AND, p, op, vals));
    }

    /**
     * 方式 2：传入 Lambda 表达式，如 w.and(s -> s.getAge()).gt(18)
     */
    public <R> PropertyOperator<WhereBuilder<T>, R> and(Function<T, R> getter) {
        PropertyPath path = recordPath(getter);
        return new PropertyOperator<>(this, path, (p, op, vals) -> appendCondition(LogicalOperator.AND, p, op, vals));
    }

    /**
     * 方式 1：直接传入代理 getter 调用结果，如 w.or(s.getAge()).lt(10)
     */
    public <R> PropertyOperator<WhereBuilder<T>, R> or(R val) {
        PropertyPath path = RecordingContext.finish();
        if (path == null) {
            throw new IllegalStateException("No proxy property accessed before calling or(...)! Did you call s.getFoo()?");
        }
        return new PropertyOperator<>(this, path, (p, op, vals) -> appendCondition(LogicalOperator.OR, p, op, vals));
    }

    /**
     * 方式 2：传入 Lambda 表达式，如 w.or(s -> s.getAge()).lt(10)
     */
    public <R> PropertyOperator<WhereBuilder<T>, R> or(Function<T, R> getter) {
        PropertyPath path = recordPath(getter);
        return new PropertyOperator<>(this, path, (p, op, vals) -> appendCondition(LogicalOperator.OR, p, op, vals));
    }

    public WhereBuilder<T> andGroup(Consumer<WhereBuilder<T>> groupConsumer) {
        GroupCondition group = new GroupCondition(LogicalOperator.AND);
        WhereBuilder<T> sub = new WhereBuilder<>(entityClass, rootQuery, scopeAlias, group::addCondition);
        groupConsumer.accept(sub);
        if (!group.getChildren().isEmpty()) {
            conditionReceiver.accept(group);
        }
        return this;
    }

    public WhereBuilder<T> andGroup(BiWhereConsumer<T> groupConsumer) {
        GroupCondition group = new GroupCondition(LogicalOperator.AND);
        WhereBuilder<T> sub = new WhereBuilder<>(entityClass, rootQuery, scopeAlias, group::addCondition);
        groupConsumer.accept(sub, sub.proxy());
        if (!group.getChildren().isEmpty()) {
            conditionReceiver.accept(group);
        }
        return this;
    }

    public WhereBuilder<T> orGroup(Consumer<WhereBuilder<T>> groupConsumer) {
        GroupCondition group = new GroupCondition(LogicalOperator.OR);
        WhereBuilder<T> sub = new WhereBuilder<>(entityClass, rootQuery, scopeAlias, group::addCondition);
        groupConsumer.accept(sub);
        if (!group.getChildren().isEmpty()) {
            conditionReceiver.accept(group);
        }
        return this;
    }

    public WhereBuilder<T> orGroup(BiWhereConsumer<T> groupConsumer) {
        GroupCondition group = new GroupCondition(LogicalOperator.OR);
        WhereBuilder<T> sub = new WhereBuilder<>(entityClass, rootQuery, scopeAlias, group::addCondition);
        groupConsumer.accept(sub, sub.proxy());
        if (!group.getChildren().isEmpty()) {
            conditionReceiver.accept(group);
        }
        return this;
    }

    private void appendCondition(LogicalOperator logOp, PropertyPath path, SqlOperator op, Object... vals) {
        String alias;
        String colName;

        if (path.hasRelations()) {
            alias = rootQuery.resolveJoinAlias(path);
            colName = rootQuery.resolveColumnName(path);
        } else {
            alias = scopeAlias;
            colName = rootQuery.resolveColumnName(path);
        }

        BinaryCondition cond = new BinaryCondition(alias, colName, op, vals);
        cond.setLogicalOperator(logOp);
        conditionReceiver.accept(cond);
    }

    private <R> PropertyPath recordPath(Function<T, R> getter) {
        RecordingContext.start(entityClass);
        try {
            T proxy = ProxyFactory.getProxy(entityClass);
            getter.apply(proxy);
            return RecordingContext.finish();
        } finally {
            RecordingContext.clear();
        }
    }

    // 快捷委托给 rootQuery 结束条件并执行
    @SuppressWarnings("unchecked")
    public <R> OrderByBuilder<T> orderBy(Function<T, R> propertyGetter) {
        PropertyPath path = recordPath(propertyGetter);
        String alias = path.hasRelations() ? rootQuery.resolveJoinAlias(path) : scopeAlias;
        String colName = rootQuery.resolveColumnName(path);
        return new OrderByBuilder<>((Query<T>) rootQuery, alias, colName);
    }

    @SuppressWarnings("unchecked")
    public Query<T> page(int pageNum, int pageSize) {
        return (Query<T>) rootQuery.page(pageNum, pageSize);
    }

    @SuppressWarnings("unchecked")
    public Query<T> limit(int limit) {
        return (Query<T>) rootQuery.limit(limit);
    }

    @SuppressWarnings("unchecked")
    public Query<T> offset(int offset) {
        return (Query<T>) rootQuery.offset(offset);
    }

    @SuppressWarnings("unchecked")
    public List<T> list() {
        return (List<T>) rootQuery.list();
    }

    @SuppressWarnings("unchecked")
    public T single() {
        return (T) rootQuery.single();
    }

    @SuppressWarnings("unchecked")
    public T firstOrNull() {
        return (T) rootQuery.firstOrNull();
    }

    public long count() {
        return rootQuery.count();
    }
}
