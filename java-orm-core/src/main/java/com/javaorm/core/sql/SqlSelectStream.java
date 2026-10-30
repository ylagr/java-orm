package com.javaorm.core.sql;

import com.javaorm.core.ast.ColumnExpression;
import com.javaorm.core.ast.SqlExpression;
import com.javaorm.core.ast.TableJoin;
import com.javaorm.core.dialect.SqlAndParams;
import com.javaorm.core.hydration.EntityHydrator;
import com.javaorm.core.metadata.ColumnMetadata;
import com.javaorm.core.metadata.EntityMetadata;
import com.javaorm.core.metadata.EntityMetadataManager;
import com.javaorm.core.proxy.PropertyPath;
import com.javaorm.core.proxy.RecordingContext;
import com.javaorm.core.query.Cte;
import com.javaorm.core.query.Query;
import com.javaorm.core.query.WhereBuilder;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * SELECT 查询流式管道构建器。
 * 支持完整 SQL 语序：SELECT -> FROM -> JOIN -> WHERE -> GROUP BY -> HAVING -> ORDER BY -> 终端操作。
 */
public class SqlSelectStream {

    private final SqlEnv env;
    private final Class<?> dtoClass;
    private final Object[] rawExpressions;
    private final List<SqlExpression> projectExpressions = new ArrayList<>();

    private Object candidateRootTable;
    private boolean isFullEntitySelect = false;

    private Query<?> query;
    private WhereBuilder<?> activeWhereBuilder;

    public SqlSelectStream(SqlEnv env, Class<?> dtoClass, Object[] expressions) {
        this.env = env;
        this.dtoClass = dtoClass;
        this.rawExpressions = expressions;
        extractExpressions();
    }

    private void extractExpressions() {
        if (rawExpressions == null || rawExpressions.length == 0) {
            return;
        }

        // 单参实体代理检查：如 e.select(s) 或 e.select(Student.class)
        if (rawExpressions.length == 1 && rawExpressions[0] != null) {
            Object first = rawExpressions[0];
            SqlTableInfo info = env.findTableInfo(first);
            if (info == null && first instanceof Class<?>) {
                try {
                    info = env.resolveTable(first);
                } catch (Exception ignored) {}
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
                ColumnMetadata cm = meta != null ? meta.getColumnByProperty(path.getTerminalProperty()) : null;
                String colName = cm != null ? cm.getColumnName() : path.getTerminalProperty();
                ColumnExpression ce = new ColumnExpression(alias, colName);
                ce.setPath(path);
                projectExpressions.add(ce);
            } else if (item instanceof String) {
                projectExpressions.add(new ColumnExpression(null, (String) item));
            }
        }
    }

    public Query<?> getQuery() {
        ensureQuery();
        return query;
    }

