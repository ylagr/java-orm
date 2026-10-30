package com.javaorm.core.sql;

import com.javaorm.core.ast.*;
import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.connection.ConnectionUtils;
import com.javaorm.core.dialect.Dialect;
import com.javaorm.core.dialect.SqlAndParams;
import com.javaorm.core.hydration.EntityHydrator;
import com.javaorm.core.hydration.IncludeProcessor;
import com.javaorm.core.metadata.ColumnMetadata;
import com.javaorm.core.metadata.EntityMetadata;
import com.javaorm.core.metadata.EntityMetadataManager;
import com.javaorm.core.mutation.DeleteBuilder;
import com.javaorm.core.mutation.UpdateBuilder;
import com.javaorm.core.operator.SqlOperator;
import com.javaorm.core.proxy.PropertyPath;
import com.javaorm.core.proxy.ProxyFactory;
import com.javaorm.core.proxy.RecordingContext;
import com.javaorm.core.query.Cte;
import com.javaorm.core.query.IncludeSpec;
import com.javaorm.core.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.*;

/**
 * SQL 声明式环境上下文（SqlEnv）。
 * 统一管理运行时环境数据（DataSource、Dialect、OrmConfig、符号表、执行引擎），
 * 负责语句构建调度以及执行能力的统一分发。
 */
public class SqlEnv {

    private static final Logger log = LoggerFactory.getLogger(SqlEnv.class);

    private final DataSource dataSource;
    private final Dialect dialect;
    private final OrmConfig config;
    private Boolean printSqlOverride = null;

    private int aliasSequence = 0;
    private final List<SqlTableInfo> registeredTables = new ArrayList<>();
    private final Map<Object, SqlTableInfo> proxyMap = new IdentityHashMap<>();
    private final Map<Class<?>, SqlTableInfo> classMap = new HashMap<>();
    private final Map<String, SqlTableInfo> aliasMap = new HashMap<>();

    private Object currentOperation;

