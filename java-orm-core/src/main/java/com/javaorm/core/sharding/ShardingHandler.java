package com.javaorm.core.sharding;

import com.javaorm.core.ast.*;
import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.datasource.DataSourceContext;
import com.javaorm.core.dialect.Dialect;
import com.javaorm.core.dialect.SqlAndParams;
import com.javaorm.core.executor.SqlExecutor;
import com.javaorm.core.metadata.ColumnMetadata;
import com.javaorm.core.metadata.EntityMetadata;
import com.javaorm.core.operator.SqlOperator;
import com.javaorm.core.proxy.PropertyPath;
import com.javaorm.core.query.IncludeSpec;

import javax.sql.DataSource;
import java.lang.reflect.Array;
import java.sql.Connection;
import java.util.*;

/**
 * 分片处理器（ShardingHandler）。
 * 位于执行器（SqlExecutor）之前，负责统一处理：
 * 1. 分库路由（静态切库、动态分库计算目标 DataSource）
 * 2. 分表路由（动态分表计算物理 TableName 并改写 AST）
 * 3. 全库全表扫描安全防护
 * 4. 跨库、跨表物理执行分发与内存结果归并
 *
 * 执行完成后将物理表维度的执行委派给具体的 SqlExecutor（如 DefaultJdbcExecutor、MyBatis 等）。
 */
public class ShardingHandler {

    /**
     * 判断当前查询是否需要分库/分表处理
     */
    public static boolean isSharding(SelectStatement statement, EntityMetadata metadata) {
        if (statement != null && (statement.getShardingRouter() != null
                || statement.getDataSourceRouter() != null
                || statement.getTargetDataSource() != null)) {
            return true;
        }
        return metadata != null && metadata.isSharding();
    }

    /**
     * 判断更新/删除操作是否需要分片处理
     */
    public static boolean isSharding(EntityMetadata metadata, TableRouter<?> tableRouterOverride,
                                     DataSourceRouter<?> dsRouterOverride, String dsOverride) {
        if (tableRouterOverride != null || dsRouterOverride != null || (dsOverride != null && !dsOverride.isEmpty())) {
            return true;
        }
        return metadata != null && metadata.isSharding();
    }

    public static boolean isSharding(EntityMetadata metadata, TableRouter<?> routerOverride) {
        return isSharding(metadata, routerOverride, null, null);
    }

    /**
     * 解析目标使用的分表路由器
     */
    public static TableRouter<?> resolveTableRouter(SelectStatement statement, EntityMetadata metadata) {
        if (statement != null && statement.getShardingRouter() != null) {
            return statement.getShardingRouter();
        }
        return metadata != null ? metadata.getShardingRouter() : null;
    }

    public static TableRouter<?> resolveTableRouter(EntityMetadata metadata, TableRouter<?> routerOverride) {
        if (routerOverride != null) {
            return routerOverride;
        }
        return metadata != null ? metadata.getShardingRouter() : null;
    }

    /**
     * 解析目标使用的分库路由器
     */
    public static DataSourceRouter<?> resolveDataSourceRouter(SelectStatement statement, EntityMetadata metadata) {
        if (statement != null && statement.getDataSourceRouter() != null) {
            return statement.getDataSourceRouter();
        }
        return metadata != null ? metadata.getShardingDataSourceRouter() : null;
    }

    public static DataSourceRouter<?> resolveDataSourceRouter(EntityMetadata metadata, DataSourceRouter<?> routerOverride) {
        if (routerOverride != null) {
            return routerOverride;
        }
        return metadata != null ? metadata.getShardingDataSourceRouter() : null;
    }

    /**
     * 解析是否允许全表扫描
     */
    public static boolean resolveAllowFullScan(SelectStatement statement, EntityMetadata metadata) {
        if (statement != null && statement.getAllowFullShardingScan() != null) {
            return statement.getAllowFullShardingScan();
        }
        return metadata != null && metadata.isAllowFullShardingScan();
    }

    public static boolean resolveAllowFullScan(EntityMetadata metadata, Boolean allowFullScanOverride) {
        if (allowFullScanOverride != null) {
            return allowFullScanOverride;
        }
        return metadata != null && metadata.isAllowFullShardingScan();
    }

