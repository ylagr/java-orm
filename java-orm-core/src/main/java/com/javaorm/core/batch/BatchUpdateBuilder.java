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
 * 实体列表批处理更新构建器（参考 EasyQuery 设计哲学）。
 * 支持：
 * 1. 采用 JDBC PreparedStatement executeBatch() 执行不同实体的批量更新。
 * 2. 批大小控制（batchSize，默认 500）。
 * 3. 乐观锁集成：如果实体含有 @JoVersion 字段，自动追加 WHERE version = ?，更新成功自动将实体对象的版本递增；若某行受影响数为 0，抛出 OptimisticLockException。
 * 4. EasyQuery 智能批处理模式：按待更新有效字段指纹（Column Fingerprint）聚类分桶，自动合并相同更新结构的实体走同一个批次，剔除 null/未修改字段。
 * 5. 严格保持顺序更新选项（keepOrder）：在按原始顺序处理的同时剔除 null 字段，相邻相同字段结构的记录自动批处理。
 * 6. 实体快照变脏检测（Dirty Checking）联动：若实体被 TrackManager 追踪，仅更新脏字段。
 * 7. 分库分表感知分组。
 */
public class BatchUpdateBuilder<T> {

    private static final Logger log = LoggerFactory.getLogger(BatchUpdateBuilder.class);

    private final SqlEnv env;
    private final Collection<T> entities;
    private int batchSize = 500;
    private boolean selective = true;
    private boolean keepOrder = false;

    public BatchUpdateBuilder(SqlEnv env, Collection<T> entities) {
        this.env = env != null ? env : new SqlEnv(null, null, null);
        this.entities = entities;
        if (this.env.getConfig() != null) {
            this.keepOrder = this.env.getConfig().isBatchKeepOrder();
        }
    }

