package com.javaorm.core.batch;

import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.metadata.ColumnMetadata;
import com.javaorm.core.metadata.EntityMetadata;
import com.javaorm.core.sharding.DataSourceRouter;
import com.javaorm.core.sharding.TableRouter;
import com.javaorm.core.sql.SqlEnv;
import org.slf4j.Logger;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * 批处理构建器公共组件：统一分库分表路由分组、数据源解析与 SQL 日志打印。
 */
final class BatchSupport {

    private BatchSupport() {
    }

    static <T> Map<PhysicalTarget, List<T>> groupByPhysicalTarget(Collection<T> entities, EntityMetadata metadata) {
        return groupByPhysicalTarget(entities, metadata, Function.identity());
    }

    @SuppressWarnings("unchecked")
    static <T, V> Map<PhysicalTarget, List<V>> groupByPhysicalTarget(Collection<T> entities,
                                                                     EntityMetadata metadata,
                                                                     Function<T, V> valueExtractor) {
        Map<PhysicalTarget, List<V>> map = new LinkedHashMap<>();
        if (!metadata.isSharding()) {
            PhysicalTarget defaultTarget = new PhysicalTarget(metadata.getShardingDataSource(), metadata.getTableName());
            List<V> list = new ArrayList<>(entities.size());
            for (T e : entities) {
                list.add(valueExtractor.apply(e));
            }
            map.put(defaultTarget, list);
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
            map.computeIfAbsent(target, k -> new ArrayList<>()).add(valueExtractor.apply(entity));
        }

        return map;
    }

    static DataSource resolveDataSource(SqlEnv env, String dsName) {
        OrmConfig config = env.getConfig();
        if (config != null && config.getDataSourceManager() != null) {
            return config.getDataSourceManager().getDataSource(dsName);
        }
        return env.getDataSource();
    }

    static void logSql(Logger log, SqlEnv env, String actionLabel, String sql, List<Object> params) {
        if (env.shouldPrintSql()) {
            log.info("==>  Preparing {}: {}", actionLabel, sql);
            if (params != null && params.size() <= 20) {
                log.info("==> Parameters: {}", params);
            } else if (params != null) {
                log.info("==> Parameters: (total {} params)", params.size());
            }
        }
    }

    static final class PhysicalTarget {
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