    /**
     * 根据 WHERE 条件计算目标数据源名称列表（针对 SELECT）
     */
    @SuppressWarnings("unchecked")
    public static List<String> determineTargetDataSources(SelectStatement statement, EntityMetadata metadata) {
        if (statement != null && statement.getTargetDataSource() != null && !statement.getTargetDataSource().isEmpty()) {
            return Collections.singletonList(statement.getTargetDataSource());
        }
        DataSourceRouter<Object> router = (DataSourceRouter<Object>) resolveDataSourceRouter(statement, metadata);
        if (router != null) {
            boolean allowFullScan = resolveAllowFullScan(statement, metadata);
            ColumnMetadata shardingCol = metadata != null ? metadata.getShardingColumn() : null;
            return routeDataSourcesByConditions(statement.getRootEntityClass(), statement.getRootTableAlias(),
                    statement.getWhereConditions(), shardingCol, router, allowFullScan);
        }
        if (metadata != null && metadata.getShardingDataSource() != null && !metadata.getShardingDataSource().isEmpty()) {
            return Collections.singletonList(metadata.getShardingDataSource());
        }
        return Collections.singletonList(null);
    }

    /**
     * 根据 WHERE 条件计算目标数据源名称列表（针对 UPDATE）
     */
    @SuppressWarnings("unchecked")
    public static List<String> determineTargetDataSources(UpdateStatement statement, EntityMetadata metadata,
                                                          DataSourceRouter<?> dsRouterOverride, String dsOverride,
                                                          Boolean allowFullScanOverride) {
        if (dsOverride != null && !dsOverride.isEmpty()) {
            return Collections.singletonList(dsOverride);
        }
        if (statement != null && statement.getTargetDataSource() != null && !statement.getTargetDataSource().isEmpty()) {
            return Collections.singletonList(statement.getTargetDataSource());
        }
        DataSourceRouter<Object> router = (DataSourceRouter<Object>) (dsRouterOverride != null ? dsRouterOverride : resolveDataSourceRouter(metadata, statement != null ? statement.getDataSourceRouter() : null));
        if (router != null) {
            boolean allowFullScan = resolveAllowFullScan(metadata, allowFullScanOverride);
            ColumnMetadata shardingCol = metadata != null ? metadata.getShardingColumn() : null;
            return routeDataSourcesByConditions(statement.getEntityClass(), null,
                    statement.getWhereConditions(), shardingCol, router, allowFullScan);
        }
        if (metadata != null && metadata.getShardingDataSource() != null && !metadata.getShardingDataSource().isEmpty()) {
            return Collections.singletonList(metadata.getShardingDataSource());
        }
        return Collections.singletonList(null);
    }

    /**
     * 根据 WHERE 条件计算目标数据源名称列表（针对 DELETE）
     */
    @SuppressWarnings("unchecked")
    public static List<String> determineTargetDataSources(DeleteStatement statement, EntityMetadata metadata,
                                                          DataSourceRouter<?> dsRouterOverride, String dsOverride,
                                                          Boolean allowFullScanOverride) {
        if (dsOverride != null && !dsOverride.isEmpty()) {
            return Collections.singletonList(dsOverride);
        }
        if (statement != null && statement.getTargetDataSource() != null && !statement.getTargetDataSource().isEmpty()) {
            return Collections.singletonList(statement.getTargetDataSource());
        }
        DataSourceRouter<Object> router = (DataSourceRouter<Object>) (dsRouterOverride != null ? dsRouterOverride : resolveDataSourceRouter(metadata, statement != null ? statement.getDataSourceRouter() : null));
        if (router != null) {
            boolean allowFullScan = resolveAllowFullScan(metadata, allowFullScanOverride);
            ColumnMetadata shardingCol = metadata != null ? metadata.getShardingColumn() : null;
            return routeDataSourcesByConditions(statement.getEntityClass(), null,
                    statement.getWhereConditions(), shardingCol, router, allowFullScan);
        }
        if (metadata != null && metadata.getShardingDataSource() != null && !metadata.getShardingDataSource().isEmpty()) {
            return Collections.singletonList(metadata.getShardingDataSource());
        }
        return Collections.singletonList(null);
    }

