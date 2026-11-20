package com.javaorm.core.datasource;

import java.util.List;

/**
 * 从库负载均衡选择器接口
 */
public interface LoadBalancer {

    /**
     * 从从库列表中挑选一个目标从库
     *
     * @param slaves 从库列表（保证非空且至少包含一个元素）
     * @return 选中的从库
     */
    SlaveDataSource select(List<SlaveDataSource> slaves);
}
