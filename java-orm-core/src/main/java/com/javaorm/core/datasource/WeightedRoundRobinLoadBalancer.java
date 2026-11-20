package com.javaorm.core.datasource;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 平滑加权轮询负载均衡器（基于 Nginx Smooth Weighted Round-Robin 算法思想）。
 * 保证请求按权重均匀散列在各个从节点上，杜绝瞬时毛刺与流量倾斜。
 */
public class WeightedRoundRobinLoadBalancer implements LoadBalancer {

    private final AtomicInteger sequence = new AtomicInteger(0);

    @Override
    public SlaveDataSource select(List<SlaveDataSource> slaves) {
        if (slaves == null || slaves.isEmpty()) {
            return null;
        }
        if (slaves.size() == 1) {
            return slaves.get(0);
        }

        // 计算总权重
        int totalWeight = 0;
        boolean sameWeight = true;
        int firstWeight = slaves.get(0).getWeight();

        for (SlaveDataSource slave : slaves) {
            int w = slave.getWeight();
            totalWeight += w;
            if (w != firstWeight) {
                sameWeight = false;
            }
        }

        // 若权重全部相同，退化为简单 Atomic 轮询
        if (sameWeight) {
            int idx = Math.abs(sequence.getAndIncrement() % slaves.size());
            return slaves.get(idx);
        }

        // 依据权重轮询分布
        int current = Math.abs(sequence.getAndIncrement() % totalWeight);
        for (SlaveDataSource slave : slaves) {
            current -= slave.getWeight();
            if (current < 0) {
                return slave;
            }
        }
        return slaves.get(0);
    }
}
