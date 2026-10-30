package com.javaorm.core.connection;

import com.javaorm.core.config.OrmConfig;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * 统一数据库连接获取与释放工具。
 */
public class ConnectionUtils {

    public static Connection getConnection(DataSource dataSource, OrmConfig config) throws Exception {
        Connection txConn = TransactionContext.getConnection();
        if (txConn != null && !txConn.isClosed()) {
            return txConn;
        }
        if (config != null && config.getConnectionProvider() != null) {
            return config.getConnectionProvider().getConnection(dataSource);
        }
        return dataSource.getConnection();
    }

    public static boolean isTransactionConnection(Connection conn) {
        Connection txConn = TransactionContext.getConnection();
        return txConn != null && txConn == conn;
    }

    public static void closeConnection(Connection conn) {
        if (conn != null && !isTransactionConnection(conn)) {
            try {
                conn.close();
            } catch (Exception ignored) {
            }
        }
    }
}
