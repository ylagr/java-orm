package com.javaorm.core.query;

import com.javaorm.core.ast.*;
import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.dialect.Dialect;
import com.javaorm.core.hydration.EntityHydrator;
import com.javaorm.core.hydration.IncludeProcessor;
import com.javaorm.core.metadata.ColumnMetadata;
import com.javaorm.core.metadata.EntityMetadata;
import com.javaorm.core.metadata.EntityMetadataManager;
import com.javaorm.core.metadata.RelationMetadata;
import com.javaorm.core.operator.SqlOperator;
import com.javaorm.core.proxy.PropertyPath;
import com.javaorm.core.proxy.ProxyFactory;
import com.javaorm.core.proxy.RecordingContext;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 核心 Fluent 查询构建器。
 */
public class Query<T> {

    private final Class<T> rootEntityClass;
    private final EntityMetadata rootMetadata;
    private final DataSource dataSource;
    private final Dialect dialect;
    private final OrmConfig config;

    private final SelectStatement statement;
    private final Map<String, String> relationJoinAliases = new LinkedHashMap<>();
    private final List<PropertyPath> includes = new ArrayList<>();
    private static final ThreadLocal<Integer> ALIAS_SEQ = ThreadLocal.withInitial(() -> 0);

    public Query(Class<T> rootEntityClass, DataSource dataSource, Dialect dialect, OrmConfig config) {
        this.rootEntityClass = rootEntityClass;
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.config = config;
        this.rootMetadata = EntityMetadataManager.get(rootEntityClass, config);
        int seq = ALIAS_SEQ.get();
        ALIAS_SEQ.set(seq + 1);
        String rootAlias = "t" + seq;
        this.statement = new SelectStatement(rootEntityClass, rootMetadata.getTableName(), rootAlias);
        RecordingContext.registerAlias(rootEntityClass, rootAlias);

        // 默认自动注入逻辑删除过滤条件
        if (rootMetadata.hasLogicDelete()) {
            ColumnMetadata ld = rootMetadata.getLogicDeleteColumn();
            statement.addWhereCondition(new BinaryCondition(rootAlias, ld.getColumnName(), SqlOperator.EQ, ld.getLogicDeleteNormalValue()));
        }
    }

    /**
     * 获取当前实体类型的录制代理对象
     */
    public T proxy() {
        return ProxyFactory.getProxy(rootEntityClass);
    }

    /**
     * 自定义字段投影查询
     */
    public Query<T> select(Object... expressions) {
        statement.getProjectExpressions().clear();
        if (expressions != null && expressions.length > 0) {
            List<PropertyPath> paths = RecordingContext.finishAll();
            int pathIdx = 0;
            for (Object item : expressions) {
                SqlExpression expr;
                if (item instanceof WindowOrderByBuilder) {
                    expr = ((WindowOrderByBuilder) item).getWindowExpr();
                } else if (item instanceof SqlExpression) {
                    expr = (SqlExpression) item;
                } else if (pathIdx < paths.size()) {
                    expr = pathToColumnExpression(paths.get(pathIdx++));
                } else {
                    PropertyPath p = RecordingContext.finish();
                    if (p != null) {
                        expr = pathToColumnExpression(p);
                    } else if (item != null) {
                        expr = new RawSqlExpression("?", item);
                    } else {
                        continue;
                    }
                }
                resolveExpression(expr);
                statement.addProjectExpression(expr);
            }
        }
        return this;
    }

    /**
     * 绑定源表，转为 DTO 投影查询（例如 orm.select(StatDto.class).from(Student.class)...）
     */
    public <R> DtoQuery<T, R> from(Class<R> fromEntityClass) {
        return new DtoQuery<>(this.rootEntityClass, fromEntityClass, dataSource, dialect, config);
    }

    /**
     * 开启 WHERE 条件构建块
     */
    public WhereBuilder<T> where() {
        return new WhereBuilder<>(this);
    }

    /**
     * 作用域 WHERE 块：orm.select(Student.class).where(w -> { w.and(...); })
     */
    public Query<T> where(Consumer<WhereBuilder<T>> consumer) {
        consumer.accept(where());
        return this;
    }

    /**
     * 双参数作用域 WHERE 块：orm.select(Student.class).where((w, s) -> { w.and(s.getAge()).gt(18); })
     */
    public Query<T> where(WhereBuilder.BiWhereConsumer<T> consumer) {
        WhereBuilder<T> w = where();
        consumer.accept(w, w.proxy());
        return this;
    }

