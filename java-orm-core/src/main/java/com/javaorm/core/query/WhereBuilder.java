package com.javaorm.core.query;

import com.javaorm.core.ast.*;
import com.javaorm.core.operator.ConditionAppender;
import com.javaorm.core.operator.PropertyOperator;
import com.javaorm.core.operator.SqlOperator;
import com.javaorm.core.proxy.PropertyPath;
import com.javaorm.core.proxy.ProxyFactory;
import com.javaorm.core.proxy.RecordingContext;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * WHERE 条件链式构造器。
 * 支持两种断言方式：
 * 1. 代理值直接调用：w.and(s.getAge()).gt(18)
 * 2. Lambda 表达式调用：w.and(s -> s.getAge()).gt(18)
 * 3. SQL 函数与表达式断言：w.and(min(s.getAge())).gt(18)
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
    private final Supplier<List<?>> customListSupplier;

    public WhereBuilder(Query<T> query) {
        this(query, null);
    }

    @SuppressWarnings("unchecked")
    public WhereBuilder(Query<T> query, Supplier<List<?>> customListSupplier) {
        this.entityClass = (Class<T>) query.getStatement().getRootEntityClass();
        this.rootQuery = query;
        this.scopeAlias = query.getStatement().getRootTableAlias();
        this.conditionReceiver = query.getStatement()::addWhereCondition;
        this.customListSupplier = customListSupplier;
    }

    public WhereBuilder(Class<T> entityClass, Query<?> rootQuery, String scopeAlias) {
        this(entityClass, rootQuery, scopeAlias, rootQuery.getStatement()::addWhereCondition, null);
    }

    public WhereBuilder(Class<T> entityClass, Query<?> rootQuery, String scopeAlias, Consumer<Condition> conditionReceiver) {
        this(entityClass, rootQuery, scopeAlias, conditionReceiver, null);
    }

    public WhereBuilder(Class<T> entityClass, Query<?> rootQuery, String scopeAlias, Consumer<Condition> conditionReceiver, Supplier<List<?>> customListSupplier) {
        this.entityClass = entityClass;
        this.rootQuery = rootQuery;
        this.scopeAlias = scopeAlias;
        this.conditionReceiver = conditionReceiver;
        this.customListSupplier = customListSupplier;
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
     * SQL 表达式与函数断言：w.and(min(s.getAge())).gt(18)
     */
    public <R> PropertyOperator<WhereBuilder<T>, R> and(SqlExpression expr) {
        rootQuery.resolveExpression(expr);
        return new PropertyOperator<>(this, (op, vals) -> {
            ExpressionCondition cond = new ExpressionCondition(expr, op, vals);
            cond.setLogicalOperator(LogicalOperator.AND);
            conditionReceiver.accept(cond);
        });
    }

    /**
     * 方式 1：直接传入代理 getter 调用结果，如 w.and(s.getAge()).gt(18)
     */
    public <R> PropertyOperator<WhereBuilder<T>, R> and(R val) {
        if (val instanceof SqlExpression) {
            return and((SqlExpression) val);
        }
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
     * SQL 表达式与函数断言：w.or(min(s.getAge())).gt(18)
     */
    public <R> PropertyOperator<WhereBuilder<T>, R> or(SqlExpression expr) {
        rootQuery.resolveExpression(expr);
        return new PropertyOperator<>(this, (op, vals) -> {
            ExpressionCondition cond = new ExpressionCondition(expr, op, vals);
            cond.setLogicalOperator(LogicalOperator.OR);
            conditionReceiver.accept(cond);
        });
    }

    /**
     * 方式 1：直接传入代理 getter 调用结果，如 w.or(s.getAge()).lt(10)
     */
    public <R> PropertyOperator<WhereBuilder<T>, R> or(R val) {
        if (val instanceof SqlExpression) {
            return or((SqlExpression) val);
        }
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

    /**
     * 子查询 EXISTS 断言
     */
    public WhereBuilder<T> andExists(Object subQuery) {
        ExistsCondition cond = new ExistsCondition(subQuery, false);
        cond.setLogicalOperator(LogicalOperator.AND);
        conditionReceiver.accept(cond);
        return this;
    }

    public WhereBuilder<T> andNotExists(Object subQuery) {
        ExistsCondition cond = new ExistsCondition(subQuery, true);
        cond.setLogicalOperator(LogicalOperator.AND);
        conditionReceiver.accept(cond);
        return this;
    }

    public WhereBuilder<T> orExists(Object subQuery) {
        ExistsCondition cond = new ExistsCondition(subQuery, false);
        cond.setLogicalOperator(LogicalOperator.OR);
        conditionReceiver.accept(cond);
        return this;
    }

    public WhereBuilder<T> orNotExists(Object subQuery) {
        ExistsCondition cond = new ExistsCondition(subQuery, true);
        cond.setLogicalOperator(LogicalOperator.OR);
        conditionReceiver.accept(cond);
        return this;
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
            if (path.getRootEntityClass() != null && path.getRootEntityClass() != entityClass) {
                String reg = RecordingContext.getAlias(path.getRootEntityClass());
                if (reg != null) {
                    alias = reg;
                } else {
                    alias = scopeAlias != null ? scopeAlias : rootQuery.resolveTableAlias(path);
                }
            } else {
                alias = scopeAlias != null ? scopeAlias : rootQuery.resolveTableAlias(path);
            }
            colName = rootQuery.resolveColumnName(path);
        }

        if (vals != null) {
            for (Object v : vals) {
                if (v instanceof SqlExpression) {
                    rootQuery.resolveExpression((SqlExpression) v);
                }
            }
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

    // 快捷委托给 rootQuery
    @SuppressWarnings("unchecked")
    public Query<T> groupBy(Object... expressions) {
        return ((Query<T>) rootQuery).groupBy(expressions);
    }

    @SuppressWarnings("unchecked")
    public HavingBuilder<T> having() {
        return ((Query<T>) rootQuery).having();
    }

    @SuppressWarnings("unchecked")
    public Query<T> having(Consumer<HavingBuilder<T>> consumer) {
        return ((Query<T>) rootQuery).having(consumer);
    }

    @SuppressWarnings("unchecked")
    public <R> OrderByBuilder<WhereBuilder<T>> orderBy(Function<T, R> propertyGetter) {
        PropertyPath path = recordPath(propertyGetter);
        String alias = path.hasRelations() ? rootQuery.resolveJoinAlias(path) : scopeAlias;
        String colName = rootQuery.resolveColumnName(path);
        return new OrderByBuilder<>(this, rootQuery.getStatement(), alias, colName);
    }

    @SuppressWarnings("unchecked")
    public <R> OrderByBuilder<WhereBuilder<T>> orderBy(R val) {
        if (val instanceof SqlExpression) {
            return orderBy((SqlExpression) val);
        }
        PropertyPath path = RecordingContext.finish();
        if (path == null) {
            throw new IllegalStateException("No proxy property accessed before calling orderBy(...)");
        }
        String alias = path.hasRelations() ? rootQuery.resolveJoinAlias(path) : scopeAlias;
        String colName = rootQuery.resolveColumnName(path);
        return new OrderByBuilder<>(this, rootQuery.getStatement(), alias, colName);
    }

    public OrderByBuilder<WhereBuilder<T>> orderBy(SqlExpression expr) {
        rootQuery.resolveExpression(expr);
        return new OrderByBuilder<>(this, rootQuery.getStatement(), expr);
    }

    @SuppressWarnings("unchecked")
    public Query<T> union(Object other) {
        return ((Query<T>) rootQuery).union(other);
    }

    @SuppressWarnings("unchecked")
    public Query<T> unionAll(Object other) {
        return ((Query<T>) rootQuery).unionAll(other);
    }

    public SelectStatement getStatement() {
        return rootQuery.getStatement();
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
    public <E> List<E> list() {
        if (customListSupplier != null) {
            return (List<E>) customListSupplier.get();
        }
        return (List<E>) rootQuery.list();
    }

    public List<Map<String, Object>> listMaps() {
        return rootQuery.listMaps();
    }

    public <V> V fetchOne(Class<V> targetType) {
        return rootQuery.fetchOne(targetType);
    }

    public <V> List<V> fetchList(Class<V> targetType) {
        return rootQuery.fetchList(targetType);
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