    /**
     * 根据 WHERE 条件计算目标物理表列表（针对 SELECT）
     */
    @SuppressWarnings("unchecked")
    public static List<String> determineTargetTables(SelectStatement statement, EntityMetadata metadata) {
        TableRouter<Object> router = (TableRouter<Object>) resolveTableRouter(statement, metadata);
        if (router == null) {
            return Collections.singletonList(statement.getRootTableName());
        }
        boolean allowFullScan = resolveAllowFullScan(statement, metadata);
        ColumnMetadata shardingCol = metadata != null ? metadata.getShardingColumn() : null;
        String logicTableName = statement.getRootTableName();

        return routeByConditions(logicTableName, statement.getRootEntityClass(), statement.getRootTableAlias(),
                statement.getWhereConditions(), shardingCol, router, allowFullScan);
    }

    /**
     * 根据 WHERE 条件计算目标物理表列表（针对 UPDATE）
     */
    @SuppressWarnings("unchecked")
    public static List<String> determineTargetTables(UpdateStatement statement, EntityMetadata metadata,
                                                     TableRouter<?> routerOverride, Boolean allowFullScanOverride) {
        TableRouter<Object> router = (TableRouter<Object>) resolveTableRouter(metadata, routerOverride != null ? routerOverride : (statement != null ? statement.getShardingRouter() : null));
        if (router == null) {
            return Collections.singletonList(statement.getTableName());
        }
        boolean allowFullScan = resolveAllowFullScan(metadata, allowFullScanOverride);
        ColumnMetadata shardingCol = metadata != null ? metadata.getShardingColumn() : null;
        String logicTableName = statement.getTableName();

        return routeByConditions(logicTableName, statement.getEntityClass(), null,
                statement.getWhereConditions(), shardingCol, router, allowFullScan);
    }

    /**
     * 根据 WHERE 条件计算目标物理表列表（针对 DELETE）
     */
    @SuppressWarnings("unchecked")
    public static List<String> determineTargetTables(DeleteStatement statement, EntityMetadata metadata,
                                                     TableRouter<?> routerOverride, Boolean allowFullScanOverride) {
        TableRouter<Object> router = (TableRouter<Object>) resolveTableRouter(metadata, routerOverride != null ? routerOverride : (statement != null ? statement.getShardingRouter() : null));
        if (router == null) {
            return Collections.singletonList(statement.getTableName());
        }
        boolean allowFullScan = resolveAllowFullScan(metadata, allowFullScanOverride);
        ColumnMetadata shardingCol = metadata != null ? metadata.getShardingColumn() : null;
        String logicTableName = statement.getTableName();

        return routeByConditions(logicTableName, statement.getEntityClass(), null,
                statement.getWhereConditions(), shardingCol, router, allowFullScan);
    }

