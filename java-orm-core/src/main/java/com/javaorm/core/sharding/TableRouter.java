package com.javaorm.core.sharding;

import java.util.Collections;
import java.util.List;

/**
 * 分表路由接口规范。
 * 负责根据分片键值计算对应的物理表名，并在必要时提供所有已知的物理表集合。
 *
 * @param <T> 分片键类型（如 Long, Integer, String, LocalDateTime 等）
 */
public interface TableRouter<T> {

    /**
     * 根据逻辑表名和具体分片键值，计算出目标物理表名（单表路由）。
     *
     * @param logicTableName 逻辑表名（如 "t_order"）
     * @param shardingValue  分片键值（如 1001）
     * @return 目标物理表名（如 "t_order_1"）
     */
    String route(String logicTableName, T shardingValue);

    /**
     * 根据范围区间计算覆盖的目标物理表集合（区间路由，常用于按时间分片）。
     * 默认退化为全物理表集合。
     *
     * @param logicTableName 逻辑表名
     * @param startValue     起始值（可能为 null 表示无下界）
     * @param endValue       结束值（可能为 null 表示无上界）
     * @return 物理表名列表
     */
    default List<String> routeRange(String logicTableName, T startValue, T endValue) {
        return allTables(logicTableName);
    }

    /**
     * 获取所有可用的物理表集合（用于无分表键时的全表扫描或归并）。
     *
     * @param logicTableName 逻辑表名
     * @return 所有物理表列表
     */
    List<String> allTables(String logicTableName);
}
