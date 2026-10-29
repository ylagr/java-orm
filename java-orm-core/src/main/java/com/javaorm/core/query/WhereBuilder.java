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
    private String scopeAlias;
    private final Consumer<Condition> conditionReceiver;
    private final Supplier<List<?>> customListSupplier;
    private final java.util.function.IntSupplier executeSupplier;
    private final Supplier<com.javaorm.core.dialect.SqlAndParams> sqlAndParamsSupplier;
    private final Consumer<Boolean> printSqlConsumer;

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
    public <R> PropertyOperator<WhereBuilder<T>, R> and(SqlExpression<R> expr) {
        rootQuery.resolveExpression(expr);
        return new PropertyOperator<>(this, (op, vals) -> {
            ExpressionCondition cond = new ExpressionCondition(expr, op, vals);
            cond.setLogicalOperator(LogicalOperator.AND);
            conditionReceiver.accept(cond);
        });
    }

    public <R> PropertyOperator<WhereBuilder<T>, R> and(String propertyOrColumn) {
        PropertyPath path = RecordingContext.finish();
        if (path != null) {
            return new PropertyOperator<>(this, path, (p, op, vals) -> appendCondition(LogicalOperator.AND, p, op, vals));
        }
        if (propertyOrColumn == null) {
            throw new IllegalArgumentException("Column or property name cannot be null");
        }
        return new PropertyOperator<>(this, (op, vals) -> appendDynamicCondition(LogicalOperator.AND, propertyOrColumn, op, vals));
    }

    public WhereBuilder<T> andRaw(String rawSql, Object... params) {
        RawSqlExpression<?> raw = new RawSqlExpression<>(rawSql, params);
        ExpressionCondition cond = new ExpressionCondition(raw, null);
        cond.setLogicalOperator(LogicalOperator.AND);
        conditionReceiver.accept(cond);
        return this;
    }

    /**
     * 方式 1：直接传入代理 getter 调用结果，如 w.and(s.getAge()).gt(18)
     */
    @SuppressWarnings("unchecked")
    public <R> PropertyOperator<WhereBuilder<T>, R> and(R val) {
        if (val instanceof SqlExpression) {
            return and((SqlExpression<R>) val);
        }
        PropertyPath path = RecordingContext.finish();
        if (path == null) {
            if (val instanceof String) {
                return and((String) val);
            }
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

    public <R> PropertyOperator<WhereBuilder<T>, R> or(String propertyOrColumn) {
        PropertyPath path = RecordingContext.finish();
        if (path != null) {
            return new PropertyOperator<>(this, path, (p, op, vals) -> appendCondition(LogicalOperator.OR, p, op, vals));
        }
        if (propertyOrColumn == null) {
            throw new IllegalArgumentException("Column or property name cannot be null");
        }
        return new PropertyOperator<>(this, (op, vals) -> appendDynamicCondition(LogicalOperator.OR, propertyOrColumn, op, vals));
    }

    public WhereBuilder<T> orRaw(String rawSql, Object... params) {
        RawSqlExpression<?> raw = new RawSqlExpression<>(rawSql, params);
        ExpressionCondition cond = new ExpressionCondition(raw, null);
        cond.setLogicalOperator(LogicalOperator.OR);
        conditionReceiver.accept(cond);
        return this;
    }

    /**
     * SQL 表达式与函数断言：w.or(min(s.getAge())).gt(18)
     */
    public <R> PropertyOperator<WhereBuilder<T>, R> or(SqlExpression<R> expr) {
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
    @SuppressWarnings("unchecked")
    public <R> PropertyOperator<WhereBuilder<T>, R> or(R val) {
        if (val instanceof SqlExpression) {
            return or((SqlExpression<R>) val);
        }
        PropertyPath path = RecordingContext.finish();
        if (path == null) {
            if (val instanceof String) {
                return or((String) val);
            }
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
        WhereBuilder<T> sub = new WhereBuilder<>(entityClass, rootQuery, scopeAlias, group::addCondition, customListSupplier, executeSupplier, sqlAndParamsSupplier, printSqlConsumer);
        groupConsumer.accept(sub);
        if (!group.getChildren().isEmpty()) {
            conditionReceiver.accept(group);
        }
        return this;
    }

    public WhereBuilder<T> andGroup(BiWhereConsumer<T> groupConsumer) {
        GroupCondition group = new GroupCondition(LogicalOperator.AND);
        WhereBuilder<T> sub = new WhereBuilder<>(entityClass, rootQuery, scopeAlias, group::addCondition, customListSupplier, executeSupplier, sqlAndParamsSupplier, printSqlConsumer);
        groupConsumer.accept(sub, sub.proxy());
        if (!group.getChildren().isEmpty()) {
            conditionReceiver.accept(group);
        }
        return this;
    }

    public WhereBuilder<T> orGroup(Consumer<WhereBuilder<T>> groupConsumer) {
        GroupCondition group = new GroupCondition(LogicalOperator.OR);
        WhereBuilder<T> sub = new WhereBuilder<>(entityClass, rootQuery, scopeAlias, group::addCondition, customListSupplier, executeSupplier, sqlAndParamsSupplier, printSqlConsumer);
        groupConsumer.accept(sub);
        if (!group.getChildren().isEmpty()) {
            conditionReceiver.accept(group);
        }
        return this;
    }

    public WhereBuilder<T> orGroup(BiWhereConsumer<T> groupConsumer) {
        GroupCondition group = new GroupCondition(LogicalOperator.OR);
        WhereBuilder<T> sub = new WhereBuilder<>(entityClass, rootQuery, scopeAlias, group::addCondition, customListSupplier, executeSupplier, sqlAndParamsSupplier, printSqlConsumer);
        groupConsumer.accept(sub, sub.proxy());
        if (!group.getChildren().isEmpty()) {
            conditionReceiver.accept(group);
        }
        return this;
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

        if (path.hasRelations()) {
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
                }
            }
        } else {
            alias = scopeAlias;
            colName = str;
        }

        boolean matchedInMetadata = false;
        if (entityClass != null && rootQuery != null) {
            com.javaorm.core.metadata.EntityMetadata meta = com.javaorm.core.metadata.EntityMetadataManager.get(entityClass, rootQuery.getConfig());
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
}