    private static List<String> routeDataSourcesByConditions(Class<?> entityClass, String rootAlias,
                                                             List<Condition> conditions, ColumnMetadata shardingCol,
                                                             DataSourceRouter<Object> router, boolean allowFullScan) {
        if (shardingCol == null) {
            if (allowFullScan) {
                return router.allDataSources();
            }
            throw new IllegalStateException("Sharding key column is not defined for entity "
                    + (entityClass != null ? entityClass.getName() : "unknown")
                    + ", and allowFullShardingScan is false");
        }

        List<BinaryCondition> matchedConditions = new ArrayList<>();
        collectMatchedConditions(conditions, shardingCol, rootAlias, matchedConditions);

        if (matchedConditions.isEmpty()) {
            if (allowFullScan) {
                return router.allDataSources();
            }
            throw new IllegalStateException("Full database scan across data sources is prohibited for entity "
                    + (entityClass != null ? entityClass.getName() : "unknown")
                    + " without sharding key condition (" + shardingCol.getPropertyName() + "). "
                    + "Specify allowFullShardingScan(true) if full scan is intentional.");
        }

        // 1. EQ
        for (BinaryCondition bc : matchedConditions) {
            if (bc.getOperator() == SqlOperator.EQ) {
                Object[] vals = bc.getValues();
                if (vals != null && vals.length > 0 && vals[0] != null) {
                    return Collections.singletonList(router.route(vals[0]));
                }
            }
        }

        // 2. IN
        for (BinaryCondition bc : matchedConditions) {
            if (bc.getOperator() == SqlOperator.IN) {
                List<Object> inValues = extractValues(bc.getValues());
                if (!inValues.isEmpty()) {
                    Set<String> dsSet = new LinkedHashSet<>();
                    for (Object val : inValues) {
                        if (val != null) {
                            dsSet.add(router.route(val));
                        }
                    }
                    return new ArrayList<>(dsSet);
                }
            }
        }

        // 3. BETWEEN / 范围
        Object startVal = null;
        Object endVal = null;
        for (BinaryCondition bc : matchedConditions) {
            if (bc.getOperator() == SqlOperator.BETWEEN) {
                Object[] vals = bc.getValues();
                if (vals != null && vals.length >= 2) {
                    startVal = vals[0];
                    endVal = vals[1];
                    break;
                }
            } else if (bc.getOperator() == SqlOperator.GT || bc.getOperator() == SqlOperator.GE) {
                Object[] vals = bc.getValues();
                if (vals != null && vals.length > 0) {
                    startVal = vals[0];
                }
            } else if (bc.getOperator() == SqlOperator.LT || bc.getOperator() == SqlOperator.LE) {
                Object[] vals = bc.getValues();
                if (vals != null && vals.length > 0) {
                    endVal = vals[0];
                }
            }
        }
        if (startVal != null || endVal != null) {
            return router.routeRange(startVal, endVal);
        }

        if (allowFullScan) {
            return router.allDataSources();
        }
        throw new IllegalStateException("Could not resolve dataSource sharding route and allowFullScan is false");
    }

    private static List<String> routeByConditions(String logicTableName, Class<?> entityClass, String rootAlias,
                                                  List<Condition> conditions, ColumnMetadata shardingCol,
                                                  TableRouter<Object> router, boolean allowFullScan) {
        if (shardingCol == null) {
            if (allowFullScan) {
                return router.allTables(logicTableName);
            }
            throw new IllegalStateException("Sharding key column is not defined for entity "
                    + (entityClass != null ? entityClass.getName() : "unknown")
                    + ", and allowFullShardingScan is false");
        }

        List<BinaryCondition> matchedConditions = new ArrayList<>();
        collectMatchedConditions(conditions, shardingCol, rootAlias, matchedConditions);

        if (matchedConditions.isEmpty()) {
            if (allowFullScan) {
                return router.allTables(logicTableName);
            }
            throw new IllegalStateException("Full table scan across sharding tables is prohibited for entity "
                    + (entityClass != null ? entityClass.getName() : "unknown")
                    + " without sharding key condition (" + shardingCol.getPropertyName() + "). "
                    + "Specify allowFullShardingScan(true) if full scan is intentional.");
        }

        // 1. 优先检查精确匹配（EQ）
        for (BinaryCondition bc : matchedConditions) {
            if (bc.getOperator() == SqlOperator.EQ) {
                Object[] vals = bc.getValues();
                if (vals != null && vals.length > 0 && vals[0] != null) {
                    String target = router.route(logicTableName, vals[0]);
                    return Collections.singletonList(target);
                }
            }
        }

        // 2. 检查 IN 集合匹配
        for (BinaryCondition bc : matchedConditions) {
            if (bc.getOperator() == SqlOperator.IN) {
                List<Object> inValues = extractValues(bc.getValues());
                if (!inValues.isEmpty()) {
                    Set<String> tableSet = new LinkedHashSet<>();
                    for (Object val : inValues) {
                        if (val != null) {
                            tableSet.add(router.route(logicTableName, val));
                        }
                    }
                    return new ArrayList<>(tableSet);
                }
            }
        }

        // 3. 检查 BETWEEN 或范围匹配（GT, GE, LT, LE）
        Object startVal = null;
        Object endVal = null;
        for (BinaryCondition bc : matchedConditions) {
            if (bc.getOperator() == SqlOperator.BETWEEN) {
                Object[] vals = bc.getValues();
                if (vals != null && vals.length >= 2) {
                    startVal = vals[0];
                    endVal = vals[1];
                    break;
                }
            } else if (bc.getOperator() == SqlOperator.GT || bc.getOperator() == SqlOperator.GE) {
                Object[] vals = bc.getValues();
                if (vals != null && vals.length > 0) {
                    startVal = vals[0];
                }
            } else if (bc.getOperator() == SqlOperator.LT || bc.getOperator() == SqlOperator.LE) {
                Object[] vals = bc.getValues();
                if (vals != null && vals.length > 0) {
                    endVal = vals[0];
                }
            }
        }
        if (startVal != null || endVal != null) {
            return router.routeRange(logicTableName, startVal, endVal);
        }

        if (allowFullScan) {
            return router.allTables(logicTableName);
        }
        throw new IllegalStateException("Could not resolve sharding route for condition and allowFullScan is false");
    }

