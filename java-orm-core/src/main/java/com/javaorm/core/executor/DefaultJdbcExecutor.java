package com.javaorm.core.executor;

import com.javaorm.core.ast.InsertStatement;
import com.javaorm.core.ast.SelectStatement;
import com.javaorm.core.ast.UpsertStatement;
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
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

/**
 * 默认直连 JDBC SQL 执行器。
 */
public class DefaultJdbcExecutor implements SqlExecutor {

    @FunctionalInterface
    private interface SqlAction<R> {
        R run(Connection conn) throws Exception;
    }

    private <R> R withConnection(Connection conn, DataSource dataSource, OrmConfig config,
                                 SelectStatement statement, boolean isWrite, String errorMessage,
                                 SqlAction<R> action) {
        boolean closeConn = false;
        Connection effectiveConn = conn;
        try {
            if (effectiveConn == null) {
                effectiveConn = ConnectionUtils.getConnection(dataSource, config, statement, isWrite);
                closeConn = !ConnectionUtils.isTransactionConnection(effectiveConn);
            }
            return action.run(effectiveConn);
        } catch (Exception e) {
            throw new RuntimeException(errorMessage, e);
        } finally {
            if (closeConn && effectiveConn != null) {
                ConnectionUtils.closeConnection(effectiveConn);
            }
        }
    }

    @Override
    public <R> List<R> executeQuery(Connection conn, SelectStatement statement, EntityMetadata metadata,
                                    List<IncludeSpec> includes, Dialect dialect, OrmConfig config, DataSource dataSource) {
        return withConnection(conn, dataSource, config, statement, false, "Failed to execute query list", effectiveConn -> {
            List<R> result = EntityHydrator.queryList(effectiveConn, statement, metadata, dialect, config);
            if (includes != null && !includes.isEmpty() && result != null && !result.isEmpty()) {
                IncludeProcessor.processIncludes(effectiveConn, result, metadata, includes, dialect, config, dataSource);
            }
            return result;
        });
    }

    @Override
    public <DTO> List<DTO> executeDtoQuery(Connection conn, SelectStatement statement, Class<DTO> dtoClass,
                                           Dialect dialect, OrmConfig config, DataSource dataSource) {
        String errMsg = "Failed to query list for " + (dtoClass != null ? dtoClass.getName() : "DTO");
        return withConnection(conn, dataSource, config, statement, false, errMsg,
                effectiveConn -> EntityHydrator.queryDtoList(effectiveConn, statement, dtoClass, dialect, config));
    }

    @Override
    public List<Map<String, Object>> executeMaps(Connection conn, SelectStatement statement,
                                                 Dialect dialect, OrmConfig config, DataSource dataSource) {
        return withConnection(conn, dataSource, config, statement, false, "Failed to execute listMaps query",
                effectiveConn -> EntityHydrator.queryMaps(effectiveConn, statement, dialect, config));
    }

    @Override
    public <V> V executeScalar(Connection conn, SelectStatement statement, Class<V> targetType,
                               Dialect dialect, OrmConfig config, DataSource dataSource) {
        return withConnection(conn, dataSource, config, statement, false, "Failed to fetch one scalar",
                effectiveConn -> EntityHydrator.queryScalar(effectiveConn, statement, targetType, dialect, config));
    }

    @Override
    public <V> List<V> executeScalarList(Connection conn, SelectStatement statement, Class<V> targetType,
                                         Dialect dialect, OrmConfig config, DataSource dataSource) {
        return withConnection(conn, dataSource, config, statement, false, "Failed to fetch scalar list",
                effectiveConn -> EntityHydrator.queryScalarList(effectiveConn, statement, targetType, dialect, config));
    }

    @Override
    public int executeUpdate(Connection conn, SqlAndParams sqlAndParams, String prefix,
                             Dialect dialect, OrmConfig config, DataSource dataSource) {
        SqlAndParams effective = sqlAndParams;
        if (config != null && config.getInterceptorChain() != null && !config.getInterceptorChain().isEmpty()) {
            effective = config.getInterceptorChain().pluginUpdate(sqlAndParams, null, dialect);
        }
        final SqlAndParams finalSql = effective;
        long start = System.currentTimeMillis();
        Throwable err = null;
        try {
            return withConnection(conn, dataSource, config, null, true,
                    "Failed to execute " + (prefix != null ? prefix : "SQL") + " statement",
                    effectiveConn -> {
                        try (PreparedStatement ps = effectiveConn.prepareStatement(finalSql.getSql())) {
                            bindParams(ps, finalSql.getParams());
                            int rows = ps.executeUpdate();
                            com.javaorm.core.datasource.ReadWriteContext.recordWrite();
                            return rows;
                        }
                    });
        } catch (RuntimeException e) {
            err = e.getCause() != null ? e.getCause() : e;
            throw e;
        } finally {
            triggerAfterExecution(config, finalSql.getSql(), finalSql.getParams(), start, err);
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

        return withConnection(conn, dataSource, config, statement, false, "Failed to execute count query", effectiveConn -> {
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
            }

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
        });
    }

