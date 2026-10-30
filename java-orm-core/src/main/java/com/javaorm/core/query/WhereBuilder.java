package com.javaorm.core.query;

import com.javaorm.core.ast.*;
import com.javaorm.core.operator.ConditionAppender;
import com.javaorm.core.operator.PropertyOperator;
import com.javaorm.core.operator.SqlOperator;
import com.javaorm.core.proxy.PropertyPath;
import com.javaorm.core.proxy.ProxyFactory;
import com.javaorm.core.proxy.RecordingContext;
import com.javaorm.core.sql.SqlEnv;

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
public class WhereBuilder<T> implements TQuery<T> {

    @FunctionalInterface
    public interface BiWhereConsumer<T> {
        void accept(WhereBuilder<T> w, T s);
    }

    private final SqlEnv env;
    private final Statement statement;
    private final Class<T> entityClass;
    private final Query<?> rootQuery;
    private String scopeAlias;
    private final Consumer<Condition> conditionReceiver;
    private final Supplier<List<?>> customListSupplier;
    private final java.util.function.IntSupplier executeSupplier;
    private final Supplier<com.javaorm.core.dialect.SqlAndParams> sqlAndParamsSupplier;
    private final Consumer<Boolean> printSqlConsumer;
    private Class<?> resultTypeClass;

    private LogicalOperator currentLogOp = LogicalOperator.AND;
    private Object currentLeftOperand = null;

    @SuppressWarnings("rawtypes")
    private final PropertyOperator reusableOperator;

    public WhereBuilder(SqlEnv env, Statement statement, Class<T> entityClass) {
        this(env, statement, entityClass, null, statement != null ? statement::addWhereCondition : null, null, null, null, null, null);
    }

    public WhereBuilder(SqlEnv env, Statement statement, Class<T> entityClass, String scopeAlias, Consumer<Condition> conditionReceiver) {
        this(env, statement, entityClass, scopeAlias, conditionReceiver, null, null, null, null, null);
    }

    public WhereBuilder(Query<T> query) {
        this(query, null);
    }

    @SuppressWarnings("unchecked")
    public WhereBuilder(Query<T> query, Supplier<List<?>> customListSupplier) {
        this(query.getEnv(), query.getStatement(), (Class<T>) query.getStatement().getRootEntityClass(),
                query.getStatement().getRootTableAlias(), query.getStatement()::addWhereCondition, query,
                customListSupplier, null, query::toSqlAndParams, val -> { if (val == null) query.printSql(); else query.printSql(val); });
    }

    public WhereBuilder(Class<T> entityClass, Query<?> rootQuery, String scopeAlias) {
        this(rootQuery != null ? rootQuery.getEnv() : null, rootQuery != null ? rootQuery.getStatement() : null,
                entityClass, scopeAlias, rootQuery != null ? rootQuery.getStatement()::addWhereCondition : null,
                rootQuery, null, null, null, null);
    }

    public WhereBuilder(Class<T> entityClass, Query<?> rootQuery, String scopeAlias, Consumer<Condition> conditionReceiver) {
        this(rootQuery != null ? rootQuery.getEnv() : null, rootQuery != null ? rootQuery.getStatement() : null,
                entityClass, scopeAlias, conditionReceiver, rootQuery, null, null, null, null);
    }

    public WhereBuilder(Class<T> entityClass, Query<?> rootQuery, String scopeAlias, Consumer<Condition> conditionReceiver, java.util.function.IntSupplier executeSupplier) {
        this(rootQuery != null ? rootQuery.getEnv() : null, rootQuery != null ? rootQuery.getStatement() : null,
                entityClass, scopeAlias, conditionReceiver, rootQuery, null, executeSupplier, null, null);
    }

    public WhereBuilder(Class<T> entityClass, Query<?> rootQuery, String scopeAlias, Consumer<Condition> conditionReceiver, Supplier<List<?>> customListSupplier) {
        this(rootQuery != null ? rootQuery.getEnv() : null, rootQuery != null ? rootQuery.getStatement() : null,
                entityClass, scopeAlias, conditionReceiver, rootQuery, customListSupplier, null, null, null);
    }

    public WhereBuilder(Class<T> entityClass, Query<?> rootQuery, String scopeAlias, Consumer<Condition> conditionReceiver, Supplier<List<?>> customListSupplier, java.util.function.IntSupplier executeSupplier) {
        this(rootQuery != null ? rootQuery.getEnv() : null, rootQuery != null ? rootQuery.getStatement() : null,
                entityClass, scopeAlias, conditionReceiver, rootQuery, customListSupplier, executeSupplier, null, null);
    }

    public WhereBuilder(Class<T> entityClass, Query<?> rootQuery, String scopeAlias, Consumer<Condition> conditionReceiver,
                        Supplier<List<?>> customListSupplier, java.util.function.IntSupplier executeSupplier,
                        Supplier<com.javaorm.core.dialect.SqlAndParams> sqlAndParamsSupplier, Consumer<Boolean> printSqlConsumer) {
        this(rootQuery != null ? rootQuery.getEnv() : null, rootQuery != null ? rootQuery.getStatement() : null,
                entityClass, scopeAlias, conditionReceiver, rootQuery, customListSupplier, executeSupplier, sqlAndParamsSupplier, printSqlConsumer);
    }

    public WhereBuilder(SqlEnv env, Statement statement, Class<T> entityClass, String scopeAlias,
                        Consumer<Condition> conditionReceiver, Query<?> rootQuery,
                        Supplier<List<?>> customListSupplier, java.util.function.IntSupplier executeSupplier,
                        Supplier<com.javaorm.core.dialect.SqlAndParams> sqlAndParamsSupplier, Consumer<Boolean> printSqlConsumer) {
        this.env = env != null ? env : (rootQuery != null ? rootQuery.getEnv() : null);
        this.statement = statement != null ? statement : (rootQuery != null ? rootQuery.getStatement() : null);
        this.entityClass = entityClass;
        this.rootQuery = rootQuery;
        this.scopeAlias = scopeAlias;
        this.conditionReceiver = conditionReceiver != null ? conditionReceiver
                : (this.statement != null ? this.statement::addWhereCondition
                : (rootQuery != null ? rootQuery.getStatement()::addWhereCondition : c -> {}));
        this.customListSupplier = customListSupplier;
        this.executeSupplier = executeSupplier;
        this.sqlAndParamsSupplier = sqlAndParamsSupplier;
        this.printSqlConsumer = printSqlConsumer;
        this.reusableOperator = new PropertyOperator<>(this, (op, values) -> {
            dispatchCurrentCondition(this.currentLogOp, this.currentLeftOperand, op, values);
        });
    }

    public SqlEnv getEnv() {
        return env;
    }

