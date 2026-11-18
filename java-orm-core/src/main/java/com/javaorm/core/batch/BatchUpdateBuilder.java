package com.javaorm.core.batch;

import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.connection.ConnectionUtils;
import com.javaorm.core.dialect.Dialect;
import com.javaorm.core.exception.OptimisticLockException;
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
import java.time.LocalDateTime;
import java.util.*;

/**
 * 实体列表批处理更新构建器。
 * 1. 采用 JDBC PreparedStatement executeBatch() 执行不同实体的批量更新。
 * 2. 支持批大小控制（batchSize，默认 500）。
 * 3. 严格支持乐观锁：如果实体含有 @JoVersion 字段，自动追加 WHERE version = ?，更新成功自动将实体对象的版本递增；若某行受影响数为 0，抛出 OptimisticLockException。
 * 4. 自动分库分表感知分组。
 */
public class BatchUpdateBuilder<T> {

    private static final Logger log = LoggerFactory.getLogger(BatchUpdateBuilder.class);

    private final SqlEnv env;
    private final Collection<T> entities;
    private int batchSize = 500;
    private boolean selective = true;

    public BatchUpdateBuilder(SqlEnv env, Collection<T> entities) {
        this.env = env != null ? env : new SqlEnv(null, null, null);
        this.entities = entities;
    }

    public BatchUpdateBuilder<T> batchSize(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be greater than 0");
        }
        this.batchSize = batchSize;
        return this;
    }

    public BatchUpdateBuilder<T> selective(boolean selective) {
        this.selective = selective;
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

        if (metadata.getIdColumn() == null) {
            throw new IllegalStateException("Entity " + entityClass.getName() + " does not have a primary key for batch update");
        }

        // 分组处理分库分表
        Map<PhysicalTarget, List<T>> groups = groupEntitiesBySharding(entities, metadata);
        int totalAffected = 0;

        for (Map.Entry<PhysicalTarget, List<T>> entry : groups.entrySet()) {
            PhysicalTarget target = entry.getKey();
            List<T> targetEntities = entry.getValue();
            DataSource ds = resolveDataSource(target.dataSourceName);

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
        ColumnMetadata idCol = metadata.getIdColumn();
        ColumnMetadata versionCol = metadata.getVersionColumn();

        // 收集更新列（排除主键和版本号，版本号单独处理）
        List<ColumnMetadata> updateCols = new ArrayList<>();
        for (ColumnMetadata col : metadata.getColumns()) {
            if (col.isPrimaryKey() || col.isIgnored() || col.isVersion()) {
                continue;
            }
            updateCols.add(col);
        }

        StringBuilder sql = new StringBuilder();
        sql.append("UPDATE ").append(dialect.quote(tableName)).append(" SET ");
        for (int i = 0; i < updateCols.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append(dialect.quote(updateCols.get(i).getColumnName())).append(" = ?");
        }
        if (versionCol != null) {
            if (!updateCols.isEmpty()) sql.append(", ");
            sql.append(dialect.quote(versionCol.getColumnName())).append(" = ?");
        }
        sql.append(" WHERE ").append(dialect.quote(idCol.getColumnName())).append(" = ?");
        if (versionCol != null) {
            sql.append(" AND ").append(dialect.quote(versionCol.getColumnName())).append(" = ?");
        }

        String finalSql = sql.toString();
        logSql(finalSql, chunk.size());

        Connection conn = null;
        PreparedStatement ps = null;
        List<Object> nextVersions = new ArrayList<>();

        try {
            conn = ConnectionUtils.getConnection(ds, config);
            ps = conn.prepareStatement(finalSql);

            for (T entity : chunk) {
                int paramIdx = 1;
                // 1. 设置普通列
                for (ColumnMetadata col : updateCols) {
                    Object val = col.getValue(entity);
                    ps.setObject(paramIdx++, val);
                }

                // 2. 设置新版本号
                Object curVersion = null;
                Object nextVersion = null;
                if (versionCol != null) {
                    curVersion = versionCol.getValue(entity);
                    if (curVersion == null) curVersion = 1;
                    nextVersion = calculateNextVersion(curVersion, versionCol.getPropertyType());
                    nextVersions.add(nextVersion);
                    ps.setObject(paramIdx++, nextVersion);
                }

                // 3. 设置主键条件
                Object idVal = idCol.getValue(entity);
                if (idVal == null) {
                    throw new IllegalArgumentException("Primary key value is null in batch update for entity: " + entity);
                }
                ps.setObject(paramIdx++, idVal);

                // 4. 设置旧版本号条件
                if (versionCol != null) {
                    ps.setObject(paramIdx++, curVersion);
                }

                ps.addBatch();
            }

            int[] results = ps.executeBatch();
            int total = 0;
            for (int row = 0; row < results.length; row++) {
                int r = results[row];
                if (versionCol != null) {
                    if (r == 0) {
                        T failedEntity = chunk.get(row);
                        Object idVal = idCol.getValue(failedEntity);
                        Object verVal = versionCol.getValue(failedEntity);
                        throw new OptimisticLockException(metadata.getEntityClass(), idVal, verVal);
                    }
                    // 回写新版本号
                    versionCol.setValue(chunk.get(row), nextVersions.get(row));
                }
                if (r >= 0) total += r;
                else if (r == PreparedStatement.SUCCESS_NO_INFO) total += 1;
            }
            return total;
        } catch (OptimisticLockException ole) {
            throw ole;
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute batch update on " + tableName, e);
        } finally {
            if (ps != null) try { ps.close(); } catch (Exception ignored) {}
            ConnectionUtils.closeConnection(conn);
        }
    }

    private Object calculateNextVersion(Object curVersion, Class<?> propType) {
        if (curVersion instanceof Number) {
            long nv = ((Number) curVersion).longValue() + 1;
            if (propType == Integer.class || propType == int.class) return (int) nv;
            if (propType == Long.class || propType == long.class) return nv;
            if (propType == Short.class || propType == short.class) return (short) nv;
            return nv;
        } else if (curVersion instanceof LocalDateTime) {
            return LocalDateTime.now();
        } else if (curVersion instanceof Date) {
            return new Date();
        }
        return 1;
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

    private void logSql(String sql, int count) {
        if (env.getConfig() != null && env.getConfig().isPrintSql()) {
            log.info("==>  Preparing Batch Update ({} entities): {}", count, sql);
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