    @Override
    public int executeInsert(Connection conn, InsertStatement statement, EntityMetadata metadata,
                             boolean returnKeys, Object entity, Dialect dialect, OrmConfig config, DataSource dataSource) {
        return executeInsertOrUpsert(conn, dialect.buildInsert(statement), metadata, returnKeys, entity,
                dialect, config, dataSource, "Failed to execute insert statement");
    }

    @Override
    public int executeUpsert(Connection conn, UpsertStatement statement, EntityMetadata metadata,
                             boolean returnKeys, Object entity, Dialect dialect, OrmConfig config, DataSource dataSource) {
        return executeInsertOrUpsert(conn, dialect.buildUpsert(statement), metadata, returnKeys, entity,
                dialect, config, dataSource, "Failed to execute upsert statement");
    }

    private int executeInsertOrUpsert(Connection conn, SqlAndParams sqlAndParams, EntityMetadata metadata,
                                      boolean returnKeys, Object entity, Dialect dialect, OrmConfig config,
                                      DataSource dataSource, String errorMessage) {
        SqlAndParams effective = sqlAndParams;
        if (config != null && config.getInterceptorChain() != null && !config.getInterceptorChain().isEmpty()) {
            effective = config.getInterceptorChain().pluginUpdate(sqlAndParams, metadata, dialect);
        }
        final SqlAndParams finalSql = effective;
        long start = System.currentTimeMillis();
        Throwable err = null;
        try {
            return withConnection(conn, dataSource, config, null, true, errorMessage, effectiveConn -> {
                int autoGeneratedKeys = returnKeys ? Statement.RETURN_GENERATED_KEYS : Statement.NO_GENERATED_KEYS;
                try (PreparedStatement ps = effectiveConn.prepareStatement(finalSql.getSql(), autoGeneratedKeys)) {
                    bindParams(ps, finalSql.getParams());
                    int affected = ps.executeUpdate();
                    com.javaorm.core.datasource.ReadWriteContext.recordWrite();
                    if (returnKeys && entity != null && metadata != null && metadata.getIdColumn() != null) {
                        try (ResultSet rs = ps.getGeneratedKeys()) {
                            if (rs != null && rs.next()) {
                                Object key = rs.getObject(1);
                                metadata.getIdColumn().setValue(entity, key);
                            }
                        }
                    }
                    return affected;
                }
            });
        } catch (RuntimeException e) {
            err = e.getCause() != null ? e.getCause() : e;
            throw e;
        } finally {
            triggerAfterExecution(config, finalSql.getSql(), finalSql.getParams(), start, err);
        }
    }

    @Override
    public int[] executeBatchUpdate(Connection conn, String sql, List<List<Object>> batchParams,
                                    Dialect dialect, OrmConfig config, DataSource dataSource) {
        long start = System.currentTimeMillis();
        Throwable err = null;
        try {
            return withConnection(conn, dataSource, config, null, true, "Failed to execute batch SQL statement", effectiveConn -> {
                try (PreparedStatement ps = effectiveConn.prepareStatement(sql)) {
                    if (batchParams != null) {
                        for (List<Object> params : batchParams) {
                            bindParams(ps, params);
                            ps.addBatch();
                        }
                    }
                    int[] affected = ps.executeBatch();
                    com.javaorm.core.datasource.ReadWriteContext.recordWrite();
                    return affected;
                }
            });
        } catch (RuntimeException e) {
            err = e.getCause() != null ? e.getCause() : e;
            throw e;
        } finally {
            triggerAfterExecution(config, sql, null, start, err);
            RecordingContext.clearRecording();
            SelectBuilder.resetAliasSeq();
        }
    }

