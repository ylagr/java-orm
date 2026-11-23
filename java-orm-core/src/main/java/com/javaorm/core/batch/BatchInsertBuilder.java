package com.javaorm.core.batch;

import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.connection.ConnectionUtils;
import com.javaorm.core.dialect.Dialect;
import com.javaorm.core.metadata.ColumnMetadata;
import com.javaorm.core.metadata.EntityMetadata;
import com.javaorm.core.metadata.EntityMetadataManager;
import com.javaorm.core.sharding.DataSourceRouter;
import com.javaorm.core.sharding.ShardingHandler;
import com.javaorm.core.sharding.TableRouter;
import com.javaorm.core.sql.SqlEnv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 高性能批处理插入构建器（参考 EasyQuery 设计哲学）。
 * 支持：
 * 1. 分批执行（batchSize，默认 500），防止参数超量或打爆连接池。
 * 2. 多值批量 INSERT（INSERT INTO t VALUES (...), (...)）与 JDBC addBatch() 双模式支持。
 * 3. 自动填充租户、逻辑删除正常值、乐观锁版本号（1）、审计填充。
 * 4. EasyQuery 智能批处理模式：自动按非空字段指纹（Column Fingerprint）归类合并，剔除 null 字段，确保数据库 DEFAULT 默认值生效。
 * 5. 严格保持顺序插入选项（keepOrder / strictOrder）：在按原始顺序处理的同时剔除 null 字段，相邻相同字段结构的记录自动批处理。
 * 6. 分库分表感知：自动按照计算出的物理数据源与物理表进行分组（Grouping），并在各物理节点上独立批量执行。
 */
public class BatchInsertBuilder<T> {

    private static final Logger log = LoggerFactory.getLogger(BatchInsertBuilder.class);

    private final SqlEnv env;
    private final Collection<T> entities;
    private int batchSize = 500;
    private boolean useJdbcBatch = true;
    private boolean selective = true;
    private boolean keepOrder = false;

    public BatchInsertBuilder(SqlEnv env, Collection<T> entities) {
        this.env = env != null ? env : new SqlEnv(null, null, null);
        this.entities = entities;
    }

