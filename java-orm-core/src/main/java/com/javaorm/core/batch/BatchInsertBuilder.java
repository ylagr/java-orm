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
 * 3. 自动填充租户、逻辑删除正常值、乐观锁版本号（1）、创建时间。
 * 4. 分库分表感知：自动按照计算出的物理数据源与物理表进行分组（Grouping），并在各物理节点上独立批量执行。
 */
public class BatchInsertBuilder<T> {

    private static final Logger log = LoggerFactory.getLogger(BatchInsertBuilder.class);

    private final SqlEnv env;
    private final Collection<T> entities;
    private int batchSize = 500;
    private boolean useJdbcBatch = false;

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

    public BatchInsertBuilder<T> useJdbcBatch(boolean useJdbcBatch) {
        this.useJdbcBatch = useJdbcBatch;
        return this;
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
            // 分批执行
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

        // 收集待插入的列列表
        List<ColumnMetadata> insertCols = new ArrayList<>();
        for (ColumnMetadata col : metadata.getColumns()) {
            if (col.isIgnored()) continue;
            // 如果是自增主键且第一条记录为 null/0，则跳过主键由数据库生成
            if (col.isPrimaryKey()) {
                Object val = col.getValue(chunk.get(0));
                if (val == null || (val instanceof Number && ((Number) val).longValue() == 0)) {
                    continue;
                }
            }
            insertCols.add(col);
        }

        // 填充每个实体的默认属性（租户、版本号、逻辑删除、时间戳等）
        for (T entity : chunk) {
            prepareEntityDefaults(entity, metadata, config);
        }

        if (useJdbcBatch) {
            return executeWithJdbcBatch(chunk, metadata, tableName, insertCols, ds, dialect, config);
        } else {
            return executeWithMultiValues(chunk, metadata, tableName, insertCols, ds, dialect, config);
        }
    }

    private int executeWithMultiValues(List<T> chunk, EntityMetadata metadata, String tableName,
                                       List<ColumnMetadata> insertCols, DataSource ds, Dialect dialect, OrmConfig config) {
        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ").append(dialect.quote(tableName)).append(" (");
        for (int i = 0; i < insertCols.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append(dialect.quote(insertCols.get(i).getColumnName()));
        }
        sql.append(") VALUES ");

        List<Object> params = new ArrayList<>();
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
        sql.append("INSERT INTO ").append(dialect.quote(tableName)).append(" (");
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

        String finalSql = sql.toString();
        logSql(finalSql, Collections.singletonList("batch size: " + chunk.size()));

        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = ConnectionUtils.getConnection(ds, config);
            ps = conn.prepareStatement(finalSql);
            for (T entity : chunk) {
                for (int colIdx = 0; colIdx < insertCols.size(); colIdx++) {
                    ColumnMetadata col = insertCols.get(colIdx);
                    Object val = col.getValue(entity);
                    ps.setObject(colIdx + 1, val);
                }
                ps.addBatch();
            }
            int[] results = ps.executeBatch();
            int sum = 0;
            for (int r : results) {
                if (r >= 0) sum += r;
                else if (r == PreparedStatement.SUCCESS_NO_INFO) sum += 1;
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

            // 日期/时间戳自动填充
            if (col.getValue(entity) == null) {
                if (col.getPropertyType() == LocalDateTime.class) {
                    col.setValue(entity, LocalDateTime.now());
                } else if (col.getPropertyType() == Date.class) {
                    col.setValue(entity, new Date());
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
