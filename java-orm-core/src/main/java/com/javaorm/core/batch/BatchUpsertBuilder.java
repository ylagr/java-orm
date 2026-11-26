package com.javaorm.core.batch;

import com.javaorm.core.ast.UpsertStatement;
import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.connection.ConnectionUtils;
import com.javaorm.core.dialect.Dialect;
import com.javaorm.core.dialect.SqlAndParams;
import com.javaorm.core.metadata.ColumnMetadata;
import com.javaorm.core.metadata.EntityMetadata;
import com.javaorm.core.metadata.EntityMetadataManager;
import com.javaorm.core.proxy.PropertyPath;
import com.javaorm.core.proxy.ProxyFactory;
import com.javaorm.core.proxy.RecordingContext;
import com.javaorm.core.sharding.DataSourceRouter;
import com.javaorm.core.sharding.TableRouter;
import com.javaorm.core.sql.SqlEnv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.*;
import java.util.function.Consumer;

/**
 * 高性能跨方言批量 UPSERT（存在即更新，不存在即插入）构建器。
 * 1. 采用 JDBC PreparedStatement executeBatch() 执行批量幂等写入/更新。
 * 2. 支持批大小控制（batchSize，默认 500）。
 * 3. 跨方言自适应（MySQL, PostgreSQL, SQLite, H2, Oracle）。
 * 4. 自动填充租户、逻辑删除正常值、乐观锁版本号、审计字段。
 * 5. 自动分库分表感知分组。
 *
 * @param <T> 实体类型
 */
public class BatchUpsertBuilder<T> extends AbstractBatchBuilder<T, BatchUpsertBuilder<T>> {

    private static final Logger log = LoggerFactory.getLogger(BatchUpsertBuilder.class);

    private Boolean printSqlOverride = null;

    private final com.javaorm.core.mutation.UpsertSpec<T> spec = new com.javaorm.core.mutation.UpsertSpec<>();

    private TableRouter<?> shardingRouter;
    private String targetDataSource;
    private DataSourceRouter<?> dataSourceRouter;

    public BatchUpsertBuilder(SqlEnv env, Collection<T> entities) {
        super(env, entities);
    }

    public BatchUpsertBuilder<T> onConflict(String... columns) {
        spec.addConflictColumns(resolveEntityMetadata(), columns);
        return this;
    }

    public BatchUpsertBuilder<T> onConflict(Consumer<T> proxyAction) {
        spec.addConflictColumns(getEntityClass(), resolveEntityMetadata(), proxyAction);
        return this;
    }

    public BatchUpsertBuilder<T> updateColumns(String... columns) {
        spec.addUpdateColumns(resolveEntityMetadata(), columns);
        return this;
    }

    public BatchUpsertBuilder<T> updateColumns(Consumer<T> proxyAction) {
        spec.addUpdateColumns(getEntityClass(), resolveEntityMetadata(), proxyAction);
        return this;
    }

    public BatchUpsertBuilder<T> ignoreColumns(String... columns) {
        spec.addIgnoreColumns(resolveEntityMetadata(), columns);
        return this;
    }

    public BatchUpsertBuilder<T> doNothing() {
        spec.setDoNothing(true);
        return this;
    }

    public BatchUpsertBuilder<T> printSql() {
        this.printSqlOverride = env.getConfig() != null ? env.getConfig().isPrintSql() : false;
        return this;
    }

    public BatchUpsertBuilder<T> printSql(boolean print) {
        this.printSqlOverride = print;
        return this;
    }

    public BatchUpsertBuilder<T> sharding(TableRouter<?> router) {
        this.shardingRouter = router;
        return this;
    }

    public BatchUpsertBuilder<T> dataSource(String name) {
        this.targetDataSource = name;
        return this;
    }

    public BatchUpsertBuilder<T> dataSource(DataSourceRouter<?> router) {
        this.dataSourceRouter = router;
        return this;
    }

    @SuppressWarnings("unchecked")
    private Class<T> getEntityClass() {
        if (entities != null && !entities.isEmpty()) {
            return (Class<T>) entities.iterator().next().getClass();
        }
        return null;
    }

    private EntityMetadata resolveEntityMetadata() {
        Class<T> entityClass = getEntityClass();
        return entityClass != null ? EntityMetadataManager.get(entityClass, env.getConfig()) : null;
    }