    /**
     * 指定查询数据源（主表 / FROM）
     */
    public SqlSelectStream from(Object rootTable) {
        SqlTableInfo info = env.resolveTable(rootTable);
        if (info.isCte()) {
            this.query = new Query<>(env, info.getCte());
        } else {
            this.query = new Query<>(env, info.getEntityClass(), info.getAlias());
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
        SqlTableInfo firstTable = env.getFirstRegisteredTable();
        if (firstTable != null) {
            from(firstTable.getProxy() != null ? firstTable.getProxy() : firstTable.getEntityClass());
            return;
        }
        throw new IllegalStateException("Please specify the root table using .from(table) before configuring joins, where or orderBy.");
    }

    // ========== JOIN 相关方法（支持首字母小写与通用别名） ==========

    public SqlJoinClause leftJoin(Object targetTable) {
        return createJoin(TableJoin.JoinType.LEFT_JOIN, targetTable);
    }

    public SqlJoinClause leftjoin(Object targetTable) {
        return leftJoin(targetTable);
    }

    public SqlJoinClause innerJoin(Object targetTable) {
        return createJoin(TableJoin.JoinType.INNER_JOIN, targetTable);
    }

    public SqlJoinClause innerjoin(Object targetTable) {
        return innerJoin(targetTable);
    }

    public SqlJoinClause join(Object targetTable) {
        return innerJoin(targetTable);
    }

    public SqlJoinClause rightJoin(Object targetTable) {
        return createJoin(TableJoin.JoinType.RIGHT_JOIN, targetTable);
    }

    public SqlJoinClause rightjoin(Object targetTable) {
        return rightJoin(targetTable);
    }

    private SqlJoinClause createJoin(TableJoin.JoinType joinType, Object targetTable) {
        ensureQuery();
        SqlTableInfo targetInfo = env.resolveTable(targetTable);
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
        return new SqlJoinClause(this, join, targetInfo);
    }

    // ========== WHERE 条件相关方法 ==========

    @SuppressWarnings("unchecked")
    public <T> WhereBuilder<T> where() {
        ensureQuery();
        WhereBuilder<T> wb = (WhereBuilder<T>) query.where();
        this.activeWhereBuilder = wb;
        return wb;
    }

    public SqlSelectStream where(Consumer<WhereBuilder<?>> consumer) {
        if (consumer != null) {
            consumer.accept(where());
        }
        return this;
    }

    // ========== 排序、分组与分页 ==========

    public SqlSelectStream orderBy(Object val) {
        ensureQuery();
        query.orderBy(val);
        return this;
    }

    public SqlSelectStream orderby(Object val) {
        return orderBy(val);
    }

    public SqlSelectStream orderByAsc(Object val) {
        ensureQuery();
        query.orderByAsc(val);
        return this;
    }

    public SqlSelectStream orderByDesc(Object val) {
        ensureQuery();
        query.orderByDesc(val);
        return this;
    }

    public SqlSelectStream groupBy(Object... expressions) {
        ensureQuery();
        query.groupBy(expressions);
        return this;
    }

    public SqlSelectStream limit(int limit) {
        ensureQuery();
        query.limit(limit);
        return this;
    }

    public SqlSelectStream offset(int offset) {
        ensureQuery();
        query.offset(offset);
        return this;
    }

    public SqlSelectStream page(int pageNum, int pageSize) {
        ensureQuery();
        query.page(pageNum, pageSize);
        return this;
    }

    public SqlSelectStream printSql(Boolean print) {
        ensureQuery();
        if (print == null || print) {
            query.printSql();
        } else {
            query.printSql(false);
        }
        return this;
    }

    // ========== 终端查询操作委托 ==========

    public Class<?> getResultClass() {
        if (dtoClass != null) {
            return dtoClass;
        }
        if (isFullEntitySelect && candidateRootTable != null) {
            SqlTableInfo info = env.findTableInfo(candidateRootTable);
            if (info != null && info.getEntityClass() != null) {
                return info.getEntityClass();
            }
        }
        return getSingleTableEntityClass();
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

    @SuppressWarnings("unchecked")
    public <R> List<R> toList() {
        ensureQuery();
        if (dtoClass != null) {
            return toList((Class<R>) dtoClass);
        }
        if (isFullEntitySelect && candidateRootTable != null) {
            return (List<R>) query.toList();
        }
        Class<?> singleClass = getSingleTableEntityClass();
        if (singleClass != null) {
            return toList((Class<R>) singleClass);
        }
        throw new IllegalStateException(
                "Cannot call toList() on a multi-table or untyped projection query without specifying target class. " +
                "Please specify target type via .toList(YourDto.class) or e.select(YourDto.class, ...), or use .listMaps() instead."
        );
    }

    @SuppressWarnings("unchecked")
    public <R> List<R> toList(Class<R> targetClass) {
        if (targetClass == null) {
            throw new IllegalArgumentException("Target class must not be null");
        }
        ensureQuery();
        if (isFullEntitySelect && query != null && query.getRootEntityClass() == targetClass) {
            return (List<R>) query.toList();
        }
        if (ColumnMetadata.isScalarType(targetClass)) {
            return env.executeSelectScalarList(null, query.getStatement(), targetClass);
        }
        if (Map.class.isAssignableFrom(targetClass)) {
            return (List<R>) env.executeSelectMaps(null, query.getStatement());
        }
        return env.executeSelectDtoList(null, query.getStatement(), targetClass);
    }

    public List<Map<String, Object>> listMaps() {
        ensureQuery();
        return query.listMaps();
    }

    @SuppressWarnings("unchecked")
    public <R> R firstOrNull() {
        List<R> list = toList();
        return (list != null && !list.isEmpty()) ? list.get(0) : null;
    }

    @SuppressWarnings("unchecked")
    public <R> R firstOrNull(Class<R> targetClass) {
        if (targetClass == null) {
            throw new IllegalArgumentException("Target class must not be null");
        }
        ensureQuery();
        if (isFullEntitySelect && query != null && query.getRootEntityClass() == targetClass) {
            return (R) query.firstOrNull();
        }
        if (ColumnMetadata.isScalarType(targetClass)) {
            return env.executeSelectScalar(null, query.getStatement(), targetClass);
        }
        if (Map.class.isAssignableFrom(targetClass)) {
            List<Map<String, Object>> maps = env.executeSelectMaps(null, query.getStatement());
            return (maps != null && !maps.isEmpty()) ? (R) maps.get(0) : null;
        }
        List<R> list = toList(targetClass);
        return (list != null && !list.isEmpty()) ? list.get(0) : null;
    }

    public long count() {
        ensureQuery();
        return query.count();
    }

    public String toSql() {
        ensureQuery();
        return query.toSql();
    }

    public SqlAndParams toSqlAndParams() {
        ensureQuery();
        return query.toSqlAndParams();
    }
}