    /**
     * 分组查询 GROUP BY
     */
    public Query<T> groupBy(Object... expressions) {
        if (expressions != null && expressions.length > 0) {
            List<PropertyPath> paths = RecordingContext.finishAll();
            int pathIdx = 0;
            for (Object item : expressions) {
                SqlExpression expr;
                if (item instanceof SqlExpression) {
                    expr = (SqlExpression) item;
                } else if (pathIdx < paths.size()) {
                    expr = pathToColumnExpression(paths.get(pathIdx++));
                } else {
                    PropertyPath p = RecordingContext.finish();
                    if (p != null) {
                        expr = pathToColumnExpression(p);
                    } else if (item != null) {
                        expr = new RawSqlExpression("?", item);
                    } else {
                        continue;
                    }
                }
                resolveExpression(expr);
                statement.addGroupByExpression(expr);
            }
        }
        return this;
    }

    /**
     * HAVING 过滤条件块
     */
    public HavingBuilder<T> having() {
        return new HavingBuilder<>(this);
    }

    public Query<T> having(Consumer<HavingBuilder<T>> consumer) {
        consumer.accept(having());
        return this;
    }

    /**
     * 级联子表作用域过滤
     */
    public <TSub> Query<T> whereRelation(Function<T, TSub> relationGetter, Consumer<WhereBuilder<TSub>> subBuilderConsumer) {
        RecordingContext.start(rootEntityClass);
        try {
            T proxy = ProxyFactory.getProxy(rootEntityClass);
            relationGetter.apply(proxy);
            PropertyPath path = RecordingContext.finish();
            if (path != null) {
                String joinAlias = resolveJoinAlias(path);
                Class<?> subEntityClass = resolveTargetClass(path);
                if (subEntityClass != null) {
                    @SuppressWarnings("unchecked")
                    Class<TSub> typedSubClass = (Class<TSub>) subEntityClass;
                    WhereBuilder<TSub> subBuilder = new WhereBuilder<>(typedSubClass, this, joinAlias);
                    subBuilderConsumer.accept(subBuilder);
                }
            }
        } finally {
            RecordingContext.clear();
        }
        return this;
    }

    /**
     * 显式关联 JOIN（基于导航关系自动推导 ON 条件）
     */
    public <TJoin> Query<T> leftJoin(Function<T, TJoin> relationGetter) {
        RecordingContext.start(rootEntityClass);
        try {
            T proxy = ProxyFactory.getProxy(rootEntityClass);
            relationGetter.apply(proxy);
            PropertyPath path = RecordingContext.finish();
            if (path != null) {
                resolveJoinAlias(path);
            }
        } finally {
            RecordingContext.clear();
        }
        return this;
    }

    /**
     * 声明显式装配关联对象（避免笛卡尔积，采用 Split IN 查询）
     */
    public Query<T> include(Function<T, ?> relationGetter) {
        RecordingContext.start(rootEntityClass);
        try {
            T proxy = ProxyFactory.getProxy(rootEntityClass);
            relationGetter.apply(proxy);
            PropertyPath path = RecordingContext.finish();
            if (path != null) {
                includes.add(path);
            }
        } finally {
            RecordingContext.clear();
        }
        return this;
    }

    public <R> OrderByBuilder<Query<T>> orderBy(Function<T, R> propertyGetter) {
        PropertyPath path = recordPath(propertyGetter);
        String alias = resolveTableAlias(path);
        String colName = resolveColumnName(path);
        return new OrderByBuilder<>(this, statement, alias, colName);
    }

    public <R> OrderByBuilder<Query<T>> orderBy(R val) {
        if (val instanceof SqlExpression) {
            return orderBy((SqlExpression) val);
        }
        PropertyPath path = RecordingContext.finish();
        if (path == null) {
            throw new IllegalStateException("No proxy property accessed before calling orderBy(...)");
        }
        String alias = resolveTableAlias(path);
        String colName = resolveColumnName(path);
        return new OrderByBuilder<>(this, statement, alias, colName);
    }

    public OrderByBuilder<Query<T>> orderBy(SqlExpression expr) {
        resolveExpression(expr);
        return new OrderByBuilder<>(this, statement, expr);
    }

    public Query<T> union(Object other) {
        statement.addUnion(new UnionClause(other, false));
        return this;
    }

    public Query<T> unionAll(Object other) {
        statement.addUnion(new UnionClause(other, true));
        return this;
    }

