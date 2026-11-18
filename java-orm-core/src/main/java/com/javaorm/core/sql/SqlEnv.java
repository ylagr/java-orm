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
import com.javaorm.core.query.SelectBuilder;
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
    private final List<SqlTableInfo<?>> registeredTables = new ArrayList<>();
    private final Map<Object, SqlTableInfo<?>> proxyMap = new IdentityHashMap<>();
    private final Map<Class<?>, SqlTableInfo<?>> classMap = new HashMap<>();
    private final Map<String, SqlTableInfo<?>> aliasMap = new HashMap<>();

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

    private final com.javaorm.core.executor.SqlExecutor defaultSqlExecutor = new com.javaorm.core.executor.DefaultJdbcExecutor();

    public com.javaorm.core.executor.SqlExecutor getSqlExecutor() {
        if (config != null && config.getSqlExecutor() != null) {
            return config.getSqlExecutor();
        }
        return defaultSqlExecutor;
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

        SqlTableInfo<T> info = new SqlTableInfo<>(proxy, entityClass, null, effectiveAlias, tableName);
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

    private void registerTable(SqlTableInfo<?> info) {
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

    /**
     * 通过实体代理对象查找已注册表元信息
     */
    @SuppressWarnings("unchecked")
    public <P> SqlTableInfo<P> findTableInfoByProxy(P proxy) {
        if (proxy == null) return null;
        return (SqlTableInfo<P>) proxyMap.get(proxy);
    }

    /**
     * 通过实体 Class 查找已注册表元信息
     */
    @SuppressWarnings("unchecked")
    public <E> SqlTableInfo<E> findTableInfo(Class<E> entityClass) {
        if (entityClass == null) return null;
        return (SqlTableInfo<E>) classMap.get(entityClass);
    }

    /**
     * 通过 CTE 临时表查找已注册表元信息
     */
    @SuppressWarnings("unchecked")
    public <E> SqlTableInfo<E> findTableInfo(Cte<E> cte) {
        if (cte == null) return null;
        if (cte.getEffectiveAlias() != null) {
            return (SqlTableInfo<E>) aliasMap.get(cte.getEffectiveAlias().toLowerCase());
        }
        return null;
    }

    /**
     * 通过别名或表名字符串查找已注册表元信息
     */
    public SqlTableInfo<?> findTableInfo(String aliasOrTableName) {
        if (aliasOrTableName == null) return null;
        return aliasMap.get(aliasOrTableName.toLowerCase());
    }

    /**
     * 外部动态调用分发：入参为 Object，按实际类型分发至对应的强类型方法
     */
    public SqlTableInfo<?> findTableInfo(Object target) {
        if (target == null) return null;
        if (target instanceof Class<?>) {
            return findTableInfo((Class<?>) target);
        }
        if (target instanceof Cte<?>) {
            return findTableInfo((Cte<?>) target);
        }
        if (target instanceof String) {
            return findTableInfo((String) target);
        }
        return findTableInfoByProxy(target);
    }

    /**
     * 解析实体 Class（若未注册则动态生成表元信息并注册）
     */
    public <E> SqlTableInfo<E> resolveTable(Class<E> entityClass) {
        if (entityClass == null) {
            throw new IllegalArgumentException("Entity class cannot be null");
        }
        SqlTableInfo<E> info = findTableInfo(entityClass);
        if (info != null) {
            return info;
        }
        String alias = nextAlias();
        EntityMetadata meta = EntityMetadataManager.get(entityClass, config);
        String tableName = meta != null ? meta.getTableName() : entityClass.getSimpleName();
        SqlTableInfo<E> dynamicallyCreated = new SqlTableInfo<>(null, entityClass, null, alias, tableName);
        registerTable(dynamicallyCreated);
        return dynamicallyCreated;
    }

    /**
     * 解析 CTE 临时表（若未注册则动态生成表元信息并注册）
     */
    public <E> SqlTableInfo<E> resolveTable(Cte<E> cte) {
        if (cte == null) {
            throw new IllegalArgumentException("CTE cannot be null");
        }
        SqlTableInfo<E> info = findTableInfo(cte);
        if (info != null) {
            return info;
        }
        String alias = cte.getEffectiveAlias() != null ? cte.getEffectiveAlias() : nextAlias();
        SqlTableInfo<E> dynamicallyCreated = new SqlTableInfo<>(cte, cte.getEntityClass(), cte, alias, cte.getName());
        registerTable(dynamicallyCreated);
        return dynamicallyCreated;
    }

    /**
     * 解析实体代理对象
     */
    public <P> SqlTableInfo<P> resolveTableByProxy(P proxy) {
        if (proxy == null) {
            throw new IllegalArgumentException("Table proxy cannot be null");
        }
        SqlTableInfo<P> info = findTableInfoByProxy(proxy);
        if (info != null) {
            return info;
        }
        throw new IllegalArgumentException("Cannot resolve table from unregistered proxy: " + proxy);
    }

    /**
     * 外部动态调用分发：入参为 Object，按实际类型分发至对应的强类型解析方法
     */
    public SqlTableInfo<?> resolveTable(Object target) {
        if (target == null) {
            throw new IllegalArgumentException("Target table cannot be null");
        }
        if (target instanceof Class<?>) {
            return resolveTable((Class<?>) target);
        }
        if (target instanceof Cte<?>) {
            return resolveTable((Cte<?>) target);
        }
        if (target instanceof String) {
            SqlTableInfo<?> info = findTableInfo((String) target);
            if (info != null) {
                return info;
            }
            throw new IllegalArgumentException("Cannot resolve table from alias: " + target);
        }
        return resolveTableByProxy(target);
    }

    public SqlTableInfo<?> getFirstRegisteredTable() {
        return !registeredTables.isEmpty() ? registeredTables.get(0) : null;
    }

    // ========== 2. SELECT 管道入口与 Query 工厂 ==========

    public <T> SqlSelectStream<T> select(T tableProxy) {
        SqlTableInfo<T> info = findTableInfoByProxy(tableProxy);
        Class<T> entityClass = info != null ? info.getEntityClass() : null;
        SqlSelectStream<T> stream = new SqlSelectStream<>(this, entityClass, new Object[]{tableProxy});
        this.currentOperation = stream;
        return stream;
    }

    public <DTO> SqlSelectStream<DTO> select(Class<DTO> dtoClass, Object... expressions) {
        SqlSelectStream<DTO> stream = new SqlSelectStream<>(this, dtoClass, expressions);
        this.currentOperation = stream;
        return stream;
    }

    public <T> SqlSelectStream<T> select(Object... expressions) {
        SqlSelectStream<T> stream = new SqlSelectStream<>(this, null, expressions);
        this.currentOperation = stream;
        return stream;
    }

    public <T> SelectBuilder<T> select(Class<T> entityClass) {
        return createQuery(entityClass);
    }

    public <T> SelectBuilder<T> select(Cte<T> cte) {
        return createQuery(cte);
    }

    public <T> SelectBuilder<T> createQuery(Class<T> entityClass) {
        SelectBuilder<T> q = new SelectBuilder<>(this, entityClass);
        this.currentOperation = q;
        return q;
    }

    public <T> SelectBuilder<T> createQuery(Cte<T> cte) {
        SelectBuilder<T> q = new SelectBuilder<>(this, cte);
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

    @SuppressWarnings("unchecked")
    public <T> SqlUpdateStream<T> update(T targetTable) {
        SqlTableInfo<T> info;
        if (targetTable instanceof Class) {
            info = resolveTable((Class<T>) targetTable);
        } else if (targetTable instanceof Cte) {
            info = resolveTable((Cte<T>) targetTable);
        } else {
            info = resolveTableByProxy(targetTable);
        }
        SqlUpdateStream<T> stream = new SqlUpdateStream<>(this, info);
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

    @SuppressWarnings("unchecked")
    public <T> SqlDeleteStream<T> delete(T targetTable) {
        SqlTableInfo<T> info;
        if (targetTable instanceof Class) {
            info = resolveTable((Class<T>) targetTable);
        } else if (targetTable instanceof Cte) {
            info = resolveTable((Cte<T>) targetTable);
        } else {
            info = resolveTableByProxy(targetTable);
        }
        SqlDeleteStream<T> stream = new SqlDeleteStream<>(this, info);
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
        } else if (currentOperation instanceof SelectBuilder<?>) {
            detected = ((SelectBuilder<?>) currentOperation).getRootMetadata() != null
                    ? ((SelectBuilder<?>) currentOperation).getRootMetadata().getEntityClass() : null;
        }
        return new SqlExecution<>(this, currentOperation, (Class<T>) detected);
    }

    public <T> SqlExecution<T> toExecution(Class<T> resultClass) {
        return new SqlExecution<>(this, currentOperation, resultClass);
    }

    @SuppressWarnings("unchecked")
    public <T> SqlExecution<T> toExecutionFromReturn(Object returned) {
        if (returned == null) {
            return toExecution();
        }
        if (returned instanceof SqlExecution) {
            return (SqlExecution<T>) returned;
        }
        if (returned instanceof Class) {
            return toExecution((Class<T>) returned);
        }
        if (returned instanceof com.javaorm.core.query.OrderByBuilder) {
            return toExecutionFromReturn(((com.javaorm.core.query.OrderByBuilder<?>) returned).asc());
        }
        if (returned instanceof com.javaorm.core.query.WhereBuilder) {
            com.javaorm.core.query.WhereBuilder<?> wb = (com.javaorm.core.query.WhereBuilder<?>) returned;
            if (currentOperation instanceof SqlUpdateStream || currentOperation instanceof com.javaorm.core.mutation.UpdateBuilder) {
                return new SqlExecution<>(this, currentOperation, null);
            }
            if (currentOperation instanceof SqlDeleteStream || currentOperation instanceof com.javaorm.core.mutation.DeleteBuilder) {
                return new SqlExecution<>(this, currentOperation, null);
            }
            if (currentOperation instanceof SqlSelectStream) {
                Class<?> detected = ((SqlSelectStream<?>) currentOperation).getResultClass();
                if (detected == null) {
                    detected = wb.getEntityClass();
                }
                return new SqlExecution<>(this, currentOperation, (Class<T>) detected);
            }
            SelectBuilder<?> q = wb.getRootQuery();
            Object op = q != null ? q : wb;
            Class<?> detected = wb.getEntityClass();
            return new SqlExecution<>(this, op, (Class<T>) detected);
        }
        if (returned instanceof Query) {
            Query<T> tq = (Query<T>) returned;
            this.currentOperation = tq;
            return new SqlExecution<>(this, tq, tq.getResultClass());
        }
        if (returned instanceof SqlSelectStream) {
            SqlSelectStream<?> selectStream = (SqlSelectStream<?>) returned;
            this.currentOperation = selectStream;
            return new SqlExecution<>(this, selectStream, (Class<T>) selectStream.getResultClass());
        }
        if (returned instanceof SelectBuilder) {
            SelectBuilder<?> q = (SelectBuilder<?>) returned;
            this.currentOperation = q;
            Class<?> detected = q.getRootEntityClass();
            return new SqlExecution<>(this, q, (Class<T>) detected);
        }
        if (returned instanceof com.javaorm.core.mutation.UpdateBuilder) {
            com.javaorm.core.mutation.UpdateBuilder<?> ub = (com.javaorm.core.mutation.UpdateBuilder<?>) returned;
            this.currentOperation = ub;
            return new SqlExecution<>(this, ub, null);
        }
        if (returned instanceof SqlUpdateStream) {
            SqlUpdateStream sus = (SqlUpdateStream) returned;
            this.currentOperation = sus;
            return new SqlExecution<>(this, sus, null);
        }
        if (returned instanceof com.javaorm.core.mutation.DeleteBuilder) {
            com.javaorm.core.mutation.DeleteBuilder<?> db = (com.javaorm.core.mutation.DeleteBuilder<?>) returned;
            this.currentOperation = db;
            return new SqlExecution<>(this, db, null);
        }
        if (returned instanceof SqlDeleteStream) {
            SqlDeleteStream sds = (SqlDeleteStream) returned;
            this.currentOperation = sds;
            return new SqlExecution<>(this, sds, null);
        }
        return toExecution();
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
        return getSqlExecutor().executeUpdate(null, sqlAndParams, prefix, dialect, getEffectiveConfig(), dataSource);
    }

    public int[] executeBatchUpdate(String sql, List<List<Object>> batchParams) {
        return getSqlExecutor().executeBatchUpdate(null, sql, batchParams, dialect, getEffectiveConfig(), dataSource);
    }

    public int executeUpdate(UpdateStatement statement) {
        return executeUpdate(statement, null, null);
    }

    public int executeUpdate(UpdateStatement statement, com.javaorm.core.sharding.TableRouter<?> routerOverride, Boolean allowFullScanOverride) {
        if (statement.getSetValues().isEmpty()) {
            return 0;
        }
        Class<?> entityClass = statement.getEntityClass();
        EntityMetadata metadata = entityClass != null ? (config != null ? config.getMetadata(entityClass) : EntityMetadataManager.get(entityClass, config)) : null;
        com.javaorm.core.sharding.TableRouter<?> effRouter = routerOverride != null ? routerOverride : statement.getShardingRouter();
        Boolean effAllowFullScan = allowFullScanOverride != null ? allowFullScanOverride : statement.getAllowFullShardingScan();

        if (com.javaorm.core.sharding.ShardingHandler.isSharding(metadata, effRouter)) {
            return com.javaorm.core.sharding.ShardingHandler.executeUpdate(null, statement, metadata, effRouter, effAllowFullScan, dialect, getEffectiveConfig(), dataSource, getSqlExecutor());
        }
        SqlAndParams sqlAndParams = dialect.buildUpdate(statement);
        logSql(sqlAndParams, "UPDATE");
        return getSqlExecutor().executeUpdate(null, sqlAndParams, "UPDATE", dialect, getEffectiveConfig(), dataSource);
    }

    public int executeDelete(DeleteStatement statement) {
        return executeDelete(statement, null, null);
    }

    public int executeDelete(DeleteStatement statement, com.javaorm.core.sharding.TableRouter<?> routerOverride, Boolean allowFullScanOverride) {
        Class<?> entityClass = statement.getEntityClass();
        EntityMetadata metadata = entityClass != null ? (config != null ? config.getMetadata(entityClass) : EntityMetadataManager.get(entityClass, config)) : null;
        com.javaorm.core.sharding.TableRouter<?> effRouter = routerOverride != null ? routerOverride : statement.getShardingRouter();
        Boolean effAllowFullScan = allowFullScanOverride != null ? allowFullScanOverride : statement.getAllowFullShardingScan();

        if (com.javaorm.core.sharding.ShardingHandler.isSharding(metadata, effRouter)) {
            return com.javaorm.core.sharding.ShardingHandler.executeDelete(null, statement, metadata, effRouter, effAllowFullScan, dialect, getEffectiveConfig(), dataSource, getSqlExecutor());
        }
        SqlAndParams sqlAndParams = dialect.buildDelete(statement);
        logSql(sqlAndParams, "DELETE");
        return getSqlExecutor().executeUpdate(null, sqlAndParams, "DELETE", dialect, getEffectiveConfig(), dataSource);
    }

    public int executeLogicDelete(EntityMetadata metadata, Class<?> entityClass, List<Condition> whereConditions) {
        return executeLogicDelete(metadata, entityClass, whereConditions, null, null);
    }

    public int executeLogicDelete(EntityMetadata metadata, Class<?> entityClass, List<Condition> whereConditions,
                                  com.javaorm.core.sharding.TableRouter<?> routerOverride, Boolean allowFullScanOverride) {
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

        return executeUpdate(updateStmt, routerOverride, allowFullScanOverride);
    }

    public int executeInsert(Connection conn, com.javaorm.core.ast.InsertStatement statement, EntityMetadata metadata,
                             boolean returnKeys, Object entity, com.javaorm.core.sharding.TableRouter<?> routerOverride) {
        if (com.javaorm.core.sharding.ShardingHandler.isSharding(metadata, routerOverride)) {
            return com.javaorm.core.sharding.ShardingHandler.executeInsert(conn, statement, metadata, returnKeys, entity, routerOverride, dialect, getEffectiveConfig(), dataSource, getSqlExecutor());
        }
        return getSqlExecutor().executeInsert(conn, statement, metadata, returnKeys, entity, dialect, getEffectiveConfig(), dataSource);
    }

    public <R> List<R> executeSelectList(Connection conn, SelectStatement statement, EntityMetadata metadata, List<IncludeSpec> includes) {
        if (com.javaorm.core.sharding.ShardingHandler.isSharding(statement, metadata)) {
            return com.javaorm.core.sharding.ShardingHandler.executeSelectList(conn, statement, metadata, includes, dialect, getEffectiveConfig(), dataSource, getSqlExecutor());
        }
        return getSqlExecutor().executeQuery(conn, statement, metadata, includes, dialect, getEffectiveConfig(), dataSource);
    }

    public <DTO> List<DTO> executeSelectDtoList(Connection conn, SelectStatement statement, Class<DTO> dtoClass) {
        Class<?> rootClass = statement.getRootEntityClass();
        EntityMetadata metadata = rootClass != null ? (config != null ? config.getMetadata(rootClass) : EntityMetadataManager.get(rootClass, config)) : null;
        if (com.javaorm.core.sharding.ShardingHandler.isSharding(statement, metadata)) {
            return com.javaorm.core.sharding.ShardingHandler.executeSelectDtoList(conn, statement, dtoClass, metadata, dialect, getEffectiveConfig(), dataSource, getSqlExecutor());
        }
        return getSqlExecutor().executeDtoQuery(conn, statement, dtoClass, dialect, getEffectiveConfig(), dataSource);
    }

    public List<Map<String, Object>> executeSelectMaps(Connection conn, SelectStatement statement) {
        Class<?> rootClass = statement.getRootEntityClass();
        EntityMetadata metadata = rootClass != null ? (config != null ? config.getMetadata(rootClass) : EntityMetadataManager.get(rootClass, config)) : null;
        if (com.javaorm.core.sharding.ShardingHandler.isSharding(statement, metadata)) {
            return com.javaorm.core.sharding.ShardingHandler.executeSelectMaps(conn, statement, metadata, dialect, getEffectiveConfig(), dataSource, getSqlExecutor());
        }
        return getSqlExecutor().executeMaps(conn, statement, dialect, getEffectiveConfig(), dataSource);
    }

    public <V> V executeSelectScalar(Connection conn, SelectStatement statement, Class<V> targetType) {
        Class<?> rootClass = statement.getRootEntityClass();
        EntityMetadata metadata = rootClass != null ? (config != null ? config.getMetadata(rootClass) : EntityMetadataManager.get(rootClass, config)) : null;
        if (com.javaorm.core.sharding.ShardingHandler.isSharding(statement, metadata)) {
            return com.javaorm.core.sharding.ShardingHandler.executeSelectScalar(conn, statement, targetType, metadata, dialect, getEffectiveConfig(), dataSource, getSqlExecutor());
        }
        return getSqlExecutor().executeScalar(conn, statement, targetType, dialect, getEffectiveConfig(), dataSource);
    }

    public <V> List<V> executeSelectScalarList(Connection conn, SelectStatement statement, Class<V> targetType) {
        Class<?> rootClass = statement.getRootEntityClass();
        EntityMetadata metadata = rootClass != null ? (config != null ? config.getMetadata(rootClass) : EntityMetadataManager.get(rootClass, config)) : null;
        if (com.javaorm.core.sharding.ShardingHandler.isSharding(statement, metadata)) {
            return com.javaorm.core.sharding.ShardingHandler.executeSelectScalarList(conn, statement, targetType, metadata, dialect, getEffectiveConfig(), dataSource, getSqlExecutor());
        }
        return getSqlExecutor().executeScalarList(conn, statement, targetType, dialect, getEffectiveConfig(), dataSource);
    }

    public long executeSelectCount(Connection conn, SelectStatement statement, Dialect targetDialect, Class<?> rootEntityClass) {
        Dialect effDialect = targetDialect != null ? targetDialect : dialect;
        EntityMetadata metadata = rootEntityClass != null ? (config != null ? config.getMetadata(rootEntityClass) : EntityMetadataManager.get(rootEntityClass, config)) : null;
        if (com.javaorm.core.sharding.ShardingHandler.isSharding(statement, metadata)) {
            return com.javaorm.core.sharding.ShardingHandler.executeSelectCount(conn, statement, metadata, effDialect, getEffectiveConfig(), dataSource, getSqlExecutor());
        }
        return getSqlExecutor().executeCount(conn, statement, effDialect, getEffectiveConfig(), dataSource);
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
        ColumnMetadata col = meta != null ? meta.getColumnByProperty(path.getFullPath()) : null;
        if (col == null && meta != null) {
            col = meta.getColumnByProperty(path.getTerminalProperty());
        }
        if (col != null) {
            return col.getColumnName();
        }
        return config != null ? config.getNameConversion().convert(path.getTerminalProperty()) : path.getTerminalProperty();
    }
}