    @Override
    public int execute() {
        if (entities == null || entities.isEmpty()) {
            return 0;
        }

        Iterator<T> iterator = entities.iterator();
        T first = iterator.next();
        @SuppressWarnings("unchecked")
        Class<T> entityClass = (Class<T>) first.getClass();
        EntityMetadata metadata = EntityMetadataManager.get(entityClass, env.getConfig());

        // 分组处理分库分表（支持链式覆盖实体注解路由）
        Map<PhysicalTarget, List<T>> groups = groupByPhysicalTarget(
                entities, metadata, java.util.function.Function.identity(),
                shardingRouter, dataSourceRouter, targetDataSource);
        com.javaorm.core.executor.ExecutionContext baseCtx = printSqlOverride != null
                ? env.getExecutionContext().withPrintSql(printSqlOverride)
                : env.getExecutionContext();
        int totalAffected = 0;

        for (Map.Entry<PhysicalTarget, List<T>> entry : groups.entrySet()) {
            PhysicalTarget target = entry.getKey();
            List<T> targetEntities = entry.getValue();
            DataSource ds = resolveDataSource(target.dataSourceName);
            com.javaorm.core.executor.ExecutionContext dsCtx = baseCtx.withDataSource(ds);

            for (int i = 0; i < targetEntities.size(); i += batchSize) {
                int end = Math.min(i + batchSize, targetEntities.size());
                List<T> chunk = targetEntities.subList(i, end);
                totalAffected += executeChunk(chunk, metadata, target.tableName, dsCtx);
            }
        }

        return totalAffected;
    }

    private int executeChunk(List<T> chunk, EntityMetadata metadata, String tableName, com.javaorm.core.executor.ExecutionContext ctx) {
        if (chunk.isEmpty()) return 0;
        Dialect dialect = ctx.getDialect();
        OrmConfig config = ctx.getConfig();
        Class<T> entityClass = getEntityClass();

        // 收集参与待插入/更新的列
        List<ColumnMetadata> insertCols = new ArrayList<>();
        List<String> insertColNames = new ArrayList<>();
        for (ColumnMetadata col : metadata.getColumns()) {
            if (col.isIgnored()) continue;
            // 自增主键且第一条记录为 null/0 且不在冲突列中，则跳过
            if (col.isPrimaryKey()) {
                Object val = col.getValue(chunk.get(0));
                if ((val == null || (val instanceof Number && ((Number) val).longValue() == 0))
                        && !spec.containsConflictColumn(col.getColumnName())) {
                    continue;
                }
            }
            insertCols.add(col);
            insertColNames.add(col.getColumnName());
        }

        // 构建 UpsertStatement 模板获取 SQL
        UpsertStatement templateStmt = new UpsertStatement(entityClass, tableName);
        spec.applyTo(templateStmt, metadata, entityClass, insertColNames);
        for (String colName : insertColNames) {
            templateStmt.addColumnValue(colName, "?"); // 占位
        }

        SqlAndParams sap = dialect.buildUpsert(templateStmt);
        String sql = sap.getSql();
        if (ctx.isPrintSql()) {
            log.info("==>  Preparing Batch UPSERT ({} entities): {}", chunk.size(), sql);
        }

        List<List<Object>> batchParams = new ArrayList<>(chunk.size());
        for (T entity : chunk) {
            prepareEntityDefaults(entity, metadata, config);
            List<Object> rowParams = new ArrayList<>(insertCols.size());
            for (ColumnMetadata col : insertCols) {
                rowParams.add(col.serializeParam(col.getValue(entity)));
            }
            batchParams.add(rowParams);
        }

        int[] results = ctx.getSqlExecutor().executeBatchUpdate(null, sql, batchParams, ctx);
        return sumBatchResults(results);
    }

    private void prepareEntityDefaults(T entity, EntityMetadata metadata, OrmConfig config) {
        if (config != null && config.getAutoFillHandler() != null) {
            config.getAutoFillHandler().insertFill(entity, metadata);
        }

        for (ColumnMetadata col : metadata.getColumns()) {
            if (col.isIgnored()) continue;

            if (col.isLogicDelete()) {
                Object val = col.getValue(entity);
                if (val == null) {
                    col.setValue(entity, col.getLogicDeleteNormalValue());
                }
            }

            if (col.isTenant()) {
                Object val = col.getValue(entity);
                if (val == null) {
                    Object tenantId = config != null ? config.getCurrentTenantId() : com.javaorm.core.tenant.TenantContext.getTenantId();
                    if (tenantId != null) {
                        col.setValue(entity, tenantId);
                    }
                }
            }

            if (col.isVersion()) {
                Object val = col.getValue(entity);
                if (val == null) {
                    col.setValue(entity, 1);
                }
            }
        }
    }
}
