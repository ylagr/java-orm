package com.javaorm.core.connection;

import com.javaorm.core.config.OrmConfig;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * 统一数据库连接获取与释放工具。
 */
public class ConnectionUtils {

    public static Connection getConnection(DataSource dataSource, OrmConfig config) throws Exception {
        return getConnection(dataSource, config, null, false);
    }

    public static Connection getConnection(DataSource dataSource, OrmConfig config, com.javaorm.core.ast.SelectStatement statement, boolean isWrite) throws Exception {
        Connection txConn = TransactionContext.getConnection();
        if (txConn != null && !txConn.isClosed()) {
            return txConn;
        }
        DataSource effectiveDs = dataSource;

        // 1. 优先检查读写分离路由 (MasterSlaveManager)
        if (config != null && config.getMasterSlaveManager() != null && config.getMasterSlaveManager().isEnabled()) {
            effectiveDs = config.getMasterSlaveManager().route(isWrite, statement);
        }

        // 2. 检查当前线程显式指定的数据源或外部动态数据源 (@DS / DynamicDataSourceContextHolder)
        if (config != null && config.getDataSourceManager() != null) {
            String currentDsName = com.javaorm.core.datasource.DataSourceContext.getDataSource();
            if (currentDsName != null && !currentDsName.isEmpty()) {
                effectiveDs = config.getDataSourceManager().getDataSource(currentDsName);
            } else if (effectiveDs == null) {
                effectiveDs = config.getDataSourceManager().getDefaultDataSource();
            }
        }
        if (config != null && config.getConnectionProvider() != null) {
            return config.getConnectionProvider().getConnection(effectiveDs);
        }
        if (effectiveDs == null) {
            throw new IllegalStateException("No DataSource available to obtain connection");
        }
        return effectiveDs.getConnection();
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