    private static void collectMatchedConditions(List<Condition> conditions, ColumnMetadata shardingCol,
                                                 String rootAlias, List<BinaryCondition> matched) {
        if (conditions == null) return;
        for (Condition cond : conditions) {
            if (cond instanceof BinaryCondition) {
                BinaryCondition bc = (BinaryCondition) cond;
                if (matchesShardingColumn(bc, shardingCol, rootAlias)) {
                    matched.add(bc);
                }
            } else if (cond instanceof GroupCondition) {
                collectMatchedConditions(((GroupCondition) cond).getChildren(), shardingCol, rootAlias, matched);
            }
        }
    }

    private static boolean matchesShardingColumn(BinaryCondition cond, ColumnMetadata shardingCol, String rootAlias) {
        if (cond.getTableAlias() != null && rootAlias != null && !cond.getTableAlias().equalsIgnoreCase(rootAlias)) {
            return false;
        }
        String colName = cond.getColumnName();
        if (colName != null) {
            if (colName.equalsIgnoreCase(shardingCol.getColumnName()) || colName.equalsIgnoreCase(shardingCol.getPropertyName())) {
                return true;
            }
        }
        PropertyPath path = cond.getPath();
        if (path != null) {
            String prop = path.getTerminalProperty();
            if (prop != null && (prop.equalsIgnoreCase(shardingCol.getPropertyName()) || prop.equalsIgnoreCase(shardingCol.getColumnName()))) {
                return true;
            }
        }
        return false;
    }

    private static List<Object> extractValues(Object[] values) {
        List<Object> list = new ArrayList<>();
        if (values == null) return list;
        for (Object item : values) {
            if (item instanceof Collection) {
                list.addAll((Collection<?>) item);
            } else if (item != null && item.getClass().isArray()) {
                int len = Array.getLength(item);
                for (int i = 0; i < len; i++) {
                    list.add(Array.get(item, i));
                }
            } else if (item != null) {
                list.add(item);
            }
        }
        return list;
    }

    private static DataSource resolveDataSource(String dsName, OrmConfig config, DataSource defaultDs) {
        if (dsName == null || dsName.isEmpty() || "default".equalsIgnoreCase(dsName)) {
            if (config != null && config.getDataSourceManager() != null) {
                String currentDs = DataSourceContext.getDataSource();
                if (currentDs != null && !currentDs.isEmpty()) {
                    return config.getDataSourceManager().getDataSource(currentDs);
                }
                DataSource def = config.getDataSourceManager().getDefaultDataSource();
                if (def != null) return def;
            }
            return defaultDs;
        }
        if (config != null && config.getDataSourceManager() != null) {
            return config.getDataSourceManager().getDataSource(dsName);
        }
        return defaultDs;
    }

    // ========== 核心执行与归并方法 ==========

