package com.javaorm.core.connection;

import java.sql.Connection;

/**
 * 事务连接上下文（基于 ThreadLocal）。
 * 用于支持显式事务块内复用同一物理连接。
 */
public class TransactionContext {

    private static final ThreadLocal<Connection> CURRENT_CONNECTION = new ThreadLocal<>();

    public static Connection getConnection() {
        return CURRENT_CONNECTION.get();
    }

    public static void setConnection(Connection conn) {
        CURRENT_CONNECTION.set(conn);
    }

    public static void clear() {
        CURRENT_CONNECTION.remove();
    }
}
