package com.javaorm.core.sql;

import com.javaorm.core.ast.ColumnExpression;
import com.javaorm.core.ast.SelectStatement;
import com.javaorm.core.ast.SqlExpression;
import com.javaorm.core.ast.TableJoin;
import com.javaorm.core.dialect.SqlAndParams;
import com.javaorm.core.metadata.ColumnMetadata;
import com.javaorm.core.metadata.EntityMetadata;
import com.javaorm.core.metadata.EntityMetadataManager;
import com.javaorm.core.proxy.PropertyPath;
import com.javaorm.core.proxy.RecordingContext;
import com.javaorm.core.query.Cte;
import com.javaorm.core.query.Query;
import com.javaorm.core.query.SelectBuilder;
import com.javaorm.core.query.WhereBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * SELECT 查询流式管道构建器。
 * 支持完整 SQL 语序：SELECT -> FROM -> JOIN -> WHERE -> GROUP BY -> HAVING -> ORDER BY -> 终端操作。
 *
 * @param <T> 查询结果类型强类型约束
 */
public class SqlSelectStream<T> implements Query<T> {

    private final SqlEnv env;
    private final Class<T> targetClass;
    private final Object[] rawExpressions;
    private final List<SqlExpression> projectExpressions = new ArrayList<>();

    private Object candidateRootTable;
    private boolean isFullEntitySelect = false;

    private SelectBuilder<?> query;
    private WhereBuilder<T> activeWhereBuilder;

    public SqlSelectStream(SqlEnv env, Class<T> targetClass, Object[] expressions) {
        this.env = env;
        this.targetClass = targetClass;
        this.rawExpressions = expressions;
        extractExpressions();
    }

    private void extractExpressions() {
        if (rawExpressions == null || rawExpressions.length == 0) {
            return;
        }

        // 单参实体检查：如 e.select(s) 或 e.select(Student.class) 或 e.select(cte)
        if (rawExpressions.length == 1 && rawExpressions[0] != null) {
            Object first = rawExpressions[0];
            SqlTableInfo<?> info = null;
            if (first instanceof Class<?>) {
                info = env.findTableInfo((Class<?>) first);
                if (info == null) {
                    try {
                        info = env.resolveTable((Class<?>) first);
                    } catch (Exception ignored) {
                    }
                }
            } else if (first instanceof Cte<?>) {
                info = env.findTableInfo((Cte<?>) first);
                if (info == null) {
                    try {
                        info = env.resolveTable((Cte<?>) first);
                    } catch (Exception ignored) {
                    }
                }
            } else {
                info = env.findTableInfoByProxy(first);
            }
            if (info != null) {
                this.candidateRootTable = first;
                this.isFullEntitySelect = true;
                RecordingContext.clearRecording();
                return;
            }
        }

        // 提取参数求值期间录制的所有属性调用路径
        List<PropertyPath> paths = RecordingContext.finishAll();
        int pathIdx = 0;
        for (Object item : rawExpressions) {
            if (item instanceof SqlExpression) {
                projectExpressions.add((SqlExpression) item);
            } else if (item instanceof ColumnExpression) {
                projectExpressions.add((ColumnExpression) item);
            } else if (pathIdx < paths.size()) {
                PropertyPath path = paths.get(pathIdx++);
                String alias = path.getExplicitAlias();
                EntityMetadata meta = env.getConfig() != null
                        ? env.getConfig().getMetadata(path.getRootEntityClass())
                        : EntityMetadataManager.get(path.getRootEntityClass());
                com.javaorm.core.metadata.ValueObjectMetadata vo = meta != null ? meta.getValueObject(path.getFullPath()) : null;
                if (vo != null) {
                    for (ColumnMetadata leaf : vo.getAllLeafColumns()) {
                        ColumnExpression ce = new ColumnExpression(alias, leaf.getColumnName());
                        projectExpressions.add(ce);
                    }
                } else {
                    ColumnMetadata cm = meta != null ? meta.getColumnByProperty(path.getFullPath()) : null;
                    if (cm == null && meta != null) {
                        cm = meta.getColumnByProperty(path.getTerminalProperty());
                    }
                    String colName = cm != null ? cm.getColumnName() : path.getTerminalProperty();
                    ColumnExpression ce = new ColumnExpression(alias, colName);
                    ce.setPath(path);
                    projectExpressions.add(ce);
                }
            } else if (item instanceof String) {
                projectExpressions.add(new ColumnExpression(null, (String) item));
            }
        }
    }