    public static <R> List<R> executeSelectList(Connection conn, SelectStatement statement, EntityMetadata metadata,
                                                List<IncludeSpec> includes, Dialect dialect, OrmConfig config,
                                                DataSource defaultDs, SqlExecutor executor) {
        List<String> dataSources = determineTargetDataSources(statement, metadata);
        List<String> tables = determineTargetTables(statement, metadata);

        if (dataSources.size() == 1 && tables.size() == 1) {
            statement.setRootTableName(tables.get(0));
            DataSource ds = resolveDataSource(dataSources.get(0), config, defaultDs);
            return executor.executeQuery(conn, statement, metadata, includes, dialect, config, ds);
        }

        List<R> merged = new ArrayList<>();
        for (String dsName : dataSources) {
            DataSource ds = resolveDataSource(dsName, config, defaultDs);
            for (String table : tables) {
                SelectStatement copy = statement.copyWithTableName(table);
                List<R> part = executor.executeQuery(conn, copy, metadata, includes, dialect, config, ds);
                if (part != null && !part.isEmpty()) {
                    merged.addAll(part);
                }
            }
        }
        return applyPagination(merged, statement);
    }

    public static <DTO> List<DTO> executeSelectDtoList(Connection conn, SelectStatement statement, Class<DTO> dtoClass,
                                                       EntityMetadata metadata, Dialect dialect, OrmConfig config,
                                                       DataSource defaultDs, SqlExecutor executor) {
        List<String> dataSources = determineTargetDataSources(statement, metadata);
        List<String> tables = determineTargetTables(statement, metadata);

        if (dataSources.size() == 1 && tables.size() == 1) {
            statement.setRootTableName(tables.get(0));
            DataSource ds = resolveDataSource(dataSources.get(0), config, defaultDs);
            return executor.executeDtoQuery(conn, statement, dtoClass, dialect, config, ds);
        }

        List<DTO> merged = new ArrayList<>();
        for (String dsName : dataSources) {
            DataSource ds = resolveDataSource(dsName, config, defaultDs);
            for (String table : tables) {
                SelectStatement copy = statement.copyWithTableName(table);
                List<DTO> part = executor.executeDtoQuery(conn, copy, dtoClass, dialect, config, ds);
                if (part != null && !part.isEmpty()) {
                    merged.addAll(part);
                }
            }
        }
        return applyPagination(merged, statement);
    }

    public static List<Map<String, Object>> executeSelectMaps(Connection conn, SelectStatement statement,
                                                              EntityMetadata metadata, Dialect dialect, OrmConfig config,
                                                              DataSource defaultDs, SqlExecutor executor) {
        List<String> dataSources = determineTargetDataSources(statement, metadata);
        List<String> tables = determineTargetTables(statement, metadata);

        if (dataSources.size() == 1 && tables.size() == 1) {
            statement.setRootTableName(tables.get(0));
            DataSource ds = resolveDataSource(dataSources.get(0), config, defaultDs);
            return executor.executeMaps(conn, statement, dialect, config, ds);
        }

        List<Map<String, Object>> merged = new ArrayList<>();
        for (String dsName : dataSources) {
            DataSource ds = resolveDataSource(dsName, config, defaultDs);
            for (String table : tables) {
                SelectStatement copy = statement.copyWithTableName(table);
                List<Map<String, Object>> part = executor.executeMaps(conn, copy, dialect, config, ds);
                if (part != null && !part.isEmpty()) {
                    merged.addAll(part);
                }
            }
        }
        return applyPagination(merged, statement);
    }

    @SuppressWarnings("unchecked")
    public static <V> V executeSelectScalar(Connection conn, SelectStatement statement, Class<V> targetType,
                                            EntityMetadata metadata, Dialect dialect, OrmConfig config,
                                            DataSource defaultDs, SqlExecutor executor) {
        List<String> dataSources = determineTargetDataSources(statement, metadata);
        List<String> tables = determineTargetTables(statement, metadata);

        if (dataSources.size() == 1 && tables.size() == 1) {
            statement.setRootTableName(tables.get(0));
            DataSource ds = resolveDataSource(dataSources.get(0), config, defaultDs);
            return executor.executeScalar(conn, statement, targetType, dialect, config, ds);
        }

        long sum = 0;
        boolean hasValue = false;
        V firstVal = null;
        for (String dsName : dataSources) {
            DataSource ds = resolveDataSource(dsName, config, defaultDs);
            for (String table : tables) {
                SelectStatement copy = statement.copyWithTableName(table);
                V val = executor.executeScalar(conn, copy, targetType, dialect, config, ds);
                if (val != null) {
                    hasValue = true;
                    if (firstVal == null) firstVal = val;
                    if (val instanceof Number) {
                        sum += ((Number) val).longValue();
                    }
                }
            }
        }
        if (!hasValue) return null;
        if (targetType == Long.class || targetType == long.class) {
            return (V) Long.valueOf(sum);
        } else if (targetType == Integer.class || targetType == int.class) {
            return (V) Integer.valueOf((int) sum);
        }
        return firstVal;
    }

