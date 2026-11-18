package com.javaorm.core.sharding;

import java.util.List;

/**
 * 分库/数据源路由接口规范。
 * 负责根据分片键值计算对应的物理数据源名称（如 "ds_0", "ds_1"）。
 *
 * @param <T> 分片键类型
 */
public interface DataSourceRouter<T> {

    /**
     * 根据分片键值计算目标数据源名称。
     *
     * @param shardingValue 分片键值
     * @return 目标数据源名称
     */
    String route(T shardingValue);

    /**
     * 根据范围区间计算覆盖的目标数据源集合。
     * 默认退化为所有已知数据源集合。
     *
     * @param startValue 起始值（可能为 null 表示无下界）
     * @param endValue   结束值（可能为 null 表示无上界）
     * @return 数据源名称列表
     */
    default List<String> routeRange(T startValue, T endValue) {
        return allDataSources();
    }

    /**
     * 获取所有可用的数据源名称集合（用于广播扫描或跨库归并）。
     *
     * @return 所有数据源名称列表
     */
    List<String> allDataSources();
}