    public BatchInsertBuilder<T> batchSize(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be greater than 0");
        }
        this.batchSize = batchSize;
        return this;
    }

    /**
     * 是否使用 JDBC Batch 模式（默认为 true）。
     * 采用原生 PreparedStatement.addBatch() 与 executeBatch()，与 updateBatch/deleteBatch 保持一致的标准批处理。
     */
    public BatchInsertBuilder<T> useJdbcBatch(boolean useJdbcBatch) {
        this.useJdbcBatch = useJdbcBatch;
        return this;
    }

    /**
     * 是否使用单语句多值模式（INSERT INTO t VALUES (...), (...)，默认为 false）。
     */
    public BatchInsertBuilder<T> useMultiValues(boolean useMultiValues) {
        this.useJdbcBatch = !useMultiValues;
        return this;
    }

    /**
     * 是否剔除 null 字段（默认为 true）。
     * 剔除 null 字段可确保数据库定义的 DEFAULT 默认值正常生效，避免显式插入 NULL 覆盖默认值。
     */
    public BatchInsertBuilder<T> selective(boolean selective) {
        this.selective = selective;
        return this;
    }

    /**
     * 是否严格保持集合原始顺序插入（默认为 false，即默认使用 EasyQuery 智能聚类合并模式）。
     * 若开启 keepOrder(true)，将严格遵从原始入参顺序，连续相同字段结构的实体自动批量合并，不同字段结构自动分批切断。
     */
    public BatchInsertBuilder<T> keepOrder(boolean keepOrder) {
        this.keepOrder = keepOrder;
        return this;
    }

    /**
     * 严格保持顺序插入选项的同义别名方法。
     */
    public BatchInsertBuilder<T> strictOrder(boolean strictOrder) {
        return keepOrder(strictOrder);
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

        // 1. 分组：若启用分库/分表，按 (目标数据源, 物理表) 进行实体分组
        Map<PhysicalTarget, List<T>> groups = groupEntitiesBySharding(entities, metadata);

        int totalAffected = 0;
        for (Map.Entry<PhysicalTarget, List<T>> entry : groups.entrySet()) {
            PhysicalTarget target = entry.getKey();
            List<T> targetEntities = entry.getValue();

            DataSource ds = resolveDataSource(target.dataSourceName);
            totalAffected += executeTargetEntities(targetEntities, metadata, target.tableName, ds);
        }

        return totalAffected;
    }

    private int executeTargetEntities(List<T> targetEntities, EntityMetadata metadata, String tableName, DataSource ds) {
        if (targetEntities.isEmpty()) return 0;
        OrmConfig config = env.getConfig();

        // 1. 预处理默认属性与自动填充（租户、版本号、逻辑删除、AutoFillHandler 等）
        for (T entity : targetEntities) {
            if (config != null && config.getAutoFillHandler() != null) {
                config.getAutoFillHandler().insertFill(entity, metadata);
            }
            prepareEntityDefaults(entity, metadata, config);
        }

        int totalAffected = 0;

        if (!keepOrder) {
            // ==================== Mode A: EasyQuery 智能批处理模式 ====================
            // 根据每个实体的有效非空列特征指纹（Fingerprint）聚类分组，合并相同结构的 SQL，最大化发挥批处理性能
            Map<String, List<T>> fingerprintGroups = new LinkedHashMap<>();
            Map<String, List<ColumnMetadata>> fingerprintCols = new HashMap<>();

            for (T entity : targetEntities) {
                List<ColumnMetadata> cols = getInsertColumns(entity, metadata, selective);
                String fp = computeFingerprint(cols);
                fingerprintGroups.computeIfAbsent(fp, k -> new ArrayList<>()).add(entity);
                fingerprintCols.putIfAbsent(fp, cols);
            }

            for (Map.Entry<String, List<T>> entry : fingerprintGroups.entrySet()) {
                List<T> groupList = entry.getValue();
                List<ColumnMetadata> cols = fingerprintCols.get(entry.getKey());
                for (int i = 0; i < groupList.size(); i += batchSize) {
                    int end = Math.min(i + batchSize, groupList.size());
                    List<T> chunk = groupList.subList(i, end);
                    totalAffected += executeChunk(chunk, metadata, tableName, ds, cols);
                }
            }
        } else {
            // ==================== Mode B: 严格保持顺序插入模式 ====================
            // 严格遵从原始顺序，连续相同字段结构的实体合并为一个批次，字段结构变化时自动切批执行
            List<T> currentBatch = new ArrayList<>();
            String currentFp = null;
            List<ColumnMetadata> currentCols = null;

            for (T entity : targetEntities) {
                List<ColumnMetadata> cols = getInsertColumns(entity, metadata, selective);
                String fp = computeFingerprint(cols);

                if (currentBatch.isEmpty()) {
                    currentBatch.add(entity);
                    currentFp = fp;
                    currentCols = cols;
                } else if (currentFp.equals(fp) && currentBatch.size() < batchSize) {
                    currentBatch.add(entity);
                } else {
                    totalAffected += executeChunk(currentBatch, metadata, tableName, ds, currentCols);
                    currentBatch = new ArrayList<>();
                    currentBatch.add(entity);
                    currentFp = fp;
                    currentCols = cols;
                }
            }
            if (!currentBatch.isEmpty()) {
                totalAffected += executeChunk(currentBatch, metadata, tableName, ds, currentCols);
            }
        }

        return totalAffected;
    }

    private List<ColumnMetadata> getInsertColumns(T entity, EntityMetadata metadata, boolean selective) {
        List<ColumnMetadata> cols = new ArrayList<>();
        for (ColumnMetadata col : metadata.getColumns()) {
            if (col.isIgnored()) continue;
            // 如果是自增主键且未显式指定值，跳过由数据库生成
            if (col.isPrimaryKey() && col.isGenerated()) {
                Object val = col.getValue(entity);
                if (val == null || (val instanceof Number && ((Number) val).longValue() == 0)) {
                    continue;
                }
            }
            // 剔除 null 字段，确保数据库默认值生效
            if (selective) {
                Object val = col.getValue(entity);
                if (val == null) {
                    continue;
                }
            }
            cols.add(col);
        }
        return cols;
    }

    private String computeFingerprint(List<ColumnMetadata> cols) {
        if (cols == null || cols.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(cols.get(i).getColumnName());
        }
        return sb.toString();
    }

    private int executeChunk(List<T> chunk, EntityMetadata metadata, String tableName, DataSource ds, List<ColumnMetadata> insertCols) {
        if (chunk.isEmpty()) return 0;
        Dialect dialect = env.getDialect();
        OrmConfig config = env.getConfig();

        if (useJdbcBatch) {
            return executeWithJdbcBatch(chunk, metadata, tableName, insertCols, ds, dialect, config);
        } else {
            return executeWithMultiValues(chunk, metadata, tableName, insertCols, ds, dialect, config);
        }
    }

    private int executeWithMultiValues(List<T> chunk, EntityMetadata metadata, String tableName,
                                       List<ColumnMetadata> insertCols, DataSource ds, Dialect dialect, OrmConfig config) {
        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ").append(dialect.quote(tableName));

        List<Object> params = new ArrayList<>();
        if (insertCols.isEmpty()) {
            // 全空或全由数据库默认值/自增主键填充
            if (dialect instanceof com.javaorm.core.dialect.PostgreSQLDialect
                    || dialect instanceof com.javaorm.core.dialect.SQLiteDialect
                    || dialect instanceof com.javaorm.core.dialect.OracleDialect
                    || dialect instanceof com.javaorm.core.dialect.Oracle11Dialect) {
                sql.append(" DEFAULT VALUES");
            } else {
                sql.append(" () VALUES ()");
            }
        } else {
            sql.append(" (");
            for (int i = 0; i < insertCols.size(); i++) {
                if (i > 0) sql.append(", ");
                sql.append(dialect.quote(insertCols.get(i).getColumnName()));
            }
            sql.append(") VALUES ");

            for (int row = 0; row < chunk.size(); row++) {
                if (row > 0) sql.append(", ");
                sql.append("(");
                T entity = chunk.get(row);
                for (int colIdx = 0; colIdx < insertCols.size(); colIdx++) {
                    if (colIdx > 0) sql.append(", ");
                    sql.append("?");
                    ColumnMetadata col = insertCols.get(colIdx);
                    Object val = col.getValue(entity);
                    params.add(val);
                }
                sql.append(")");
            }
        }

        String finalSql = sql.toString();
        logSql(finalSql, params);

        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = ConnectionUtils.getConnection(ds, config);
            boolean returnKeys = metadata.getIdColumn() != null && metadata.getIdColumn().isGenerated();
            try {
                if (returnKeys) {
                    ps = conn.prepareStatement(finalSql, java.sql.Statement.RETURN_GENERATED_KEYS);
                } else {
                    ps = conn.prepareStatement(finalSql);
                }
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                int rows = ps.executeUpdate();
                if (returnKeys) {
                    try (java.sql.ResultSet rs = ps.getGeneratedKeys()) {
                        List<Object> keys = new ArrayList<>();
                        while (rs != null && rs.next()) {
                            keys.add(rs.getObject(1));
                        }
                        if (keys.size() == chunk.size()) {
                            for (int i = 0; i < chunk.size(); i++) {
                                T entity = chunk.get(i);
                                if (metadata.getIdColumn().getValue(entity) == null) {
                                    metadata.getIdColumn().setValue(entity, keys.get(i));
                                }
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
                return rows;
            } catch (Exception ex) {
                if (returnKeys) {
                    if (ps != null) {
                        try { ps.close(); } catch (Exception ignored) {}
                    }
                    ps = conn.prepareStatement(finalSql);
                    for (int i = 0; i < params.size(); i++) {
                        ps.setObject(i + 1, params.get(i));
                    }
                    return ps.executeUpdate();
                }
                throw ex;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute batch multi-values insert into " + tableName, e);
        } finally {
            if (ps != null) try { ps.close(); } catch (Exception ignored) {}
            ConnectionUtils.closeConnection(conn);
        }
    }

    private int executeWithJdbcBatch(List<T> chunk, EntityMetadata metadata, String tableName,
                                     List<ColumnMetadata> insertCols, DataSource ds, Dialect dialect, OrmConfig config) {
        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ").append(dialect.quote(tableName));

        if (insertCols.isEmpty()) {
            if (dialect instanceof com.javaorm.core.dialect.PostgreSQLDialect
                    || dialect instanceof com.javaorm.core.dialect.SQLiteDialect
                    || dialect instanceof com.javaorm.core.dialect.OracleDialect
                    || dialect instanceof com.javaorm.core.dialect.Oracle11Dialect) {
                sql.append(" DEFAULT VALUES");
            } else {
                sql.append(" () VALUES ()");
            }
        } else {
            sql.append(" (");
            for (int i = 0; i < insertCols.size(); i++) {
                if (i > 0) sql.append(", ");
                sql.append(dialect.quote(insertCols.get(i).getColumnName()));
            }
            sql.append(") VALUES (");
            for (int i = 0; i < insertCols.size(); i++) {
                if (i > 0) sql.append(", ");
                sql.append("?");
            }
            sql.append(")");
        }

        String finalSql = sql.toString();
        logSql(finalSql, Collections.singletonList("batch size: " + chunk.size()));

        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = ConnectionUtils.getConnection(ds, config);
            boolean returnKeys = metadata.getIdColumn() != null && metadata.getIdColumn().isGenerated();
            if (returnKeys) {
                try {
                    ps = conn.prepareStatement(finalSql, java.sql.Statement.RETURN_GENERATED_KEYS);
                } catch (Exception ex) {
                    returnKeys = false;
                    ps = conn.prepareStatement(finalSql);
                }
            } else {
                ps = conn.prepareStatement(finalSql);
            }

            for (T entity : chunk) {
                if (!insertCols.isEmpty()) {
                    for (int colIdx = 0; colIdx < insertCols.size(); colIdx++) {
                        ColumnMetadata col = insertCols.get(colIdx);
                        Object val = col.getValue(entity);
                        ps.setObject(colIdx + 1, val);
                    }
                }
                ps.addBatch();
            }
            int[] results = ps.executeBatch();
            int sum = 0;
            for (int r : results) {
                if (r >= 0) sum += r;
                else if (r == PreparedStatement.SUCCESS_NO_INFO) sum += 1;
            }

            if (returnKeys) {
                try (java.sql.ResultSet rs = ps.getGeneratedKeys()) {
                    int idx = 0;
                    while (rs != null && rs.next() && idx < chunk.size()) {
                        T entity = chunk.get(idx++);
                        if (metadata.getIdColumn().getValue(entity) == null) {
                            metadata.getIdColumn().setValue(entity, rs.getObject(1));
                        }
                    }
                } catch (Exception ignored) {}
            }

            return sum;
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute JDBC batch insert into " + tableName, e);
        } finally {
            if (ps != null) try { ps.close(); } catch (Exception ignored) {}
            ConnectionUtils.closeConnection(conn);
        }
    }

    private void prepareEntityDefaults(T entity, EntityMetadata metadata, OrmConfig config) {
        for (ColumnMetadata col : metadata.getColumns()) {
            if (col.isIgnored()) continue;

            // 逻辑删除默认正常值
            if (col.isLogicDelete() && col.getValue(entity) == null) {
                col.setValue(entity, col.getLogicDeleteNormalValue());
            }

            // 多租户默认值
            if (col.isTenant() && col.getValue(entity) == null) {
                Object tenantId = config != null ? config.getCurrentTenantId() : com.javaorm.core.tenant.TenantContext.getTenantId();
                if (tenantId != null) {
                    col.setValue(entity, tenantId);
                }
            }

            // 乐观锁版本号默认值 (1)
            if (col.isVersion() && col.getValue(entity) == null) {
                Class<?> propType = col.getPropertyType();
                if (propType == Integer.class || propType == int.class) {
                    col.setValue(entity, 1);
                } else if (propType == Long.class || propType == long.class) {
                    col.setValue(entity, 1L);
                } else if (propType == Short.class || propType == short.class) {
                    col.setValue(entity, (short) 1);
                } else if (propType == LocalDateTime.class) {
                    col.setValue(entity, LocalDateTime.now());
                } else if (propType == Date.class) {
                    col.setValue(entity, new Date());
                } else {
                    col.setValue(entity, 1);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<PhysicalTarget, List<T>> groupEntitiesBySharding(Collection<T> entities, EntityMetadata metadata) {
        Map<PhysicalTarget, List<T>> map = new LinkedHashMap<>();
        if (!metadata.isSharding()) {
            PhysicalTarget defaultTarget = new PhysicalTarget(metadata.getShardingDataSource(), metadata.getTableName());
            map.put(defaultTarget, new ArrayList<>(entities));
            return map;
        }

        DataSourceRouter<Object> dsRouter = (DataSourceRouter<Object>) metadata.getShardingDataSourceRouter();
        TableRouter<Object> tableRouter = (TableRouter<Object>) metadata.getShardingRouter();
        ColumnMetadata shardingCol = metadata.getShardingColumn();

        for (T entity : entities) {
            Object shardingVal = shardingCol != null ? shardingCol.getValue(entity) : null;
            String dsName = metadata.getShardingDataSource();
            if (dsRouter != null) {
                if (shardingVal == null) {
                    throw new IllegalArgumentException("Sharding key value cannot be null for entity: " + entity);
                }
                dsName = dsRouter.route(shardingVal);
            }

            String tableName = metadata.getTableName();
            if (tableRouter != null) {
                if (shardingVal == null) {
                    throw new IllegalArgumentException("Sharding key value cannot be null for entity: " + entity);
                }
                tableName = tableRouter.route(metadata.getTableName(), shardingVal);
            }

            PhysicalTarget target = new PhysicalTarget(dsName, tableName);
            map.computeIfAbsent(target, k -> new ArrayList<>()).add(entity);
        }

        return map;
    }

    private DataSource resolveDataSource(String dsName) {
        OrmConfig config = env.getConfig();
        if (config != null && config.getDataSourceManager() != null) {
            return config.getDataSourceManager().getDataSource(dsName);
        }
        return env.getDataSource();
    }

    private void logSql(String sql, List<Object> params) {
        if (env.getConfig() != null && env.getConfig().isPrintSql()) {
            log.info("==>  Preparing Batch Insert: {}", sql);
            if (params != null && params.size() <= 20) {
                log.info("==> Parameters: {}", params);
            } else if (params != null) {
                log.info("==> Parameters: (total {} params)", params.size());
            }
        }
    }

    private static class PhysicalTarget {
        final String dataSourceName;
        final String tableName;

        PhysicalTarget(String dataSourceName, String tableName) {
            this.dataSourceName = dataSourceName;
            this.tableName = tableName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PhysicalTarget that = (PhysicalTarget) o;
            return Objects.equals(dataSourceName, that.dataSourceName) &&
                    Objects.equals(tableName, that.tableName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(dataSourceName, tableName);
        }
    }
}
