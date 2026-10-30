package com.javaorm.core.query;

import com.javaorm.core.ast.*;
import com.javaorm.core.operator.ConditionAppender;
import com.javaorm.core.operator.PropertyOperator;
import com.javaorm.core.operator.SqlOperator;
import com.javaorm.core.proxy.PropertyPath;
import com.javaorm.core.proxy.ProxyFactory;
import com.javaorm.core.proxy.RecordingContext;

import java.util.Collection;
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
    private String scopeAlias;
    private final Consumer<Condition> conditionReceiver;
    private final Supplier<List<?>> customListSupplier;
    private final java.util.function.IntSupplier executeSupplier;
    private final Supplier<com.javaorm.core.dialect.SqlAndParams> sqlAndParamsSupplier;
    private final Consumer<Boolean> printSqlConsumer;

    private LogicalOperator currentLogOp = LogicalOperator.AND;
    private Object currentLeftOperand = null;

    @SuppressWarnings("rawtypes")
    private final PropertyOperator reusableOperator;

    public WhereBuilder(Query<T> query) {
        this(query, null);
    }

    @SuppressWarnings("unchecked")
    public WhereBuilder(Query<T> query, Supplier<List<?>> customListSupplier) {
        this((Class<T>) query.getStatement().getRootEntityClass(), query, query.getStatement().getRootTableAlias(),
                query.getStatement()::addWhereCondition, customListSupplier, null,
                query::toSqlAndParams, val -> { if (val == null) query.printSql(); else query.printSql(val); });
    }

    public WhereBuilder(Class<T> entityClass, Query<?> rootQuery, String scopeAlias) {
        this(entityClass, rootQuery, scopeAlias, rootQuery.getStatement()::addWhereCondition, null, null, null, null);
    }

    public WhereBuilder(Class<T> entityClass, Query<?> rootQuery, String scopeAlias, Consumer<Condition> conditionReceiver) {
        this(entityClass, rootQuery, scopeAlias, conditionReceiver, null, null, null, null);
    }

    public WhereBuilder(Class<T> entityClass, Query<?> rootQuery, String scopeAlias, Consumer<Condition> conditionReceiver, java.util.function.IntSupplier executeSupplier) {
        this(entityClass, rootQuery, scopeAlias, conditionReceiver, null, executeSupplier, null, null);
    }

    public WhereBuilder(Class<T> entityClass, Query<?> rootQuery, String scopeAlias, Consumer<Condition> conditionReceiver, Supplier<List<?>> customListSupplier) {
        this(entityClass, rootQuery, scopeAlias, conditionReceiver, customListSupplier, null, null, null);
    }

    public WhereBuilder(Class<T> entityClass, Query<?> rootQuery, String scopeAlias, Consumer<Condition> conditionReceiver, Supplier<List<?>> customListSupplier, java.util.function.IntSupplier executeSupplier) {
        this(entityClass, rootQuery, scopeAlias, conditionReceiver, customListSupplier, executeSupplier, null, null);
    }

    public WhereBuilder(Class<T> entityClass, Query<?> rootQuery, String scopeAlias, Consumer<Condition> conditionReceiver,
                        Supplier<List<?>> customListSupplier, java.util.function.IntSupplier executeSupplier,
                        Supplier<com.javaorm.core.dialect.SqlAndParams> sqlAndParamsSupplier, Consumer<Boolean> printSqlConsumer) {
        this.entityClass = entityClass;
        this.rootQuery = rootQuery;
        this.scopeAlias = scopeAlias;
        this.conditionReceiver = conditionReceiver;
        this.customListSupplier = customListSupplier;
        this.executeSupplier = executeSupplier;
        this.sqlAndParamsSupplier = sqlAndParamsSupplier;
        this.printSqlConsumer = printSqlConsumer;
        this.reusableOperator = new PropertyOperator<>(this, (op, values) -> {
            dispatchCurrentCondition(this.currentLogOp, this.currentLeftOperand, op, values);
        });
    }

    public int execute() {
        if (executeSupplier != null) {
            return executeSupplier.getAsInt();
        }
        throw new UnsupportedOperationException("execute() is only available on UPDATE and DELETE operations");
    }

    /**
     * 获取当前实体类型的录制代理对象
     */
    public T proxy() {
        return entityClass != null ? ProxyFactory.getProxy(entityClass) : null;
    }

    /**
     * 允许语法糖调用 .where()
     */
    public WhereBuilder<T> where() {
        return this;
    }

    public void cancelCurrentCondition() {
        this.currentLogOp = LogicalOperator.AND;
        this.currentLeftOperand = null;
        RecordingContext.clearRecording();
    }

    void dispatchCurrentCondition(LogicalOperator logOp, Object leftOperand, SqlOperator op, Object... vals) {
        this.currentLogOp = LogicalOperator.AND;
        this.currentLeftOperand = null;
        addResolvedCondition(logOp, leftOperand, op, vals);
    }

    private Object resolveOperandValue(Object value) {
        if (value instanceof SqlExpression) {
            return value;
        }
        PropertyPath rightPath = RecordingContext.finishCurrent();
        if (rightPath != null) {
            return new ColumnExpression<>(rightPath);
        }
        return value;
    }

    private void addResolvedCondition(LogicalOperator logOp, Object leftOperand, SqlOperator op, Object... vals) {
        if (leftOperand instanceof SqlExpression) {
            SqlExpression<?> expr = (SqlExpression<?>) leftOperand;
            if (rootQuery != null) {
                rootQuery.resolveExpression(expr);
                if (vals != null) {
                    for (Object v : vals) {
                        if (v instanceof SqlExpression) {
                            rootQuery.resolveExpression((SqlExpression) v);
                        }
                    }
                }
            }
            ExpressionCondition cond = new ExpressionCondition(expr, op, vals);
            cond.setLogicalOperator(logOp);
            conditionReceiver.accept(cond);
        } else if (leftOperand instanceof Function) {
            PropertyPath path = recordPath((Function<T, ?>) leftOperand);
            appendCondition(logOp, path, op, vals);
        } else if (leftOperand instanceof PropertyPath) {
            appendCondition(logOp, (PropertyPath) leftOperand, op, vals);
        } else if (leftOperand instanceof String) {
            appendDynamicCondition(logOp, (String) leftOperand, op, vals);
        } else {
            throw new IllegalStateException("No property path or column specified for condition");
        }
    }

    private WhereBuilder<T> executeFlatCondition(boolean condition, Object propOrExpr, SqlOperator op, Object... vals) {
        LogicalOperator logOp = this.currentLogOp;
        this.currentLogOp = LogicalOperator.AND;

        if (!condition) {
            RecordingContext.clearRecording();
            return this;
        }

        Object leftOperand;
        if (propOrExpr instanceof SqlExpression) {
            leftOperand = propOrExpr;
        } else if (propOrExpr instanceof Function) {
            leftOperand = recordPath((Function<T, ?>) propOrExpr);
        } else {
            List<PropertyPath> paths = RecordingContext.finishLast(vals != null ? 1 + vals.length : 1);
            if (!paths.isEmpty()) {
                leftOperand = paths.get(0);
                if (paths.size() > 1 && vals != null) {
                    Object[] resolvedVals = new Object[vals.length];
                    int pathIdx = 1;
                    for (int i = 0; i < vals.length; i++) {
                        if (pathIdx < paths.size()) {
                            resolvedVals[i] = new ColumnExpression<>(paths.get(pathIdx++));
                        } else {
                            resolvedVals[i] = vals[i];
                        }
                    }
                    addResolvedCondition(logOp, leftOperand, op, resolvedVals);
                    return this;
                }
            } else if (propOrExpr instanceof String) {
                leftOperand = propOrExpr;
            } else {
                throw new IllegalStateException("No property path or column specified for condition");
            }
        }

        Object[] resolvedVals = vals;
        if (vals != null && vals.length > 0) {
            resolvedVals = new Object[vals.length];
            for (int i = 0; i < vals.length; i++) {
                resolvedVals[i] = resolveOperandValue(vals[i]);
            }
        }

        addResolvedCondition(logOp, leftOperand, op, resolvedVals);
        return this;
    }

    public WhereBuilder<T> and() {
        this.currentLogOp = LogicalOperator.AND;
        return this;
    }

    public WhereBuilder<T> or() {
        this.currentLogOp = LogicalOperator.OR;
        return this;
    }

    @SuppressWarnings("unchecked")
    public <R> PropertyOperator<WhereBuilder<T>, R> and(R val) {
        this.currentLogOp = LogicalOperator.AND;
        PropertyPath recorded = RecordingContext.finishCurrent();
        if (recorded != null) {
            this.currentLeftOperand = recorded;
        } else {
            this.currentLeftOperand = val;
        }
        return (PropertyOperator<WhereBuilder<T>, R>) this.reusableOperator;
    }

    @SuppressWarnings("unchecked")
    public <R> PropertyOperator<WhereBuilder<T>, R> and(Function<T, R> getter) {
        this.currentLogOp = LogicalOperator.AND;
        if (getter != null) {
            this.currentLeftOperand = recordPath(getter);
        } else {
            this.currentLeftOperand = null;
        }
        return (PropertyOperator<WhereBuilder<T>, R>) this.reusableOperator;
    }

    @SuppressWarnings("unchecked")
    public <R> PropertyOperator<WhereBuilder<T>, R> and(SqlExpression<R> expr) {
        this.currentLogOp = LogicalOperator.AND;
        if (expr != null && rootQuery != null) {
            rootQuery.resolveExpression(expr);
        }
        this.currentLeftOperand = expr;
        return (PropertyOperator<WhereBuilder<T>, R>) this.reusableOperator;
    }

    @SuppressWarnings("unchecked")
    public <R> PropertyOperator<WhereBuilder<T>, R> and(String propertyOrColumn) {
        this.currentLogOp = LogicalOperator.AND;
        PropertyPath recorded = RecordingContext.finishCurrent();
        if (recorded != null) {
            this.currentLeftOperand = recorded;
        } else {
            this.currentLeftOperand = propertyOrColumn;
        }
        return (PropertyOperator<WhereBuilder<T>, R>) this.reusableOperator;
    }

    @SuppressWarnings("unchecked")
    public <R> PropertyOperator<WhereBuilder<T>, R> or(R val) {
        this.currentLogOp = LogicalOperator.OR;
        PropertyPath recorded = RecordingContext.finishCurrent();
        if (recorded != null) {
            this.currentLeftOperand = recorded;
        } else {
            this.currentLeftOperand = val;
        }
        return (PropertyOperator<WhereBuilder<T>, R>) this.reusableOperator;
    }

    @SuppressWarnings("unchecked")
    public <R> PropertyOperator<WhereBuilder<T>, R> or(Function<T, R> getter) {
        this.currentLogOp = LogicalOperator.OR;
        if (getter != null) {
            this.currentLeftOperand = recordPath(getter);
        } else {
            this.currentLeftOperand = null;
        }
        return (PropertyOperator<WhereBuilder<T>, R>) this.reusableOperator;
    }

    @SuppressWarnings("unchecked")
    public <R> PropertyOperator<WhereBuilder<T>, R> or(SqlExpression<R> expr) {
        this.currentLogOp = LogicalOperator.OR;
        if (expr != null && rootQuery != null) {
            rootQuery.resolveExpression(expr);
        }
        this.currentLeftOperand = expr;
        return (PropertyOperator<WhereBuilder<T>, R>) this.reusableOperator;
    }

    @SuppressWarnings("unchecked")
    public <R> PropertyOperator<WhereBuilder<T>, R> or(String propertyOrColumn) {
        this.currentLogOp = LogicalOperator.OR;
        PropertyPath recorded = RecordingContext.finishCurrent();
        if (recorded != null) {
            this.currentLeftOperand = recorded;
        } else {
            this.currentLeftOperand = propertyOrColumn;
        }
        return (PropertyOperator<WhereBuilder<T>, R>) this.reusableOperator;
    }

    public WhereBuilder<T> andRaw(String rawSql, Object... params) {
        return andRaw(true, rawSql, params);
    }

    public WhereBuilder<T> andRaw(boolean condition, String rawSql, Object... params) {
        if (!condition) {
            return this;
        }
        RawSqlExpression<?> raw = new RawSqlExpression<>(rawSql, params);
        ExpressionCondition cond = new ExpressionCondition(raw, null);
        cond.setLogicalOperator(LogicalOperator.AND);
        conditionReceiver.accept(cond);
        return this;
    }

    public WhereBuilder<T> orRaw(String rawSql, Object... params) {
        return orRaw(true, rawSql, params);
    }

    public WhereBuilder<T> orRaw(boolean condition, String rawSql, Object... params) {
        if (!condition) {
            return this;
        }
        RawSqlExpression<?> raw = new RawSqlExpression<>(rawSql, params);
        ExpressionCondition cond = new ExpressionCondition(raw, null);
        cond.setLogicalOperator(LogicalOperator.OR);
        conditionReceiver.accept(cond);
        return this;
    }

    public WhereBuilder<T> andExists(Object subQuery) {
        return andExists(true, subQuery);
    }

    public WhereBuilder<T> andExists(boolean condition, Object subQuery) {
        if (!condition) {
            return this;
        }
        ExistsCondition cond = new ExistsCondition(subQuery, false);
        cond.setLogicalOperator(LogicalOperator.AND);
        conditionReceiver.accept(cond);
        return this;
    }

    public WhereBuilder<T> andNotExists(Object subQuery) {
        return andNotExists(true, subQuery);
    }

    public WhereBuilder<T> andNotExists(boolean condition, Object subQuery) {
        if (!condition) {
            return this;
        }
        ExistsCondition cond = new ExistsCondition(subQuery, true);
        cond.setLogicalOperator(LogicalOperator.AND);
        conditionReceiver.accept(cond);
        return this;
    }

    public WhereBuilder<T> orExists(Object subQuery) {
        return orExists(true, subQuery);
    }

    public WhereBuilder<T> orExists(boolean condition, Object subQuery) {
        if (!condition) {
            return this;
        }
        ExistsCondition cond = new ExistsCondition(subQuery, false);
        cond.setLogicalOperator(LogicalOperator.OR);
        conditionReceiver.accept(cond);
        return this;
    }

    public WhereBuilder<T> orNotExists(Object subQuery) {
        return orNotExists(true, subQuery);
    }

    public WhereBuilder<T> orNotExists(boolean condition, Object subQuery) {
        if (!condition) {
            return this;
        }
        ExistsCondition cond = new ExistsCondition(subQuery, true);
        cond.setLogicalOperator(LogicalOperator.OR);
        conditionReceiver.accept(cond);
        return this;
    }

    public WhereBuilder<T> andGroup(Consumer<WhereBuilder<T>> groupConsumer) {
        return andGroup(true, groupConsumer);
    }

    public WhereBuilder<T> andGroup(boolean condition, Consumer<WhereBuilder<T>> groupConsumer) {
        if (!condition) {
            return this;
        }
        GroupCondition group = new GroupCondition(LogicalOperator.AND);
        WhereBuilder<T> sub = new WhereBuilder<>(entityClass, rootQuery, scopeAlias, group::addCondition, customListSupplier, executeSupplier, sqlAndParamsSupplier, printSqlConsumer);
        groupConsumer.accept(sub);
        if (!group.getChildren().isEmpty()) {
            conditionReceiver.accept(group);
        }
        return this;
    }

    public WhereBuilder<T> andGroup(BiWhereConsumer<T> groupConsumer) {
        return andGroup(true, groupConsumer);
    }

    public WhereBuilder<T> andGroup(boolean condition, BiWhereConsumer<T> groupConsumer) {
        if (!condition) {
            return this;
        }
        GroupCondition group = new GroupCondition(LogicalOperator.AND);
        WhereBuilder<T> sub = new WhereBuilder<>(entityClass, rootQuery, scopeAlias, group::addCondition, customListSupplier, executeSupplier, sqlAndParamsSupplier, printSqlConsumer);
        groupConsumer.accept(sub, sub.proxy());
        if (!group.getChildren().isEmpty()) {
            conditionReceiver.accept(group);
        }
        return this;
    }

    public WhereBuilder<T> orGroup(Consumer<WhereBuilder<T>> groupConsumer) {
        return orGroup(true, groupConsumer);
    }

    public WhereBuilder<T> orGroup(boolean condition, Consumer<WhereBuilder<T>> groupConsumer) {
        if (!condition) {
            return this;
        }
        GroupCondition group = new GroupCondition(LogicalOperator.OR);
        WhereBuilder<T> sub = new WhereBuilder<>(entityClass, rootQuery, scopeAlias, group::addCondition, customListSupplier, executeSupplier, sqlAndParamsSupplier, printSqlConsumer);
        groupConsumer.accept(sub);
        if (!group.getChildren().isEmpty()) {
            conditionReceiver.accept(group);
        }
        return this;
    }

    public WhereBuilder<T> orGroup(BiWhereConsumer<T> groupConsumer) {
        return orGroup(true, groupConsumer);
    }

    public WhereBuilder<T> orGroup(boolean condition, BiWhereConsumer<T> groupConsumer) {
        if (!condition) {
            return this;
        }
        GroupCondition group = new GroupCondition(LogicalOperator.OR);
        WhereBuilder<T> sub = new WhereBuilder<>(entityClass, rootQuery, scopeAlias, group::addCondition, customListSupplier, executeSupplier, sqlAndParamsSupplier, printSqlConsumer);
        groupConsumer.accept(sub, sub.proxy());
        if (!group.getChildren().isEmpty()) {
            conditionReceiver.accept(group);
        }
        return this;
    }

    // ==================== 快捷扁平断言语法 (eq, ne, gt, ge, lt, le, like 等) ====================

    // --- eq ---
    public <R> WhereBuilder<T> eq(R propOrExpr, Object val) {
        return eq(true, propOrExpr, val);
    }
    public <R> WhereBuilder<T> eq(boolean condition, R propOrExpr, Object val) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.EQ, val);
    }
    public <R> WhereBuilder<T> eq(SqlExpression<R> expr, Object val) {
        return eq(true, expr, val);
    }
    public <R> WhereBuilder<T> eq(boolean condition, SqlExpression<R> expr, Object val) {
        return executeFlatCondition(condition, expr, SqlOperator.EQ, val);
    }
    public WhereBuilder<T> eq(String propertyOrColumn, Object val) {
        return eq(true, propertyOrColumn, val);
    }
    public WhereBuilder<T> eq(boolean condition, String propertyOrColumn, Object val) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.EQ, val);
    }
    public <R> WhereBuilder<T> eq(Function<T, R> getter, R val) {
        return eq(true, getter, val);
    }
    public <R> WhereBuilder<T> eq(boolean condition, Function<T, R> getter, R val) {
        return executeFlatCondition(condition, getter, SqlOperator.EQ, val);
    }

    // --- ne ---
    public <R> WhereBuilder<T> ne(R propOrExpr, Object val) {
        return ne(true, propOrExpr, val);
    }
    public <R> WhereBuilder<T> ne(boolean condition, R propOrExpr, Object val) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.NE, val);
    }
    public <R> WhereBuilder<T> ne(SqlExpression<R> expr, Object val) {
        return ne(true, expr, val);
    }
    public <R> WhereBuilder<T> ne(boolean condition, SqlExpression<R> expr, Object val) {
        return executeFlatCondition(condition, expr, SqlOperator.NE, val);
    }
    public WhereBuilder<T> ne(String propertyOrColumn, Object val) {
        return ne(true, propertyOrColumn, val);
    }
    public WhereBuilder<T> ne(boolean condition, String propertyOrColumn, Object val) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.NE, val);
    }
    public <R> WhereBuilder<T> ne(Function<T, R> getter, R val) {
        return ne(true, getter, val);
    }
    public <R> WhereBuilder<T> ne(boolean condition, Function<T, R> getter, R val) {
        return executeFlatCondition(condition, getter, SqlOperator.NE, val);
    }

    // --- gt ---
    public <R> WhereBuilder<T> gt(R propOrExpr, Object val) {
        return gt(true, propOrExpr, val);
    }
    public <R> WhereBuilder<T> gt(boolean condition, R propOrExpr, Object val) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.GT, val);
    }
    public <R> WhereBuilder<T> gt(SqlExpression<R> expr, Object val) {
        return gt(true, expr, val);
    }
    public <R> WhereBuilder<T> gt(boolean condition, SqlExpression<R> expr, Object val) {
        return executeFlatCondition(condition, expr, SqlOperator.GT, val);
    }
    public WhereBuilder<T> gt(String propertyOrColumn, Object val) {
        return gt(true, propertyOrColumn, val);
    }
    public WhereBuilder<T> gt(boolean condition, String propertyOrColumn, Object val) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.GT, val);
    }
    public <R> WhereBuilder<T> gt(Function<T, R> getter, R val) {
        return gt(true, getter, val);
    }
    public <R> WhereBuilder<T> gt(boolean condition, Function<T, R> getter, R val) {
        return executeFlatCondition(condition, getter, SqlOperator.GT, val);
    }

    // --- ge ---
    public <R> WhereBuilder<T> ge(R propOrExpr, Object val) {
        return ge(true, propOrExpr, val);
    }
    public <R> WhereBuilder<T> ge(boolean condition, R propOrExpr, Object val) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.GE, val);
    }
    public <R> WhereBuilder<T> ge(SqlExpression<R> expr, Object val) {
        return ge(true, expr, val);
    }
    public <R> WhereBuilder<T> ge(boolean condition, SqlExpression<R> expr, Object val) {
        return executeFlatCondition(condition, expr, SqlOperator.GE, val);
    }
    public WhereBuilder<T> ge(String propertyOrColumn, Object val) {
        return ge(true, propertyOrColumn, val);
    }
    public WhereBuilder<T> ge(boolean condition, String propertyOrColumn, Object val) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.GE, val);
    }
    public <R> WhereBuilder<T> ge(Function<T, R> getter, R val) {
        return ge(true, getter, val);
    }
    public <R> WhereBuilder<T> ge(boolean condition, Function<T, R> getter, R val) {
        return executeFlatCondition(condition, getter, SqlOperator.GE, val);
    }

    // --- lt ---
    public <R> WhereBuilder<T> lt(R propOrExpr, Object val) {
        return lt(true, propOrExpr, val);
    }
    public <R> WhereBuilder<T> lt(boolean condition, R propOrExpr, Object val) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.LT, val);
    }
    public <R> WhereBuilder<T> lt(SqlExpression<R> expr, Object val) {
        return lt(true, expr, val);
    }
    public <R> WhereBuilder<T> lt(boolean condition, SqlExpression<R> expr, Object val) {
        return executeFlatCondition(condition, expr, SqlOperator.LT, val);
    }
    public WhereBuilder<T> lt(String propertyOrColumn, Object val) {
        return lt(true, propertyOrColumn, val);
    }
    public WhereBuilder<T> lt(boolean condition, String propertyOrColumn, Object val) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.LT, val);
    }
    public <R> WhereBuilder<T> lt(Function<T, R> getter, R val) {
        return lt(true, getter, val);
    }
    public <R> WhereBuilder<T> lt(boolean condition, Function<T, R> getter, R val) {
        return executeFlatCondition(condition, getter, SqlOperator.LT, val);
    }

    // --- le ---
    public <R> WhereBuilder<T> le(R propOrExpr, Object val) {
        return le(true, propOrExpr, val);
    }
    public <R> WhereBuilder<T> le(boolean condition, R propOrExpr, Object val) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.LE, val);
    }
    public <R> WhereBuilder<T> le(SqlExpression<R> expr, Object val) {
        return le(true, expr, val);
    }
    public <R> WhereBuilder<T> le(boolean condition, SqlExpression<R> expr, Object val) {
        return executeFlatCondition(condition, expr, SqlOperator.LE, val);
    }
    public WhereBuilder<T> le(String propertyOrColumn, Object val) {
        return le(true, propertyOrColumn, val);
    }
    public WhereBuilder<T> le(boolean condition, String propertyOrColumn, Object val) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.LE, val);
    }
    public <R> WhereBuilder<T> le(Function<T, R> getter, R val) {
        return le(true, getter, val);
    }
    public <R> WhereBuilder<T> le(boolean condition, Function<T, R> getter, R val) {
        return executeFlatCondition(condition, getter, SqlOperator.LE, val);
    }

    // --- like ---
    public <R> WhereBuilder<T> like(R propOrExpr, Object val) {
        return like(true, propOrExpr, val);
    }
    public <R> WhereBuilder<T> like(boolean condition, R propOrExpr, Object val) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.LIKE, val);
    }
    public WhereBuilder<T> like(String propertyOrColumn, Object val) {
        return like(true, propertyOrColumn, val);
    }
    public WhereBuilder<T> like(boolean condition, String propertyOrColumn, Object val) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.LIKE, val);
    }
    public <R> WhereBuilder<T> like(Function<T, R> getter, Object val) {
        return like(true, getter, val);
    }
    public <R> WhereBuilder<T> like(boolean condition, Function<T, R> getter, Object val) {
        return executeFlatCondition(condition, getter, SqlOperator.LIKE, val);
    }

    public <R> WhereBuilder<T> likeLeft(R propOrExpr, Object val) {
        return likeLeft(true, propOrExpr, val);
    }
    public <R> WhereBuilder<T> likeLeft(boolean condition, R propOrExpr, Object val) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.LIKE, "%" + val);
    }
    public WhereBuilder<T> likeLeft(String propertyOrColumn, Object val) {
        return likeLeft(true, propertyOrColumn, val);
    }
    public WhereBuilder<T> likeLeft(boolean condition, String propertyOrColumn, Object val) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.LIKE, "%" + val);
    }
    public <R> WhereBuilder<T> likeLeft(Function<T, R> getter, Object val) {
        return likeLeft(true, getter, val);
    }
    public <R> WhereBuilder<T> likeLeft(boolean condition, Function<T, R> getter, Object val) {
        return executeFlatCondition(condition, getter, SqlOperator.LIKE, "%" + val);
    }

    public <R> WhereBuilder<T> likeRight(R propOrExpr, Object val) {
        return likeRight(true, propOrExpr, val);
    }
    public <R> WhereBuilder<T> likeRight(boolean condition, R propOrExpr, Object val) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.LIKE, val + "%");
    }
    public WhereBuilder<T> likeRight(String propertyOrColumn, Object val) {
        return likeRight(true, propertyOrColumn, val);
    }
    public WhereBuilder<T> likeRight(boolean condition, String propertyOrColumn, Object val) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.LIKE, val + "%");
    }
    public <R> WhereBuilder<T> likeRight(Function<T, R> getter, Object val) {
        return likeRight(true, getter, val);
    }
    public <R> WhereBuilder<T> likeRight(boolean condition, Function<T, R> getter, Object val) {
        return executeFlatCondition(condition, getter, SqlOperator.LIKE, val + "%");
    }

    public <R> WhereBuilder<T> likeAll(R propOrExpr, Object val) {
        return likeAll(true, propOrExpr, val);
    }
    public <R> WhereBuilder<T> likeAll(boolean condition, R propOrExpr, Object val) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.LIKE, "%" + val + "%");
    }
    public WhereBuilder<T> likeAll(String propertyOrColumn, Object val) {
        return likeAll(true, propertyOrColumn, val);
    }
    public WhereBuilder<T> likeAll(boolean condition, String propertyOrColumn, Object val) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.LIKE, "%" + val + "%");
    }
    public <R> WhereBuilder<T> likeAll(Function<T, R> getter, Object val) {
        return likeAll(true, getter, val);
    }
    public <R> WhereBuilder<T> likeAll(boolean condition, Function<T, R> getter, Object val) {
        return executeFlatCondition(condition, getter, SqlOperator.LIKE, "%" + val + "%");
    }

    public <R> WhereBuilder<T> notLike(R propOrExpr, Object val) {
        return notLike(true, propOrExpr, val);
    }
    public <R> WhereBuilder<T> notLike(boolean condition, R propOrExpr, Object val) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.NOT_LIKE, val);
    }
    public WhereBuilder<T> notLike(String propertyOrColumn, Object val) {
        return notLike(true, propertyOrColumn, val);
    }
    public WhereBuilder<T> notLike(boolean condition, String propertyOrColumn, Object val) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.NOT_LIKE, val);
    }
    public <R> WhereBuilder<T> notLike(Function<T, R> getter, Object val) {
        return notLike(true, getter, val);
    }
    public <R> WhereBuilder<T> notLike(boolean condition, Function<T, R> getter, Object val) {
        return executeFlatCondition(condition, getter, SqlOperator.NOT_LIKE, val);
    }

    // --- isNull / isNotNull ---
    public <R> WhereBuilder<T> isNull(R propOrExpr) {
        return isNull(true, propOrExpr);
    }
    public <R> WhereBuilder<T> isNull(boolean condition, R propOrExpr) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.IS_NULL);
    }
    public WhereBuilder<T> isNull(String propertyOrColumn) {
        return isNull(true, propertyOrColumn);
    }
    public WhereBuilder<T> isNull(boolean condition, String propertyOrColumn) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.IS_NULL);
    }
    public <R> WhereBuilder<T> isNull(Function<T, R> getter) {
        return isNull(true, getter);
    }
    public <R> WhereBuilder<T> isNull(boolean condition, Function<T, R> getter) {
        return executeFlatCondition(condition, getter, SqlOperator.IS_NULL);
    }

    public <R> WhereBuilder<T> isNotNull(R propOrExpr) {
        return isNotNull(true, propOrExpr);
    }
    public <R> WhereBuilder<T> isNotNull(boolean condition, R propOrExpr) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.IS_NOT_NULL);
    }
    public WhereBuilder<T> isNotNull(String propertyOrColumn) {
        return isNotNull(true, propertyOrColumn);
    }
    public WhereBuilder<T> isNotNull(boolean condition, String propertyOrColumn) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.IS_NOT_NULL);
    }
    public <R> WhereBuilder<T> isNotNull(Function<T, R> getter) {
        return isNotNull(true, getter);
    }
    public <R> WhereBuilder<T> isNotNull(boolean condition, Function<T, R> getter) {
        return executeFlatCondition(condition, getter, SqlOperator.IS_NOT_NULL);
    }

    // --- in / notIn ---
    public <R> WhereBuilder<T> in(R propOrExpr, Collection<?> values) {
        return in(true, propOrExpr, values);
    }
    public <R> WhereBuilder<T> in(boolean condition, R propOrExpr, Collection<?> values) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.IN, values);
    }
    public <R> WhereBuilder<T> in(R propOrExpr, Object... values) {
        return in(true, propOrExpr, values);
    }
    public <R> WhereBuilder<T> in(boolean condition, R propOrExpr, Object... values) {
        if (values != null && values.length == 1 && values[0] instanceof Collection) {
            return in(condition, propOrExpr, (Collection<?>) values[0]);
        }
        return executeFlatCondition(condition, propOrExpr, SqlOperator.IN, values);
    }
    public WhereBuilder<T> in(String propertyOrColumn, Collection<?> values) {
        return in(true, propertyOrColumn, values);
    }
    public WhereBuilder<T> in(boolean condition, String propertyOrColumn, Collection<?> values) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.IN, values);
    }
    public WhereBuilder<T> in(String propertyOrColumn, Object... values) {
        return in(true, propertyOrColumn, values);
    }
    public WhereBuilder<T> in(boolean condition, String propertyOrColumn, Object... values) {
        if (values != null && values.length == 1 && values[0] instanceof Collection) {
            return in(condition, propertyOrColumn, (Collection<?>) values[0]);
        }
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.IN, values);
    }
    public <R> WhereBuilder<T> in(Function<T, R> getter, Collection<?> values) {
        return in(true, getter, values);
    }
    public <R> WhereBuilder<T> in(boolean condition, Function<T, R> getter, Collection<?> values) {
        return executeFlatCondition(condition, getter, SqlOperator.IN, values);
    }
    public <R> WhereBuilder<T> in(Function<T, R> getter, Object... values) {
        return in(true, getter, values);
    }
    public <R> WhereBuilder<T> in(boolean condition, Function<T, R> getter, Object... values) {
        if (values != null && values.length == 1 && values[0] instanceof Collection) {
            return in(condition, getter, (Collection<?>) values[0]);
        }
        return executeFlatCondition(condition, getter, SqlOperator.IN, values);
    }

    public <R> WhereBuilder<T> notIn(R propOrExpr, Collection<?> values) {
        return notIn(true, propOrExpr, values);
    }
    public <R> WhereBuilder<T> notIn(boolean condition, R propOrExpr, Collection<?> values) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.NOT_IN, values);
    }
    public <R> WhereBuilder<T> notIn(R propOrExpr, Object... values) {
        return notIn(true, propOrExpr, values);
    }
    public <R> WhereBuilder<T> notIn(boolean condition, R propOrExpr, Object... values) {
        if (values != null && values.length == 1 && values[0] instanceof Collection) {
            return notIn(condition, propOrExpr, (Collection<?>) values[0]);
        }
        return executeFlatCondition(condition, propOrExpr, SqlOperator.NOT_IN, values);
    }
    public WhereBuilder<T> notIn(String propertyOrColumn, Collection<?> values) {
        return notIn(true, propertyOrColumn, values);
    }
    public WhereBuilder<T> notIn(boolean condition, String propertyOrColumn, Collection<?> values) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.NOT_IN, values);
    }
    public WhereBuilder<T> notIn(String propertyOrColumn, Object... values) {
        return notIn(true, propertyOrColumn, values);
    }
    public WhereBuilder<T> notIn(boolean condition, String propertyOrColumn, Object... values) {
        if (values != null && values.length == 1 && values[0] instanceof Collection) {
            return notIn(condition, propertyOrColumn, (Collection<?>) values[0]);
        }
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.NOT_IN, values);
    }
    public <R> WhereBuilder<T> notIn(Function<T, R> getter, Collection<?> values) {
        return notIn(true, getter, values);
    }
    public <R> WhereBuilder<T> notIn(boolean condition, Function<T, R> getter, Collection<?> values) {
        return executeFlatCondition(condition, getter, SqlOperator.NOT_IN, values);
    }
    public <R> WhereBuilder<T> notIn(Function<T, R> getter, Object... values) {
        return notIn(true, getter, values);
    }
    public <R> WhereBuilder<T> notIn(boolean condition, Function<T, R> getter, Object... values) {
        if (values != null && values.length == 1 && values[0] instanceof Collection) {
            return notIn(condition, getter, (Collection<?>) values[0]);
        }
        return executeFlatCondition(condition, getter, SqlOperator.NOT_IN, values);
    }

    // --- between ---
    public <R> WhereBuilder<T> between(R propOrExpr, Object min, Object max) {
        return between(true, propOrExpr, min, max);
    }
    public <R> WhereBuilder<T> between(boolean condition, R propOrExpr, Object min, Object max) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.BETWEEN, min, max);
    }
    public WhereBuilder<T> between(String propertyOrColumn, Object min, Object max) {
        return between(true, propertyOrColumn, min, max);
    }
    public WhereBuilder<T> between(boolean condition, String propertyOrColumn, Object min, Object max) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.BETWEEN, min, max);
    }
    public <R> WhereBuilder<T> between(Function<T, R> getter, R min, R max) {
        return between(true, getter, min, max);
    }
    public <R> WhereBuilder<T> between(boolean condition, Function<T, R> getter, R min, R max) {
        return executeFlatCondition(condition, getter, SqlOperator.BETWEEN, min, max);
    }

    private void appendCondition(LogicalOperator logOp, PropertyPath path, SqlOperator op, Object... vals) {
        if (vals != null && rootQuery != null) {
            for (Object v : vals) {
                if (v instanceof SqlExpression) {
                    rootQuery.resolveExpression((SqlExpression) v);
                }
            }
        }

        if (rootQuery != null && entityClass == rootQuery.getRootEntityClass() && rootQuery.getStatement().getRootTableAlias() != null) {
            this.scopeAlias = rootQuery.getStatement().getRootTableAlias();
        }

        String alias;
        String colName;

        if (path.getExplicitAlias() != null) {
            alias = path.getExplicitAlias();
            colName = rootQuery != null ? rootQuery.resolveColumnName(path) : path.getTerminalProperty();
        } else if (path.hasRelations()) {
            alias = rootQuery != null ? rootQuery.resolveJoinAlias(path) : null;
            colName = rootQuery != null ? rootQuery.resolveColumnName(path) : path.getTerminalProperty();
        } else {
            if (path.getRootEntityClass() != null && path.getRootEntityClass() != entityClass) {
                String reg = RecordingContext.getAlias(path.getRootEntityClass());
                if (reg != null) {
                    if (reg.equalsIgnoreCase(scopeAlias) && rootQuery != null) {
                        this.scopeAlias = rootQuery.shiftRootTableAlias();
                    }
                    alias = reg;
                } else {
                    alias = scopeAlias;
                }
            } else {
                alias = scopeAlias;
            }
            colName = rootQuery != null ? rootQuery.resolveColumnName(path) : path.getTerminalProperty();
        }

        BinaryCondition cond = new BinaryCondition(alias, colName, op, vals);
        cond.setLogicalOperator(logOp);
        conditionReceiver.accept(cond);
    }

    private static final java.util.regex.Pattern SAFE_IDENTIFIER =
            java.util.regex.Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)*$");

    public static boolean isValidIdentifier(String name) {
        if (name == null || name.trim().isEmpty()) return false;
        return SAFE_IDENTIFIER.matcher(name.trim()).matches();
    }

    private void appendDynamicCondition(LogicalOperator logOp, String propertyOrColumn, SqlOperator op, Object... vals) {
        if (vals != null && rootQuery != null) {
            for (Object v : vals) {
                if (v instanceof SqlExpression) {
                    rootQuery.resolveExpression((SqlExpression) v);
                }
            }
        }

        if (rootQuery != null && entityClass == rootQuery.getRootEntityClass() && rootQuery.getStatement().getRootTableAlias() != null) {
            this.scopeAlias = rootQuery.getStatement().getRootTableAlias();
        }

        String str = propertyOrColumn.trim();
        String alias;
        String colName;
        if (str.contains(".")) {
            int dot = str.lastIndexOf('.');
            alias = str.substring(0, dot);
            colName = str.substring(dot + 1);
            if (scopeAlias != null) {
                boolean isRoot = alias.equalsIgnoreCase(scopeAlias)
                        || (entityClass != null && alias.equalsIgnoreCase(entityClass.getSimpleName()))
                        || (rootQuery != null && alias.equalsIgnoreCase(rootQuery.getStatement().getRootTableName()))
                        || (rootQuery != null && rootQuery.getStatement().getJoins().isEmpty());
                if (isRoot) {
                    alias = scopeAlias;
                } else if (rootQuery != null) {
                    for (TableJoin join : rootQuery.getStatement().getJoins()) {
                        if (alias.equalsIgnoreCase(join.getTableName())) {
                            alias = join.getTableAlias();
                            break;
                        }
                    }
                }
            }
        } else {
            alias = scopeAlias;
            colName = str;
        }

        boolean matchedInMetadata = false;
        if (entityClass != null && rootQuery != null) {
            com.javaorm.core.metadata.EntityMetadata meta = (entityClass == rootQuery.getRootEntityClass() && rootQuery.getRootMetadata() != null)
                    ? rootQuery.getRootMetadata()
                    : com.javaorm.core.metadata.EntityMetadataManager.get(entityClass, rootQuery.getConfig());
            com.javaorm.core.metadata.ColumnMetadata cm = meta.getColumnByProperty(colName);
            if (cm != null) {
                colName = cm.getColumnName();
                matchedInMetadata = true;
            } else {
                com.javaorm.core.metadata.ColumnMetadata dbCol = meta.getColumnByDbName(colName);
                if (dbCol != null) {
                    colName = dbCol.getColumnName();
                    matchedInMetadata = true;
                } else if (rootQuery.getConfig() != null && rootQuery.getConfig().getNameConversion() != null) {
                    String converted = rootQuery.getConfig().getNameConversion().convert(colName);
                    com.javaorm.core.metadata.ColumnMetadata convCol = meta.getColumnByDbName(converted);
                    if (convCol != null) {
                        colName = convCol.getColumnName();
                        matchedInMetadata = true;
                    }
                }
            }
        }

        if (!matchedInMetadata && !isValidIdentifier(colName)) {
            throw new IllegalArgumentException("Invalid dynamic property or column name '" + propertyOrColumn +
                    "'. Must be a mapped entity property or a valid SQL identifier. For raw SQL conditions, use andRaw(...) or orRaw(...).");
        }
        if (alias != null && !isValidIdentifier(alias)) {
            throw new IllegalArgumentException("Invalid table alias '" + alias + "'. Must be a valid SQL identifier.");
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
            RecordingContext.clearRecording();
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
            if (val instanceof String) {
                return orderBy((String) val);
            }
            throw new IllegalStateException("No proxy property accessed before calling orderBy(...)");
        }
        String alias = path.hasRelations() ? rootQuery.resolveJoinAlias(path) : scopeAlias;
        String colName = rootQuery.resolveColumnName(path);
        return new OrderByBuilder<>(this, rootQuery.getStatement(), alias, colName);
    }

    public OrderByBuilder<WhereBuilder<T>> orderBy(String propertyOrColumn) {
        PropertyPath path = RecordingContext.finish();
        if (path != null) {
            String alias = path.hasRelations() ? rootQuery.resolveJoinAlias(path) : scopeAlias;
            String colName = rootQuery.resolveColumnName(path);
            return new OrderByBuilder<>(this, rootQuery.getStatement(), alias, colName);
        }
        if (propertyOrColumn == null) {
            throw new IllegalArgumentException("Order by column cannot be null");
        }
        ColumnExpression ce = rootQuery != null ? rootQuery.stringToColumnExpression(propertyOrColumn) : new ColumnExpression(scopeAlias, propertyOrColumn);
        return new OrderByBuilder<>(this, rootQuery.getStatement(), ce.getTableAlias(), ce.getColumnName());
    }

    public OrderByBuilder<WhereBuilder<T>> orderBy(SqlExpression<?> expr) {
        rootQuery.resolveExpression(expr);
        return new OrderByBuilder<>(this, rootQuery.getStatement(), expr);
    }

    public <R> WhereBuilder<T> orderByAsc(R val) {
        orderBy(val).asc();
        return this;
    }

    public <R> WhereBuilder<T> orderByDesc(R val) {
        orderBy(val).desc();
        return this;
    }

    public <R> WhereBuilder<T> orderBy(R val, boolean isAsc) {
        orderBy(val).sort(isAsc);
        return this;
    }

    public <R> WhereBuilder<T> orderBy(R val, OrderDirection direction) {
        orderBy(val).sort(direction);
        return this;
    }

    public <R> WhereBuilder<T> orderBy(R val, String direction) {
        orderBy(val).sort(direction);
        return this;
    }

    public <R> WhereBuilder<T> orderBy(boolean condition, R val, boolean isAsc) {
        if (condition) {
            orderBy(val).sort(isAsc);
        }
        return this;
    }

    public <R> WhereBuilder<T> orderBy(Function<T, R> getter, boolean isAsc) {
        orderBy(getter).sort(isAsc);
        return this;
    }

    public <R> WhereBuilder<T> orderBy(boolean condition, Function<T, R> getter, boolean isAsc) {
        if (condition) {
            orderBy(getter).sort(isAsc);
        }
        return this;
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

    public List<Map<String, Object>> maps() {
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

    public com.javaorm.core.dialect.SqlAndParams toSqlAndParams() {
        if (sqlAndParamsSupplier != null) {
            return sqlAndParamsSupplier.get();
        }
        if (rootQuery != null) {
            return rootQuery.toSqlAndParams();
        }
        throw new UnsupportedOperationException("toSqlAndParams() is not supported on this builder");
    }

    public String toSql() {
        return toSqlAndParams().getSql();
    }

    @Override
    public String toString() {
        try {
            return toSql();
        } catch (Exception e) {
            return super.toString();
        }
    }

    public WhereBuilder<T> printSql() {
        if (printSqlConsumer != null) {
            printSqlConsumer.accept(null);
        } else if (rootQuery != null) {
            rootQuery.printSql();
        }
        return this;
    }

    public WhereBuilder<T> printSql(boolean print) {
        if (printSqlConsumer != null) {
            printSqlConsumer.accept(print);
        } else if (rootQuery != null) {
            rootQuery.printSql(print);
        }
        return this;
    }

    @SuppressWarnings("unchecked")
    public Query<T> toQuery() {
        return (Query<T>) rootQuery;
    }

    public Query<?> getRootQuery() {
        return rootQuery;
    }

    public Class<T> getEntityClass() {
        return entityClass;
    }

    @SuppressWarnings("unchecked")
    public List<T> toList() {
        return (List<T>) list();
    }

    @SuppressWarnings("unchecked")
    public List<T> toList(java.sql.Connection conn) {
        if (rootQuery instanceof Query) {
            return (List<T>) ((Query<?>) rootQuery).list(conn);
        }
        return (List<T>) list();
    }

    public Cte<T> asCte(String name) {
        return new Cte<>(name, entityClass, this, false);
    }

    public Cte<?> asDynamicCte(String name) {
        return new Cte<>(name, null, this, false);
    }

    public <C> Cte<C> asCte(String name, Class<C> cteClass) {
        return new Cte<>(name, cteClass, this, false);
    }

    public Cte<T> asCteRecursive(String name) {
        return new Cte<>(name, entityClass, this, true);
    }

    public Cte<?> asDynamicCteRecursive(String name) {
        return new Cte<>(name, null, this, true);
    }

    public <C> Cte<C> asCteRecursive(String name, Class<C> cteClass) {
        return new Cte<>(name, cteClass, this, true);
    }
}