    @Override
    public int executeBatchInsert(Connection conn, String sql, List<List<Object>> batchParams, List<?> entities,
                                  EntityMetadata metadata, boolean returnKeys, boolean multiValues,
                                  Dialect dialect, OrmConfig config, DataSource dataSource) {
        long start = System.currentTimeMillis();
        Throwable err = null;
        try {
            return withConnection(conn, dataSource, config, null, true, "Failed to execute batch insert statement", effectiveConn -> {
                if (multiValues) {
                    List<Object> flatParams = (batchParams != null && !batchParams.isEmpty()) ? batchParams.get(0) : java.util.Collections.emptyList();
                    return executeMultiValuesBatchInsert(effectiveConn, sql, flatParams, entities, metadata, returnKeys);
                }
                return executeJdbcBatchInsert(effectiveConn, sql, batchParams, entities, metadata, returnKeys);
            });
        } catch (RuntimeException e) {
            err = e.getCause() != null ? e.getCause() : e;
            throw e;
        } finally {
            triggerAfterExecution(config, sql, null, start, err);
            RecordingContext.clearRecording();
            SelectBuilder.resetAliasSeq();
        }
    }

    private int executeMultiValuesBatchInsert(Connection effectiveConn, String sql, List<Object> flatParams,
                                              List<?> entities, EntityMetadata metadata, boolean returnKeys) throws Exception {
        try {
            int autoKeys = returnKeys ? Statement.RETURN_GENERATED_KEYS : Statement.NO_GENERATED_KEYS;
            try (PreparedStatement ps = effectiveConn.prepareStatement(sql, autoKeys)) {
                bindParams(ps, flatParams);
                int rows = ps.executeUpdate();
                com.javaorm.core.datasource.ReadWriteContext.recordWrite();
                if (returnKeys && entities != null && metadata != null && metadata.getIdColumn() != null) {
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        List<Object> keys = new java.util.ArrayList<>();
                        while (rs != null && rs.next()) {
                            keys.add(rs.getObject(1));
                        }
                        if (keys.size() == entities.size()) {
                            for (int i = 0; i < entities.size(); i++) {
                                Object entity = entities.get(i);
                                if (metadata.getIdColumn().getValue(entity) == null) {
                                    metadata.getIdColumn().setValue(entity, keys.get(i));
                                }
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
                return rows;
            }
        } catch (Exception ex) {
            if (returnKeys) {
                try (PreparedStatement ps = effectiveConn.prepareStatement(sql)) {
                    bindParams(ps, flatParams);
                    int rows = ps.executeUpdate();
                    com.javaorm.core.datasource.ReadWriteContext.recordWrite();
                    return rows;
                }
            }
            throw ex;
        }
    }

    private int executeJdbcBatchInsert(Connection effectiveConn, String sql, List<List<Object>> batchParams,
                                       List<?> entities, EntityMetadata metadata, boolean returnKeys) throws Exception {
        boolean useGeneratedKeys = returnKeys;
        PreparedStatement ps;
        if (useGeneratedKeys) {
            try {
                ps = effectiveConn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            } catch (Exception ex) {
                useGeneratedKeys = false;
                ps = effectiveConn.prepareStatement(sql);
            }
        } else {
            ps = effectiveConn.prepareStatement(sql);
        }
        try (PreparedStatement statement = ps) {
            if (batchParams != null) {
                for (List<Object> params : batchParams) {
                    bindParams(statement, params);
                    statement.addBatch();
                }
            }
            int[] results = statement.executeBatch();
            com.javaorm.core.datasource.ReadWriteContext.recordWrite();
            int sum = 0;
            for (int r : results) {
                if (r >= 0) sum += r;
                else if (r == Statement.SUCCESS_NO_INFO) sum += 1;
            }
            if (useGeneratedKeys && entities != null && metadata != null && metadata.getIdColumn() != null) {
                try (ResultSet rs = statement.getGeneratedKeys()) {
                    int idx = 0;
                    while (rs != null && rs.next() && idx < entities.size()) {
                        Object entity = entities.get(idx++);
                        if (metadata.getIdColumn().getValue(entity) == null) {
                            metadata.getIdColumn().setValue(entity, rs.getObject(1));
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            return sum;
        }
    }

    private static void bindParams(PreparedStatement ps, List<Object> params) throws Exception {
        if (params != null) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
        }
    }

    private static void triggerAfterExecution(OrmConfig config, String sql, List<Object> params, long startMillis, Throwable err) {
        if (config != null && config.getInterceptorChain() != null && !config.getInterceptorChain().isEmpty() && sql != null) {
            config.getInterceptorChain().triggerAfterExecution(sql, params, System.currentTimeMillis() - startMillis, err);
        }
    }
}