    public SelectBuilder<?> getQuery() {
        ensureQuery();
        return query;
    }

    @Override
    public SqlEnv getEnv() {
        return env;
    }

    @Override
    public SelectStatement getStatement() {
        ensureQuery();
        return query != null ? query.getStatement() : null;
    }

    /**
     * 指定查询数据源（主表 / FROM） - 实体 Class
     */
    public <E> SqlSelectStream<T> from(Class<E> entityClass) {
        SqlTableInfo<E> info = env.resolveTable(entityClass);
        return applyFromTableInfo(info);
    }

    /**
     * 指定查询数据源（主表 / FROM） - CTE 临时表
     */
    public <E> SqlSelectStream<T> from(Cte<E> cte) {
        SqlTableInfo<E> info = env.resolveTable(cte);
        return applyFromTableInfo(info);
    }

    /**
     * 指定查询数据源（主表 / FROM） - 实体代理对象或动态对象
     */
    public <P> SqlSelectStream<T> from(P rootTable) {
        if (rootTable instanceof Class<?>) {
            return from((Class<?>) rootTable);
        }
        if (rootTable instanceof Cte<?>) {
            return from((Cte<?>) rootTable);
        }
        if (rootTable instanceof String) {
            SqlTableInfo<?> info = env.findTableInfo((String) rootTable);
            if (info != null) {
                return applyFromTableInfo(info);
            }
            throw new IllegalArgumentException("Cannot resolve table from alias: " + rootTable);
        }
        SqlTableInfo<P> info = env.resolveTableByProxy(rootTable);
        return applyFromTableInfo(info);
    }

    private SqlSelectStream<T> applyFromTableInfo(SqlTableInfo<?> info) {
        if (info.isCte()) {
            this.query = new SelectBuilder<>(env, info.getCte());
        } else {
            this.query = new SelectBuilder<>(env, info.getEntityClass(), info.getAlias());
        }

        if (!isFullEntitySelect && !projectExpressions.isEmpty()) {
            this.query.getStatement().getProjectExpressions().clear();
            for (SqlExpression expr : projectExpressions) {
                this.query.resolveExpression(expr);
                this.query.getStatement().addProjectExpression(expr);
            }
        }
        return this;
    }

    private void ensureQuery() {
        if (this.query != null) {
            return;
        }
        if (candidateRootTable != null) {
            from(candidateRootTable);
            return;
        }
        SqlTableInfo<?> firstTable = env.getFirstRegisteredTable();
        if (firstTable != null) {
            applyFromTableInfo(firstTable);
            return;
        }
        throw new IllegalStateException("Please specify the root table using .from(table) before configuring joins, where or orderBy.");
    }

    // ========== JOIN 相关方法（支持强类型重载、首字母小写与通用别名） ==========

    public <E> SqlJoinClause<T> leftJoin(Class<E> entityClass) {
        return createJoin(TableJoin.JoinType.LEFT_JOIN, env.resolveTable(entityClass));
    }

    public <E> SqlJoinClause<T> leftJoin(Cte<E> cte) {
        return createJoin(TableJoin.JoinType.LEFT_JOIN, env.resolveTable(cte));
    }

    public <P> SqlJoinClause<T> leftJoin(P targetTable) {
        return createJoin(TableJoin.JoinType.LEFT_JOIN, (Object) targetTable);
    }

    public <E> SqlJoinClause<T> leftjoin(Class<E> entityClass) {
        return leftJoin(entityClass);
    }

    public <E> SqlJoinClause<T> leftjoin(Cte<E> cte) {
        return leftJoin(cte);
    }

    public <P> SqlJoinClause<T> leftjoin(P targetTable) {
        return leftJoin(targetTable);
    }

    public <E> SqlJoinClause<T> innerJoin(Class<E> entityClass) {
        return createJoin(TableJoin.JoinType.INNER_JOIN, env.resolveTable(entityClass));
    }

    public <E> SqlJoinClause<T> innerJoin(Cte<E> cte) {
        return createJoin(TableJoin.JoinType.INNER_JOIN, env.resolveTable(cte));
    }

    public <P> SqlJoinClause<T> innerJoin(P targetTable) {
        return createJoin(TableJoin.JoinType.INNER_JOIN, (Object) targetTable);
    }

    public <E> SqlJoinClause<T> innerjoin(Class<E> entityClass) {
        return innerJoin(entityClass);
    }

