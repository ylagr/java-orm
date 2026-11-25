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
public class BatchUpsertBuilder<T> {

    private static final Logger log = LoggerFactory.getLogger(BatchUpsertBuilder.class);

    private final SqlEnv env;
    private final Collection<T> entities;
    private int batchSize = 500;
    private Boolean printSqlOverride = null;

    private final List<String> conflictColumns = new ArrayList<>();
    private final List<String> updateColumns = new ArrayList<>();
    private final Set<String> ignoreColumns = new HashSet<>();
    private boolean doNothing = false;

    private TableRouter<?> shardingRouter;
    private String targetDataSource;
    private DataSourceRouter<?> dataSourceRouter;

    public BatchUpsertBuilder(SqlEnv env, Collection<T> entities) {
        this.env = env != null ? env : new SqlEnv(null, null, null);
        this.entities = entities;
    }

    public BatchUpsertBuilder<T> batchSize(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be greater than 0");
        }
        this.batchSize = batchSize;
        return this;
    }

    public BatchUpsertBuilder<T> onConflict(String... columns) {
        if (columns != null) {
            for (String col : columns) {
                if (col != null && !col.trim().isEmpty()) {
                    String resolved = resolveColumnName(col.trim());
                    if (!this.conflictColumns.contains(resolved)) {
                        this.conflictColumns.add(resolved);
                    }
                }
            }
        }
        return this;
    }

    public BatchUpsertBuilder<T> onConflict(Consumer<T> proxyAction) {
        addResolvedColumns(this.conflictColumns, proxyAction);
        return this;
    }

    public BatchUpsertBuilder<T> updateColumns(String... columns) {
        if (columns != null) {
            for (String col : columns) {
                if (col != null && !col.trim().isEmpty()) {
                    String resolved = resolveColumnName(col.trim());
                    if (!this.updateColumns.contains(resolved)) {
                        this.updateColumns.add(resolved);
                    }
                }
            }
        }
        return this;
    }

    public BatchUpsertBuilder<T> updateColumns(Consumer<T> proxyAction) {
        addResolvedColumns(this.updateColumns, proxyAction);
        return this;
    }

    private void addResolvedColumns(List<String> target, Consumer<T> proxyAction) {
        for (PropertyPath path : RecordingContext.recordPaths(getEntityClass(), proxyAction)) {
            String resolved = resolveColumnName(path.getTerminalProperty());
            if (!target.contains(resolved)) {
                target.add(resolved);
            }
        }
    }

    public BatchUpsertBuilder<T> ignoreColumns(String... columns) {
        if (columns != null) {
            for (String col : columns) {
                if (col != null && !col.trim().isEmpty()) {
                    this.ignoreColumns.add(resolveColumnName(col.trim()));
                }
            }
        }
        return this;
    }

    public BatchUpsertBuilder<T> doNothing() {
        this.doNothing = true;
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

    private String resolveColumnName(String propertyOrColumn) {
        Class<T> entityClass = getEntityClass();
        if (entityClass == null) return propertyOrColumn;
        EntityMetadata metadata = EntityMetadataManager.get(entityClass, env.getConfig());
        ColumnMetadata col = metadata.getColumnByProperty(propertyOrColumn);
        if (col != null) {
            return col.getColumnName();
        }
        col = metadata.getColumnByDbName(propertyOrColumn);
        if (col != null) {
            return col.getColumnName();
        }
        return propertyOrColumn;
    }

    private boolean isDefaultCreationAuditColumn(String colName) {
        if (colName == null) return false;
        String lower = colName.toLowerCase();
        return lower.equals("create_time") || lower.equals("createtime") || lower.equals("created_at")
                || lower.equals("gmt_create") || lower.equals("create_by") || lower.equals("createby") || lower.equals("creator");
    }

    public int exec() {
        return execute();
    }

    public int execute() {
        if (entities == null || entities.isEmpty()) {
            return 0;
        }

        Iterator<T> iterator = entities.iterator();
        T first = iterator.next();
        @SuppressWarnings("unchecked")
        Class<T> entityClass = (Class<T>) first.getClass();
        EntityMetadata metadata = EntityMetadataManager.get(entityClass, env.getConfig());

        // 分组处理分库分表
        Map<BatchSupport.PhysicalTarget, List<T>> groups = BatchSupport.groupByPhysicalTarget(entities, metadata);
        int totalAffected = 0;

        for (Map.Entry<BatchSupport.PhysicalTarget, List<T>> entry : groups.entrySet()) {
            BatchSupport.PhysicalTarget target = entry.getKey();
            List<T> targetEntities = entry.getValue();
            DataSource ds = BatchSupport.resolveDataSource(env, target.dataSourceName);

            for (int i = 0; i < targetEntities.size(); i += batchSize) {
                int end = Math.min(i + batchSize, targetEntities.size());
                List<T> chunk = targetEntities.subList(i, end);
                totalAffected += executeChunk(chunk, metadata, target.tableName, ds);
            }
        }

        return totalAffected;
    }

    private int executeChunk(List<T> chunk, EntityMetadata metadata, String tableName, DataSource ds) {
        if (chunk.isEmpty()) return 0;
        Dialect dialect = env.getDialect();
        OrmConfig config = env.getConfig();
        Class<T> entityClass = getEntityClass();

        // 收集参与待插入/更新的列
        List<ColumnMetadata> insertCols = new ArrayList<>();
        for (ColumnMetadata col : metadata.getColumns()) {
            if (col.isIgnored()) continue;
            // 自增主键且第一条记录为 null/0 且不在冲突列中，则跳过
            if (col.isPrimaryKey()) {
                Object val = col.getValue(chunk.get(0));
                if ((val == null || (val instanceof Number && ((Number) val).longValue() == 0))
                        && !this.conflictColumns.contains(col.getColumnName())) {
                    continue;
                }
            }
            insertCols.add(col);
        }

        // 确定 conflictColumns
        List<String> finalConflictCols = new ArrayList<>(this.conflictColumns);
        if (finalConflictCols.isEmpty()) {
            if (metadata.getIdColumn() != null) {
                finalConflictCols.add(metadata.getIdColumn().getColumnName());
            } else if (!metadata.getIndexes().isEmpty()) {
                for (com.javaorm.core.metadata.IndexMetadata idx : metadata.getIndexes()) {
                    if (idx.isUnique() && !idx.getColumns().isEmpty()) {
                        finalConflictCols.addAll(idx.getColumns());
                        break;
                    }
                }
            }
        }
        if (finalConflictCols.isEmpty()) {
            throw new IllegalStateException("UPSERT requires a primary key or explicit onConflict columns for entity: " + entityClass.getName());
        }

        // 确定 updateColumns
        List<String> finalUpdateCols = new ArrayList<>();
        if (!this.doNothing) {
            if (!this.updateColumns.isEmpty()) {
                finalUpdateCols.addAll(this.updateColumns);
            } else {
                for (ColumnMetadata col : insertCols) {
                    String colName = col.getColumnName();
                    if (finalConflictCols.contains(colName)) continue;
                    if (metadata.getIdColumn() != null && colName.equalsIgnoreCase(metadata.getIdColumn().getColumnName())) continue;
                    if (ignoreColumns.contains(colName)) continue;
                    if (isDefaultCreationAuditColumn(colName)) continue;
                    finalUpdateCols.add(colName);
                }
            }
        }

        // 构建 UpsertStatement 模板获取 SQL
        UpsertStatement templateStmt = new UpsertStatement(entityClass, tableName);
        templateStmt.setDoNothing(this.doNothing);
        templateStmt.addConflictColumns(finalConflictCols);
        templateStmt.addUpdateColumns(finalUpdateCols);
        for (ColumnMetadata col : insertCols) {
            templateStmt.addColumnValue(col.getColumnName(), "?"); // 占位
        }

        SqlAndParams sap = dialect.buildUpsert(templateStmt);
        String sql = sap.getSql();
        logSql(sql, chunk.size());

        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = ConnectionUtils.getConnection(ds, config);
            ps = conn.prepareStatement(sql);

            for (T entity : chunk) {
                prepareEntityDefaults(entity, metadata, config);

                int paramIdx = 1;
                for (ColumnMetadata col : insertCols) {
                    Object val = col.getValue(entity);
                    ps.setObject(paramIdx++, col.serializeParam(val));
                }
                ps.addBatch();
            }

            int[] results = ps.executeBatch();
            int total = 0;
            for (int r : results) {
                if (r >= 0) total += r;
                else if (r == PreparedStatement.SUCCESS_NO_INFO) total += 1;
            }
            return total;
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute batch upsert on " + tableName, e);
        } finally {
            if (ps != null) try { ps.close(); } catch (Exception ignored) {}
            ConnectionUtils.closeConnection(conn);
        }
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

    private void logSql(String sql, int count) {
        boolean shouldLog = printSqlOverride != null ? printSqlOverride : env.shouldPrintSql();
        if (shouldLog) {
            log.info("==>  Preparing Batch UPSERT ({} entities): {}", count, sql);
        }
    }
}
