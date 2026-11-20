package com.javaorm.core.datasource;

/**
 * 读写分离路由角色策略
 */
public enum RouteRole {
    /**
     * 自动推导（写操作走主库，读操作走从库；事务中或写后窗口自动锁定主库）
     */
    AUTO,

    /**
     * 强制路由到 Master 主库
     */
    MASTER,

    /**
     * 强制路由到 Slave 从库
     */
    SLAVE
}