    public static <V> List<V> executeSelectScalarList(Connection conn, SelectStatement statement, Class<V> targetType,
                                                      EntityMetadata metadata, Dialect dialect, OrmConfig config,
                                                      DataSource defaultDs, SqlExecutor executor) {
        List<String> dataSources = determineTargetDataSources(statement, metadata);
        List<String> tables = determineTargetTables(statement, metadata);

        if (dataSources.size() == 1 && tables.size() == 1) {
            statement.setRootTableName(tables.get(0));
            DataSource ds = resolveDataSource(dataSources.get(0), config, defaultDs);
            return executor.executeScalarList(conn, statement, targetType, dialect, config, ds);
        }

        List<V> merged = new ArrayList<>();
        for (String dsName : dataSources) {
            DataSource ds = resolveDataSource(dsName, config, defaultDs);
            for (String table : tables) {
                SelectStatement copy = statement.copyWithTableName(table);
                List<V> part = executor.executeScalarList(conn, copy, targetType, dialect, config, ds);
                if (part != null && !part.isEmpty()) {
                    merged.addAll(part);
                }
            }
        }
        return applyPagination(merged, statement);
    }

    public static long executeSelectCount(Connection conn, SelectStatement statement, EntityMetadata metadata,
                                          Dialect dialect, OrmConfig config, DataSource defaultDs, SqlExecutor executor) {
        List<String> dataSources = determineTargetDataSources(statement, metadata);
        List<String> tables = determineTargetTables(statement, metadata);

        if (dataSources.size() == 1 && tables.size() == 1) {
            statement.setRootTableName(tables.get(0));
            DataSource ds = resolveDataSource(dataSources.get(0), config, defaultDs);
            return executor.executeCount(conn, statement, dialect, config, ds);
        }

        long total = 0;
        for (String dsName : dataSources) {
            DataSource ds = resolveDataSource(dsName, config, defaultDs);
            for (String table : tables) {
                SelectStatement copy = statement.copyWithTableName(table);
                total += executor.executeCount(conn, copy, dialect, config, ds);
            }
        }
        return total;
    }

    public static int executeUpdate(Connection conn, UpdateStatement statement, EntityMetadata metadata,
                                    TableRouter<?> routerOverride, DataSourceRouter<?> dsRouterOverride,
                                    String dsOverride, Boolean allowFullScanOverride,
                                    Dialect dialect, OrmConfig config, DataSource defaultDs, SqlExecutor executor) {
        List<String> dataSources = determineTargetDataSources(statement, metadata, dsRouterOverride, dsOverride, allowFullScanOverride);
        List<String> tables = determineTargetTables(statement, metadata, routerOverride, allowFullScanOverride);

        if (dataSources.size() == 1 && tables.size() == 1) {
            statement.setTableName(tables.get(0));
            DataSource ds = resolveDataSource(dataSources.get(0), config, defaultDs);
            SqlAndParams sqlAndParams = dialect.buildUpdate(statement);
            return executor.executeUpdate(conn, sqlAndParams, "UPDATE", dialect, config, ds);
        }

        int affected = 0;
        for (String dsName : dataSources) {
            DataSource ds = resolveDataSource(dsName, config, defaultDs);
            for (String table : tables) {
                UpdateStatement copy = statement.copyWithTableName(table);
                SqlAndParams sqlAndParams = dialect.buildUpdate(copy);
                affected += executor.executeUpdate(conn, sqlAndParams, "UPDATE", dialect, config, ds);
            }
        }
        return affected;
    }

