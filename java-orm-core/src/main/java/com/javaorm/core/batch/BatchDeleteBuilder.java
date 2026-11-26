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
public class BatchDeleteBuilder<T> extends AbstractBatchBuilder<T, BatchDeleteBuilder<T>> {

    private static final Logger log = LoggerFactory.getLogger(BatchDeleteBuilder.class);

    private final Class<T> entityClass;
    private final Collection<?> ids;

    public BatchDeleteBuilder(SqlEnv env, Class<T> entityClass, Collection<?> ids) {
        super(env, null);
        this.entityClass = entityClass;
        this.ids = ids;
    }

    public BatchDeleteBuilder(SqlEnv env, Collection<T> entities) {
        super(env, entities);
        if (entities != null && !entities.isEmpty()) {
            @SuppressWarnings("unchecked")
            Class<T> clazz = (Class<T>) entities.iterator().next().getClass();
            this.entityClass = clazz;
        } else {
            this.entityClass = null;
        }
        this.ids = null;
    }

    @Override
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
            Map<PhysicalTarget, List<Object>> groups = groupByPhysicalTarget(entities, metadata, idCol::getValue);
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
        logSql(log, "Batch Delete", finalSql, params);
        return env.getExecutionContext().getSqlExecutor().executeUpdate(
                null,
                new com.javaorm.core.dialect.SqlAndParams(finalSql, params),
                ldCol != null ? "UPDATE" : "DELETE",
                dialect,
                config,
                ds
        );
    }
}
