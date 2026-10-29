package com.javaorm.core.connection;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * 统一数据库连接提供者接口。
 * 可用于接入 Spring 事务管理器 (如 DataSourceUtils.getConnection(dataSource)) 或自定义连接池。
 */
@FunctionalInterface
public interface ConnectionProvider {

    Connection getConnection(DataSource dataSource) throws Exception;
}