    public Query<T> page(int pageNum, int pageSize) {
        int offset = (Math.max(pageNum, 1) - 1) * pageSize;
        statement.setLimit(pageSize);
        statement.setOffset(offset);
        return this;
    }

    public Query<T> limit(int limit) {
        statement.setLimit(limit);
        return this;
    }

    public Query<T> offset(int offset) {
        statement.setOffset(offset);
        return this;
    }

    public Query<T> distinct() {
        statement.setDistinct(true);
        return this;
    }

    // 执行方法
    public List<T> list() {
        try (Connection conn = dataSource.getConnection()) {
            List<T> result = EntityHydrator.queryList(conn, statement, rootMetadata, dialect, config);
            if (!includes.isEmpty() && !result.isEmpty()) {
                IncludeProcessor.processIncludes(conn, result, rootMetadata, includes, dialect, config);
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute query list", e);
        }
    }

    public List<Map<String, Object>> listMaps() {
        try (Connection conn = dataSource.getConnection()) {
            return EntityHydrator.queryMaps(conn, statement, dialect, config);
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute listMaps query", e);
        }
    }

    public List<Map<String, Object>> maps() {
        return listMaps();
    }

    public <V> V fetchOne(Class<V> targetType) {
        statement.setLimit(1);
        try (Connection conn = dataSource.getConnection()) {
            return EntityHydrator.queryScalar(conn, statement, targetType, dialect, config);
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch one scalar", e);
        }
    }

    public <V> List<V> fetchList(Class<V> targetType) {
        try (Connection conn = dataSource.getConnection()) {
            return EntityHydrator.queryScalarList(conn, statement, targetType, dialect, config);
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch scalar list", e);
        }
    }

    public T single() {
        statement.setLimit(1);
        List<T> list = list();
        return list.isEmpty() ? null : list.get(0);
    }

    public T firstOrNull() {
        return single();
    }

    public long count() {
        SelectStatement countStmt = new SelectStatement(rootEntityClass, rootMetadata.getTableName(), statement.getRootTableAlias());
        countStmt.addSelectColumn("COUNT(1)");
        for (TableJoin join : statement.getJoins()) {
            countStmt.addJoin(join);
        }
        for (Condition cond : statement.getWhereConditions()) {
            countStmt.addWhereCondition(cond);
        }

        try (Connection conn = dataSource.getConnection()) {
            return EntityHydrator.queryCount(conn, countStmt, dialect, config);
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute count query", e);
        }
    }

    // 内部路径解析器
    public <R> PropertyPath recordPath(Function<T, R> getter) {
        RecordingContext.start(rootEntityClass);
        try {
            T proxy = ProxyFactory.getProxy(rootEntityClass);
            getter.apply(proxy);
            return RecordingContext.finish();
        } finally {
            RecordingContext.clear();
        }
    }

    public void resolveExpression(SqlExpression expr) {
        if (expr == null) return;
        if (expr instanceof WindowOrderByBuilder) {
            expr = ((WindowOrderByBuilder) expr).getWindowExpr();
        }
        if (expr instanceof ColumnExpression) {
            ColumnExpression ce = (ColumnExpression) expr;
            if (ce.getTableAlias() == null && ce.getPath() != null) {
                ce.setTableAlias(resolveTableAlias(ce.getPath()));
                ce.setColumnName(resolveColumnName(ce.getPath()));
            } else if (ce.getTableAlias() == null && ce.getColumnName() == null) {
                PropertyPath path = RecordingContext.finish();
                if (path != null) {
                    ce.setPath(path);
                    ce.setTableAlias(resolveTableAlias(path));
                    ce.setColumnName(resolveColumnName(path));
                }
            }
        } else if (expr instanceof FunctionExpression) {
            FunctionExpression fe = (FunctionExpression) expr;
            for (Object arg : fe.getArguments()) {
                if (arg instanceof SqlExpression) {
                    resolveExpression((SqlExpression) arg);
                }
            }
        } else if (expr instanceof WindowFunctionExpression) {
            WindowFunctionExpression wfe = (WindowFunctionExpression) expr;
            resolveExpression(wfe.getFunctionExpr());
            for (SqlExpression part : wfe.getPartitionBys()) {
                resolveExpression(part);
            }
            for (OrderBy ob : wfe.getOrderBys()) {
                if (ob.getExpression() != null) {
                    resolveExpression(ob.getExpression());
                }
            }
        }
    }

    public ColumnExpression pathToColumnExpression(PropertyPath path) {
        if (path == null) {
            path = RecordingContext.finish();
        }
        if (path == null) {
            throw new IllegalStateException("No property path recorded!");
        }
        String tableAlias = resolveTableAlias(path);
        String columnName = resolveColumnName(path);
        ColumnExpression col = new ColumnExpression(tableAlias, columnName);
        col.setPath(path);
        return col;
    }

    public String resolveTableAlias(PropertyPath path) {
        if (!path.hasRelations()) {
            if (path.getRootEntityClass() != null && path.getRootEntityClass() != rootEntityClass) {
                String reg = RecordingContext.getAlias(path.getRootEntityClass());
                if (reg != null) return reg;
            }
            return statement.getRootTableAlias();
        }
        return resolveJoinAlias(path);
    }

    public String resolveColumnName(PropertyPath path) {
        Class<?> targetClass = resolveTargetClass(path);
        if (path.getRootEntityClass() != null && path.getRootEntityClass() != rootEntityClass && !path.hasRelations()) {
            targetClass = path.getRootEntityClass();
        }
        EntityMetadata targetMeta = EntityMetadataManager.get(targetClass, config);
        ColumnMetadata col = targetMeta.getColumnByProperty(path.getTerminalProperty());
        if (col != null) {
            return col.getColumnName();
        }
        return config.getNameConversion().convert(path.getTerminalProperty());
    }

    public String resolveJoinAlias(PropertyPath path) {
        String currentAlias = statement.getRootTableAlias();
        Class<?> currentClass = rootEntityClass;
        EntityMetadata currentMeta = rootMetadata;

        StringBuilder pathKey = new StringBuilder();
        for (String step : path.getRelationSteps()) {
            if (pathKey.length() > 0) pathKey.append(".");
            pathKey.append(step);

            String existingAlias = relationJoinAliases.get(pathKey.toString());
            if (existingAlias != null) {
                currentAlias = existingAlias;
                RelationMetadata rel = currentMeta.getRelation(step);
                currentClass = rel.getTargetEntityClass();
                currentMeta = EntityMetadataManager.get(currentClass, config);
                continue;
            }

            RelationMetadata rel = currentMeta.getRelation(step);
            if (rel == null) {
                throw new IllegalStateException("Relation property '" + step + "' not found on " + currentClass.getName());
            }

            int joinSeq = ALIAS_SEQ.get();
            ALIAS_SEQ.set(joinSeq + 1);
            String nextAlias = "t" + joinSeq;
            RecordingContext.registerAlias(rel.getTargetEntityClass(), nextAlias);
            Class<?> targetClass = rel.getTargetEntityClass();
            EntityMetadata targetMeta = EntityMetadataManager.get(targetClass, config);

            TableJoin join = new TableJoin(TableJoin.JoinType.LEFT_JOIN, targetMeta.getTableName(), nextAlias);

            // 构建 ON 条件
            ColumnMetadata selfCol = currentMeta.getColumnByProperty(rel.getSelfProperty());
            ColumnMetadata targetCol = targetMeta.getColumnByProperty(rel.getTargetProperty());

            String selfColName = selfCol != null ? selfCol.getColumnName() : config.getNameConversion().convert(rel.getSelfProperty());
            String targetColName = targetCol != null ? targetCol.getColumnName() : config.getNameConversion().convert(rel.getTargetProperty());

            join.addOnCondition(new ColumnEqualityCondition(currentAlias, selfColName, nextAlias, targetColName));

            // 子表逻辑删除自动过滤
            if (targetMeta.hasLogicDelete()) {
                ColumnMetadata ld = targetMeta.getLogicDeleteColumn();
                join.addOnCondition(new BinaryCondition(nextAlias, ld.getColumnName(), SqlOperator.EQ, ld.getLogicDeleteNormalValue()));
            }

            statement.addJoin(join);
            relationJoinAliases.put(pathKey.toString(), nextAlias);

            currentAlias = nextAlias;
            currentClass = targetClass;
            currentMeta = targetMeta;
        }

        return currentAlias;
    }

    private Class<?> resolveTargetClass(PropertyPath path) {
        Class<?> current = rootEntityClass;
        EntityMetadata currentMeta = rootMetadata;
        for (String step : path.getRelationSteps()) {
            RelationMetadata rel = currentMeta.getRelation(step);
            if (rel == null) break;
            current = rel.getTargetEntityClass();
            currentMeta = EntityMetadataManager.get(current, config);
        }
        return current;
    }

    public SelectStatement getStatement() { return statement; }
    public OrmConfig getConfig() { return config; }
    public Dialect getDialect() { return dialect; }
}