    public void setResultClass(Class<?> resultClass) {
        this.resultTypeClass = resultClass;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<T> getResultClass() {
        if (resultTypeClass != null) {
            return (Class<T>) resultTypeClass;
        }
        return entityClass;
    }

    public Statement getAstStatement() {
        return statement;
    }

    public int execute() {
        if (executeSupplier != null) {
            return executeSupplier.getAsInt();
        }
        if (env != null && statement instanceof UpdateStatement) {
            return env.executeUpdate((UpdateStatement) statement);
        }
        if (env != null && statement instanceof DeleteStatement) {
            return env.executeDelete((DeleteStatement) statement);
        }
        throw new UnsupportedOperationException("execute() is only available on UPDATE and DELETE operations");
    }

    public int executeRows() {
        return execute();
    }

    public int exec() {
        return execute();
    }

    public Statement getRawStatement() {
        return statement != null ? statement : (rootQuery != null ? rootQuery.getStatement() : null);
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

    public WhereBuilder<T> whereById(Object id) {
        return whereById(true, id);
    }

    public WhereBuilder<T> whereById(boolean condition, Object id) {
        if (!condition) return this;
        com.javaorm.core.metadata.EntityMetadata meta = entityClass != null ? com.javaorm.core.metadata.EntityMetadataManager.get(entityClass) : null;
        com.javaorm.core.metadata.ColumnMetadata idCol = meta != null ? meta.getIdColumn() : null;
        String idProp = idCol != null ? idCol.getPropertyName() : "id";
        and(idProp).eq(id);
        return this;
    }

    public WhereBuilder<T> whereByIds(Collection<?> ids) {
        return whereByIds(true, ids);
    }

    public WhereBuilder<T> whereByIds(boolean condition, Collection<?> ids) {
        if (!condition) return this;
        com.javaorm.core.metadata.EntityMetadata meta = entityClass != null ? com.javaorm.core.metadata.EntityMetadataManager.get(entityClass) : null;
        com.javaorm.core.metadata.ColumnMetadata idCol = meta != null ? meta.getIdColumn() : null;
        String idProp = idCol != null ? idCol.getPropertyName() : "id";
        and(idProp).in((Collection) ids);
        return this;
    }

    public final WhereBuilder<T> whereByIds(Object... ids) {
        if (ids != null && ids.length == 1 && ids[0] instanceof Collection) {
            return whereByIds((Collection<?>) ids[0]);
        }
        return whereByIds(ids != null ? java.util.Arrays.asList(ids) : java.util.Collections.emptyList());
    }

    public final WhereBuilder<T> whereByIds(boolean condition, Object... ids) {
        if (!condition) return this;
        return whereByIds(ids);
    }

    public WhereBuilder<T> whereObject(Object request) {
        return whereObject(true, request);
    }

    public WhereBuilder<T> whereObject(boolean condition, Object request) {
        if (!condition || request == null) {
            return this;
        }
        com.javaorm.core.metadata.EntityMetadata meta = entityClass != null ? com.javaorm.core.metadata.EntityMetadataManager.get(entityClass) : null;
        java.lang.reflect.Field[] fields = request.getClass().getDeclaredFields();
        for (java.lang.reflect.Field field : fields) {
            field.setAccessible(true);
            try {
                Object val = field.get(request);
                if (val == null) continue;
                if (val instanceof String && ((String) val).trim().isEmpty()) continue;
                if (val instanceof Collection && ((Collection<?>) val).isEmpty()) continue;

                com.javaorm.core.annotation.JoWhereCondition ann = field.getAnnotation(com.javaorm.core.annotation.JoWhereCondition.class);
                String targetProp = (ann != null && !ann.propName().isEmpty()) ? ann.propName() : field.getName();
                if (meta != null && meta.getColumnByProperty(targetProp) == null && meta.getColumnByDbName(targetProp) == null) {
                    continue;
                }

                com.javaorm.core.annotation.JoWhereCondition.Condition condType = ann != null ? ann.type() : com.javaorm.core.annotation.JoWhereCondition.Condition.EQ;
                switch (condType) {
                    case EQ: and(targetProp).eq(val); break;
                    case NE: and(targetProp).ne(val); break;
                    case GT: and(targetProp).gt(val); break;
                    case GE:
                    case RANGE_LEFT_CLOSED: and(targetProp).ge(val); break;
                    case RANGE_LEFT_OPEN: and(targetProp).gt(val); break;
                    case LT: and(targetProp).lt(val); break;
                    case LE:
                    case RANGE_RIGHT_CLOSED: and(targetProp).le(val); break;
                    case RANGE_RIGHT_OPEN: and(targetProp).lt(val); break;
                    case LIKE: and(targetProp).like(val.toString()); break;
                    case LIKE_MATCH_LEFT: and(targetProp).likeMatchLeft(val.toString()); break;
                    case LIKE_MATCH_RIGHT: and(targetProp).likeMatchRight(val.toString()); break;
                    case NOT_LIKE: and(targetProp).notLike(val.toString()); break;
                    case IN:
                        if (val instanceof Collection) and(targetProp).in((Collection) val);
                        else and(targetProp).eq(val);
                        break;
                    case NOT_IN:
                        if (val instanceof Collection) and(targetProp).notIn((Collection) val);
                        else and(targetProp).ne(val);
                        break;
                    default: and(targetProp).eq(val); break;
                }
            } catch (Exception ignored) {}
        }
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

    public WhereBuilder<T> andExists(Query<?> subQuery) {
        return andExists(true, subQuery);
    }

    public WhereBuilder<T> andExists(boolean condition, Query<?> subQuery) {
        return andExists(condition, (Object) subQuery);
    }

    public WhereBuilder<T> andExists(WhereBuilder<?> subQuery) {
        return andExists(true, subQuery);
    }

    public WhereBuilder<T> andExists(boolean condition, WhereBuilder<?> subQuery) {
        return andExists(condition, (Object) subQuery);
    }

    public WhereBuilder<T> andExists(SqlExpression<?> subQuery) {
        return andExists(true, subQuery);
    }

    public WhereBuilder<T> andExists(boolean condition, SqlExpression<?> subQuery) {
        return andExists(condition, (Object) subQuery);
    }

    public WhereBuilder<T> andExists(String rawSql, Object... params) {
        return andExists(true, rawSql, params);
    }

    public WhereBuilder<T> andExists(boolean condition, String rawSql, Object... params) {
        if (!condition) {
            return this;
        }
        return andExists(condition, (Object) new RawSqlExpression<>(rawSql, params));
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

    public WhereBuilder<T> andNotExists(Query<?> subQuery) {
        return andNotExists(true, subQuery);
    }

    public WhereBuilder<T> andNotExists(boolean condition, Query<?> subQuery) {
        return andNotExists(condition, (Object) subQuery);
    }

    public WhereBuilder<T> andNotExists(WhereBuilder<?> subQuery) {
        return andNotExists(true, subQuery);
    }

    public WhereBuilder<T> andNotExists(boolean condition, WhereBuilder<?> subQuery) {
        return andNotExists(condition, (Object) subQuery);
    }

    public WhereBuilder<T> andNotExists(SqlExpression<?> subQuery) {
        return andNotExists(true, subQuery);
    }

    public WhereBuilder<T> andNotExists(boolean condition, SqlExpression<?> subQuery) {
        return andNotExists(condition, (Object) subQuery);
    }

    public WhereBuilder<T> andNotExists(String rawSql, Object... params) {
        return andNotExists(true, rawSql, params);
    }

    public WhereBuilder<T> andNotExists(boolean condition, String rawSql, Object... params) {
        if (!condition) {
            return this;
        }
        return andNotExists(condition, (Object) new RawSqlExpression<>(rawSql, params));
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

    public WhereBuilder<T> orExists(Query<?> subQuery) {
        return orExists(true, subQuery);
    }

    public WhereBuilder<T> orExists(boolean condition, Query<?> subQuery) {
        return orExists(condition, (Object) subQuery);
    }

    public WhereBuilder<T> orExists(WhereBuilder<?> subQuery) {
        return orExists(true, subQuery);
    }

    public WhereBuilder<T> orExists(boolean condition, WhereBuilder<?> subQuery) {
        return orExists(condition, (Object) subQuery);
    }

    public WhereBuilder<T> orExists(SqlExpression<?> subQuery) {
        return orExists(true, subQuery);
    }

    public WhereBuilder<T> orExists(boolean condition, SqlExpression<?> subQuery) {
        return orExists(condition, (Object) subQuery);
    }

    public WhereBuilder<T> orExists(String rawSql, Object... params) {
        return orExists(true, rawSql, params);
    }

    public WhereBuilder<T> orExists(boolean condition, String rawSql, Object... params) {
        if (!condition) {
            return this;
        }
        return orExists(condition, (Object) new RawSqlExpression<>(rawSql, params));
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

    public WhereBuilder<T> orNotExists(Query<?> subQuery) {
        return orNotExists(true, subQuery);
    }

    public WhereBuilder<T> orNotExists(boolean condition, Query<?> subQuery) {
        return orNotExists(condition, (Object) subQuery);
    }

    public WhereBuilder<T> orNotExists(WhereBuilder<?> subQuery) {
        return orNotExists(true, subQuery);
    }

    public WhereBuilder<T> orNotExists(boolean condition, WhereBuilder<?> subQuery) {
        return orNotExists(condition, (Object) subQuery);
    }

    public WhereBuilder<T> orNotExists(SqlExpression<?> subQuery) {
        return orNotExists(true, subQuery);
    }

    public WhereBuilder<T> orNotExists(boolean condition, SqlExpression<?> subQuery) {
        return orNotExists(condition, (Object) subQuery);
    }

    public WhereBuilder<T> orNotExists(String rawSql, Object... params) {
        return orNotExists(true, rawSql, params);
    }

    public WhereBuilder<T> orNotExists(boolean condition, String rawSql, Object... params) {
        if (!condition) {
            return this;
        }
        return orNotExists(condition, (Object) new RawSqlExpression<>(rawSql, params));
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
        WhereBuilder<T> sub = new WhereBuilder<>(env, statement, entityClass, scopeAlias, group::addCondition, rootQuery, customListSupplier, executeSupplier, sqlAndParamsSupplier, printSqlConsumer);
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
        WhereBuilder<T> sub = new WhereBuilder<>(env, statement, entityClass, scopeAlias, group::addCondition, rootQuery, customListSupplier, executeSupplier, sqlAndParamsSupplier, printSqlConsumer);
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
        WhereBuilder<T> sub = new WhereBuilder<>(env, statement, entityClass, scopeAlias, group::addCondition, rootQuery, customListSupplier, executeSupplier, sqlAndParamsSupplier, printSqlConsumer);
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
        WhereBuilder<T> sub = new WhereBuilder<>(env, statement, entityClass, scopeAlias, group::addCondition, rootQuery, customListSupplier, executeSupplier, sqlAndParamsSupplier, printSqlConsumer);
        groupConsumer.accept(sub, sub.proxy());
        if (!group.getChildren().isEmpty()) {
            conditionReceiver.accept(group);
        }
        return this;
    }

    // ==================== 快捷扁平断言语法 (eq, ne, gt, ge, lt, le, like 等) ====================

    // --- eq ---
    public <R> WhereBuilder<T> eq(R propOrExpr, R val) {
        return eq(true, propOrExpr, val);
    }
    public <R> WhereBuilder<T> eq(boolean condition, R propOrExpr, R val) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.EQ, val);
    }
    public <R extends Number> WhereBuilder<T> eq(R propOrExpr, Number val, Object... dummy) {
        return eq(true, propOrExpr, val);
    }
    public <R extends Number> WhereBuilder<T> eq(boolean condition, R propOrExpr, Number val, Object... dummy) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.EQ, val);
    }
    public <R> WhereBuilder<T> eq(R propOrExpr, SqlExpression<? extends R> val) {
        return eq(true, propOrExpr, val);
    }
    public <R> WhereBuilder<T> eq(boolean condition, R propOrExpr, SqlExpression<? extends R> val) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.EQ, val);
    }
    public <R> WhereBuilder<T> eq(R propOrExpr, Query<?> subQuery) {
        return eq(true, propOrExpr, subQuery);
    }
    public <R> WhereBuilder<T> eq(boolean condition, R propOrExpr, Query<?> subQuery) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.EQ, subQuery);
    }
    public <R> WhereBuilder<T> eq(R propOrExpr, WhereBuilder<?> subQuery) {
        return eq(true, propOrExpr, subQuery);
    }
    public <R> WhereBuilder<T> eq(boolean condition, R propOrExpr, WhereBuilder<?> subQuery) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.EQ, subQuery);
    }

    public <R> WhereBuilder<T> eq(SqlExpression<R> expr, SqlExpression<? extends R> val) {
        return eq(true, expr, val);
    }
    public <R> WhereBuilder<T> eq(boolean condition, SqlExpression<R> expr, SqlExpression<? extends R> val) {
        return executeFlatCondition(condition, expr, SqlOperator.EQ, val);
    }
    public <R> WhereBuilder<T> eq(SqlExpression<R> expr, R val, Object... dummy) {
        return eq(true, expr, val);
    }
    public <R> WhereBuilder<T> eq(boolean condition, SqlExpression<R> expr, R val, Object... dummy) {
        return executeFlatCondition(condition, expr, SqlOperator.EQ, val);
    }
    public <R> WhereBuilder<T> eq(SqlExpression<R> expr, Query<?> subQuery) {
        return eq(true, expr, subQuery);
    }
    public <R> WhereBuilder<T> eq(boolean condition, SqlExpression<R> expr, Query<?> subQuery) {
        return executeFlatCondition(condition, expr, SqlOperator.EQ, subQuery);
    }
    public <R> WhereBuilder<T> eq(SqlExpression<R> expr, WhereBuilder<?> subQuery) {
        return eq(true, expr, subQuery);
    }
    public <R> WhereBuilder<T> eq(boolean condition, SqlExpression<R> expr, WhereBuilder<?> subQuery) {
        return executeFlatCondition(condition, expr, SqlOperator.EQ, subQuery);
    }

    public <R> WhereBuilder<T> eq(Function<T, R> getter, R val) {
        return eq(true, getter, val);
    }
    public <R> WhereBuilder<T> eq(boolean condition, Function<T, R> getter, R val) {
        return executeFlatCondition(condition, getter, SqlOperator.EQ, val);
    }
    public <R extends Number> WhereBuilder<T> eq(Function<T, R> getter, Number val, Object... dummy) {
        return eq(true, getter, val);
    }
    public <R extends Number> WhereBuilder<T> eq(boolean condition, Function<T, R> getter, Number val, Object... dummy) {
        return executeFlatCondition(condition, getter, SqlOperator.EQ, val);
    }
    public <R> WhereBuilder<T> eq(Function<T, R> getter, SqlExpression<? extends R> val) {
        return eq(true, getter, val);
    }
    public <R> WhereBuilder<T> eq(boolean condition, Function<T, R> getter, SqlExpression<? extends R> val) {
        return executeFlatCondition(condition, getter, SqlOperator.EQ, val);
    }
    public <R> WhereBuilder<T> eq(Function<T, R> getter, Query<?> subQuery) {
        return eq(true, getter, subQuery);
    }
    public <R> WhereBuilder<T> eq(boolean condition, Function<T, R> getter, Query<?> subQuery) {
        return executeFlatCondition(condition, getter, SqlOperator.EQ, subQuery);
    }
    public <R> WhereBuilder<T> eq(Function<T, R> getter, WhereBuilder<?> subQuery) {
        return eq(true, getter, subQuery);
    }
    public <R> WhereBuilder<T> eq(boolean condition, Function<T, R> getter, WhereBuilder<?> subQuery) {
        return executeFlatCondition(condition, getter, SqlOperator.EQ, subQuery);
    }

    public <R> WhereBuilder<T> eq(String propertyOrColumn, R val) {
        return eq(true, propertyOrColumn, val);
    }
    public <R> WhereBuilder<T> eq(boolean condition, String propertyOrColumn, R val) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.EQ, val);
    }
    public WhereBuilder<T> eq(String propertyOrColumn, SqlExpression<?> val) {
        return eq(true, propertyOrColumn, val);
    }
    public WhereBuilder<T> eq(boolean condition, String propertyOrColumn, SqlExpression<?> val) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.EQ, val);
    }
    public WhereBuilder<T> eq(String propertyOrColumn, Query<?> subQuery) {
        return eq(true, propertyOrColumn, subQuery);
    }
    public WhereBuilder<T> eq(boolean condition, String propertyOrColumn, Query<?> subQuery) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.EQ, subQuery);
    }
    public WhereBuilder<T> eq(String propertyOrColumn, WhereBuilder<?> subQuery) {
        return eq(true, propertyOrColumn, subQuery);
    }
    public WhereBuilder<T> eq(boolean condition, String propertyOrColumn, WhereBuilder<?> subQuery) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.EQ, subQuery);
    }

    // --- ne ---
    public <R> WhereBuilder<T> ne(R propOrExpr, R val) {
        return ne(true, propOrExpr, val);
    }
    public <R> WhereBuilder<T> ne(boolean condition, R propOrExpr, R val) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.NE, val);
    }
    public <R extends Number> WhereBuilder<T> ne(R propOrExpr, Number val, Object... dummy) {
        return ne(true, propOrExpr, val);
    }
    public <R extends Number> WhereBuilder<T> ne(boolean condition, R propOrExpr, Number val, Object... dummy) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.NE, val);
    }
    public <R> WhereBuilder<T> ne(R propOrExpr, SqlExpression<? extends R> val) {
        return ne(true, propOrExpr, val);
    }
    public <R> WhereBuilder<T> ne(boolean condition, R propOrExpr, SqlExpression<? extends R> val) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.NE, val);
    }
    public <R> WhereBuilder<T> ne(R propOrExpr, Query<?> subQuery) {
        return ne(true, propOrExpr, subQuery);
    }
    public <R> WhereBuilder<T> ne(boolean condition, R propOrExpr, Query<?> subQuery) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.NE, subQuery);
    }
    public <R> WhereBuilder<T> ne(R propOrExpr, WhereBuilder<?> subQuery) {
        return ne(true, propOrExpr, subQuery);
    }
    public <R> WhereBuilder<T> ne(boolean condition, R propOrExpr, WhereBuilder<?> subQuery) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.NE, subQuery);
    }

    public <R> WhereBuilder<T> ne(SqlExpression<R> expr, SqlExpression<? extends R> val) {
        return ne(true, expr, val);
    }
    public <R> WhereBuilder<T> ne(boolean condition, SqlExpression<R> expr, SqlExpression<? extends R> val) {
        return executeFlatCondition(condition, expr, SqlOperator.NE, val);
    }
    public <R> WhereBuilder<T> ne(SqlExpression<R> expr, R val, Object... dummy) {
        return ne(true, expr, val);
    }
    public <R> WhereBuilder<T> ne(boolean condition, SqlExpression<R> expr, R val, Object... dummy) {
        return executeFlatCondition(condition, expr, SqlOperator.NE, val);
    }
    public <R> WhereBuilder<T> ne(SqlExpression<R> expr, Query<?> subQuery) {
        return ne(true, expr, subQuery);
    }
    public <R> WhereBuilder<T> ne(boolean condition, SqlExpression<R> expr, Query<?> subQuery) {
        return executeFlatCondition(condition, expr, SqlOperator.NE, subQuery);
    }
    public <R> WhereBuilder<T> ne(SqlExpression<R> expr, WhereBuilder<?> subQuery) {
        return ne(true, expr, subQuery);
    }
    public <R> WhereBuilder<T> ne(boolean condition, SqlExpression<R> expr, WhereBuilder<?> subQuery) {
        return executeFlatCondition(condition, expr, SqlOperator.NE, subQuery);
    }

    public <R> WhereBuilder<T> ne(Function<T, R> getter, R val) {
        return ne(true, getter, val);
    }
    public <R> WhereBuilder<T> ne(boolean condition, Function<T, R> getter, R val) {
        return executeFlatCondition(condition, getter, SqlOperator.NE, val);
    }
    public <R extends Number> WhereBuilder<T> ne(Function<T, R> getter, Number val, Object... dummy) {
        return ne(true, getter, val);
    }
    public <R extends Number> WhereBuilder<T> ne(boolean condition, Function<T, R> getter, Number val, Object... dummy) {
        return executeFlatCondition(condition, getter, SqlOperator.NE, val);
    }
    public <R> WhereBuilder<T> ne(Function<T, R> getter, SqlExpression<? extends R> val) {
        return ne(true, getter, val);
    }
    public <R> WhereBuilder<T> ne(boolean condition, Function<T, R> getter, SqlExpression<? extends R> val) {
        return executeFlatCondition(condition, getter, SqlOperator.NE, val);
    }
    public <R> WhereBuilder<T> ne(Function<T, R> getter, Query<?> subQuery) {
        return ne(true, getter, subQuery);
    }
    public <R> WhereBuilder<T> ne(boolean condition, Function<T, R> getter, Query<?> subQuery) {
        return executeFlatCondition(condition, getter, SqlOperator.NE, subQuery);
    }
    public <R> WhereBuilder<T> ne(Function<T, R> getter, WhereBuilder<?> subQuery) {
        return ne(true, getter, subQuery);
    }
    public <R> WhereBuilder<T> ne(boolean condition, Function<T, R> getter, WhereBuilder<?> subQuery) {
        return executeFlatCondition(condition, getter, SqlOperator.NE, subQuery);
    }

    public <R> WhereBuilder<T> ne(String propertyOrColumn, R val) {
        return ne(true, propertyOrColumn, val);
    }
    public <R> WhereBuilder<T> ne(boolean condition, String propertyOrColumn, R val) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.NE, val);
    }
    public WhereBuilder<T> ne(String propertyOrColumn, SqlExpression<?> val) {
        return ne(true, propertyOrColumn, val);
    }
    public WhereBuilder<T> ne(boolean condition, String propertyOrColumn, SqlExpression<?> val) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.NE, val);
    }
    public WhereBuilder<T> ne(String propertyOrColumn, Query<?> subQuery) {
        return ne(true, propertyOrColumn, subQuery);
    }
    public WhereBuilder<T> ne(boolean condition, String propertyOrColumn, Query<?> subQuery) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.NE, subQuery);
    }
    public WhereBuilder<T> ne(String propertyOrColumn, WhereBuilder<?> subQuery) {
        return ne(true, propertyOrColumn, subQuery);
    }
    public WhereBuilder<T> ne(boolean condition, String propertyOrColumn, WhereBuilder<?> subQuery) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.NE, subQuery);
    }

    // --- gt ---
    public <R extends Comparable<? super R>> WhereBuilder<T> gt(R propOrExpr, R val) {
        return gt(true, propOrExpr, val);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> gt(boolean condition, R propOrExpr, R val) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.GT, val);
    }
    public <R extends Number & Comparable<?>> WhereBuilder<T> gt(R propOrExpr, Number val, Object... dummy) {
        return gt(true, propOrExpr, val);
    }
    public <R extends Number & Comparable<?>> WhereBuilder<T> gt(boolean condition, R propOrExpr, Number val, Object... dummy) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.GT, val);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> gt(R propOrExpr, SqlExpression<? extends R> val) {
        return gt(true, propOrExpr, val);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> gt(boolean condition, R propOrExpr, SqlExpression<? extends R> val) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.GT, val);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> gt(R propOrExpr, Query<?> subQuery) {
        return gt(true, propOrExpr, subQuery);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> gt(boolean condition, R propOrExpr, Query<?> subQuery) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.GT, subQuery);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> gt(R propOrExpr, WhereBuilder<?> subQuery) {
        return gt(true, propOrExpr, subQuery);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> gt(boolean condition, R propOrExpr, WhereBuilder<?> subQuery) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.GT, subQuery);
    }

    public <R extends Comparable<? super R>> WhereBuilder<T> gt(SqlExpression<R> expr, SqlExpression<? extends R> val) {
        return gt(true, expr, val);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> gt(boolean condition, SqlExpression<R> expr, SqlExpression<? extends R> val) {
        return executeFlatCondition(condition, expr, SqlOperator.GT, val);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> gt(SqlExpression<R> expr, R val) {
        return gt(true, expr, val);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> gt(boolean condition, SqlExpression<R> expr, R val) {
        return executeFlatCondition(condition, expr, SqlOperator.GT, val);
    }
    public <R extends Number & Comparable<?>> WhereBuilder<T> gt(SqlExpression<R> expr, Number val, Object... dummy) {
        return gt(true, expr, val);
    }
    public <R extends Number & Comparable<?>> WhereBuilder<T> gt(boolean condition, SqlExpression<R> expr, Number val, Object... dummy) {
        return executeFlatCondition(condition, expr, SqlOperator.GT, val);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> gt(SqlExpression<R> expr, Query<?> subQuery) {
        return gt(true, expr, subQuery);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> gt(boolean condition, SqlExpression<R> expr, Query<?> subQuery) {
        return executeFlatCondition(condition, expr, SqlOperator.GT, subQuery);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> gt(SqlExpression<R> expr, WhereBuilder<?> subQuery) {
        return gt(true, expr, subQuery);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> gt(boolean condition, SqlExpression<R> expr, WhereBuilder<?> subQuery) {
        return executeFlatCondition(condition, expr, SqlOperator.GT, subQuery);
    }

    public <R extends Comparable<? super R>> WhereBuilder<T> gt(Function<T, R> getter, R val) {
        return gt(true, getter, val);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> gt(boolean condition, Function<T, R> getter, R val) {
        return executeFlatCondition(condition, getter, SqlOperator.GT, val);
    }
    public <R extends Number & Comparable<?>> WhereBuilder<T> gt(Function<T, R> getter, Number val, Object... dummy) {
        return gt(true, getter, val);
    }
    public <R extends Number & Comparable<?>> WhereBuilder<T> gt(boolean condition, Function<T, R> getter, Number val, Object... dummy) {
        return executeFlatCondition(condition, getter, SqlOperator.GT, val);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> gt(Function<T, R> getter, SqlExpression<? extends R> val) {
        return gt(true, getter, val);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> gt(boolean condition, Function<T, R> getter, SqlExpression<? extends R> val) {
        return executeFlatCondition(condition, getter, SqlOperator.GT, val);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> gt(Function<T, R> getter, Query<?> subQuery) {
        return gt(true, getter, subQuery);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> gt(boolean condition, Function<T, R> getter, Query<?> subQuery) {
        return executeFlatCondition(condition, getter, SqlOperator.GT, subQuery);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> gt(Function<T, R> getter, WhereBuilder<?> subQuery) {
        return gt(true, getter, subQuery);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> gt(boolean condition, Function<T, R> getter, WhereBuilder<?> subQuery) {
        return executeFlatCondition(condition, getter, SqlOperator.GT, subQuery);
    }

    public <R extends Comparable<? super R>> WhereBuilder<T> gt(String propertyOrColumn, R val) {
        return gt(true, propertyOrColumn, val);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> gt(boolean condition, String propertyOrColumn, R val) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.GT, val);
    }
    public WhereBuilder<T> gt(String propertyOrColumn, SqlExpression<?> val) {
        return gt(true, propertyOrColumn, val);
    }
    public WhereBuilder<T> gt(boolean condition, String propertyOrColumn, SqlExpression<?> val) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.GT, val);
    }
    public WhereBuilder<T> gt(String propertyOrColumn, Query<?> subQuery) {
        return gt(true, propertyOrColumn, subQuery);
    }
    public WhereBuilder<T> gt(boolean condition, String propertyOrColumn, Query<?> subQuery) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.GT, subQuery);
    }
    public WhereBuilder<T> gt(String propertyOrColumn, WhereBuilder<?> subQuery) {
        return gt(true, propertyOrColumn, subQuery);
    }
    public WhereBuilder<T> gt(boolean condition, String propertyOrColumn, WhereBuilder<?> subQuery) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.GT, subQuery);
    }

    // --- ge ---
    public <R extends Comparable<? super R>> WhereBuilder<T> ge(R propOrExpr, R val) {
        return ge(true, propOrExpr, val);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> ge(boolean condition, R propOrExpr, R val) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.GE, val);
    }
    public <R extends Number & Comparable<?>> WhereBuilder<T> ge(R propOrExpr, Number val, Object... dummy) {
        return ge(true, propOrExpr, val);
    }
    public <R extends Number & Comparable<?>> WhereBuilder<T> ge(boolean condition, R propOrExpr, Number val, Object... dummy) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.GE, val);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> ge(R propOrExpr, SqlExpression<? extends R> val) {
        return ge(true, propOrExpr, val);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> ge(boolean condition, R propOrExpr, SqlExpression<? extends R> val) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.GE, val);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> ge(R propOrExpr, Query<?> subQuery) {
        return ge(true, propOrExpr, subQuery);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> ge(boolean condition, R propOrExpr, Query<?> subQuery) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.GE, subQuery);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> ge(R propOrExpr, WhereBuilder<?> subQuery) {
        return ge(true, propOrExpr, subQuery);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> ge(boolean condition, R propOrExpr, WhereBuilder<?> subQuery) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.GE, subQuery);
    }

    public <R extends Comparable<? super R>> WhereBuilder<T> ge(SqlExpression<R> expr, SqlExpression<? extends R> val) {
        return ge(true, expr, val);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> ge(boolean condition, SqlExpression<R> expr, SqlExpression<? extends R> val) {
        return executeFlatCondition(condition, expr, SqlOperator.GE, val);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> ge(SqlExpression<R> expr, R val) {
        return ge(true, expr, val);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> ge(boolean condition, SqlExpression<R> expr, R val) {
        return executeFlatCondition(condition, expr, SqlOperator.GE, val);
    }
    public <R extends Number & Comparable<?>> WhereBuilder<T> ge(SqlExpression<R> expr, Number val, Object... dummy) {
        return ge(true, expr, val);
    }
    public <R extends Number & Comparable<?>> WhereBuilder<T> ge(boolean condition, SqlExpression<R> expr, Number val, Object... dummy) {
        return executeFlatCondition(condition, expr, SqlOperator.GE, val);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> ge(SqlExpression<R> expr, Query<?> subQuery) {
        return ge(true, expr, subQuery);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> ge(boolean condition, SqlExpression<R> expr, Query<?> subQuery) {
        return executeFlatCondition(condition, expr, SqlOperator.GE, subQuery);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> ge(SqlExpression<R> expr, WhereBuilder<?> subQuery) {
        return ge(true, expr, subQuery);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> ge(boolean condition, SqlExpression<R> expr, WhereBuilder<?> subQuery) {
        return executeFlatCondition(condition, expr, SqlOperator.GE, subQuery);
    }

    public <R extends Comparable<? super R>> WhereBuilder<T> ge(Function<T, R> getter, R val) {
        return ge(true, getter, val);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> ge(boolean condition, Function<T, R> getter, R val) {
        return executeFlatCondition(condition, getter, SqlOperator.GE, val);
    }
    public <R extends Number & Comparable<?>> WhereBuilder<T> ge(Function<T, R> getter, Number val, Object... dummy) {
        return ge(true, getter, val);
    }
    public <R extends Number & Comparable<?>> WhereBuilder<T> ge(boolean condition, Function<T, R> getter, Number val, Object... dummy) {
        return executeFlatCondition(condition, getter, SqlOperator.GE, val);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> ge(Function<T, R> getter, SqlExpression<? extends R> val) {
        return ge(true, getter, val);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> ge(boolean condition, Function<T, R> getter, SqlExpression<? extends R> val) {
        return executeFlatCondition(condition, getter, SqlOperator.GE, val);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> ge(Function<T, R> getter, Query<?> subQuery) {
        return ge(true, getter, subQuery);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> ge(boolean condition, Function<T, R> getter, Query<?> subQuery) {
        return executeFlatCondition(condition, getter, SqlOperator.GE, subQuery);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> ge(Function<T, R> getter, WhereBuilder<?> subQuery) {
        return ge(true, getter, subQuery);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> ge(boolean condition, Function<T, R> getter, WhereBuilder<?> subQuery) {
        return executeFlatCondition(condition, getter, SqlOperator.GE, subQuery);
    }

    public <R extends Comparable<? super R>> WhereBuilder<T> ge(String propertyOrColumn, R val) {
        return ge(true, propertyOrColumn, val);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> ge(boolean condition, String propertyOrColumn, R val) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.GE, val);
    }
    public WhereBuilder<T> ge(String propertyOrColumn, SqlExpression<?> val) {
        return ge(true, propertyOrColumn, val);
    }
    public WhereBuilder<T> ge(boolean condition, String propertyOrColumn, SqlExpression<?> val) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.GE, val);
    }
    public WhereBuilder<T> ge(String propertyOrColumn, Query<?> subQuery) {
        return ge(true, propertyOrColumn, subQuery);
    }
    public WhereBuilder<T> ge(boolean condition, String propertyOrColumn, Query<?> subQuery) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.GE, subQuery);
    }
    public WhereBuilder<T> ge(String propertyOrColumn, WhereBuilder<?> subQuery) {
        return ge(true, propertyOrColumn, subQuery);
    }
    public WhereBuilder<T> ge(boolean condition, String propertyOrColumn, WhereBuilder<?> subQuery) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.GE, subQuery);
    }

    // --- lt ---
    public <R extends Comparable<? super R>> WhereBuilder<T> lt(R propOrExpr, R val) {
        return lt(true, propOrExpr, val);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> lt(boolean condition, R propOrExpr, R val) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.LT, val);
    }
    public <R extends Number & Comparable<?>> WhereBuilder<T> lt(R propOrExpr, Number val, Object... dummy) {
        return lt(true, propOrExpr, val);
    }
    public <R extends Number & Comparable<?>> WhereBuilder<T> lt(boolean condition, R propOrExpr, Number val, Object... dummy) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.LT, val);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> lt(R propOrExpr, SqlExpression<? extends R> val) {
        return lt(true, propOrExpr, val);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> lt(boolean condition, R propOrExpr, SqlExpression<? extends R> val) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.LT, val);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> lt(R propOrExpr, Query<?> subQuery) {
        return lt(true, propOrExpr, subQuery);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> lt(boolean condition, R propOrExpr, Query<?> subQuery) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.LT, subQuery);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> lt(R propOrExpr, WhereBuilder<?> subQuery) {
        return lt(true, propOrExpr, subQuery);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> lt(boolean condition, R propOrExpr, WhereBuilder<?> subQuery) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.LT, subQuery);
    }

    public <R extends Comparable<? super R>> WhereBuilder<T> lt(SqlExpression<R> expr, SqlExpression<? extends R> val) {
        return lt(true, expr, val);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> lt(boolean condition, SqlExpression<R> expr, SqlExpression<? extends R> val) {
        return executeFlatCondition(condition, expr, SqlOperator.LT, val);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> lt(SqlExpression<R> expr, R val) {
        return lt(true, expr, val);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> lt(boolean condition, SqlExpression<R> expr, R val) {
        return executeFlatCondition(condition, expr, SqlOperator.LT, val);
    }
    public <R extends Number & Comparable<?>> WhereBuilder<T> lt(SqlExpression<R> expr, Number val, Object... dummy) {
        return lt(true, expr, val);
    }
    public <R extends Number & Comparable<?>> WhereBuilder<T> lt(boolean condition, SqlExpression<R> expr, Number val, Object... dummy) {
        return executeFlatCondition(condition, expr, SqlOperator.LT, val);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> lt(SqlExpression<R> expr, Query<?> subQuery) {
        return lt(true, expr, subQuery);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> lt(boolean condition, SqlExpression<R> expr, Query<?> subQuery) {
        return executeFlatCondition(condition, expr, SqlOperator.LT, subQuery);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> lt(SqlExpression<R> expr, WhereBuilder<?> subQuery) {
        return lt(true, expr, subQuery);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> lt(boolean condition, SqlExpression<R> expr, WhereBuilder<?> subQuery) {
        return executeFlatCondition(condition, expr, SqlOperator.LT, subQuery);
    }

    public <R extends Comparable<? super R>> WhereBuilder<T> lt(Function<T, R> getter, R val) {
        return lt(true, getter, val);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> lt(boolean condition, Function<T, R> getter, R val) {
        return executeFlatCondition(condition, getter, SqlOperator.LT, val);
    }
    public <R extends Number & Comparable<?>> WhereBuilder<T> lt(Function<T, R> getter, Number val, Object... dummy) {
        return lt(true, getter, val);
    }
    public <R extends Number & Comparable<?>> WhereBuilder<T> lt(boolean condition, Function<T, R> getter, Number val, Object... dummy) {
        return executeFlatCondition(condition, getter, SqlOperator.LT, val);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> lt(Function<T, R> getter, SqlExpression<? extends R> val) {
        return lt(true, getter, val);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> lt(boolean condition, Function<T, R> getter, SqlExpression<? extends R> val) {
        return executeFlatCondition(condition, getter, SqlOperator.LT, val);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> lt(Function<T, R> getter, Query<?> subQuery) {
        return lt(true, getter, subQuery);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> lt(boolean condition, Function<T, R> getter, Query<?> subQuery) {
        return executeFlatCondition(condition, getter, SqlOperator.LT, subQuery);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> lt(Function<T, R> getter, WhereBuilder<?> subQuery) {
        return lt(true, getter, subQuery);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> lt(boolean condition, Function<T, R> getter, WhereBuilder<?> subQuery) {
        return executeFlatCondition(condition, getter, SqlOperator.LT, subQuery);
    }

    public <R extends Comparable<? super R>> WhereBuilder<T> lt(String propertyOrColumn, R val) {
        return lt(true, propertyOrColumn, val);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> lt(boolean condition, String propertyOrColumn, R val) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.LT, val);
    }
    public WhereBuilder<T> lt(String propertyOrColumn, SqlExpression<?> val) {
        return lt(true, propertyOrColumn, val);
    }
    public WhereBuilder<T> lt(boolean condition, String propertyOrColumn, SqlExpression<?> val) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.LT, val);
    }
    public WhereBuilder<T> lt(String propertyOrColumn, Query<?> subQuery) {
        return lt(true, propertyOrColumn, subQuery);
    }
    public WhereBuilder<T> lt(boolean condition, String propertyOrColumn, Query<?> subQuery) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.LT, subQuery);
    }
    public WhereBuilder<T> lt(String propertyOrColumn, WhereBuilder<?> subQuery) {
        return lt(true, propertyOrColumn, subQuery);
    }
    public WhereBuilder<T> lt(boolean condition, String propertyOrColumn, WhereBuilder<?> subQuery) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.LT, subQuery);
    }

    // --- le ---
    public <R extends Comparable<? super R>> WhereBuilder<T> le(R propOrExpr, R val) {
        return le(true, propOrExpr, val);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> le(boolean condition, R propOrExpr, R val) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.LE, val);
    }
    public <R extends Number & Comparable<?>> WhereBuilder<T> le(R propOrExpr, Number val, Object... dummy) {
        return le(true, propOrExpr, val);
    }
    public <R extends Number & Comparable<?>> WhereBuilder<T> le(boolean condition, R propOrExpr, Number val, Object... dummy) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.LE, val);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> le(R propOrExpr, SqlExpression<? extends R> val) {
        return le(true, propOrExpr, val);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> le(boolean condition, R propOrExpr, SqlExpression<? extends R> val) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.LE, val);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> le(R propOrExpr, Query<?> subQuery) {
        return le(true, propOrExpr, subQuery);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> le(boolean condition, R propOrExpr, Query<?> subQuery) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.LE, subQuery);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> le(R propOrExpr, WhereBuilder<?> subQuery) {
        return le(true, propOrExpr, subQuery);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> le(boolean condition, R propOrExpr, WhereBuilder<?> subQuery) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.LE, subQuery);
    }

    public <R extends Comparable<? super R>> WhereBuilder<T> le(SqlExpression<R> expr, SqlExpression<? extends R> val) {
        return le(true, expr, val);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> le(boolean condition, SqlExpression<R> expr, SqlExpression<? extends R> val) {
        return executeFlatCondition(condition, expr, SqlOperator.LE, val);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> le(SqlExpression<R> expr, R val) {
        return le(true, expr, val);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> le(boolean condition, SqlExpression<R> expr, R val) {
        return executeFlatCondition(condition, expr, SqlOperator.LE, val);
    }
    public <R extends Number & Comparable<?>> WhereBuilder<T> le(SqlExpression<R> expr, Number val, Object... dummy) {
        return le(true, expr, val);
    }
    public <R extends Number & Comparable<?>> WhereBuilder<T> le(boolean condition, SqlExpression<R> expr, Number val, Object... dummy) {
        return executeFlatCondition(condition, expr, SqlOperator.LE, val);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> le(SqlExpression<R> expr, Query<?> subQuery) {
        return le(true, expr, subQuery);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> le(boolean condition, SqlExpression<R> expr, Query<?> subQuery) {
        return executeFlatCondition(condition, expr, SqlOperator.LE, subQuery);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> le(SqlExpression<R> expr, WhereBuilder<?> subQuery) {
        return le(true, expr, subQuery);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> le(boolean condition, SqlExpression<R> expr, WhereBuilder<?> subQuery) {
        return executeFlatCondition(condition, expr, SqlOperator.LE, subQuery);
    }

    public <R extends Comparable<? super R>> WhereBuilder<T> le(Function<T, R> getter, R val) {
        return le(true, getter, val);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> le(boolean condition, Function<T, R> getter, R val) {
        return executeFlatCondition(condition, getter, SqlOperator.LE, val);
    }
    public <R extends Number & Comparable<?>> WhereBuilder<T> le(Function<T, R> getter, Number val, Object... dummy) {
        return le(true, getter, val);
    }
    public <R extends Number & Comparable<?>> WhereBuilder<T> le(boolean condition, Function<T, R> getter, Number val, Object... dummy) {
        return executeFlatCondition(condition, getter, SqlOperator.LE, val);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> le(Function<T, R> getter, SqlExpression<? extends R> val) {
        return le(true, getter, val);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> le(boolean condition, Function<T, R> getter, SqlExpression<? extends R> val) {
        return executeFlatCondition(condition, getter, SqlOperator.LE, val);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> le(Function<T, R> getter, Query<?> subQuery) {
        return le(true, getter, subQuery);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> le(boolean condition, Function<T, R> getter, Query<?> subQuery) {
        return executeFlatCondition(condition, getter, SqlOperator.LE, subQuery);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> le(Function<T, R> getter, WhereBuilder<?> subQuery) {
        return le(true, getter, subQuery);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> le(boolean condition, Function<T, R> getter, WhereBuilder<?> subQuery) {
        return executeFlatCondition(condition, getter, SqlOperator.LE, subQuery);
    }

    public <R extends Comparable<? super R>> WhereBuilder<T> le(String propertyOrColumn, R val) {
        return le(true, propertyOrColumn, val);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> le(boolean condition, String propertyOrColumn, R val) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.LE, val);
    }
    public WhereBuilder<T> le(String propertyOrColumn, SqlExpression<?> val) {
        return le(true, propertyOrColumn, val);
    }
    public WhereBuilder<T> le(boolean condition, String propertyOrColumn, SqlExpression<?> val) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.LE, val);
    }
    public WhereBuilder<T> le(String propertyOrColumn, Query<?> subQuery) {
        return le(true, propertyOrColumn, subQuery);
    }
    public WhereBuilder<T> le(boolean condition, String propertyOrColumn, Query<?> subQuery) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.LE, subQuery);
    }
    public WhereBuilder<T> le(String propertyOrColumn, WhereBuilder<?> subQuery) {
        return le(true, propertyOrColumn, subQuery);
    }
    public WhereBuilder<T> le(boolean condition, String propertyOrColumn, WhereBuilder<?> subQuery) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.LE, subQuery);
    }

    // --- like ---
    public <R extends CharSequence> WhereBuilder<T> like(R propOrExpr, String val) {
        return like(true, propOrExpr, val);
    }
    public <R extends CharSequence> WhereBuilder<T> like(boolean condition, R propOrExpr, String val) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.LIKE, val);
    }
    public <R extends CharSequence> WhereBuilder<T> like(R propOrExpr, SqlExpression<? extends CharSequence> val) {
        return like(true, propOrExpr, val);
    }
    public <R extends CharSequence> WhereBuilder<T> like(boolean condition, R propOrExpr, SqlExpression<? extends CharSequence> val) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.LIKE, val);
    }
    public <R extends CharSequence> WhereBuilder<T> like(SqlExpression<R> expr, String val) {
        return like(true, expr, val);
    }
    public <R extends CharSequence> WhereBuilder<T> like(boolean condition, SqlExpression<R> expr, String val) {
        return executeFlatCondition(condition, expr, SqlOperator.LIKE, val);
    }
    public <R extends CharSequence> WhereBuilder<T> like(SqlExpression<R> expr, SqlExpression<? extends CharSequence> val) {
        return like(true, expr, val);
    }
    public <R extends CharSequence> WhereBuilder<T> like(boolean condition, SqlExpression<R> expr, SqlExpression<? extends CharSequence> val) {
        return executeFlatCondition(condition, expr, SqlOperator.LIKE, val);
    }
    public <R extends CharSequence> WhereBuilder<T> like(Function<T, R> getter, String val) {
        return like(true, getter, val);
    }
    public <R extends CharSequence> WhereBuilder<T> like(boolean condition, Function<T, R> getter, String val) {
        return executeFlatCondition(condition, getter, SqlOperator.LIKE, val);
    }
    public <R extends CharSequence> WhereBuilder<T> like(Function<T, R> getter, SqlExpression<? extends CharSequence> val) {
        return like(true, getter, val);
    }
    public <R extends CharSequence> WhereBuilder<T> like(boolean condition, Function<T, R> getter, SqlExpression<? extends CharSequence> val) {
        return executeFlatCondition(condition, getter, SqlOperator.LIKE, val);
    }
    public WhereBuilder<T> like(String propertyOrColumn, String val) {
        return like(true, propertyOrColumn, val);
    }
    public WhereBuilder<T> like(boolean condition, String propertyOrColumn, String val) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.LIKE, val);
    }
    public WhereBuilder<T> like(String propertyOrColumn, SqlExpression<? extends CharSequence> val) {
        return like(true, propertyOrColumn, val);
    }
    public WhereBuilder<T> like(boolean condition, String propertyOrColumn, SqlExpression<? extends CharSequence> val) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.LIKE, val);
    }

    public <R extends CharSequence> WhereBuilder<T> likeLeft(R propOrExpr, String val) {
        return likeLeft(true, propOrExpr, val);
    }
    public <R extends CharSequence> WhereBuilder<T> likeLeft(boolean condition, R propOrExpr, String val) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.LIKE, "%" + val);
    }
    public <R extends CharSequence> WhereBuilder<T> likeLeft(SqlExpression<R> expr, String val) {
        return likeLeft(true, expr, val);
    }
    public <R extends CharSequence> WhereBuilder<T> likeLeft(boolean condition, SqlExpression<R> expr, String val) {
        return executeFlatCondition(condition, expr, SqlOperator.LIKE, "%" + val);
    }
    public <R extends CharSequence> WhereBuilder<T> likeLeft(Function<T, R> getter, String val) {
        return likeLeft(true, getter, val);
    }
    public <R extends CharSequence> WhereBuilder<T> likeLeft(boolean condition, Function<T, R> getter, String val) {
        return executeFlatCondition(condition, getter, SqlOperator.LIKE, "%" + val);
    }
    public WhereBuilder<T> likeLeft(String propertyOrColumn, String val) {
        return likeLeft(true, propertyOrColumn, val);
    }
    public WhereBuilder<T> likeLeft(boolean condition, String propertyOrColumn, String val) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.LIKE, "%" + val);
    }

    public <R extends CharSequence> WhereBuilder<T> likeRight(R propOrExpr, String val) {
        return likeRight(true, propOrExpr, val);
    }
    public <R extends CharSequence> WhereBuilder<T> likeRight(boolean condition, R propOrExpr, String val) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.LIKE, val + "%");
    }
    public <R extends CharSequence> WhereBuilder<T> likeRight(SqlExpression<R> expr, String val) {
        return likeRight(true, expr, val);
    }
    public <R extends CharSequence> WhereBuilder<T> likeRight(boolean condition, SqlExpression<R> expr, String val) {
        return executeFlatCondition(condition, expr, SqlOperator.LIKE, val + "%");
    }
    public <R extends CharSequence> WhereBuilder<T> likeRight(Function<T, R> getter, String val) {
        return likeRight(true, getter, val);
    }
    public <R extends CharSequence> WhereBuilder<T> likeRight(boolean condition, Function<T, R> getter, String val) {
        return executeFlatCondition(condition, getter, SqlOperator.LIKE, val + "%");
    }
    public WhereBuilder<T> likeRight(String propertyOrColumn, String val) {
        return likeRight(true, propertyOrColumn, val);
    }
    public WhereBuilder<T> likeRight(boolean condition, String propertyOrColumn, String val) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.LIKE, val + "%");
    }

    public <R extends CharSequence> WhereBuilder<T> likeAll(R propOrExpr, String val) {
        return likeAll(true, propOrExpr, val);
    }
    public <R extends CharSequence> WhereBuilder<T> likeAll(boolean condition, R propOrExpr, String val) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.LIKE, "%" + val + "%");
    }
    public <R extends CharSequence> WhereBuilder<T> likeAll(SqlExpression<R> expr, String val) {
        return likeAll(true, expr, val);
    }
    public <R extends CharSequence> WhereBuilder<T> likeAll(boolean condition, SqlExpression<R> expr, String val) {
        return executeFlatCondition(condition, expr, SqlOperator.LIKE, "%" + val + "%");
    }
    public <R extends CharSequence> WhereBuilder<T> likeAll(Function<T, R> getter, String val) {
        return likeAll(true, getter, val);
    }
    public <R extends CharSequence> WhereBuilder<T> likeAll(boolean condition, Function<T, R> getter, String val) {
        return executeFlatCondition(condition, getter, SqlOperator.LIKE, "%" + val + "%");
    }
    public WhereBuilder<T> likeAll(String propertyOrColumn, String val) {
        return likeAll(true, propertyOrColumn, val);
    }
    public WhereBuilder<T> likeAll(boolean condition, String propertyOrColumn, String val) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.LIKE, "%" + val + "%");
    }

    public <R extends CharSequence> WhereBuilder<T> notLike(R propOrExpr, String val) {
        return notLike(true, propOrExpr, val);
    }
    public <R extends CharSequence> WhereBuilder<T> notLike(boolean condition, R propOrExpr, String val) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.NOT_LIKE, val);
    }
    public <R extends CharSequence> WhereBuilder<T> notLike(R propOrExpr, SqlExpression<? extends CharSequence> val) {
        return notLike(true, propOrExpr, val);
    }
    public <R extends CharSequence> WhereBuilder<T> notLike(boolean condition, R propOrExpr, SqlExpression<? extends CharSequence> val) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.NOT_LIKE, val);
    }
    public <R extends CharSequence> WhereBuilder<T> notLike(SqlExpression<R> expr, String val) {
        return notLike(true, expr, val);
    }
    public <R extends CharSequence> WhereBuilder<T> notLike(boolean condition, SqlExpression<R> expr, String val) {
        return executeFlatCondition(condition, expr, SqlOperator.NOT_LIKE, val);
    }
    public <R extends CharSequence> WhereBuilder<T> notLike(SqlExpression<R> expr, SqlExpression<? extends CharSequence> val) {
        return notLike(true, expr, val);
    }
    public <R extends CharSequence> WhereBuilder<T> notLike(boolean condition, SqlExpression<R> expr, SqlExpression<? extends CharSequence> val) {
        return executeFlatCondition(condition, expr, SqlOperator.NOT_LIKE, val);
    }
    public <R extends CharSequence> WhereBuilder<T> notLike(Function<T, R> getter, String val) {
        return notLike(true, getter, val);
    }
    public <R extends CharSequence> WhereBuilder<T> notLike(boolean condition, Function<T, R> getter, String val) {
        return executeFlatCondition(condition, getter, SqlOperator.NOT_LIKE, val);
    }
    public <R extends CharSequence> WhereBuilder<T> notLike(Function<T, R> getter, SqlExpression<? extends CharSequence> val) {
        return notLike(true, getter, val);
    }
    public <R extends CharSequence> WhereBuilder<T> notLike(boolean condition, Function<T, R> getter, SqlExpression<? extends CharSequence> val) {
        return executeFlatCondition(condition, getter, SqlOperator.NOT_LIKE, val);
    }
    public WhereBuilder<T> notLike(String propertyOrColumn, String val) {
        return notLike(true, propertyOrColumn, val);
    }
    public WhereBuilder<T> notLike(boolean condition, String propertyOrColumn, String val) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.NOT_LIKE, val);
    }
    public WhereBuilder<T> notLike(String propertyOrColumn, SqlExpression<? extends CharSequence> val) {
        return notLike(true, propertyOrColumn, val);
    }
    public WhereBuilder<T> notLike(boolean condition, String propertyOrColumn, SqlExpression<? extends CharSequence> val) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.NOT_LIKE, val);
    }

    // --- isNull / isNotNull ---
    public <R> WhereBuilder<T> isNull(R propOrExpr) {
        return isNull(true, propOrExpr);
    }
    public <R> WhereBuilder<T> isNull(boolean condition, R propOrExpr) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.IS_NULL);
    }
    public <R> WhereBuilder<T> isNull(SqlExpression<R> expr) {
        return isNull(true, expr);
    }
    public <R> WhereBuilder<T> isNull(boolean condition, SqlExpression<R> expr) {
        return executeFlatCondition(condition, expr, SqlOperator.IS_NULL);
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
    public <R> WhereBuilder<T> isNotNull(SqlExpression<R> expr) {
        return isNotNull(true, expr);
    }
    public <R> WhereBuilder<T> isNotNull(boolean condition, SqlExpression<R> expr) {
        return executeFlatCondition(condition, expr, SqlOperator.IS_NOT_NULL);
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
    public <R> WhereBuilder<T> in(R propOrExpr, Collection<? extends R> values) {
        return in(true, propOrExpr, values);
    }
    public <R> WhereBuilder<T> in(boolean condition, R propOrExpr, Collection<? extends R> values) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.IN, values);
    }
    @SafeVarargs
    public final <R> WhereBuilder<T> in(R propOrExpr, R... values) {
        return in(true, propOrExpr, values);
    }
    @SafeVarargs
    public final <R> WhereBuilder<T> in(boolean condition, R propOrExpr, R... values) {
        if (values != null && values.length == 1 && values[0] instanceof Collection) {
            return in(condition, propOrExpr, (Collection<? extends R>) values[0]);
        }
        return executeFlatCondition(condition, propOrExpr, SqlOperator.IN, (Object[]) values);
    }
    public <R> WhereBuilder<T> in(R propOrExpr, Query<?> subQuery) {
        return in(true, propOrExpr, subQuery);
    }
    public <R> WhereBuilder<T> in(boolean condition, R propOrExpr, Query<?> subQuery) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.IN, subQuery);
    }
    public <R> WhereBuilder<T> in(R propOrExpr, WhereBuilder<?> subQuery) {
        return in(true, propOrExpr, subQuery);
    }
    public <R> WhereBuilder<T> in(boolean condition, R propOrExpr, WhereBuilder<?> subQuery) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.IN, subQuery);
    }
    public <R> WhereBuilder<T> in(R propOrExpr, SqlExpression<?> subQuery) {
        return in(true, propOrExpr, subQuery);
    }
    public <R> WhereBuilder<T> in(boolean condition, R propOrExpr, SqlExpression<?> subQuery) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.IN, subQuery);
    }

    public <R> WhereBuilder<T> in(SqlExpression<R> expr, Collection<? extends R> values) {
        return in(true, expr, values);
    }
    public <R> WhereBuilder<T> in(boolean condition, SqlExpression<R> expr, Collection<? extends R> values) {
        return executeFlatCondition(condition, expr, SqlOperator.IN, values);
    }
    @SafeVarargs
    public final <R> WhereBuilder<T> in(SqlExpression<R> expr, R... values) {
        return in(true, expr, values);
    }
    @SafeVarargs
    public final <R> WhereBuilder<T> in(boolean condition, SqlExpression<R> expr, R... values) {
        if (values != null && values.length == 1 && values[0] instanceof Collection) {
            return in(condition, expr, (Collection<? extends R>) values[0]);
        }
        return executeFlatCondition(condition, expr, SqlOperator.IN, (Object[]) values);
    }
    public <R> WhereBuilder<T> in(SqlExpression<R> expr, Query<?> subQuery) {
        return in(true, expr, subQuery);
    }
    public <R> WhereBuilder<T> in(boolean condition, SqlExpression<R> expr, Query<?> subQuery) {
        return executeFlatCondition(condition, expr, SqlOperator.IN, subQuery);
    }
    public <R> WhereBuilder<T> in(SqlExpression<R> expr, WhereBuilder<?> subQuery) {
        return in(true, expr, subQuery);
    }
    public <R> WhereBuilder<T> in(boolean condition, SqlExpression<R> expr, WhereBuilder<?> subQuery) {
        return executeFlatCondition(condition, expr, SqlOperator.IN, subQuery);
    }
    public <R> WhereBuilder<T> in(SqlExpression<R> expr, SqlExpression<?> subQuery) {
        return in(true, expr, subQuery);
    }
    public <R> WhereBuilder<T> in(boolean condition, SqlExpression<R> expr, SqlExpression<?> subQuery) {
        return executeFlatCondition(condition, expr, SqlOperator.IN, subQuery);
    }

    public <R> WhereBuilder<T> in(Function<T, R> getter, Collection<? extends R> values) {
        return in(true, getter, values);
    }
    public <R> WhereBuilder<T> in(boolean condition, Function<T, R> getter, Collection<? extends R> values) {
        return executeFlatCondition(condition, getter, SqlOperator.IN, values);
    }
    @SafeVarargs
    public final <R> WhereBuilder<T> in(Function<T, R> getter, R... values) {
        return in(true, getter, values);
    }
    @SafeVarargs
    public final <R> WhereBuilder<T> in(boolean condition, Function<T, R> getter, R... values) {
        if (values != null && values.length == 1 && values[0] instanceof Collection) {
            return in(condition, getter, (Collection<? extends R>) values[0]);
        }
        return executeFlatCondition(condition, getter, SqlOperator.IN, (Object[]) values);
    }
    public <R> WhereBuilder<T> in(Function<T, R> getter, Query<?> subQuery) {
        return in(true, getter, subQuery);
    }
    public <R> WhereBuilder<T> in(boolean condition, Function<T, R> getter, Query<?> subQuery) {
        return executeFlatCondition(condition, getter, SqlOperator.IN, subQuery);
    }
    public <R> WhereBuilder<T> in(Function<T, R> getter, WhereBuilder<?> subQuery) {
        return in(true, getter, subQuery);
    }
    public <R> WhereBuilder<T> in(boolean condition, Function<T, R> getter, WhereBuilder<?> subQuery) {
        return executeFlatCondition(condition, getter, SqlOperator.IN, subQuery);
    }
    public <R> WhereBuilder<T> in(Function<T, R> getter, SqlExpression<?> subQuery) {
        return in(true, getter, subQuery);
    }
    public <R> WhereBuilder<T> in(boolean condition, Function<T, R> getter, SqlExpression<?> subQuery) {
        return executeFlatCondition(condition, getter, SqlOperator.IN, subQuery);
    }

    public <R> WhereBuilder<T> in(String propertyOrColumn, Collection<? extends R> values) {
        return in(true, propertyOrColumn, values);
    }
    public <R> WhereBuilder<T> in(boolean condition, String propertyOrColumn, Collection<? extends R> values) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.IN, values);
    }
    @SafeVarargs
    public final <R> WhereBuilder<T> in(String propertyOrColumn, R... values) {
        return in(true, propertyOrColumn, values);
    }
    @SafeVarargs
    public final <R> WhereBuilder<T> in(boolean condition, String propertyOrColumn, R... values) {
        if (values != null && values.length == 1 && values[0] instanceof Collection) {
            return in(condition, propertyOrColumn, (Collection<? extends R>) values[0]);
        }
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.IN, (Object[]) values);
    }
    public WhereBuilder<T> in(String propertyOrColumn, Query<?> subQuery) {
        return in(true, propertyOrColumn, subQuery);
    }
    public WhereBuilder<T> in(boolean condition, String propertyOrColumn, Query<?> subQuery) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.IN, subQuery);
    }
    public WhereBuilder<T> in(String propertyOrColumn, WhereBuilder<?> subQuery) {
        return in(true, propertyOrColumn, subQuery);
    }
    public WhereBuilder<T> in(boolean condition, String propertyOrColumn, WhereBuilder<?> subQuery) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.IN, subQuery);
    }
    public WhereBuilder<T> in(String propertyOrColumn, SqlExpression<?> subQuery) {
        return in(true, propertyOrColumn, subQuery);
    }
    public WhereBuilder<T> in(boolean condition, String propertyOrColumn, SqlExpression<?> subQuery) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.IN, subQuery);
    }

    public <R> WhereBuilder<T> notIn(R propOrExpr, Collection<? extends R> values) {
        return notIn(true, propOrExpr, values);
    }
    public <R> WhereBuilder<T> notIn(boolean condition, R propOrExpr, Collection<? extends R> values) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.NOT_IN, values);
    }
    @SafeVarargs
    public final <R> WhereBuilder<T> notIn(R propOrExpr, R... values) {
        return notIn(true, propOrExpr, values);
    }
    @SafeVarargs
    public final <R> WhereBuilder<T> notIn(boolean condition, R propOrExpr, R... values) {
        if (values != null && values.length == 1 && values[0] instanceof Collection) {
            return notIn(condition, propOrExpr, (Collection<? extends R>) values[0]);
        }
        return executeFlatCondition(condition, propOrExpr, SqlOperator.NOT_IN, (Object[]) values);
    }
    public <R> WhereBuilder<T> notIn(R propOrExpr, Query<?> subQuery) {
        return notIn(true, propOrExpr, subQuery);
    }
    public <R> WhereBuilder<T> notIn(boolean condition, R propOrExpr, Query<?> subQuery) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.NOT_IN, subQuery);
    }
    public <R> WhereBuilder<T> notIn(R propOrExpr, WhereBuilder<?> subQuery) {
        return notIn(true, propOrExpr, subQuery);
    }
    public <R> WhereBuilder<T> notIn(boolean condition, R propOrExpr, WhereBuilder<?> subQuery) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.NOT_IN, subQuery);
    }
    public <R> WhereBuilder<T> notIn(R propOrExpr, SqlExpression<?> subQuery) {
        return notIn(true, propOrExpr, subQuery);
    }
    public <R> WhereBuilder<T> notIn(boolean condition, R propOrExpr, SqlExpression<?> subQuery) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.NOT_IN, subQuery);
    }

    public <R> WhereBuilder<T> notIn(SqlExpression<R> expr, Collection<? extends R> values) {
        return notIn(true, expr, values);
    }
    public <R> WhereBuilder<T> notIn(boolean condition, SqlExpression<R> expr, Collection<? extends R> values) {
        return executeFlatCondition(condition, expr, SqlOperator.NOT_IN, values);
    }
    @SafeVarargs
    public final <R> WhereBuilder<T> notIn(SqlExpression<R> expr, R... values) {
        return notIn(true, expr, values);
    }
    @SafeVarargs
    public final <R> WhereBuilder<T> notIn(boolean condition, SqlExpression<R> expr, R... values) {
        if (values != null && values.length == 1 && values[0] instanceof Collection) {
            return notIn(condition, expr, (Collection<? extends R>) values[0]);
        }
        return executeFlatCondition(condition, expr, SqlOperator.NOT_IN, (Object[]) values);
    }
    public <R> WhereBuilder<T> notIn(SqlExpression<R> expr, Query<?> subQuery) {
        return notIn(true, expr, subQuery);
    }
    public <R> WhereBuilder<T> notIn(boolean condition, SqlExpression<R> expr, Query<?> subQuery) {
        return executeFlatCondition(condition, expr, SqlOperator.NOT_IN, subQuery);
    }
    public <R> WhereBuilder<T> notIn(SqlExpression<R> expr, WhereBuilder<?> subQuery) {
        return notIn(true, expr, subQuery);
    }
    public <R> WhereBuilder<T> notIn(boolean condition, SqlExpression<R> expr, WhereBuilder<?> subQuery) {
        return executeFlatCondition(condition, expr, SqlOperator.NOT_IN, subQuery);
    }
    public <R> WhereBuilder<T> notIn(SqlExpression<R> expr, SqlExpression<?> subQuery) {
        return notIn(true, expr, subQuery);
    }
    public <R> WhereBuilder<T> notIn(boolean condition, SqlExpression<R> expr, SqlExpression<?> subQuery) {
        return executeFlatCondition(condition, expr, SqlOperator.NOT_IN, subQuery);
    }

    public <R> WhereBuilder<T> notIn(Function<T, R> getter, Collection<? extends R> values) {
        return notIn(true, getter, values);
    }
    public <R> WhereBuilder<T> notIn(boolean condition, Function<T, R> getter, Collection<? extends R> values) {
        return executeFlatCondition(condition, getter, SqlOperator.NOT_IN, values);
    }
    @SafeVarargs
    public final <R> WhereBuilder<T> notIn(Function<T, R> getter, R... values) {
        return notIn(true, getter, values);
    }
    @SafeVarargs
    public final <R> WhereBuilder<T> notIn(boolean condition, Function<T, R> getter, R... values) {
        if (values != null && values.length == 1 && values[0] instanceof Collection) {
            return notIn(condition, getter, (Collection<? extends R>) values[0]);
        }
        return executeFlatCondition(condition, getter, SqlOperator.NOT_IN, (Object[]) values);
    }
    public <R> WhereBuilder<T> notIn(Function<T, R> getter, Query<?> subQuery) {
        return notIn(true, getter, subQuery);
    }
    public <R> WhereBuilder<T> notIn(boolean condition, Function<T, R> getter, Query<?> subQuery) {
        return executeFlatCondition(condition, getter, SqlOperator.NOT_IN, subQuery);
    }
    public <R> WhereBuilder<T> notIn(Function<T, R> getter, WhereBuilder<?> subQuery) {
        return notIn(true, getter, subQuery);
    }
    public <R> WhereBuilder<T> notIn(boolean condition, Function<T, R> getter, WhereBuilder<?> subQuery) {
        return executeFlatCondition(condition, getter, SqlOperator.NOT_IN, subQuery);
    }
    public <R> WhereBuilder<T> notIn(Function<T, R> getter, SqlExpression<?> subQuery) {
        return notIn(true, getter, subQuery);
    }
    public <R> WhereBuilder<T> notIn(boolean condition, Function<T, R> getter, SqlExpression<?> subQuery) {
        return executeFlatCondition(condition, getter, SqlOperator.NOT_IN, subQuery);
    }

    public <R> WhereBuilder<T> notIn(String propertyOrColumn, Collection<? extends R> values) {
        return notIn(true, propertyOrColumn, values);
    }
    public <R> WhereBuilder<T> notIn(boolean condition, String propertyOrColumn, Collection<? extends R> values) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.NOT_IN, values);
    }
    @SafeVarargs
    public final <R> WhereBuilder<T> notIn(String propertyOrColumn, R... values) {
        return notIn(true, propertyOrColumn, values);
    }
    @SafeVarargs
    public final <R> WhereBuilder<T> notIn(boolean condition, String propertyOrColumn, R... values) {
        if (values != null && values.length == 1 && values[0] instanceof Collection) {
            return notIn(condition, propertyOrColumn, (Collection<? extends R>) values[0]);
        }
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.NOT_IN, (Object[]) values);
    }
    public WhereBuilder<T> notIn(String propertyOrColumn, Query<?> subQuery) {
        return notIn(true, propertyOrColumn, subQuery);
    }
    public WhereBuilder<T> notIn(boolean condition, String propertyOrColumn, Query<?> subQuery) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.NOT_IN, subQuery);
    }
    public WhereBuilder<T> notIn(String propertyOrColumn, WhereBuilder<?> subQuery) {
        return notIn(true, propertyOrColumn, subQuery);
    }
    public WhereBuilder<T> notIn(boolean condition, String propertyOrColumn, WhereBuilder<?> subQuery) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.NOT_IN, subQuery);
    }
    public WhereBuilder<T> notIn(String propertyOrColumn, SqlExpression<?> subQuery) {
        return notIn(true, propertyOrColumn, subQuery);
    }
    public WhereBuilder<T> notIn(boolean condition, String propertyOrColumn, SqlExpression<?> subQuery) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.NOT_IN, subQuery);
    }

    // --- between ---
    public <R extends Comparable<? super R>> WhereBuilder<T> between(R propOrExpr, R min, R max) {
        return between(true, propOrExpr, min, max);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> between(boolean condition, R propOrExpr, R min, R max) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.BETWEEN, min, max);
    }
    public <R extends Number & Comparable<?>> WhereBuilder<T> between(R propOrExpr, Number min, Number max, Object... dummy) {
        return between(true, propOrExpr, min, max);
    }
    public <R extends Number & Comparable<?>> WhereBuilder<T> between(boolean condition, R propOrExpr, Number min, Number max, Object... dummy) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.BETWEEN, min, max);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> between(R propOrExpr, SqlExpression<? extends R> min, SqlExpression<? extends R> max) {
        return between(true, propOrExpr, min, max);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> between(boolean condition, R propOrExpr, SqlExpression<? extends R> min, SqlExpression<? extends R> max) {
        return executeFlatCondition(condition, propOrExpr, SqlOperator.BETWEEN, min, max);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> between(SqlExpression<R> expr, SqlExpression<? extends R> min, SqlExpression<? extends R> max) {
        return between(true, expr, min, max);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> between(boolean condition, SqlExpression<R> expr, SqlExpression<? extends R> min, SqlExpression<? extends R> max) {
        return executeFlatCondition(condition, expr, SqlOperator.BETWEEN, min, max);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> between(SqlExpression<R> expr, R min, R max) {
        return between(true, expr, min, max);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> between(boolean condition, SqlExpression<R> expr, R min, R max) {
        return executeFlatCondition(condition, expr, SqlOperator.BETWEEN, min, max);
    }
    public <R extends Number & Comparable<?>> WhereBuilder<T> between(SqlExpression<R> expr, Number min, Number max, Object... dummy) {
        return between(true, expr, min, max);
    }
    public <R extends Number & Comparable<?>> WhereBuilder<T> between(boolean condition, SqlExpression<R> expr, Number min, Number max, Object... dummy) {
        return executeFlatCondition(condition, expr, SqlOperator.BETWEEN, min, max);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> between(Function<T, R> getter, R min, R max) {
        return between(true, getter, min, max);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> between(boolean condition, Function<T, R> getter, R min, R max) {
        return executeFlatCondition(condition, getter, SqlOperator.BETWEEN, min, max);
    }
    public <R extends Number & Comparable<?>> WhereBuilder<T> between(Function<T, R> getter, Number min, Number max, Object... dummy) {
        return between(true, getter, min, max);
    }
    public <R extends Number & Comparable<?>> WhereBuilder<T> between(boolean condition, Function<T, R> getter, Number min, Number max, Object... dummy) {
        return executeFlatCondition(condition, getter, SqlOperator.BETWEEN, min, max);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> between(Function<T, R> getter, SqlExpression<? extends R> min, SqlExpression<? extends R> max) {
        return between(true, getter, min, max);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> between(boolean condition, Function<T, R> getter, SqlExpression<? extends R> min, SqlExpression<? extends R> max) {
        return executeFlatCondition(condition, getter, SqlOperator.BETWEEN, min, max);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> between(String propertyOrColumn, R min, R max) {
        return between(true, propertyOrColumn, min, max);
    }
    public <R extends Comparable<? super R>> WhereBuilder<T> between(boolean condition, String propertyOrColumn, R min, R max) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.BETWEEN, min, max);
    }
    public WhereBuilder<T> between(String propertyOrColumn, SqlExpression<?> min, SqlExpression<?> max) {
        return between(true, propertyOrColumn, min, max);
    }
    public WhereBuilder<T> between(boolean condition, String propertyOrColumn, SqlExpression<?> min, SqlExpression<?> max) {
        return executeFlatCondition(condition, propertyOrColumn, SqlOperator.BETWEEN, min, max);
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

    @Override
    public SelectStatement getStatement() {
        if (statement instanceof SelectStatement) {
            return (SelectStatement) statement;
        }
        return rootQuery != null ? rootQuery.getStatement() : null;
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
        if (rootQuery != null) {
            return (List<E>) rootQuery.list();
        }
        if (env != null && statement instanceof SelectStatement) {
            return (List<E>) env.executeSelectList(null, (SelectStatement) statement, null, null);
        }
        throw new UnsupportedOperationException("list() is not supported on this builder");
    }

    public List<Map<String, Object>> listMaps() {
        if (rootQuery != null) {
            return rootQuery.listMaps();
        }
        if (env != null && statement instanceof SelectStatement) {
            return env.executeSelectMaps(null, (SelectStatement) statement);
        }
        throw new UnsupportedOperationException("listMaps() is not supported on this builder");
    }

    public List<Map<String, Object>> maps() {
        return listMaps();
    }

    public <V> V fetchOne(Class<V> targetType) {
        if (rootQuery != null) {
            return rootQuery.fetchOne(targetType);
        }
        if (env != null && statement instanceof SelectStatement) {
            ((SelectStatement) statement).setLimit(1);
            return env.executeSelectScalar(null, (SelectStatement) statement, targetType);
        }
        throw new UnsupportedOperationException("fetchOne() is not supported on this builder");
    }

    public <V> List<V> fetchList(Class<V> targetType) {
        if (rootQuery != null) {
            return rootQuery.fetchList(targetType);
        }
        if (env != null && statement instanceof SelectStatement) {
            return env.executeSelectScalarList(null, (SelectStatement) statement, targetType);
        }
        throw new UnsupportedOperationException("fetchList() is not supported on this builder");
    }

    @SuppressWarnings("unchecked")
    public T single() {
        if (rootQuery != null) {
            return (T) rootQuery.single();
        }
        List<T> l = list();
        return (l != null && !l.isEmpty()) ? l.get(0) : null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T firstOrNull() {
        return single();
    }

    @Override
    public <R> R firstOrNull(Class<R> targetClass) {
        List<R> list = toList(targetClass);
        return (list != null && !list.isEmpty()) ? list.get(0) : null;
    }

    public long count() {
        if (rootQuery != null) {
            return rootQuery.count();
        }
        if (env != null && statement instanceof SelectStatement) {
            return env.executeSelectCount(null, (SelectStatement) statement, env.getDialect(), entityClass);
        }
        throw new UnsupportedOperationException("count() is not supported on this builder");
    }

    public com.javaorm.core.dialect.SqlAndParams toSqlAndParams() {
        if (sqlAndParamsSupplier != null) {
            return sqlAndParamsSupplier.get();
        }
        if (rootQuery != null) {
            return rootQuery.toSqlAndParams();
        }
        if (env != null && statement != null) {
            if (statement instanceof SelectStatement) {
                return env.getDialect().buildSelect((SelectStatement) statement);
            } else if (statement instanceof UpdateStatement) {
                return env.getDialect().buildUpdate((UpdateStatement) statement);
            } else if (statement instanceof DeleteStatement) {
                return env.getDialect().buildDelete((DeleteStatement) statement);
            }
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
        return printSql(true);
    }

    public WhereBuilder<T> printSql(boolean print) {
        if (printSqlConsumer != null) {
            printSqlConsumer.accept(print);
        } else if (rootQuery != null) {
            rootQuery.printSql(print);
        } else if (env != null) {
            env.setPrintSql(print);
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

    @Override
    @SuppressWarnings("unchecked")
    public List<T> toList() {
        return (List<T>) list();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R> List<R> toList(Class<R> targetClass) {
        if (targetClass == null) {
            throw new IllegalArgumentException("Target class must not be null");
        }
        if (rootQuery != null) {
            return rootQuery.toList(targetClass);
        }
        if (env != null && statement instanceof SelectStatement) {
            SelectStatement stmt = (SelectStatement) statement;
            if (targetClass == entityClass) {
                return (List<R>) env.executeSelectList(null, stmt, null, null);
            }
            if (com.javaorm.core.metadata.ColumnMetadata.isScalarType(targetClass)) {
                return env.executeSelectScalarList(null, stmt, targetClass);
            }
            if (Map.class.isAssignableFrom(targetClass)) {
                return (List<R>) env.executeSelectMaps(null, stmt);
            }
            return env.executeSelectDtoList(null, stmt, targetClass);
        }
        throw new UnsupportedOperationException("toList(Class) is not supported on this builder");
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
