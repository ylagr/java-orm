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
 * 自动复用 MyBatis 的事务连接与生命周期管理。
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

    static {
        java.lang.reflect.Method getMethod = null;
        try {
            Class<?> utilsClass = Class.forName("org.mybatis.spring.SqlSessionUtils");
            getMethod = utilsClass.getMethod("getSqlSession", SqlSessionFactory.class);
        } catch (Throwable ignored) {
        }
        GET_SQL_SESSION_METHOD = getMethod;
    }

    private Connection getEffectiveConnection(Connection conn) {
        if (conn != null) {
            return conn;
        }
        if (sqlSession != null) {
            return sqlSession.getConnection();
        }
        if (sqlSessionFactory != null && GET_SQL_SESSION_METHOD != null) {
            try {
                SqlSession springSession = (SqlSession) GET_SQL_SESSION_METHOD.invoke(null, sqlSessionFactory);
                if (springSession != null) {
                    return springSession.getConnection();
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    @Override
    public <R> List<R> executeQuery(Connection conn, SelectStatement statement, EntityMetadata metadata,
                                    List<IncludeSpec> includes, Dialect dialect, OrmConfig config, DataSource dataSource) {
        return delegate.executeQuery(getEffectiveConnection(conn), statement, metadata, includes, dialect, config, dataSource);
    }

    @Override
    public <DTO> List<DTO> executeDtoQuery(Connection conn, SelectStatement statement, Class<DTO> dtoClass,
                                           Dialect dialect, OrmConfig config, DataSource dataSource) {
        return delegate.executeDtoQuery(getEffectiveConnection(conn), statement, dtoClass, dialect, config, dataSource);
    }

    @Override
    public List<Map<String, Object>> executeMaps(Connection conn, SelectStatement statement,
                                                  Dialect dialect, OrmConfig config, DataSource dataSource) {
        return delegate.executeMaps(getEffectiveConnection(conn), statement, dialect, config, dataSource);
    }

    @Override
    public <V> V executeScalar(Connection conn, SelectStatement statement, Class<V> targetType,
                                Dialect dialect, OrmConfig config, DataSource dataSource) {
        return delegate.executeScalar(getEffectiveConnection(conn), statement, targetType, dialect, config, dataSource);
    }

    @Override
    public <V> List<V> executeScalarList(Connection conn, SelectStatement statement, Class<V> targetType,
                                          Dialect dialect, OrmConfig config, DataSource dataSource) {
        return delegate.executeScalarList(getEffectiveConnection(conn), statement, targetType, dialect, config, dataSource);
    }

    @Override
    public long executeCount(Connection conn, SelectStatement statement,
                             Dialect dialect, OrmConfig config, DataSource dataSource) {
        return delegate.executeCount(getEffectiveConnection(conn), statement, dialect, config, dataSource);
    }

    @Override
    public int executeUpdate(Connection conn, SqlAndParams sqlAndParams, String prefix,
                             Dialect dialect, OrmConfig config, DataSource dataSource) {
        return delegate.executeUpdate(getEffectiveConnection(conn), sqlAndParams, prefix, dialect, config, dataSource);
    }

    @Override
    public int executeInsert(Connection conn, InsertStatement statement, EntityMetadata metadata,
                             boolean returnKeys, Object entity, Dialect dialect, OrmConfig config, DataSource dataSource) {
        return delegate.executeInsert(getEffectiveConnection(conn), statement, metadata, returnKeys, entity, dialect, config, dataSource);
    }

    @Override
    public int[] executeBatchUpdate(Connection conn, String sql, List<List<Object>> batchParams,
                                     Dialect dialect, OrmConfig config, DataSource dataSource) {
        return delegate.executeBatchUpdate(getEffectiveConnection(conn), sql, batchParams, dialect, config, dataSource);
    }
}
