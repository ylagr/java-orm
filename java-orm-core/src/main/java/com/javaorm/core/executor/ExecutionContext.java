package com.javaorm.core.executor;

import com.javaorm.core.ast.*;
import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.dialect.Dialect;
import com.javaorm.core.dialect.SqlAndParams;
import com.javaorm.core.metadata.ColumnMetadata;
import com.javaorm.core.metadata.EntityMetadata;
import com.javaorm.core.metadata.EntityMetadataManager;
import com.javaorm.core.operator.SqlOperator;
import com.javaorm.core.query.IncludeSpec;
import com.javaorm.core.sharding.DataSourceRouter;
import com.javaorm.core.sharding.ShardingHandler;
import com.javaorm.core.sharding.TableRouter;
import com.javaorm.core.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;
import java.util.Map;

/**
 * SQL 执行上下文（ExecutionContext）。
 * <p>
 * 显式持有全局状态（{@link OrmConfig}、{@link DataSource}、{@link Dialect}）与当前语句级覆盖状态
 * （如 {@code printSqlOverride}）。读取执行期选项时优先使用当前语句状态，缺失时回退到全局状态，
 * 避免为了单条语句覆盖而复制整份 {@link OrmConfig}，也避免引入额外的 {@link ThreadLocal} 隐式状态。
 */
public final class ExecutionContext {

    private static final Logger log = LoggerFactory.getLogger(ExecutionContext.class);
    private static final SqlExecutor DEFAULT_EXECUTOR = new DefaultJdbcExecutor();

    private final DataSource dataSource;
    private final Dialect dialect;
    private final OrmConfig config;
    private final Boolean printSqlOverride;

    public ExecutionContext(DataSource dataSource, Dialect dialect, OrmConfig config) {
        this(dataSource, dialect, config, null);
    }

