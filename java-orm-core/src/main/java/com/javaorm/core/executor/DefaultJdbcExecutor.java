package com.javaorm.core.executor;

import com.javaorm.core.ast.InsertStatement;
import com.javaorm.core.ast.SelectStatement;
import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.connection.ConnectionUtils;
import com.javaorm.core.dialect.Dialect;
import com.javaorm.core.dialect.SqlAndParams;
import com.javaorm.core.hydration.EntityHydrator;
import com.javaorm.core.hydration.IncludeProcessor;
import com.javaorm.core.metadata.EntityMetadata;
import com.javaorm.core.proxy.RecordingContext;
import com.javaorm.core.query.IncludeSpec;
import com.javaorm.core.query.SelectBuilder;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;

/**
 * 原生 JDBC 执行引擎实现（默认实现）。
 * 负责底层的连接获取与释放、PreparedStatement 参数设置、结果集水合与批量操作。
 */
public class DefaultJdbcExecutor implements SqlExecutor {

    @Override
    public <R> List<R> executeQuery(Connection conn, SelectStatement statement, EntityMetadata metadata,
                                    List<IncludeSpec> includes, Dialect dialect, OrmConfig config, DataSource dataSource) {
        boolean closeConn = false;
        Connection effectiveConn = conn;
        try {
            if (effectiveConn == null) {
                effectiveConn = ConnectionUtils.getConnection(dataSource, config);
                closeConn = !ConnectionUtils.isTransactionConnection(effectiveConn);
            }
            List<R> result = EntityHydrator.queryList(effectiveConn, statement, metadata, dialect, config);
            if (includes != null && !includes.isEmpty() && result != null && !result.isEmpty()) {
                IncludeProcessor.processIncludes(effectiveConn, result, metadata, includes, dialect, config, dataSource);
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute query list", e);
        } finally {
            if (closeConn && effectiveConn != null) {
                ConnectionUtils.closeConnection(effectiveConn);
            }
        }
    }

    @Override
    public <DTO> List<DTO> executeDtoQuery(Connection conn, SelectStatement statement, Class<DTO> dtoClass,
                                           Dialect dialect, OrmConfig config, DataSource dataSource) {
        boolean closeConn = false;
        Connection effectiveConn = conn;
        try {
            if (effectiveConn == null) {
                effectiveConn = ConnectionUtils.getConnection(dataSource, config);
                closeConn = !ConnectionUtils.isTransactionConnection(effectiveConn);
            }
            return EntityHydrator.queryDtoList(effectiveConn, statement, dtoClass, dialect, config);
        } catch (Exception e) {
            throw new RuntimeException("Failed to query list for " + (dtoClass != null ? dtoClass.getName() : "DTO"), e);
        } finally {
            if (closeConn && effectiveConn != null) {
                ConnectionUtils.closeConnection(effectiveConn);
            }
        }
    }

    @Override
    public List<Map<String, Object>> executeMaps(Connection conn, SelectStatement statement,
                                                 Dialect dialect, OrmConfig config, DataSource dataSource) {
        boolean closeConn = false;
        Connection effectiveConn = conn;
        try {
            if (effectiveConn == null) {
                effectiveConn = ConnectionUtils.getConnection(dataSource, config);
                closeConn = !ConnectionUtils.isTransactionConnection(effectiveConn);
            }
            return EntityHydrator.queryMaps(effectiveConn, statement, dialect, config);
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute listMaps query", e);
        } finally {
            if (closeConn && effectiveConn != null) {
                ConnectionUtils.closeConnection(effectiveConn);
            }
        }
    }

    @Override
    public <V> V executeScalar(Connection conn, SelectStatement statement, Class<V> targetType,
                               Dialect dialect, OrmConfig config, DataSource dataSource) {
        boolean closeConn = false;
        Connection effectiveConn = conn;
        try {
            if (effectiveConn == null) {
                effectiveConn = ConnectionUtils.getConnection(dataSource, config);
                closeConn = !ConnectionUtils.isTransactionConnection(effectiveConn);
            }
            return EntityHydrator.queryScalar(effectiveConn, statement, targetType, dialect, config);
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch one scalar", e);
        } finally {
            if (closeConn && effectiveConn != null) {
                ConnectionUtils.closeConnection(effectiveConn);
            }
        }
    }

    @Override
    public <V> List<V> executeScalarList(Connection conn, SelectStatement statement, Class<V> targetType,
                                         Dialect dialect, OrmConfig config, DataSource dataSource) {
        boolean closeConn = false;
        Connection effectiveConn = conn;
        try {
            if (effectiveConn == null) {
                effectiveConn = ConnectionUtils.getConnection(dataSource, config);
                closeConn = !ConnectionUtils.isTransactionConnection(effectiveConn);
            }
            return EntityHydrator.queryScalarList(effectiveConn, statement, targetType, dialect, config);
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch scalar list", e);
        } finally {
            if (closeConn && effectiveConn != null) {
                ConnectionUtils.closeConnection(effectiveConn);
            }
        }
    }

    @Override
    public int executeUpdate(Connection conn, SqlAndParams sqlAndParams, String prefix,
                             Dialect dialect, OrmConfig config, DataSource dataSource) {
        Connection effectiveConn = conn;
        boolean closeConn = false;
        try {
            if (effectiveConn == null) {
                effectiveConn = ConnectionUtils.getConnection(dataSource, config);
                closeConn = !ConnectionUtils.isTransactionConnection(effectiveConn);
            }
            try (PreparedStatement ps = effectiveConn.prepareStatement(sqlAndParams.getSql())) {
                List<Object> params = sqlAndParams.getParams();
                if (params != null) {
                    for (int i = 0; i < params.size(); i++) {
                        ps.setObject(i + 1, params.get(i));
                    }
                }
                return ps.executeUpdate();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute " + (prefix != null ? prefix : "SQL") + " statement", e);
        } finally {
            if (closeConn && effectiveConn != null) {
                ConnectionUtils.closeConnection(effectiveConn);
            }
            RecordingContext.clearRecording();
            SelectBuilder.resetAliasSeq();
        }
    }

    @Override
    public long executeCount(Connection conn, SelectStatement statement, Dialect dialect, OrmConfig config, DataSource dataSource) {
        boolean isComplex = statement.isDistinct()
                || !statement.getGroupByExpressions().isEmpty()
                || !statement.getHavingConditions().isEmpty()
                || !statement.getUnions().isEmpty();

        boolean closeConn = false;
        Connection effectiveConn = conn;
        try {
            if (effectiveConn == null) {
                effectiveConn = ConnectionUtils.getConnection(dataSource, config);
                closeConn = !ConnectionUtils.isTransactionConnection(effectiveConn);
            }

            if (!isComplex) {
                SelectStatement countStmt = new SelectStatement(statement.getRootEntityClass(), statement.getRootTableName(), statement.getRootTableAlias());
                for (com.javaorm.core.ast.CteClause cte : statement.getCtes()) {
                    countStmt.addCte(cte);
                }
                countStmt.addSelectColumn("COUNT(1)");
                for (com.javaorm.core.ast.TableJoin join : statement.getJoins()) {
                    countStmt.addJoin(join);
                }
                for (com.javaorm.core.ast.Condition cond : statement.getSystemConditions()) {
                    countStmt.addSystemCondition(cond);
                }
                for (com.javaorm.core.ast.Condition cond : statement.getWhereConditions()) {
                    countStmt.addWhereCondition(cond);
                }
                return EntityHydrator.queryCount(effectiveConn, countStmt, dialect, config);
            } else {
                SelectStatement innerStmt = new SelectStatement(statement.getRootEntityClass(), statement.getRootTableName(), statement.getRootTableAlias());
                for (com.javaorm.core.ast.CteClause cte : statement.getCtes()) {
                    innerStmt.addCte(cte);
                }
                innerStmt.setDistinct(statement.isDistinct());
                for (com.javaorm.core.ast.SqlExpression expr : statement.getProjectExpressions()) {
                    innerStmt.addProjectExpression(expr);
                }
                for (String col : statement.getSelectColumns()) {
                    innerStmt.addSelectColumn(col);
                }
                if (!statement.getGroupByExpressions().isEmpty() && innerStmt.getProjectExpressions().isEmpty() && innerStmt.getSelectColumns().isEmpty()) {
                    for (com.javaorm.core.ast.SqlExpression expr : statement.getGroupByExpressions()) {
                        innerStmt.addProjectExpression(expr);
                    }
                }
                for (com.javaorm.core.ast.TableJoin join : statement.getJoins()) {
                    innerStmt.addJoin(join);
                }
                for (com.javaorm.core.ast.Condition cond : statement.getSystemConditions()) {
                    innerStmt.addSystemCondition(cond);
                }
                for (com.javaorm.core.ast.Condition cond : statement.getWhereConditions()) {
                    innerStmt.addWhereCondition(cond);
                }
                for (com.javaorm.core.ast.SqlExpression expr : statement.getGroupByExpressions()) {
                    innerStmt.addGroupByExpression(expr);
                }
                for (com.javaorm.core.ast.Condition cond : statement.getHavingConditions()) {
                    innerStmt.addHavingCondition(cond);
                }
                for (com.javaorm.core.ast.UnionClause u : statement.getUnions()) {
                    innerStmt.addUnion(u);
                }

                SqlAndParams countSqlAndParams;
                if (!innerStmt.getCtes().isEmpty()) {
                    SqlAndParams withSql = dialect.buildSelect(innerStmt, true);
                    SqlAndParams innerWithoutCte = dialect.buildSelect(innerStmt, false);
                    String sqlStr = withSql.getSql();
                    int selectIdx = sqlStr.indexOf("SELECT ");
                    String withPrefix = selectIdx > 0 ? sqlStr.substring(0, selectIdx) : "";
                    String countSql = withPrefix + "SELECT COUNT(1) FROM (" + innerWithoutCte.getSql() + ") count_sub";
                    countSqlAndParams = new SqlAndParams(countSql, withSql.getParams());
                } else {
                    SqlAndParams innerSql = dialect.buildSelect(innerStmt);
                    String countSql = "SELECT COUNT(1) FROM (" + innerSql.getSql() + ") count_sub";
                    countSqlAndParams = new SqlAndParams(countSql, innerSql.getParams());
                }
                return EntityHydrator.queryCount(effectiveConn, countSqlAndParams, config, !innerStmt.getCtes().isEmpty(), dialect);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute count query", e);
        } finally {
            if (closeConn && effectiveConn != null) {
                ConnectionUtils.closeConnection(effectiveConn);
            }
        }
    }

    @Override
    public int executeInsert(Connection conn, com.javaorm.core.ast.InsertStatement statement, EntityMetadata metadata,
                             boolean returnKeys, Object entity, Dialect dialect, OrmConfig config, DataSource dataSource) {
        Connection effectiveConn = conn;
        boolean closeConn = false;
        try {
            if (effectiveConn == null) {
                effectiveConn = ConnectionUtils.getConnection(dataSource, config);
                closeConn = !ConnectionUtils.isTransactionConnection(effectiveConn);
            }
            SqlAndParams sqlAndParams = dialect.buildInsert(statement);
            int autoGeneratedKeys = returnKeys ? java.sql.Statement.RETURN_GENERATED_KEYS : java.sql.Statement.NO_GENERATED_KEYS;
            try (PreparedStatement ps = effectiveConn.prepareStatement(sqlAndParams.getSql(), autoGeneratedKeys)) {
                List<Object> params = sqlAndParams.getParams();
                if (params != null) {
                    for (int i = 0; i < params.size(); i++) {
                        ps.setObject(i + 1, params.get(i));
                    }
                }
                int affected = ps.executeUpdate();
                if (returnKeys && entity != null && metadata != null && metadata.getIdColumn() != null) {
                    try (java.sql.ResultSet rs = ps.getGeneratedKeys()) {
                        if (rs != null && rs.next()) {
                            Object key = rs.getObject(1);
                            metadata.getIdColumn().setValue(entity, key);
                        }
                    }
                }
                return affected;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute insert statement", e);
        } finally {
            if (closeConn && effectiveConn != null) {
                ConnectionUtils.closeConnection(effectiveConn);
            }
        }
    }

    @Override
    public int[] executeBatchUpdate(Connection conn, String sql, List<List<Object>> batchParams,
                                    Dialect dialect, OrmConfig config, DataSource dataSource) {
        Connection effectiveConn = conn;
        boolean closeConn = false;
        try {
            if (effectiveConn == null) {
                effectiveConn = ConnectionUtils.getConnection(dataSource, config);
                closeConn = !ConnectionUtils.isTransactionConnection(effectiveConn);
            }
            try (PreparedStatement ps = effectiveConn.prepareStatement(sql)) {
                if (batchParams != null) {
                    for (List<Object> params : batchParams) {
                        if (params != null) {
                            for (int i = 0; i < params.size(); i++) {
                                ps.setObject(i + 1, params.get(i));
                            }
                        }
                        ps.addBatch();
                    }
                }
                return ps.executeBatch();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute batch SQL statement", e);
        } finally {
            if (closeConn && effectiveConn != null) {
                ConnectionUtils.closeConnection(effectiveConn);
            }
            RecordingContext.clearRecording();
            SelectBuilder.resetAliasSeq();
        }
    }
}
