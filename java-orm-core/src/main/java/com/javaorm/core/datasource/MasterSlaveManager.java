package com.javaorm.core.datasource;

import com.javaorm.core.ast.SelectStatement;

import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 读写分离主从路由器与管理器。
 * 1. 管理 1 个 Master 主数据源与多个权重 Slave 从数据源；
 * 2. 默认写操作走 Master，读操作负载均衡走 Slaves；
 * 3. 事务环境内（Spring 事务或本框架事务）强制将读操作 Pin 到 Master，保证强一致性；
 * 4. 支持写后粘性时间窗口（Pin Master After Write），自动解决刚写完立刻查的主从复制延迟；
 * 5. 支持流式 API（useMaster / useSlave）或线程上下文控制；
 * 6. 支持从库高可用软熔断与降级（从库全部故障或未配置时自动回退 Master）。
 */
public class MasterSlaveManager {

    private DataSource masterDataSource;
    private String masterName = "master";

    private final List<SlaveDataSource> slaveList = new CopyOnWriteArrayList<>();
    private final Map<String, SlaveDataSource> slaveMap = new HashMap<>();

    private LoadBalancer loadBalancer = new WeightedRoundRobinLoadBalancer();
    private long pinMasterAfterWriteMillis = 0L;
    private boolean fallbackToMasterWhenNoSlave = true;

    public MasterSlaveManager() {
    }

    public MasterSlaveManager(DataSource masterDataSource) {
        setMaster(masterDataSource);
    }

    public DataSource getMasterDataSource() {
        return masterDataSource;
    }

    public MasterSlaveManager setMaster(DataSource masterDataSource) {
        return setMaster("master", masterDataSource);
    }

    public synchronized MasterSlaveManager setMaster(String masterName, DataSource masterDataSource) {
        if (masterDataSource == null) {
            throw new IllegalArgumentException("Master DataSource must not be null");
        }
        this.masterName = (masterName != null && !masterName.isEmpty()) ? masterName : "master";
        this.masterDataSource = masterDataSource;
        return this;
    }

    public String getMasterName() {
        return masterName;
    }

    public synchronized MasterSlaveManager addSlave(DataSource slaveDataSource) {
        int index = slaveList.size() + 1;
        return addSlave("slave_" + index, slaveDataSource, 1);
    }

    public synchronized MasterSlaveManager addSlave(String name, DataSource slaveDataSource) {
        return addSlave(name, slaveDataSource, 1);
    }

    public synchronized MasterSlaveManager addSlave(String name, DataSource slaveDataSource, int weight) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Slave name must not be empty");
        }
        if (slaveDataSource == null) {
            throw new IllegalArgumentException("Slave DataSource must not be null");
        }
        SlaveDataSource sds = new SlaveDataSource(name.trim(), slaveDataSource, weight);
        slaveList.add(sds);
        slaveMap.put(name.trim().toLowerCase(), sds);
        return this;
    }

    public synchronized MasterSlaveManager removeSlave(String name) {
        if (name != null) {
            SlaveDataSource removed = slaveMap.remove(name.trim().toLowerCase());
            if (removed != null) {
                slaveList.remove(removed);
            }
        }
        return this;
    }

    public List<SlaveDataSource> getSlaves() {
        return Collections.unmodifiableList(slaveList);
    }

    public SlaveDataSource getSlave(String name) {
        if (name == null) return null;
        return slaveMap.get(name.trim().toLowerCase());
    }

    public LoadBalancer getLoadBalancer() {
        return loadBalancer;
    }

    public MasterSlaveManager setLoadBalancer(LoadBalancer loadBalancer) {
        if (loadBalancer != null) {
            this.loadBalancer = loadBalancer;
        }
        return this;
    }

    public long getPinMasterAfterWriteMillis() {
        return pinMasterAfterWriteMillis;
    }

    public MasterSlaveManager setPinMasterAfterWriteMillis(long pinMasterAfterWriteMillis) {
        this.pinMasterAfterWriteMillis = Math.max(0, pinMasterAfterWriteMillis);
        return this;
    }

    public boolean isFallbackToMasterWhenNoSlave() {
        return fallbackToMasterWhenNoSlave;
    }

    public MasterSlaveManager setFallbackToMasterWhenNoSlave(boolean fallbackToMasterWhenNoSlave) {
        this.fallbackToMasterWhenNoSlave = fallbackToMasterWhenNoSlave;
        return this;
    }

    /**
     * 判断当前是否启用了主从读写分离（配置了主库与从库）
     */
    public boolean isEnabled() {
        return masterDataSource != null;
    }

    /**
     * 执行路由计算，挑选用于当前 SQL 操作的目标 DataSource
     *
     * @param isWrite 是否为写操作（INSERT / UPDATE / DELETE / UPSERT）
     * @param stmt    当前查询的 SelectStatement（写操作时可为 null）
     * @return 目标物理 DataSource
     */
    public DataSource route(boolean isWrite, SelectStatement stmt) {
        // 1. 写操作：100% 路由到 Master 主库
        if (isWrite) {
            return masterDataSource;
        }

        // 2. 显式指定了数据源名称（例如通过 .useDataSource("slave_2")）
        if (stmt != null && stmt.getTargetDataSource() != null && !stmt.getTargetDataSource().isEmpty()) {
            String target = stmt.getTargetDataSource().trim().toLowerCase();
            if (masterName.equalsIgnoreCase(target) || "master".equals(target) || "default".equals(target)) {
                return masterDataSource;
            }
            SlaveDataSource slave = slaveMap.get(target);
            if (slave != null) {
                return slave.getDataSource();
            }
        }

        // 3. 语句或当前线程显式指定了强制走 Master 主库
        if ((stmt != null && stmt.getRouteRole() == RouteRole.MASTER)
                || ReadWriteContext.getRouteRole() == RouteRole.MASTER) {
            return masterDataSource;
        }

        // 4. 事务强一致性检查：若处于事务环境内，读操作自动强制锁定 Master 主库
        if (ReadWriteContext.isTransactionActive()) {
            return masterDataSource;
        }

        // 5. 写后粘性时间窗口检查：若刚发生过写操作且处于时间窗口内，读操作自动锁定 Master 主库
        if (ReadWriteContext.isPinMaster(this.pinMasterAfterWriteMillis)) {
            return masterDataSource;
        }

        // 6. 显式指定走特定从库名称
        String specifiedSlave = ReadWriteContext.getSpecifiedSlave();
        if (specifiedSlave != null && !specifiedSlave.isEmpty()) {
            SlaveDataSource slave = slaveMap.get(specifiedSlave.toLowerCase());
            if (slave != null) {
                return slave.getDataSource();
            }
        }

        // 7. 常规读操作（走从库负载均衡）
        if (!slaveList.isEmpty()) {
            SlaveDataSource selected = loadBalancer.select(slaveList);
            if (selected != null) {
                return selected.getDataSource();
            }
        }

        // 8. 无从库可用时的降级与回退
        if (fallbackToMasterWhenNoSlave) {
            return masterDataSource;
        }

        throw new IllegalStateException("No available slave DataSource found and fallback to master is disabled.");
    }
}