    public <E> SqlJoinClause<T> innerjoin(Cte<E> cte) {
        return innerJoin(cte);
    }

    public <P> SqlJoinClause<T> innerjoin(P targetTable) {
        return innerJoin(targetTable);
    }

    public <E> SqlJoinClause<T> join(Class<E> entityClass) {
        return innerJoin(entityClass);
    }

    public <E> SqlJoinClause<T> join(Cte<E> cte) {
        return innerJoin(cte);
    }

    public <P> SqlJoinClause<T> join(P targetTable) {
        return innerJoin(targetTable);
    }

    public <E> SqlJoinClause<T> rightJoin(Class<E> entityClass) {
        return createJoin(TableJoin.JoinType.RIGHT_JOIN, env.resolveTable(entityClass));
    }

    public <E> SqlJoinClause<T> rightJoin(Cte<E> cte) {
        return createJoin(TableJoin.JoinType.RIGHT_JOIN, env.resolveTable(cte));
    }

    public <P> SqlJoinClause<T> rightJoin(P targetTable) {
        return createJoin(TableJoin.JoinType.RIGHT_JOIN, (Object) targetTable);
    }

    public <E> SqlJoinClause<T> rightjoin(Class<E> entityClass) {
        return rightJoin(entityClass);
    }

    public <E> SqlJoinClause<T> rightjoin(Cte<E> cte) {
        return rightJoin(cte);
    }

    public <P> SqlJoinClause<T> rightjoin(P targetTable) {
        return rightJoin(targetTable);
    }

    private SqlJoinClause<T> createJoin(TableJoin.JoinType joinType, SqlTableInfo<?> targetInfo) {
        ensureQuery();
        TableJoin join;
        if (targetInfo.isCte()) {
            Cte<?> cte = targetInfo.getCte();
            join = new TableJoin(joinType, cte.getName(), targetInfo.getAlias());
            query.getStatement().addCte(cte.toClause());
        } else {
            join = new TableJoin(joinType, targetInfo.getTableName(), targetInfo.getAlias());
            if (env.getConfig() != null && targetInfo.getEntityClass() != null) {
                EntityMetadata meta = EntityMetadataManager.get(targetInfo.getEntityClass(), env.getConfig());
                if (meta != null && meta.hasLogicDelete()) {
                    ColumnMetadata ld = meta.getLogicDeleteColumn();
                    join.addOnCondition(new com.javaorm.core.ast.BinaryCondition(
                            targetInfo.getAlias(), ld.getColumnName(),
                            com.javaorm.core.operator.SqlOperator.EQ, ld.getLogicDeleteNormalValue()));
                }
            }
        }
        query.getStatement().addJoin(join);
        return new SqlJoinClause<>(this, join, targetInfo);
    }

    private SqlJoinClause<T> createJoin(TableJoin.JoinType joinType, Object targetTable) {
        if (targetTable instanceof Class<?>) {
            return createJoin(joinType, env.resolveTable((Class<?>) targetTable));
        }
        if (targetTable instanceof Cte<?>) {
            return createJoin(joinType, env.resolveTable((Cte<?>) targetTable));
        }
        if (targetTable instanceof String) {
            SqlTableInfo<?> info = env.findTableInfo((String) targetTable);
            if (info != null) {
                return createJoin(joinType, info);
            }
            throw new IllegalArgumentException("Cannot resolve table from alias: " + targetTable);
        }
        return createJoin(joinType, env.resolveTableByProxy(targetTable));
    }

    // ========== WHERE 条件相关方法 ==========

    @SuppressWarnings("unchecked")
    public WhereBuilder<T> where() {
        ensureQuery();
        Class<T> resClass = getResultClass();
        WhereBuilder<T> wb = (WhereBuilder<T>) query.where();
        if (resClass != null) {
            wb.setResultClass(resClass);
        }
        this.activeWhereBuilder = wb;
        return wb;
    }

    public SqlSelectStream<T> where(Consumer<WhereBuilder<T>> consumer) {
        if (consumer != null) {
            consumer.accept(where());
        }
        return this;
    }

    // ========== 排序、分组与分页 ==========

    public SqlSelectStream<T> orderBy(Object val) {
        ensureQuery();
        query.orderBy(val);
        return this;
    }

    public SqlSelectStream<T> orderby(Object val) {
        return orderBy(val);
    }

    public SqlSelectStream<T> orderByAsc(Object val) {
        ensureQuery();
        query.orderByAsc(val);
        return this;
    }