    public BatchUpdateBuilder<T> batchSize(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be greater than 0");
        }
        this.batchSize = batchSize;
        return this;
    }

    /**
     * 是否剔除 null / 未修改字段进行更新（默认为 true）。
     * 若为 false，将无差别全量更新实体定义的所有字段（除主键和版本号）。
     */
    public BatchUpdateBuilder<T> selective(boolean selective) {
        this.selective = selective;
        return this;
    }

    /**
     * 开启严格保持集合原始顺序更新（默认为 false，即默认使用 EasyQuery 智能聚类合并模式）。
     * 开启后将严格遵从原始入参顺序，连续相同字段结构的实体自动批量合并，不同字段结构自动分批切断。
     */
    public BatchUpdateBuilder<T> keepOrder() {
        this.keepOrder = true;
        return this;
    }

    public BatchUpdateBuilder<T> keepOrder(boolean keepOrder) {
        this.keepOrder = keepOrder;
        return this;
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

        if (metadata.getIdColumn() == null) {
            throw new IllegalStateException("Entity " + entityClass.getName() + " does not have a primary key for batch update");
        }

        // 分组处理分库分表
        Map<BatchSupport.PhysicalTarget, List<T>> groups = BatchSupport.groupByPhysicalTarget(entities, metadata);
        int totalAffected = 0;

        for (Map.Entry<BatchSupport.PhysicalTarget, List<T>> entry : groups.entrySet()) {
            BatchSupport.PhysicalTarget target = entry.getKey();
            List<T> targetEntities = entry.getValue();
            DataSource ds = BatchSupport.resolveDataSource(env, target.dataSourceName);
            totalAffected += executeTargetEntities(targetEntities, metadata, target.tableName, ds);
        }

        return totalAffected;
    }

    private int executeTargetEntities(List<T> targetEntities, EntityMetadata metadata, String tableName, DataSource ds) {
        if (targetEntities.isEmpty()) return 0;
        OrmConfig config = env.getConfig();

        // 1. 触发审计更新填充
        for (T entity : targetEntities) {
            if (config != null && config.getAutoFillHandler() != null) {
                config.getAutoFillHandler().updateFill(entity, metadata);
            }
        }

        int totalAffected = 0;

        if (!keepOrder) {
            // ==================== Mode A: EasyQuery 智能批处理模式 ====================
            // 根据每个实体的有效更新列特征指纹（Fingerprint）聚类分组，合并相同结构的 SQL，最大化发挥批处理性能
            Map<String, List<T>> fingerprintGroups = new LinkedHashMap<>();
            Map<String, List<ColumnMetadata>> fingerprintCols = new HashMap<>();

            for (T entity : targetEntities) {
                List<ColumnMetadata> cols = getUpdateColumns(entity, metadata, selective);
                if (cols.isEmpty() && metadata.getVersionColumn() == null) {
                    // 无任何待更新字段且无乐观锁版本号，跳过
                    continue;
                }
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
            // ==================== Mode B: 严格保持顺序更新模式 ====================
            // 严格遵从原始顺序，连续相同字段结构的实体合并为一个批次，字段结构变化时自动切批执行
            List<T> currentBatch = new ArrayList<>();
            String currentFp = null;
            List<ColumnMetadata> currentCols = null;

            for (T entity : targetEntities) {
                List<ColumnMetadata> cols = getUpdateColumns(entity, metadata, selective);
                if (cols.isEmpty() && metadata.getVersionColumn() == null) {
                    continue;
                }
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

    private List<ColumnMetadata> getUpdateColumns(T entity, EntityMetadata metadata, boolean selective) {
        com.javaorm.core.track.TrackManager trackManager = env.getConfig() != null ? env.getConfig().getTrackManager() : null;
        com.javaorm.core.track.EntitySnapshot snapshot = trackManager != null ? trackManager.getSnapshot(entity) : null;
        java.util.Set<String> dirtyColumns = snapshot != null ? snapshot.getDirtyColumnNames() : null;

        List<ColumnMetadata> cols = new ArrayList<>();
        for (ColumnMetadata col : metadata.getColumns()) {
            if (col.isPrimaryKey() || col.isIgnored() || col.isVersion()) {
                continue;
            }
            // 实体快照变脏检测联动
            if (snapshot != null) {
                if (dirtyColumns != null && dirtyColumns.contains(col.getColumnName())) {
                    cols.add(col);
                }
                continue;
            }
            // 剔除 null 字段
            if (selective) {
                if (col.getValue(entity) != null) {
                    cols.add(col);
                }
            } else {
                cols.add(col);
            }
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

    private int executeChunk(List<T> chunk, EntityMetadata metadata, String tableName, DataSource ds, List<ColumnMetadata> updateCols) {
        if (chunk.isEmpty()) return 0;
        Dialect dialect = env.getDialect();
        OrmConfig config = env.getConfig();
        ColumnMetadata idCol = metadata.getIdColumn();
        ColumnMetadata versionCol = metadata.getVersionColumn();

        if (updateCols.isEmpty() && versionCol == null) {
            return 0;
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

        List<List<Object>> batchParams = new ArrayList<>(chunk.size());
        List<Object> nextVersions = new ArrayList<>(chunk.size());

        for (T entity : chunk) {
            List<Object> rowParams = new ArrayList<>();
            for (ColumnMetadata col : updateCols) {
                rowParams.add(col.getValue(entity));
            }

            Object curVersion = null;
            if (versionCol != null) {
                curVersion = versionCol.getValue(entity);
                if (curVersion == null) curVersion = 1;
                Object nextVersion = calculateNextVersion(curVersion, versionCol.getPropertyType());
                nextVersions.add(nextVersion);
                rowParams.add(nextVersion);
            }

            Object idVal = idCol.getValue(entity);
            if (idVal == null) {
                throw new IllegalArgumentException("Primary key value is null in batch update for entity: " + entity);
            }
            rowParams.add(idVal);

            if (versionCol != null) {
                rowParams.add(curVersion);
            }
            batchParams.add(rowParams);
        }

        int[] results = env.getExecutionContext().getSqlExecutor().executeBatchUpdate(null, finalSql, batchParams, dialect, config, ds);
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
                versionCol.setValue(chunk.get(row), nextVersions.get(row));
            }
            if (r >= 0) total += r;
            else if (r == PreparedStatement.SUCCESS_NO_INFO) total += 1;
        }
        return total;
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

    private void logSql(String sql, int count) {
        if (env.shouldPrintSql()) {
            log.info("==>  Preparing Batch Update ({} entities): {}", count, sql);
        }
    }
}
