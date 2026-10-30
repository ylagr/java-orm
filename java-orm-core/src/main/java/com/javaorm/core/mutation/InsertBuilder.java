package com.javaorm.core.mutation;

import com.javaorm.core.annotation.AutoFillType;
import com.javaorm.core.ast.InsertStatement;
import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.dialect.Dialect;
import com.javaorm.core.dialect.SqlAndParams;
import com.javaorm.core.metadata.ColumnMetadata;
import com.javaorm.core.metadata.EntityMetadata;
import com.javaorm.core.metadata.EntityMetadataManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.javaorm.core.connection.ConnectionUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Date;

import com.javaorm.core.sql.SqlEnv;

public class InsertBuilder<T> {

    private static final Logger log = LoggerFactory.getLogger(InsertBuilder.class);

    private final SqlEnv env;
    private final T entity;

    public InsertBuilder(SqlEnv env, T entity) {
        this.env = env != null ? env : new SqlEnv(null, null, null);
        this.entity = entity;
    }

    public InsertBuilder(T entity, DataSource dataSource, Dialect dialect, OrmConfig config) {
        this(new SqlEnv(dataSource, dialect, config), entity);
    }

    public SqlEnv getEnv() {
        return env;
    }

    public DataSource getDataSource() {
        return env.getDataSource();
    }

    public Dialect getDialect() {
        return env.getDialect();
    }

    public OrmConfig getConfig() {
        return env.getConfig();
    }

    public T getEntity() {
        return entity;
    }

    @SuppressWarnings("unchecked")
    public InsertStatement buildInsertStatement() {
        if (entity == null) {
            return null;
        }

        Class<T> entityClass = (Class<T>) entity.getClass();
        EntityMetadata metadata = EntityMetadataManager.get(entityClass, getConfig());

        InsertStatement stmt = new InsertStatement(entityClass, metadata.getTableName());

        // 遍历所有列
        for (ColumnMetadata col : metadata.getColumns()) {
            if (col.isIgnored()) {
                continue;
            }

            // 如果是主键且当前为 null 或 0，跳过（由数据库自增生成）
            if (col.isPrimaryKey()) {
                Object val = col.getValue(entity);
                if (val == null || (val instanceof Number && ((Number) val).longValue() == 0)) {
                    continue;
                }
            }

            // 处理逻辑删除默认值
            if (col.isLogicDelete()) {
                Object val = col.getValue(entity);
                if (val == null) {
                    val = col.getLogicDeleteNormalValue();
                    col.setValue(entity, val);
                }
            }

            // 处理多租户字段默认填充
            if (col.isTenant()) {
                Object val = col.getValue(entity);
                if (val == null) {
                    Object tenantId = getConfig() != null ? getConfig().getCurrentTenantId() : com.javaorm.core.tenant.TenantContext.getTenantId();
                    if (tenantId != null) {
                        col.setValue(entity, tenantId);
                    } else if (getConfig() != null && getConfig().isTenantStrict()) {
                        throw new IllegalStateException("Tenant context is required for multi-tenant entity: " + entityClass.getName());
                    }
                }
            }

            // 自动填充日期/时间
            Object val = col.getValue(entity);
            if (val == null) {
                if (col.getPropertyType() == LocalDateTime.class) {
                    val = LocalDateTime.now();
                    col.setValue(entity, val);
                } else if (col.getPropertyType() == Date.class) {
                    val = new Date();
                    col.setValue(entity, val);
                }
            }

            stmt.addColumnValue(col.getColumnName(), val);
        }
        return stmt;
    }

    public SqlAndParams toSqlAndParams() {
        return toSqlAndParams(getDialect());
    }

    public SqlAndParams toSqlAndParams(Dialect targetDialect) {
        InsertStatement stmt = buildInsertStatement();
        if (stmt == null) {
            return new SqlAndParams("", java.util.Collections.emptyList());
        }
        return (targetDialect != null ? targetDialect : getDialect()).buildInsert(stmt);
    }

    public String toSql() {
        return toSqlAndParams().getSql();
    }

    public String toSQL() {
        return toSql();
    }

    public String toSql(Dialect targetDialect) {
        return toSqlAndParams(targetDialect).getSql();
    }

    public String toSQL(Dialect targetDialect) {
        return toSql(targetDialect);
    }

    @Override
    public String toString() {
        return toSql();
    }

    public int executeRows() {
        return execute(true);
    }

    public int executeRows(boolean returnKeys) {
        return execute(returnKeys);
    }

    public int execute() {
        return execute(true);
    }

    public int execute(boolean returnKeys) {
        if (entity == null) {
            return 0;
        }

        @SuppressWarnings("unchecked")
        Class<T> entityClass = (Class<T>) entity.getClass();
        EntityMetadata metadata = EntityMetadataManager.get(entityClass, getConfig());
        InsertStatement stmt = buildInsertStatement();
        if (stmt == null) {
            return 0;
        }

        SqlAndParams sqlAndParams = getDialect().buildInsert(stmt);
        if (getConfig() != null && getConfig().isPrintSql()) {
            log.info("==>  Preparing: {}", sqlAndParams.getSql());
            log.info("==> Parameters: {}", sqlAndParams.getParams());
        }

        Connection conn = null;
        boolean isTx = false;
        try {
            conn = ConnectionUtils.getConnection(getDataSource(), getConfig());
            isTx = ConnectionUtils.isTransactionConnection(conn);
            int autoGeneratedKeys = returnKeys ? Statement.RETURN_GENERATED_KEYS : Statement.NO_GENERATED_KEYS;
            try (PreparedStatement ps = conn.prepareStatement(sqlAndParams.getSql(), autoGeneratedKeys)) {

                for (int i = 0; i < sqlAndParams.getParams().size(); i++) {
                    ps.setObject(i + 1, sqlAndParams.getParams().get(i));
                }

                int affected = ps.executeUpdate();

                // 主键自增值回填
                if (returnKeys && metadata.getIdColumn() != null) {
                    try (ResultSet rs = ps.getGeneratedKeys()) {
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
            if (!isTx) ConnectionUtils.closeConnection(conn);
        }
    }
}
