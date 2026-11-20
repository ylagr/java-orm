package com.javaorm.core.datasource;

import com.javaorm.core.connection.TransactionContext;

import java.lang.reflect.Method;
import java.util.function.Supplier;

/**
 * 读写分离线程上下文管理器。
 * 1. 控制当前线程的路由倾向（MASTER / SLAVE / AUTO）；
 * 2. 记录当前线程最近一次写操作的时间戳，用于“写后粘性时间窗口（Pin Master After Write）”防主从延迟；
 * 3. 自动探测事务环境（本框架 TransactionContext 或 Spring 声明式事务），事务中强制锁定主库。
 */
public class ReadWriteContext {

    private static final ThreadLocal<RouteRole> ROLE_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<String> SPECIFIED_SLAVE_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<Long> LAST_WRITE_TIME_HOLDER = new ThreadLocal<>();

    // 缓存 Spring TransactionSynchronizationManager.isActualTransactionActive 反射 Method
    private static Method springIsTxActiveMethod = null;
    private static boolean springTxChecked = false;

    /**
     * 标记当前线程发生了写操作（记录写时间戳）
     */
    public static void recordWrite() {
        LAST_WRITE_TIME_HOLDER.set(System.currentTimeMillis());
    }

    /**
     * 获取当前线程最后一次写操作发生的时间戳（若未发生过写操作返回 0）
     */
    public static long getLastWriteTime() {
        Long time = LAST_WRITE_TIME_HOLDER.get();
        return time != null ? time : 0L;
    }

    /**
     * 检查当前线程是否处于“写后锁定主库”的时间窗口内
     *
     * @param windowMillis 窗口毫秒数
     * @return true 若处于窗口期内，应强制读主库
     */
    public static boolean isPinMaster(long windowMillis) {
        if (windowMillis <= 0) {
            return false;
        }
        long last = getLastWriteTime();
        if (last <= 0) {
            return false;
        }
        return (System.currentTimeMillis() - last) <= windowMillis;
    }

    /**
     * 获取当前线程显式指定的路由角色
     */
    public static RouteRole getRouteRole() {
        return ROLE_HOLDER.get();
    }

    /**
     * 设置当前线程的路由角色
     */
    public static void setRouteRole(RouteRole role) {
        if (role == null || role == RouteRole.AUTO) {
            ROLE_HOLDER.remove();
        } else {
            ROLE_HOLDER.set(role);
        }
    }

    /**
     * 获取当前线程显式指定的特定从库名称
     */
    public static String getSpecifiedSlave() {
        return SPECIFIED_SLAVE_HOLDER.get();
    }

    /**
     * 显式指定从库名称
     */
    public static void setSpecifiedSlave(String slaveName) {
        if (slaveName == null || slaveName.trim().isEmpty()) {
            SPECIFIED_SLAVE_HOLDER.remove();
        } else {
            SPECIFIED_SLAVE_HOLDER.set(slaveName.trim());
            ROLE_HOLDER.set(RouteRole.SLAVE);
        }
    }

    /**
     * 检查当前是否处于事务环境中（本框架事务或 Spring 事务）
     */
    public static boolean isTransactionActive() {
        // 1. 本框架原生事务检测
        try {
            if (TransactionContext.getConnection() != null && !TransactionContext.getConnection().isClosed()) {
                return true;
            }
        } catch (Exception ignored) {
        }

        // 2. Spring 声明式事务检测（轻量反射探测，绝不强依赖 Spring）
        if (!springTxChecked) {
            try {
                Class<?> clazz = Class.forName("org.springframework.transaction.support.TransactionSynchronizationManager");
                springIsTxActiveMethod = clazz.getMethod("isActualTransactionActive");
            } catch (Throwable ignored) {
            } finally {
                springTxChecked = true;
            }
        }
        if (springIsTxActiveMethod != null) {
            try {
                Boolean active = (Boolean) springIsTxActiveMethod.invoke(null);
                if (Boolean.TRUE.equals(active)) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    /**
     * 在强制使用 Master 主库的上下文中执行操作（带返回值）
     */
    public static <T> T useMaster(Supplier<T> action) {
        RouteRole prev = getRouteRole();
        try {
            setRouteRole(RouteRole.MASTER);
            return action.get();
        } finally {
            setRouteRole(prev);
        }
    }

    /**
     * 在强制使用 Master 主库的上下文中执行操作（无返回值）
     */
    public static void useMaster(Runnable action) {
        useMaster(() -> {
            action.run();
            return null;
        });
    }

    /**
     * 在强制使用 Slave 从库的上下文中执行操作（带返回值）
     */
    public static <T> T useSlave(Supplier<T> action) {
        RouteRole prev = getRouteRole();
        try {
            setRouteRole(RouteRole.SLAVE);
            return action.get();
        } finally {
            setRouteRole(prev);
        }
    }

    /**
     * 在强制使用 Slave 从库的上下文中执行操作（无返回值）
     */
    public static void useSlave(Runnable action) {
        useSlave(() -> {
            action.run();
            return null;
        });
    }

    /**
     * 清理当前线程的读写分离上下文
     */
    public static void clear() {
        ROLE_HOLDER.remove();
        SPECIFIED_SLAVE_HOLDER.remove();
        LAST_WRITE_TIME_HOLDER.remove();
    }
}