    public ExecutionContext(DataSource dataSource, Dialect dialect, OrmConfig config, Boolean printSqlOverride) {
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.config = config != null ? config : new OrmConfig();
        this.printSqlOverride = printSqlOverride;
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

    public Boolean getPrintSqlOverride() {
        return printSqlOverride;
    }

    /**
     * 当前语句优先，缺失时回退全局 {@link OrmConfig#isPrintSql()}。
     */
    public boolean isPrintSql() {
        return printSqlOverride != null ? printSqlOverride : config.isPrintSql();
    }

    public ExecutionContext withPrintSql(Boolean printSql) {
        return new ExecutionContext(dataSource, dialect, config, printSql);
    }

    public ExecutionContext withDialect(Dialect targetDialect) {
        if (targetDialect == null || targetDialect == this.dialect) {
            return this;
        }
        return new ExecutionContext(dataSource, targetDialect, config, printSqlOverride);
    }

    public ExecutionContext withDataSource(DataSource targetDataSource) {
        if (targetDataSource == this.dataSource) {
            return this;
        }
        return new ExecutionContext(targetDataSource, dialect, config, printSqlOverride);
    }

    public SqlExecutor getSqlExecutor() {
        SqlExecutor configured = config.getSqlExecutor();
        return configured != null ? configured : DEFAULT_EXECUTOR;
    }

    public void logSql(SqlAndParams sqlAndParams, String prefix) {
        if (isPrintSql() && sqlAndParams != null) {
            String p = (prefix != null && !prefix.isEmpty()) ? "[" + prefix + "] " : "";
            log.info("==> {}Preparing:  {}", p, sqlAndParams.getSql());
            log.info("==> {}Parameters: {}", p, sqlAndParams.getParams());
        }
    }

    public int executeSqlAndParams(SqlAndParams sqlAndParams, String prefix) {
        logSql(sqlAndParams, prefix);
        return getSqlExecutor().executeUpdate(null, sqlAndParams, prefix, this);
    }

    public int[] executeBatchUpdate(String sql, List<List<Object>> batchParams) {
        return getSqlExecutor().executeBatchUpdate(null, sql, batchParams, this);
    }

    public int executeUpdate(UpdateStatement statement) {
        return executeUpdate(statement, null, null);
    }

    public int executeUpdate(UpdateStatement statement, TableRouter<?> routerOverride, Boolean allowFullScanOverride) {
        if (statement.getSetValues().isEmpty()) {
            return 0;
        }
        EntityMetadata metadata = resolveMetadata(statement.getEntityClass());
        TableRouter<?> effRouter = routerOverride != null ? routerOverride : statement.getShardingRouter();
        DataSourceRouter<?> effDsRouter = statement.getDataSourceRouter();
        String effDsOverride = statement.getTargetDataSource();
        Boolean effAllowFullScan = allowFullScanOverride != null ? allowFullScanOverride : statement.getAllowFullShardingScan();

        if (ShardingHandler.isSharding(metadata, effRouter, effDsRouter, effDsOverride)) {
            return ShardingHandler.executeUpdate(null, statement, metadata, effRouter, effDsRouter, effDsOverride, effAllowFullScan, this);
        }
        SqlAndParams sqlAndParams = dialect.buildUpdate(statement);
        logSql(sqlAndParams, "UPDATE");
        return getSqlExecutor().executeUpdate(null, sqlAndParams, "UPDATE", this);
    }

    public int executeDelete(DeleteStatement statement) {
        return executeDelete(statement, null, null);
    }

    public int executeDelete(DeleteStatement statement, TableRouter<?> routerOverride, Boolean allowFullScanOverride) {
        EntityMetadata metadata = resolveMetadata(statement.getEntityClass());
        TableRouter<?> effRouter = routerOverride != null ? routerOverride : statement.getShardingRouter();
        DataSourceRouter<?> effDsRouter = statement.getDataSourceRouter();
        String effDsOverride = statement.getTargetDataSource();
        Boolean effAllowFullScan = allowFullScanOverride != null ? allowFullScanOverride : statement.getAllowFullShardingScan();

        if (ShardingHandler.isSharding(metadata, effRouter, effDsRouter, effDsOverride)) {
            return ShardingHandler.executeDelete(null, statement, metadata, effRouter, effDsRouter, effDsOverride, effAllowFullScan, this);
        }
        SqlAndParams sqlAndParams = dialect.buildDelete(statement);
        logSql(sqlAndParams, "DELETE");
        return getSqlExecutor().executeUpdate(null, sqlAndParams, "DELETE", this);
    }

    public int executeLogicDelete(EntityMetadata metadata, Class<?> entityClass, List<Condition> whereConditions) {
        return executeLogicDelete(metadata, entityClass, whereConditions, null, null);
    }

    public int executeLogicDelete(EntityMetadata metadata, Class<?> entityClass, List<Condition> whereConditions,
                                  TableRouter<?> routerOverride, Boolean allowFullScanOverride) {
        ColumnMetadata ld = metadata.getLogicDeleteColumn();
        UpdateStatement updateStmt = new UpdateStatement(entityClass, metadata.getTableName());
        updateStmt.addSet(ld.getColumnName(), ld.getLogicDeleteDeletedValue());

        for (Condition c : whereConditions) {
            updateStmt.addWhereCondition(c);
        }
        updateStmt.addSystemCondition(new BinaryCondition(null, ld.getColumnName(), SqlOperator.EQ, ld.getLogicDeleteNormalValue()));
        if (metadata.hasTenant()) {
            ColumnMetadata tc = metadata.getTenantColumn();
            Object tenantId = config.getCurrentTenantId() != null ? config.getCurrentTenantId() : TenantContext.getTenantId();
            if (tenantId != null) {
                updateStmt.addSystemCondition(new BinaryCondition(null, tc.getColumnName(), SqlOperator.EQ, tenantId));
            } else if (config.isTenantStrict()) {
                throw new IllegalStateException("Tenant context is required for multi-tenant entity: " + entityClass.getName());
            }
        }

        return executeUpdate(updateStmt, routerOverride, allowFullScanOverride);
    }

    public int executeInsert(Connection conn, InsertStatement statement, EntityMetadata metadata,
                             boolean returnKeys, Object entity, TableRouter<?> routerOverride) {
        TableRouter<?> effRouter = routerOverride != null ? routerOverride : (statement != null ? statement.getShardingRouter() : null);
        DataSourceRouter<?> effDsRouter = statement != null ? statement.getDataSourceRouter() : null;
        String effDsOverride = statement != null ? statement.getTargetDataSource() : null;

        if (ShardingHandler.isSharding(metadata, effRouter, effDsRouter, effDsOverride)) {
            return ShardingHandler.executeInsert(conn, statement, metadata, returnKeys, entity, effRouter, effDsRouter, effDsOverride, this);
        }
        return getSqlExecutor().executeInsert(conn, statement, metadata, returnKeys, entity, this);
    }

    public int executeUpsert(Connection conn, UpsertStatement statement, EntityMetadata metadata,
                             boolean returnKeys, Object entity, TableRouter<?> routerOverride) {
        TableRouter<?> effRouter = routerOverride != null ? routerOverride : (statement != null ? statement.getShardingRouter() : null);
        DataSourceRouter<?> effDsRouter = statement != null ? statement.getDataSourceRouter() : null;
        String effDsOverride = statement != null ? statement.getTargetDataSource() : null;

        if (ShardingHandler.isSharding(metadata, effRouter, effDsRouter, effDsOverride)) {
            return ShardingHandler.executeUpsert(conn, statement, metadata, returnKeys, entity, effRouter, effDsRouter, effDsOverride, this);
        }
        return getSqlExecutor().executeUpsert(conn, statement, metadata, returnKeys, entity, this);
    }

    public <R> List<R> executeSelectList(Connection conn, SelectStatement statement, EntityMetadata metadata, List<IncludeSpec> includes) {
        if (ShardingHandler.isSharding(statement, metadata)) {
            return ShardingHandler.executeSelectList(conn, statement, metadata, includes, this);
        }
        return getSqlExecutor().executeQuery(conn, statement, metadata, includes, this);
    }

    public <DTO> List<DTO> executeSelectDtoList(Connection conn, SelectStatement statement, Class<DTO> dtoClass) {
        EntityMetadata metadata = resolveMetadata(statement.getRootEntityClass());
        if (ShardingHandler.isSharding(statement, metadata)) {
            return ShardingHandler.executeSelectDtoList(conn, statement, dtoClass, metadata, this);
        }
        return getSqlExecutor().executeDtoQuery(conn, statement, dtoClass, this);
    }

    public List<Map<String, Object>> executeSelectMaps(Connection conn, SelectStatement statement) {
        EntityMetadata metadata = resolveMetadata(statement.getRootEntityClass());
        if (ShardingHandler.isSharding(statement, metadata)) {
            return ShardingHandler.executeSelectMaps(conn, statement, metadata, this);
        }
        return getSqlExecutor().executeMaps(conn, statement, this);
    }

    public <V> V executeSelectScalar(Connection conn, SelectStatement statement, Class<V> targetType) {
        EntityMetadata metadata = resolveMetadata(statement.getRootEntityClass());
        if (ShardingHandler.isSharding(statement, metadata)) {
            return ShardingHandler.executeSelectScalar(conn, statement, targetType, metadata, this);
        }
        return getSqlExecutor().executeScalar(conn, statement, targetType, this);
    }

    public <V> List<V> executeSelectScalarList(Connection conn, SelectStatement statement, Class<V> targetType) {
        EntityMetadata metadata = resolveMetadata(statement.getRootEntityClass());
        if (ShardingHandler.isSharding(statement, metadata)) {
            return ShardingHandler.executeSelectScalarList(conn, statement, targetType, metadata, this);
        }
        return getSqlExecutor().executeScalarList(conn, statement, targetType, this);
    }

    public long executeSelectCount(Connection conn, SelectStatement statement, Dialect targetDialect, Class<?> rootEntityClass) {
        ExecutionContext effCtx = withDialect(targetDialect);
        EntityMetadata metadata = resolveMetadata(rootEntityClass);
        if (ShardingHandler.isSharding(statement, metadata)) {
            return ShardingHandler.executeSelectCount(conn, statement, metadata, effCtx);
        }
        return getSqlExecutor().executeCount(conn, statement, effCtx);
    }

    private EntityMetadata resolveMetadata(Class<?> entityClass) {
        if (entityClass == null) {
            return null;
        }
        EntityMetadata cached = config.getMetadata(entityClass);
        return cached != null ? cached : EntityMetadataManager.get(entityClass, config);
    }
}