    public SqlEnv(DataSource dataSource, Dialect dialect, OrmConfig config) {
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.config = config != null ? config : new OrmConfig();
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public Dialect getDialect() {
        return dialect;
    }

    public OrmConfig getConfig() {
        return config;
    }

    public SqlEnv setPrintSql(Boolean print) {
        this.printSqlOverride = print;
        return this;
    }

    public Boolean getPrintSqlOverride() {
        return printSqlOverride;
    }

    public boolean shouldPrintSql() {
        return printSqlOverride != null ? printSqlOverride : (config != null && config.isPrintSql());
    }

    public OrmConfig getEffectiveConfig() {
        if (printSqlOverride == null) {
            return config;
        }
        OrmConfig effective = new OrmConfig();
        if (config != null) {
            effective.setNameConversion(config.getNameConversion());
            effective.setDefaultLogicDeleteProperty(config.getDefaultLogicDeleteProperty());
            effective.setDefaultLogicDeleteNormalValue(config.getDefaultLogicDeleteNormalValue());
            effective.setDefaultLogicDeleteDeletedValue(config.getDefaultLogicDeleteDeletedValue());
        }
        effective.setPrintSql(printSqlOverride);
        return effective;
    }

    public String nextAlias() {
        return "t" + (aliasSequence++);
    }

    public void resetAliasSeq() {
        this.aliasSequence = 0;
        RecordingContext.clearAliases();
    }

    // ========== 1. 声明与注册表（table） ==========

    /**
     * 声明实体表，自动分配别名（t0, t1...），返回实体代理
     */
    public <T> T table(Class<T> entityClass) {
        return table(entityClass, nextAlias());
    }

    /**
     * 声明实体表并显式指定别名，返回实体代理
     */
    public <T> T table(Class<T> entityClass, String alias) {
        if (entityClass == null) {
            throw new IllegalArgumentException("Entity class cannot be null");
        }
        String effectiveAlias = (alias != null && !alias.trim().isEmpty()) ? alias.trim() : nextAlias();
        T proxy = ProxyFactory.createProxy(entityClass, effectiveAlias);
        EntityMetadata meta = EntityMetadataManager.get(entityClass, config);
        String tableName = meta != null ? meta.getTableName() : entityClass.getSimpleName();

        SqlTableInfo info = new SqlTableInfo(proxy, entityClass, null, effectiveAlias, tableName);
        registerTable(info);
        RecordingContext.registerProxyAlias(proxy, effectiveAlias);
        RecordingContext.registerAlias(entityClass, effectiveAlias);
        return proxy;
    }

    /**
     * 声明 CTE 临时表，返回带别名的 CTE 实例
     */
    public <R> Cte<R> table(Cte<R> cte) {
        String alias = cte.getEffectiveAlias() != null ? cte.getEffectiveAlias() : nextAlias();
        return table(cte, alias);
    }

    /**
     * 声明 CTE 临时表并显式指定别名
     */
    public <R> Cte<R> table(Cte<R> cte, String alias) {
        if (cte == null) {
            throw new IllegalArgumentException("CTE cannot be null");
        }
        String effectiveAlias = (alias != null && !alias.trim().isEmpty()) ? alias.trim() : nextAlias();
        Cte<R> aliasedCte = cte.as(effectiveAlias);
        SqlTableInfo info = new SqlTableInfo(aliasedCte, cte.getEntityClass(), aliasedCte, effectiveAlias, cte.getName());
        registerTable(info);
        return aliasedCte;
    }

    private void registerTable(SqlTableInfo info) {
        registeredTables.add(info);
        if (info.getProxy() != null) {
            proxyMap.put(info.getProxy(), info);
        }
        if (info.getEntityClass() != null && !classMap.containsKey(info.getEntityClass())) {
            classMap.put(info.getEntityClass(), info);
        }
        if (info.getAlias() != null) {
            aliasMap.put(info.getAlias().toLowerCase(), info);
        }
    }

    public SqlTableInfo findTableInfo(Object target) {
        if (target == null) return null;
        SqlTableInfo info = proxyMap.get(target);
        if (info != null) return info;
        if (target instanceof Class) {
            return classMap.get(target);
        }
        if (target instanceof Cte) {
            Cte<?> cte = (Cte<?>) target;
            if (cte.getEffectiveAlias() != null) {
                return aliasMap.get(cte.getEffectiveAlias().toLowerCase());
            }
        }
        if (target instanceof String) {
            return aliasMap.get(((String) target).toLowerCase());
        }
        return null;
    }

    public SqlTableInfo resolveTable(Object target) {
        SqlTableInfo info = findTableInfo(target);
        if (info != null) {
            return info;
        }
        if (target instanceof Class) {
            @SuppressWarnings("unchecked")
            Class<?> clazz = (Class<?>) target;
            String alias = nextAlias();
            EntityMetadata meta = EntityMetadataManager.get(clazz, config);
            String tableName = meta != null ? meta.getTableName() : clazz.getSimpleName();
            SqlTableInfo dynamicallyCreated = new SqlTableInfo(null, clazz, null, alias, tableName);
            registerTable(dynamicallyCreated);
            return dynamicallyCreated;
        }
        if (target instanceof Cte) {
            Cte<?> cte = (Cte<?>) target;
            String alias = cte.getEffectiveAlias() != null ? cte.getEffectiveAlias() : nextAlias();
            SqlTableInfo dynamicallyCreated = new SqlTableInfo(cte, cte.getEntityClass(), cte, alias, cte.getName());
            registerTable(dynamicallyCreated);
            return dynamicallyCreated;
        }
        throw new IllegalArgumentException("Cannot resolve table from object: " + target);
    }

    public SqlTableInfo getFirstRegisteredTable() {
        return !registeredTables.isEmpty() ? registeredTables.get(0) : null;
    }

    // ========== 2. SELECT 管道入口与 Query 工厂 ==========

    public SqlSelectStream select(Object... expressions) {
        SqlSelectStream stream = new SqlSelectStream(this, null, expressions);
        this.currentOperation = stream;
        return stream;
    }

    public <DTO> SqlSelectStream select(Class<DTO> dtoClass, Object... expressions) {
        SqlSelectStream stream = new SqlSelectStream(this, dtoClass, expressions);
        this.currentOperation = stream;
        return stream;
    }

    public <T> Query<T> select(Class<T> entityClass) {
        return createQuery(entityClass);
    }

    public <T> Query<T> select(Cte<T> cte) {
        return createQuery(cte);
    }

    public <T> Query<T> createQuery(Class<T> entityClass) {
        Query<T> q = new Query<>(this, entityClass);
        this.currentOperation = q;
        return q;
    }

    public <T> Query<T> createQuery(Cte<T> cte) {
        Query<T> q = new Query<>(this, cte);
        this.currentOperation = q;
        return q;
    }

    public <T> com.javaorm.core.ast.ColumnExpression<T> col(T prop) {
        return com.javaorm.core.SQL.col(prop);
    }

    public <T> com.javaorm.core.ast.ColumnExpression<T> col(String propertyOrColumn) {
        return com.javaorm.core.SQL.col(propertyOrColumn);
    }

    // ========== 3. UPDATE 管道入口与 UpdateBuilder 工厂 ==========

    public SqlUpdateStream update(Object targetTable) {
        SqlTableInfo info = resolveTable(targetTable);
        SqlUpdateStream stream = new SqlUpdateStream(this, info);
        this.currentOperation = stream;
        return stream;
    }

    public <T> UpdateBuilder<T> update(Class<T> entityClass) {
        return createUpdate(entityClass);
    }

    public <T> UpdateBuilder<T> createUpdate(Class<T> entityClass) {
        UpdateBuilder<T> ub = new UpdateBuilder<>(this, entityClass);
        this.currentOperation = ub;
        return ub;
    }

    // ========== 4. DELETE 管道入口与 DeleteBuilder 工厂 ==========

    public SqlDeleteStream delete(Object targetTable) {
        SqlTableInfo info = resolveTable(targetTable);
        SqlDeleteStream stream = new SqlDeleteStream(this, info);
        this.currentOperation = stream;
        return stream;
    }

    public <T> DeleteBuilder<T> delete(Class<T> entityClass) {
        return createDelete(entityClass);
    }

    public <T> DeleteBuilder<T> createDelete(Class<T> entityClass) {
        DeleteBuilder<T> db = new DeleteBuilder<>(this, entityClass);
        this.currentOperation = db;
        return db;
    }

    // ========== 4.5 INSERT 工厂 ==========

    public <T> com.javaorm.core.mutation.InsertBuilder<T> insert(T entity) {
        return createInsert(entity);
    }

    public <T> com.javaorm.core.mutation.InsertBuilder<T> createInsert(T entity) {
        return new com.javaorm.core.mutation.InsertBuilder<>(this, entity);
    }

    // ========== 5. 输出统一执行门面 ==========

    @SuppressWarnings("unchecked")
    public <T> SqlExecution<T> toExecution() {
        Class<?> detected = null;
        if (currentOperation instanceof SqlSelectStream) {
            detected = ((SqlSelectStream) currentOperation).getResultClass();
        } else if (currentOperation instanceof Query<?>) {
            detected = ((Query<?>) currentOperation).getRootMetadata() != null
                    ? ((Query<?>) currentOperation).getRootMetadata().getEntityClass() : null;
        }
        return new SqlExecution<>(this, currentOperation, (Class<T>) detected);
    }

    public <T> SqlExecution<T> toExecution(Class<T> resultClass) {
        return new SqlExecution<>(this, currentOperation, resultClass);
    }

    // ========== 6. 执行引擎方法（底层调度与生命周期管理） ==========

    public void logSql(SqlAndParams sqlAndParams, String prefix) {
        if (shouldPrintSql() && sqlAndParams != null) {
            String p = (prefix != null && !prefix.isEmpty()) ? "[" + prefix + "] " : "";
            log.info("==> {}Preparing:  {}", p, sqlAndParams.getSql());
            log.info("==> {}Parameters: {}", p, sqlAndParams.getParams());
        }
    }

    public int executeSqlAndParams(SqlAndParams sqlAndParams, String prefix) {
        logSql(sqlAndParams, prefix);
        Connection conn = null;
        boolean isTx = false;
        try {
            conn = ConnectionUtils.getConnection(dataSource, config);
            isTx = ConnectionUtils.isTransactionConnection(conn);
            try (PreparedStatement ps = conn.prepareStatement(sqlAndParams.getSql())) {
                List<Object> params = sqlAndParams.getParams();
                if (params != null) {
                    for (int i = 0; i < params.size(); i++) {
                        ps.setObject(i + 1, params.get(i));
                    }
                }
                return ps.executeUpdate();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute " + (prefix != null ? prefix : "SQL") + " statement", e);
        } finally {
            if (!isTx && conn != null) {
                ConnectionUtils.closeConnection(conn);
            }
            RecordingContext.clearRecording();
            Query.resetAliasSeq();
        }
    }

    public int executeUpdate(UpdateStatement statement) {
        if (statement.getSetValues().isEmpty()) {
            return 0;
        }
        SqlAndParams sqlAndParams = dialect.buildUpdate(statement);
        return executeSqlAndParams(sqlAndParams, "UPDATE");
    }

    public int executeDelete(DeleteStatement statement) {
        SqlAndParams sqlAndParams = dialect.buildDelete(statement);
        return executeSqlAndParams(sqlAndParams, "DELETE");
    }

    public int executeLogicDelete(EntityMetadata metadata, Class<?> entityClass, List<Condition> whereConditions) {
        ColumnMetadata ld = metadata.getLogicDeleteColumn();
        UpdateStatement updateStmt = new UpdateStatement(entityClass, metadata.getTableName());
        updateStmt.addSet(ld.getColumnName(), ld.getLogicDeleteDeletedValue());

        for (Condition c : whereConditions) {
            updateStmt.addWhereCondition(c);
        }
        updateStmt.addSystemCondition(new BinaryCondition(null, ld.getColumnName(), SqlOperator.EQ, ld.getLogicDeleteNormalValue()));
        if (metadata.hasTenant()) {
            ColumnMetadata tc = metadata.getTenantColumn();
            Object tenantId = config != null ? config.getCurrentTenantId() : com.javaorm.core.tenant.TenantContext.getTenantId();
            if (tenantId != null) {
                updateStmt.addSystemCondition(new BinaryCondition(null, tc.getColumnName(), SqlOperator.EQ, tenantId));
            } else if (config != null && config.isTenantStrict()) {
                throw new IllegalStateException("Tenant context is required for multi-tenant entity: " + entityClass.getName());
            }
        }

        SqlAndParams sqlAndParams = dialect.buildUpdate(updateStmt);
        return executeSqlAndParams(sqlAndParams, "LogicDelete");
    }

    public <R> List<R> executeSelectList(Connection conn, SelectStatement statement, EntityMetadata metadata, List<IncludeSpec> includes) {
        boolean closeConn = false;
        Connection effectiveConn = conn;
        try {
            if (effectiveConn == null) {
                effectiveConn = ConnectionUtils.getConnection(dataSource, config);
                closeConn = !ConnectionUtils.isTransactionConnection(effectiveConn);
            }
            List<R> result = EntityHydrator.queryList(effectiveConn, statement, metadata, dialect, getEffectiveConfig());
            if (includes != null && !includes.isEmpty() && result != null && !result.isEmpty()) {
                IncludeProcessor.processIncludes(effectiveConn, result, metadata, includes, dialect, getEffectiveConfig(), dataSource);
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute query list", e);
        } finally {
            if (closeConn && effectiveConn != null) {
                ConnectionUtils.closeConnection(effectiveConn);
            }
        }
    }

    public <DTO> List<DTO> executeSelectDtoList(Connection conn, SelectStatement statement, Class<DTO> dtoClass) {
        boolean closeConn = false;
        Connection effectiveConn = conn;
        try {
            if (effectiveConn == null) {
                effectiveConn = ConnectionUtils.getConnection(dataSource, config);
                closeConn = !ConnectionUtils.isTransactionConnection(effectiveConn);
            }
            return EntityHydrator.queryDtoList(effectiveConn, statement, dtoClass, dialect, getEffectiveConfig());
        } catch (Exception e) {
            throw new RuntimeException("Failed to query list for " + (dtoClass != null ? dtoClass.getName() : "DTO"), e);
        } finally {
            if (closeConn && effectiveConn != null) {
                ConnectionUtils.closeConnection(effectiveConn);
            }
        }
    }

    public List<Map<String, Object>> executeSelectMaps(Connection conn, SelectStatement statement) {
        boolean closeConn = false;
        Connection effectiveConn = conn;
        try {
            if (effectiveConn == null) {
                effectiveConn = ConnectionUtils.getConnection(dataSource, config);
                closeConn = !ConnectionUtils.isTransactionConnection(effectiveConn);
            }
            return EntityHydrator.queryMaps(effectiveConn, statement, dialect, getEffectiveConfig());
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute listMaps query", e);
        } finally {
            if (closeConn && effectiveConn != null) {
                ConnectionUtils.closeConnection(effectiveConn);
            }
        }
    }

    public <V> V executeSelectScalar(Connection conn, SelectStatement statement, Class<V> targetType) {
        boolean closeConn = false;
        Connection effectiveConn = conn;
        try {
            if (effectiveConn == null) {
                effectiveConn = ConnectionUtils.getConnection(dataSource, config);
                closeConn = !ConnectionUtils.isTransactionConnection(effectiveConn);
            }
            return EntityHydrator.queryScalar(effectiveConn, statement, targetType, dialect, getEffectiveConfig());
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch one scalar", e);
        } finally {
            if (closeConn && effectiveConn != null) {
                ConnectionUtils.closeConnection(effectiveConn);
            }
        }
    }

    public <V> List<V> executeSelectScalarList(Connection conn, SelectStatement statement, Class<V> targetType) {
        boolean closeConn = false;
        Connection effectiveConn = conn;
        try {
            if (effectiveConn == null) {
                effectiveConn = ConnectionUtils.getConnection(dataSource, config);
                closeConn = !ConnectionUtils.isTransactionConnection(effectiveConn);
            }
            return EntityHydrator.queryScalarList(effectiveConn, statement, targetType, dialect, getEffectiveConfig());
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch scalar list", e);
        } finally {
            if (closeConn && effectiveConn != null) {
                ConnectionUtils.closeConnection(effectiveConn);
            }
        }
    }

    public long executeSelectCount(Connection conn, SelectStatement statement, Dialect targetDialect, Class<?> rootEntityClass) {
        Dialect effDialect = targetDialect != null ? targetDialect : dialect;
        boolean isComplex = statement.isDistinct()
                || !statement.getGroupByExpressions().isEmpty()
                || !statement.getHavingConditions().isEmpty()
                || !statement.getUnions().isEmpty();

        boolean closeConn = false;
        Connection effectiveConn = conn;
        try {
            if (effectiveConn == null) {
                effectiveConn = ConnectionUtils.getConnection(dataSource, config);
                closeConn = !ConnectionUtils.isTransactionConnection(effectiveConn);
            }

            if (!isComplex) {
                SelectStatement countStmt = new SelectStatement(rootEntityClass, statement.getRootTableName(), statement.getRootTableAlias());
                for (CteClause cte : statement.getCtes()) {
                    countStmt.addCte(cte);
                }
                countStmt.addSelectColumn("COUNT(1)");
                for (TableJoin join : statement.getJoins()) {
                    countStmt.addJoin(join);
                }
                for (Condition cond : statement.getSystemConditions()) {
                    countStmt.addSystemCondition(cond);
                }
                for (Condition cond : statement.getWhereConditions()) {
                    countStmt.addWhereCondition(cond);
                }
                return EntityHydrator.queryCount(effectiveConn, countStmt, effDialect, getEffectiveConfig());
            } else {
                SelectStatement innerStmt = new SelectStatement(rootEntityClass, statement.getRootTableName(), statement.getRootTableAlias());
                for (CteClause cte : statement.getCtes()) {
                    innerStmt.addCte(cte);
                }
                innerStmt.setDistinct(statement.isDistinct());
                for (SqlExpression expr : statement.getProjectExpressions()) {
                    innerStmt.addProjectExpression(expr);
                }
                for (String col : statement.getSelectColumns()) {
                    innerStmt.addSelectColumn(col);
                }
                if (!statement.getGroupByExpressions().isEmpty() && innerStmt.getProjectExpressions().isEmpty() && innerStmt.getSelectColumns().isEmpty()) {
                    for (SqlExpression expr : statement.getGroupByExpressions()) {
                        innerStmt.addProjectExpression(expr);
                    }
                }
                for (TableJoin join : statement.getJoins()) {
                    innerStmt.addJoin(join);
                }
                for (Condition cond : statement.getSystemConditions()) {
                    innerStmt.addSystemCondition(cond);
                }
                for (Condition cond : statement.getWhereConditions()) {
                    innerStmt.addWhereCondition(cond);
                }
                for (SqlExpression expr : statement.getGroupByExpressions()) {
                    innerStmt.addGroupByExpression(expr);
                }
                for (Condition cond : statement.getHavingConditions()) {
                    innerStmt.addHavingCondition(cond);
                }
                for (UnionClause u : statement.getUnions()) {
                    innerStmt.addUnion(u);
                }

                SqlAndParams countSqlAndParams;
                if (!innerStmt.getCtes().isEmpty()) {
                    SqlAndParams withSql = effDialect.buildSelect(innerStmt, true);
                    SqlAndParams innerWithoutCte = effDialect.buildSelect(innerStmt, false);
                    String sqlStr = withSql.getSql();
                    int selectIdx = sqlStr.indexOf("SELECT ");
                    String withPrefix = selectIdx > 0 ? sqlStr.substring(0, selectIdx) : "";
                    String countSql = withPrefix + "SELECT COUNT(1) FROM (" + innerWithoutCte.getSql() + ") count_sub";
                    countSqlAndParams = new SqlAndParams(countSql, withSql.getParams());
                } else {
                    SqlAndParams innerSql = effDialect.buildSelect(innerStmt);
                    String countSql = "SELECT COUNT(1) FROM (" + innerSql.getSql() + ") count_sub";
                    countSqlAndParams = new SqlAndParams(countSql, innerSql.getParams());
                }
                return EntityHydrator.queryCount(effectiveConn, countSqlAndParams, getEffectiveConfig(), !innerStmt.getCtes().isEmpty(), effDialect);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute count query", e);
        } finally {
            if (closeConn && effectiveConn != null) {
                ConnectionUtils.closeConnection(effectiveConn);
            }
        }
    }

    public void resolveExpression(SqlExpression<?> expr, SelectStatement statement) {
        if (expr == null) return;
        if (expr instanceof ColumnExpression) {
            ColumnExpression<?> ce = (ColumnExpression<?>) expr;
            if (ce.getPath() != null) {
                PropertyPath path = ce.getPath();
                if (ce.getTableAlias() == null) {
                    if (path.getExplicitAlias() != null) {
                        ce.setTableAlias(path.getExplicitAlias());
                    } else if (statement != null) {
                        ce.setTableAlias(statement.getRootTableAlias());
                    }
                }
                if (ce.getColumnName() == null) {
                    ce.setColumnName(resolveColumnName(path));
                }
            } else if (ce.getTableAlias() == null && statement != null) {
                ce.setTableAlias(statement.getRootTableAlias());
            }
        } else if (expr instanceof FunctionExpression) {
            FunctionExpression<?> fe = (FunctionExpression<?>) expr;
            for (Object arg : fe.getArguments()) {
                if (arg instanceof SqlExpression) {
                    resolveExpression((SqlExpression<?>) arg, statement);
                }
            }
        } else if (expr instanceof WindowOrderByBuilder) {
            resolveExpression(((WindowOrderByBuilder) expr).getWindowExpr(), statement);
        } else if (expr instanceof WindowFunctionExpression) {
            WindowFunctionExpression<?> wfe = (WindowFunctionExpression<?>) expr;
            if (wfe.getFunctionExpr() != null) {
                resolveExpression(wfe.getFunctionExpr(), statement);
            }
            if (wfe.getPartitionBys() != null) {
                for (SqlExpression<?> part : wfe.getPartitionBys()) {
                    resolveExpression(part, statement);
                }
            }
            if (wfe.getOrderBys() != null) {
                for (OrderBy ob : wfe.getOrderBys()) {
                    if (ob.getExpression() != null) {
                        resolveExpression(ob.getExpression(), statement);
                    }
                }
            }
        } else if (expr instanceof CaseWhenBuilder) {
            CaseWhenBuilder<?> cwb = (CaseWhenBuilder<?>) expr;
            for (CaseWhenBuilder.WhenBranch branch : cwb.getBranches()) {
                if (branch.getThenVal() instanceof SqlExpression) {
                    resolveExpression((SqlExpression<?>) branch.getThenVal(), statement);
                }
            }
            if (cwb.getElseVal() instanceof SqlExpression) {
                resolveExpression((SqlExpression<?>) cwb.getElseVal(), statement);
            }
        }
    }

    public String resolveColumnName(PropertyPath path) {
        if (path == null) return null;
        Class<?> entityClass = path.getRootEntityClass();
        if (path.hasRelations() && entityClass != null) {
            EntityMetadata currentMeta = config != null ? config.getMetadata(entityClass) : EntityMetadataManager.get(entityClass, config);
            for (String step : path.getRelationSteps()) {
                com.javaorm.core.metadata.RelationMetadata rel = currentMeta != null ? currentMeta.getRelation(step) : null;
                if (rel == null) break;
                entityClass = rel.getTargetEntityClass();
                currentMeta = config != null ? config.getMetadata(entityClass) : EntityMetadataManager.get(entityClass, config);
            }
        }
        EntityMetadata meta = (entityClass != null)
                ? (config != null ? config.getMetadata(entityClass) : EntityMetadataManager.get(entityClass, config))
                : null;
        ColumnMetadata col = meta != null ? meta.getColumnByProperty(path.getTerminalProperty()) : null;
        if (col != null) {
            return col.getColumnName();
        }
        return config != null ? config.getNameConversion().convert(path.getTerminalProperty()) : path.getTerminalProperty();
    }
}
