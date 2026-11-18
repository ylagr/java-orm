package com.javaorm.core.batch;

import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.connection.ConnectionUtils;
import com.javaorm.core.dialect.Dialect;
import com.javaorm.core.metadata.ColumnMetadata;
import com.javaorm.core.metadata.EntityMetadata;
import com.javaorm.core.metadata.EntityMetadataManager;
import com.javaorm.core.sharding.DataSourceRouter;
import com.javaorm.core.sharding.TableRouter;
import com.javaorm.core.sql.SqlEnv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.*;

/**
 * 批处理删除构建器（支持实体列表删除与 ID 列表删除）。
 * 1. 自动分批（batchSize，默认 500）。
 * 2. 逻辑删除自动兼容：若实体配置了 @JoLogicDelete，自动转为批量 UPDATE 逻辑删除。
 * 3. 分库分表感知分组。
 */
public class BatchDeleteBuilder<T> {

    private static final Logger log = LoggerFactory.getLogger(BatchDeleteBuilder.class);

    private final SqlEnv env;
    private final Class<T> entityClass;
    private final Collection<T> entities;
    private final Collection<?> ids;
    private int batchSize = 500;

    public BatchDeleteBuilder(SqlEnv env, Class<T> entityClass, Collection<?> ids) {
        this.env = env != null ? env : new SqlEnv(null, null, null);
        this.entityClass = entityClass;
        this.entities = null;
        this.ids = ids;
    }

    public BatchDeleteBuilder(SqlEnv env, Collection<T> entities) {
        this.env = env != null ? env : new SqlEnv(null, null, null);
        this.entities = entities;
        if (entities != null && !entities.isEmpty()) {
            @SuppressWarnings("unchecked")
            Class<T> clazz = (Class<T>) entities.iterator().next().getClass();
            this.entityClass = clazz;
        } else {
            this.entityClass = null;
        }
        this.ids = null;
    }

    public BatchDeleteBuilder<T> batchSize(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be greater than 0");
        }
        this.batchSize = batchSize;
        return this;
    }

    public int execute() {
        if (entityClass == null) return 0;
        EntityMetadata metadata = EntityMetadataManager.get(entityClass, env.getConfig());
        ColumnMetadata idCol = metadata.getIdColumn();
        if (idCol == null) {
            throw new IllegalStateException("Entity " + entityClass.getName() + " does not have a primary key for batch delete");
        }

        // 1. 如果传入的是实体列表
        if (entities != null) {
            if (entities.isEmpty()) return 0;
            Map<PhysicalTarget, List<Object>> groups = groupEntitiesToIdList(entities, metadata, idCol);
            int total = 0;
            for (Map.Entry<PhysicalTarget, List<Object>> entry : groups.entrySet()) {
                PhysicalTarget target = entry.getKey();
                List<Object> idList = entry.getValue();
                DataSource ds = resolveDataSource(target.dataSourceName);
                total += deleteIdChunks(idList, metadata, target.tableName, ds);
            }
            return total;
        }

        // 2. 如果传入的是 ID 集合
        if (ids != null) {
            if (ids.isEmpty()) return 0;
            List<Object> idList = new ArrayList<>(ids);
            // 非分片实体直接单表删除；分片实体且未指定具体分片键时广播全分片或默认库表
            DataSource ds = resolveDataSource(metadata.getShardingDataSource());
            return deleteIdChunks(idList, metadata, metadata.getTableName(), ds);
        }

        return 0;
    }

    private int deleteIdChunks(List<Object> idList, EntityMetadata metadata, String tableName, DataSource ds) {
        int total = 0;
        for (int i = 0; i < idList.size(); i += batchSize) {
            int end = Math.min(i + batchSize, idList.size());
            List<Object> chunk = idList.subList(i, end);
            total += executeChunk(chunk, metadata, tableName, ds);
        }
        return total;
    }

    private int executeChunk(List<Object> chunkIds, EntityMetadata metadata, String tableName, DataSource ds) {
        if (chunkIds.isEmpty()) return 0;
        Dialect dialect = env.getDialect();
        OrmConfig config = env.getConfig();
        ColumnMetadata idCol = metadata.getIdColumn();
        ColumnMetadata ldCol = metadata.getLogicDeleteColumn();

        StringBuilder sql = new StringBuilder();
        List<Object> params = new ArrayList<>();

        if (ldCol != null) {
            // 逻辑删除
            sql.append("UPDATE ").append(dialect.quote(tableName))
                    .append(" SET ").append(dialect.quote(ldCol.getColumnName())).append(" = ?")
                    .append(" WHERE ").append(dialect.quote(idCol.getColumnName())).append(" IN (");
            params.add(ldCol.getLogicDeleteDeletedValue());
            for (int i = 0; i < chunkIds.size(); i++) {
                if (i > 0) sql.append(", ");
                sql.append("?");
                params.add(chunkIds.get(i));
            }
            sql.append(")");
            // 系统过滤正常记录
            sql.append(" AND ").append(dialect.quote(ldCol.getColumnName())).append(" = ?");
            params.add(ldCol.getLogicDeleteNormalValue());
        } else {
            // 物理删除
            sql.append("DELETE FROM ").append(dialect.quote(tableName))
                    .append(" WHERE ").append(dialect.quote(idCol.getColumnName())).append(" IN (");
            for (int i = 0; i < chunkIds.size(); i++) {
                if (i > 0) sql.append(", ");
                sql.append("?");
                params.add(chunkIds.get(i));
            }
            sql.append(")");
        }

        if (metadata.hasTenant()) {
            ColumnMetadata tc = metadata.getTenantColumn();
            Object tenantId = config != null ? config.getCurrentTenantId() : com.javaorm.core.tenant.TenantContext.getTenantId();
            if (tenantId != null) {
                sql.append(" AND ").append(dialect.quote(tc.getColumnName())).append(" = ?");
                params.add(tenantId);
            }
        }

        String finalSql = sql.toString();
        logSql(finalSql, params);

        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = ConnectionUtils.getConnection(ds, config);
            ps = conn.prepareStatement(finalSql);
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            return ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute batch delete on " + tableName, e);
        } finally {
            if (ps != null) try { ps.close(); } catch (Exception ignored) {}
            ConnectionUtils.closeConnection(conn);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<PhysicalTarget, List<Object>> groupEntitiesToIdList(Collection<T> entities, EntityMetadata metadata, ColumnMetadata idCol) {
        Map<PhysicalTarget, List<Object>> map = new LinkedHashMap<>();
        if (!metadata.isSharding()) {
            PhysicalTarget defaultTarget = new PhysicalTarget(metadata.getShardingDataSource(), metadata.getTableName());
            List<Object> idList = new ArrayList<>(entities.size());
            for (T e : entities) {
                idList.add(idCol.getValue(e));
            }
            map.put(defaultTarget, idList);
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
            map.computeIfAbsent(target, k -> new ArrayList<>()).add(idCol.getValue(entity));
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
            log.info("==>  Preparing Batch Delete: {}", sql);
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