    public SqlSelectStream<T> orderByDesc(Object val) {
        ensureQuery();
        query.orderByDesc(val);
        return this;
    }

    public SqlSelectStream<T> groupBy(Object... expressions) {
        ensureQuery();
        query.groupBy(expressions);
        return this;
    }

    public SqlSelectStream<T> distinct() {
        ensureQuery();
        query.distinct();
        return this;
    }

    public SqlSelectStream<T> limit(int limit) {
        ensureQuery();
        query.limit(limit);
        return this;
    }

    public SqlSelectStream<T> offset(int offset) {
        ensureQuery();
        query.offset(offset);
        return this;
    }

    public SqlSelectStream<T> page(int pageNum, int pageSize) {
        ensureQuery();
        query.page(pageNum, pageSize);
        return this;
    }

    public SqlSelectStream<T> printSql() {
        return printSql(true);
    }

    public SqlSelectStream<T> printSql(Boolean print) {
        ensureQuery();
        if (print == null || print) {
            query.printSql();
        } else {
            query.printSql(false);
        }
        return this;
    }

    // ========== 终端查询操作委托 ==========

    @Override
    @SuppressWarnings("unchecked")
    public Class<T> getResultClass() {
        if (targetClass != null) {
            return targetClass;
        }
        if (isFullEntitySelect && candidateRootTable != null) {
            SqlTableInfo info = env.findTableInfo(candidateRootTable);
            if (info != null && info.getEntityClass() != null) {
                return (Class<T>) info.getEntityClass();
            }
        }
        return (Class<T>) getSingleTableEntityClass();
    }

    public Class<?> getSingleTableEntityClass() {
        if (projectExpressions.isEmpty()) {
            if (candidateRootTable != null) {
                SqlTableInfo info = env.findTableInfo(candidateRootTable);
                return info != null ? info.getEntityClass() : null;
            }
            return null;
        }
        String singleAlias = null;
        Class<?> singleClass = null;
        for (SqlExpression expr : projectExpressions) {
            if (expr instanceof ColumnExpression) {
                ColumnExpression<?> ce = (ColumnExpression<?>) expr;
                String ta = ce.getTableAlias();
                PropertyPath pp = ce.getPath();
                Class<?> ec = pp != null ? pp.getRootEntityClass() : null;
                if (ta != null) {
                    if (singleAlias == null) {
                        singleAlias = ta;
                    } else if (!singleAlias.equalsIgnoreCase(ta)) {
                        return null; // 多表投影
                    }
                }
                if (ec != null) {
                    if (singleClass == null) {
                        singleClass = ec;
                    } else if (singleClass != ec) {
                        return null; // 多实体投影
                    }
                }
            }
        }
        if (singleClass != null) {
            return singleClass;
        }
        if (candidateRootTable != null) {
            SqlTableInfo info = env.findTableInfo(candidateRootTable);
            return info != null ? info.getEntityClass() : null;
        }
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<T> toList() {
        ensureQuery();
        Class<T> resClass = getResultClass();
        if (resClass != null) {
            return toList(resClass);
        }
        if (isFullEntitySelect && candidateRootTable != null) {
            return (List<T>) query.toList();
        }
        throw new IllegalStateException(
                "Cannot call toList() on a multi-table or untyped projection query without specifying target class. " +
                        "Please specify target type via .toList(YourDto.class) or e.select(YourDto.class, ...), or use .listMaps() instead."
        );
    }

    @Override
    public <R> List<R> toList(Class<R> targetClass) {
        ensureQuery();
        return query.toList(targetClass);
    }

    @Override
    public List<Map<String, Object>> listMaps() {
        ensureQuery();
        return query.listMaps();
    }

    @Override
    public T firstOrNull() {
        List<T> list = toList();
        return (list != null && !list.isEmpty()) ? list.get(0) : null;
    }

    @Override
    public T first() {
        return firstOrNull();
    }

    @Override
    public <R> R firstOrNull(Class<R> targetClass) {
        ensureQuery();
        return query.firstOrNull(targetClass);
    }

    @Override
    public <R> R first(Class<R> targetClass) {
        return firstOrNull(targetClass);
    }

    @Override
    public long count() {
        ensureQuery();
        return query.count();
    }

    @Override
    public String toSql() {
        ensureQuery();
        return query.toSql();
    }

    @Override
    public SqlAndParams toSqlAndParams() {
        ensureQuery();
        return query.toSqlAndParams();
    }
}
