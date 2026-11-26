package com.javaorm.core.mutation;

import com.javaorm.core.ast.InsertStatement;
import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.connection.ConnectionUtils;
import com.javaorm.core.dialect.Dialect;
import com.javaorm.core.dialect.SqlAndParams;
import com.javaorm.core.metadata.ColumnMetadata;
import com.javaorm.core.metadata.EntityMetadata;
import com.javaorm.core.metadata.EntityMetadataManager;
import com.javaorm.core.sql.SqlEnv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Date;

public class InsertBuilder<T> extends com.javaorm.core.sql.AbstractStatementBuilder<InsertBuilder<T>> {

    private static final Logger log = LoggerFactory.getLogger(InsertBuilder.class);

    private final T entity;
    private com.javaorm.core.sharding.TableRouter<?> shardingRouter;
    private String targetDataSource;
    private com.javaorm.core.sharding.DataSourceRouter<?> dataSourceRouter;

    public InsertBuilder(SqlEnv env, T entity) {
        super(env);
        this.entity = entity;
    }

    public InsertBuilder(T entity, DataSource dataSource, Dialect dialect, OrmConfig config) {
        this(new SqlEnv(dataSource, dialect, config), entity);
    }

    @Override
    protected void applyTableRouter(com.javaorm.core.sharding.TableRouter<?> router) {
        this.shardingRouter = router;
    }

    @Override
    protected void applyTargetDataSource(String name) {
        this.targetDataSource = name;
    }

    @Override
    protected void applyDataSourceRouter(com.javaorm.core.sharding.DataSourceRouter<?> router) {
        this.dataSourceRouter = router;
    }

    public com.javaorm.core.sharding.TableRouter<?> getShardingRouter() {
        return shardingRouter;
    }

    public String getTargetDataSource() {
        return targetDataSource;
    }

    public com.javaorm.core.sharding.DataSourceRouter<?> getDataSourceRouter() {
        return dataSourceRouter;
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

        if (getConfig() != null && getConfig().getAutoFillHandler() != null) {
            getConfig().getAutoFillHandler().insertFill(entity, metadata);
        }

        InsertStatement stmt = new InsertStatement(entityClass, metadata.getTableName());
        if (this.shardingRouter != null) {
            stmt.setShardingRouter(this.shardingRouter);
        }
        if (this.targetDataSource != null) {
            stmt.setTargetDataSource(this.targetDataSource);
        }
        if (this.dataSourceRouter != null) {
            stmt.setDataSourceRouter(this.dataSourceRouter);
        }

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

            // 处理乐观锁版本号初始值
            if (col.isVersion()) {
                Object val = col.getValue(entity);
                if (val == null) {
                    Class<?> propType = col.getPropertyType();
                    if (propType == Integer.class || propType == int.class) {
                        val = 1;
                    } else if (propType == Long.class || propType == long.class) {
                        val = 1L;
                    } else if (propType == Short.class || propType == short.class) {
                        val = (short) 1;
                    } else if (propType == java.time.LocalDateTime.class) {
                        val = java.time.LocalDateTime.now();
                    } else if (propType == java.util.Date.class) {
                        val = new java.util.Date();
                    }
                    col.setValue(entity, val);
                }
            }

            // 处理逻辑删除初始值
            if (col.isLogicDelete()) {
                Object ldVal = col.getValue(entity);
                if (ldVal == null && col.getLogicDeleteNormalValue() != null) {
                    col.setValue(entity, col.getLogicDeleteNormalValue());
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

    public String toSql(Dialect targetDialect) {
        return toSqlAndParams(targetDialect).getSql();
    }

    @Override
    public String toString() {
        return toSql();
    }

    public int exec() {
        return execute();
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

        return env.getExecutionContext().executeInsert(null, stmt, metadata, returnKeys, entity, this.shardingRouter);
    }
}
