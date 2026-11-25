package com.javaorm.bridge.mybatis;

import com.javaorm.core.ast.InsertStatement;
import com.javaorm.core.ast.SelectStatement;
import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.dialect.Dialect;
import com.javaorm.core.dialect.SqlAndParams;
import com.javaorm.core.executor.DefaultJdbcExecutor;
import com.javaorm.core.executor.SqlExecutor;
import com.javaorm.core.metadata.EntityMetadata;
import com.javaorm.core.query.IncludeSpec;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;
import java.util.Map;

/**
 * MyBatis 执行器桥接
 * 将 JavaORM 的 SQL 执行接入 MyBatis 的 SqlSession / SqlSessionFactory 上下文，
 * 自动复用 MyBatis 的事务连接与生命周期管理，并通过 SqlSessionUtils 正确归还连接池。
 */
public class MyBatisSqlExecutor implements SqlExecutor {

    private final SqlSession sqlSession;
    private final SqlSessionFactory sqlSessionFactory;
    private final SqlExecutor delegate = new DefaultJdbcExecutor();

    public MyBatisSqlExecutor(SqlSession sqlSession) {
        this.sqlSession = sqlSession;
        this.sqlSessionFactory = null;
    }

    public MyBatisSqlExecutor(SqlSessionFactory sqlSessionFactory) {
        this.sqlSession = null;
        this.sqlSessionFactory = sqlSessionFactory;
    }

    private static final java.lang.reflect.Method GET_SQL_SESSION_METHOD;
    private static final java.lang.reflect.Method CLOSE_SQL_SESSION_METHOD;

    static {
        java.lang.reflect.Method getMethod = null;
        java.lang.reflect.Method closeMethod = null;
        try {
            Class<?> utilsClass = Class.forName("org.mybatis.spring.SqlSessionUtils");
            getMethod = utilsClass.getMethod("getSqlSession", SqlSessionFactory.class);
            closeMethod = utilsClass.getMethod("closeSqlSession", SqlSession.class, SqlSessionFactory.class);
        } catch (Throwable ignored) {
        }
        GET_SQL_SESSION_METHOD = getMethod;
        CLOSE_SQL_SESSION_METHOD = closeMethod;
    }

    @FunctionalInterface
    private interface SqlCallback<R> {
        R execute(Connection conn);
    }

    private <R> R withConnection(Connection conn, SqlCallback<R> callback) {
        if (conn != null) {
            return callback.execute(conn);
        }
        if (sqlSession != null) {
            return callback.execute(sqlSession.getConnection());
        }
        if (sqlSessionFactory != null && GET_SQL_SESSION_METHOD != null) {
            SqlSession session = null;
            try {
                session = (SqlSession) GET_SQL_SESSION_METHOD.invoke(null, sqlSessionFactory);
                Connection effectiveConn = session != null ? session.getConnection() : null;
                return callback.execute(effectiveConn);
            } catch (Throwable t) {
                if (t instanceof RuntimeException) throw (RuntimeException) t;
                throw new RuntimeException("Failed to acquire connection from SqlSessionUtils", t);
            } finally {
                if (session != null && CLOSE_SQL_SESSION_METHOD != null) {
                    try {
                        CLOSE_SQL_SESSION_METHOD.invoke(null, session, sqlSessionFactory);
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        return callback.execute(null);
    }

    @Override
    public <R> List<R> executeQuery(Connection conn, SelectStatement statement, EntityMetadata metadata,
                                    List<IncludeSpec> includes, Dialect dialect, OrmConfig config, DataSource dataSource) {
        return withConnection(conn, c -> delegate.executeQuery(c, statement, metadata, includes, dialect, config, dataSource));
    }

    @Override
    public <DTO> List<DTO> executeDtoQuery(Connection conn, SelectStatement statement, Class<DTO> dtoClass,
                                           Dialect dialect, OrmConfig config, DataSource dataSource) {
        return withConnection(conn, c -> delegate.executeDtoQuery(c, statement, dtoClass, dialect, config, dataSource));
    }

    @Override
    public List<Map<String, Object>> executeMaps(Connection conn, SelectStatement statement,
                                                  Dialect dialect, OrmConfig config, DataSource dataSource) {
        return withConnection(conn, c -> delegate.executeMaps(c, statement, dialect, config, dataSource));
    }

    @Override
    public <V> V executeScalar(Connection conn, SelectStatement statement, Class<V> targetType,
                                Dialect dialect, OrmConfig config, DataSource dataSource) {
        return withConnection(conn, c -> delegate.executeScalar(c, statement, targetType, dialect, config, dataSource));
    }

    @Override
    public <V> List<V> executeScalarList(Connection conn, SelectStatement statement, Class<V> targetType,
                                          Dialect dialect, OrmConfig config, DataSource dataSource) {
        return withConnection(conn, c -> delegate.executeScalarList(c, statement, targetType, dialect, config, dataSource));
    }

    @Override
    public long executeCount(Connection conn, SelectStatement statement,
                             Dialect dialect, OrmConfig config, DataSource dataSource) {
        return withConnection(conn, c -> delegate.executeCount(c, statement, dialect, config, dataSource));
    }

    @Override
    public int executeUpdate(Connection conn, SqlAndParams sqlAndParams, String prefix,
                             Dialect dialect, OrmConfig config, DataSource dataSource) {
        return withConnection(conn, c -> delegate.executeUpdate(c, sqlAndParams, prefix, dialect, config, dataSource));
    }

    @Override
    public int executeInsert(Connection conn, InsertStatement statement, EntityMetadata metadata,
                             boolean returnKeys, Object entity, Dialect dialect, OrmConfig config, DataSource dataSource) {
        return withConnection(conn, c -> delegate.executeInsert(c, statement, metadata, returnKeys, entity, dialect, config, dataSource));
    }

    @Override
    public int[] executeBatchUpdate(Connection conn, String sql, List<List<Object>> batchParams,
                                     Dialect dialect, OrmConfig config, DataSource dataSource) {
        return withConnection(conn, c -> delegate.executeBatchUpdate(c, sql, batchParams, dialect, config, dataSource));
    }

    @Override
    public int executeBatchInsert(Connection conn, String sql, List<List<Object>> batchParams, List<?> entities,
                                  EntityMetadata metadata, boolean returnKeys, boolean multiValues,
                                  Dialect dialect, OrmConfig config, DataSource dataSource) {
        return withConnection(conn, c -> delegate.executeBatchInsert(c, sql, batchParams, entities, metadata, returnKeys, multiValues, dialect, config, dataSource));
    }
}
