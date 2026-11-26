package com.javaorm.core.batch;

import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.metadata.ColumnMetadata;
import com.javaorm.core.metadata.EntityMetadata;
import com.javaorm.core.sharding.DataSourceRouter;
import com.javaorm.core.sharding.TableRouter;
import com.javaorm.core.sql.SqlEnv;
import org.slf4j.Logger;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * 批处理构建器抽象基类（统一批大小配置、分库分表物理节点分组、数据源解析、列指纹计算与日志）。
 *
 * @param <T>    实体类型
 * @param <SELF> 具体构建器自引用类型
 */
public abstract class AbstractBatchBuilder<T, SELF extends AbstractBatchBuilder<T, SELF>> {

    protected final SqlEnv env;
    protected final Collection<T> entities;
    protected int batchSize = 500;

    protected AbstractBatchBuilder(SqlEnv env, Collection<T> entities) {
        this.env = env != null ? env : new SqlEnv(null, null, null);
        this.entities = entities;
    }

    @SuppressWarnings("unchecked")
    protected final SELF self() {
        return (SELF) this;
    }

    public SELF batchSize(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be greater than 0");
        }
        this.batchSize = batchSize;
        return self();
    }

    public int exec() {
        return execute();
    }

    public abstract int execute();

    protected Map<PhysicalTarget, List<T>> groupByPhysicalTarget(Collection<T> items, EntityMetadata metadata) {
        return groupByPhysicalTarget(items, metadata, Function.identity());
    }

    @SuppressWarnings("unchecked")
    protected <V> Map<PhysicalTarget, List<V>> groupByPhysicalTarget(Collection<T> items,
                                                                     EntityMetadata metadata,
                                                                     Function<T, V> valueExtractor) {
        Map<PhysicalTarget, List<V>> map = new LinkedHashMap<>();
        if (!metadata.isSharding()) {
            PhysicalTarget defaultTarget = new PhysicalTarget(metadata.getShardingDataSource(), metadata.getTableName());
            List<V> list = new ArrayList<>(items.size());
            for (T e : items) {
                list.add(valueExtractor.apply(e));
            }
            map.put(defaultTarget, list);
            return map;
        }

        DataSourceRouter<Object> dsRouter = (DataSourceRouter<Object>) metadata.getShardingDataSourceRouter();
        TableRouter<Object> tableRouter = (TableRouter<Object>) metadata.getShardingRouter();
        ColumnMetadata shardingCol = metadata.getShardingColumn();

        for (T entity : items) {
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
            map.computeIfAbsent(target, k -> new ArrayList<>()).add(valueExtractor.apply(entity));
        }

        return map;
    }

    protected DataSource resolveDataSource(String dsName) {
        OrmConfig config = env.getConfig();
        if (config != null && config.getDataSourceManager() != null) {
            return config.getDataSourceManager().getDataSource(dsName);
        }
        return env.getDataSource();
    }

    protected void logSql(Logger log, String actionLabel, String sql, List<Object> params) {
        if (env.shouldPrintSql()) {
            log.info("==>  Preparing {}: {}", actionLabel, sql);
            if (params != null && params.size() <= 20) {
                log.info("==> Parameters: {}", params);
            } else if (params != null) {
                log.info("==> Parameters: (total {} params)", params.size());
            }
        }
    }

    protected static String computeFingerprint(List<ColumnMetadata> cols) {
        if (cols == null || cols.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(cols.get(i).getColumnName());
        }
        return sb.toString();
    }

    protected static int sumBatchResults(int[] results) {
        int total = 0;
        for (int r : results) {
            if (r >= 0) {
                total += r;
            } else if (r == PreparedStatement.SUCCESS_NO_INFO) {
                total += 1;
            }
        }
        return total;
    }

    protected static final class PhysicalTarget {
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
            return Objects.equals(dataSourceName, that.dataSourceName) && Objects.equals(tableName, that.tableName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(dataSourceName, tableName);
        }
    }
}