    public static int executeDelete(Connection conn, DeleteStatement statement, EntityMetadata metadata,
                                    TableRouter<?> routerOverride, DataSourceRouter<?> dsRouterOverride,
                                    String dsOverride, Boolean allowFullScanOverride,
                                    Dialect dialect, OrmConfig config, DataSource defaultDs, SqlExecutor executor) {
        List<String> dataSources = determineTargetDataSources(statement, metadata, dsRouterOverride, dsOverride, allowFullScanOverride);
        List<String> tables = determineTargetTables(statement, metadata, routerOverride, allowFullScanOverride);

        if (dataSources.size() == 1 && tables.size() == 1) {
            statement.setTableName(tables.get(0));
            DataSource ds = resolveDataSource(dataSources.get(0), config, defaultDs);
            SqlAndParams sqlAndParams = dialect.buildDelete(statement);
            return executor.executeUpdate(conn, sqlAndParams, "DELETE", dialect, config, ds);
        }

        int affected = 0;
        for (String dsName : dataSources) {
            DataSource ds = resolveDataSource(dsName, config, defaultDs);
            for (String table : tables) {
                DeleteStatement copy = statement.copyWithTableName(table);
                SqlAndParams sqlAndParams = dialect.buildDelete(copy);
                affected += executor.executeUpdate(conn, sqlAndParams, "DELETE", dialect, config, ds);
            }
        }
        return affected;
    }

    @SuppressWarnings("unchecked")
    public static int executeInsert(Connection conn, InsertStatement statement, EntityMetadata metadata,
                                    boolean returnKeys, Object entity,
                                    TableRouter<?> routerOverride, DataSourceRouter<?> dsRouterOverride,
                                    String dsOverride, Dialect dialect, OrmConfig config,
                                    DataSource defaultDs, SqlExecutor executor) {
        TableRouter<Object> tableRouter = (TableRouter<Object>) resolveTableRouter(metadata, routerOverride);
        DataSourceRouter<Object> dsRouter = (DataSourceRouter<Object>) resolveDataSourceRouter(metadata, dsRouterOverride);

        ColumnMetadata shardingCol = metadata != null ? metadata.getShardingColumn() : null;
        Object shardingValue = null;
        if (shardingCol != null && entity != null) {
            shardingValue = shardingCol.getValue(entity);
        }

        // 1. 确定物理数据源
        String targetDsName = null;
        if (dsOverride != null && !dsOverride.isEmpty()) {
            targetDsName = dsOverride;
        } else if (statement.getTargetDataSource() != null && !statement.getTargetDataSource().isEmpty()) {
            targetDsName = statement.getTargetDataSource();
        } else if (dsRouter != null) {
            if (shardingValue == null) {
                throw new IllegalArgumentException("Sharding key value cannot be null for dataSource routing insert on "
                        + (metadata != null ? metadata.getEntityClass().getName() : "entity"));
            }
            targetDsName = dsRouter.route(shardingValue);
        } else if (metadata != null && metadata.getShardingDataSource() != null && !metadata.getShardingDataSource().isEmpty()) {
            targetDsName = metadata.getShardingDataSource();
        }

        DataSource targetDs = resolveDataSource(targetDsName, config, defaultDs);

        // 2. 确定物理表
        if (tableRouter != null) {
            if (shardingValue == null) {
                throw new IllegalArgumentException("Sharding key value cannot be null for table routing insert on "
                        + (metadata != null ? metadata.getEntityClass().getName() : "entity"));
            }
            String targetTable = tableRouter.route(statement.getTableName(), shardingValue);
            statement.setTableName(targetTable);
        }

        return executor.executeInsert(conn, statement, metadata, returnKeys, entity, dialect, config, targetDs);
    }

    private static <T> List<T> applyPagination(List<T> list, SelectStatement statement) {
        if (statement.getOffset() == null && statement.getLimit() == null) {
            return list;
        }
        int offset = statement.getOffset() != null ? statement.getOffset() : 0;
        int limit = statement.getLimit() != null ? statement.getLimit() : Integer.MAX_VALUE;
        int from = Math.min(offset, list.size());
        int to = Math.min(offset + limit, list.size());
        return new ArrayList<>(list.subList(from, to));
    }
}
