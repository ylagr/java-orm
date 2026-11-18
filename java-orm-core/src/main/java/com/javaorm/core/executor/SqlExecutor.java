package com.javaorm.core.executor;

import com.javaorm.core.ast.InsertStatement;
import com.javaorm.core.ast.SelectStatement;
import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.dialect.Dialect;
import com.javaorm.core.dialect.SqlAndParams;
import com.javaorm.core.metadata.EntityMetadata;
import com.javaorm.core.query.IncludeSpec;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;
import java.util.Map;

/**
 * 底层通用 SQL 执行器接口。
 * 解耦上层 AST/SQL 构造与底层具体的物理数据库执行机制。
 * 支持原生 JDBC (DefaultJdbcExecutor)、MyBatis、MyBatis-Plus、EasyQuery 等第三方执行引擎无缝接入。
 */
public interface SqlExecutor {

    /**
     * 执行实体列表查询
     */
    <R> List<R> executeQuery(Connection conn, SelectStatement statement, EntityMetadata metadata,
                             List<IncludeSpec> includes, Dialect dialect, OrmConfig config, DataSource dataSource);

    /**
     * 执行 DTO 投影查询
     */
    <DTO> List<DTO> executeDtoQuery(Connection conn, SelectStatement statement, Class<DTO> dtoClass,
                                    Dialect dialect, OrmConfig config, DataSource dataSource);

    /**
     * 执行 Map 列表查询
     */
    List<Map<String, Object>> executeMaps(Connection conn, SelectStatement statement,
                                          Dialect dialect, OrmConfig config, DataSource dataSource);

    /**
     * 执行单标量查询（如 count, max 等）
     */
    <V> V executeScalar(Connection conn, SelectStatement statement, Class<V> targetType,
                        Dialect dialect, OrmConfig config, DataSource dataSource);

    /**
     * 执行标量列表查询（如 list of IDs）
     */
    <V> List<V> executeScalarList(Connection conn, SelectStatement statement, Class<V> targetType,
                                  Dialect dialect, OrmConfig config, DataSource dataSource);

    /**
     * 执行 count 查询
     */
    long executeCount(Connection conn, SelectStatement statement, Dialect dialect, OrmConfig config, DataSource dataSource);

    /**
     * 执行更新/删除/单条插入操作
     */
    int executeUpdate(Connection conn, SqlAndParams sqlAndParams, String prefix,
                      Dialect dialect, OrmConfig config, DataSource dataSource);

    /**
     * 执行实体插入（支持主键自增回填）
     */
    int executeInsert(Connection conn, com.javaorm.core.ast.InsertStatement statement, EntityMetadata metadata,
                      boolean returnKeys, Object entity, Dialect dialect, OrmConfig config, DataSource dataSource);

    /**
     * 执行批量更新/插入操作
     */
    int[] executeBatchUpdate(Connection conn, String sql, List<List<Object>> batchParams,
                             Dialect dialect, OrmConfig config, DataSource dataSource);
}
