package com.javaorm.core.mutation;

import com.javaorm.core.ast.UpsertStatement;
import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.connection.ConnectionUtils;
import com.javaorm.core.dialect.Dialect;
import com.javaorm.core.dialect.SqlAndParams;
import com.javaorm.core.metadata.ColumnMetadata;
import com.javaorm.core.metadata.EntityMetadata;
import com.javaorm.core.metadata.EntityMetadataManager;
import com.javaorm.core.proxy.PropertyPath;
import com.javaorm.core.proxy.ProxyFactory;
import com.javaorm.core.proxy.RecordingContext;
import com.javaorm.core.sql.SqlEnv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;

/**
 * 跨方言实体级 UPSERT（存在即更新，不存在即插入）构建器。
 * 针对各主流数据库（MySQL, PostgreSQL, SQLite, H2, Oracle）自适应生成对应的原子 UPSERT / MERGE 语法。
 *
 * @param <T> 实体类型
 */
public class UpsertBuilder<T> extends com.javaorm.core.sql.AbstractStatementBuilder<UpsertBuilder<T>> {

    private static final Logger log = LoggerFactory.getLogger(UpsertBuilder.class);

    private final T entity;
    private final Class<T> entityClass;
    private final EntityMetadata metadata;
    private final UpsertSpec<T> spec = new UpsertSpec<>();

    private com.javaorm.core.sharding.TableRouter<?> shardingRouter;
    private String targetDataSource;
    private com.javaorm.core.sharding.DataSourceRouter<?> dataSourceRouter;

    @SuppressWarnings("unchecked")
    public UpsertBuilder(SqlEnv env, T entity) {
        super(env);
        this.entity = entity;
        this.entityClass = entity != null ? (Class<T>) entity.getClass() : null;
        this.metadata = entityClass != null ? EntityMetadataManager.get(entityClass, this.env.getConfig()) : null;
    }

    public UpsertBuilder(T entity, DataSource dataSource, Dialect dialect, OrmConfig config) {
        this(new SqlEnv(dataSource, dialect, config), entity);
    }

    /**
     * 指定作为冲突判定的列名（如主键或唯一索引列）
     */
    public UpsertBuilder<T> onConflict(String... columns) {
        spec.addConflictColumns(metadata, columns);
        return this;
    }

    /**
     * 通过强类型 Getter 代理指定冲突判定的属性
     */
    public UpsertBuilder<T> onConflict(Consumer<T> proxyAction) {
        spec.addConflictColumns(entityClass, metadata, proxyAction);
        return this;
    }

    /**
     * 显式指定发生冲突时仅更新的列名
     */
    public UpsertBuilder<T> updateColumns(String... columns) {
        spec.addUpdateColumns(metadata, columns);
        return this;
    }

    /**
     * 通过强类型 Getter 代理显式指定发生冲突时仅更新的属性
     */
    public UpsertBuilder<T> updateColumns(Consumer<T> proxyAction) {
        spec.addUpdateColumns(entityClass, metadata, proxyAction);
        return this;
    }

    /**
     * 忽略更新的列名（例如创建时间、创建人等）
     */
    public UpsertBuilder<T> ignoreColumns(String... columns) {
        spec.addIgnoreColumns(metadata, columns);
        return this;
    }

    /**
     * 存在冲突时不做任何更新操作（DO NOTHING）
     */
    public UpsertBuilder<T> doNothing() {
        spec.setDoNothing(true);
        return this;
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

    public T getEntity() {
        return entity;
    }

    public UpsertStatement buildUpsertStatement() {
        if (entity == null || metadata == null) {
            return null;
        }

        // 触发自动填充
        if (getConfig() != null && getConfig().getAutoFillHandler() != null) {
            getConfig().getAutoFillHandler().insertFill(entity, metadata);
        }

        UpsertStatement stmt = new UpsertStatement(entityClass, metadata.getTableName());
        if (this.shardingRouter != null) {
            stmt.setShardingRouter(this.shardingRouter);
        }
        if (this.targetDataSource != null) {
            stmt.setTargetDataSource(this.targetDataSource);
        }
        if (this.dataSourceRouter != null) {
            stmt.setDataSourceRouter(this.dataSourceRouter);
        }

        // 收集所有列和值
        for (ColumnMetadata col : metadata.getColumns()) {
            if (col.isIgnored()) {
                continue;
            }

            // 处理逻辑删除默认值
            if (col.isLogicDelete()) {
                Object val = col.getValue(entity);
                if (val == null) {
                    val = col.getLogicDeleteNormalValue();
                    col.setValue(entity, val);
                }
            }

            // 处理多租户默认值
            if (col.isTenant()) {
                Object val = col.getValue(entity);
                if (val == null) {
                    Object tenantId = getConfig() != null ? getConfig().getCurrentTenantId() : com.javaorm.core.tenant.TenantContext.getTenantId();
                    if (tenantId != null) {
                        col.setValue(entity, tenantId);
                    }
                }
            }

            // 处理版本号初始值
            if (col.isVersion()) {
                Object val = col.getValue(entity);
                if (val == null) {
                    col.setValue(entity, 1);
                }
            }

            Object val = col.getValue(entity);
            // 主键自增为 null 或 0 且未在 conflictColumns 中时，插入时可由数据库生成
            if (col.isPrimaryKey() && (val == null || (val instanceof Number && ((Number) val).longValue() == 0))) {
                if (spec.containsConflictColumn(col.getColumnName())) {
                    stmt.addColumnValue(col.getColumnName(), col.serializeParam(val));
                }
                continue;
            }

            stmt.addColumnValue(col.getColumnName(), col.serializeParam(val));
        }

        spec.applyTo(stmt, metadata, entityClass, stmt.getColumns());
        return stmt;
    }

    public SqlAndParams toSqlAndParams() {
        return toSqlAndParams(getDialect());
    }

    public SqlAndParams toSqlAndParams(Dialect targetDialect) {
        UpsertStatement stmt = buildUpsertStatement();
        if (stmt == null) {
            return new SqlAndParams("", Collections.emptyList());
        }
        return (targetDialect != null ? targetDialect : getDialect()).buildUpsert(stmt);
    }

    public String toSql() {
        return toSqlAndParams().getSql();
    }

    public String toSql(Dialect targetDialect) {
        return toSqlAndParams(targetDialect).getSql();
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

        UpsertStatement stmt = buildUpsertStatement();
        if (stmt == null) {
            return 0;
        }

        return getExecutionContext().executeUpsert(null, stmt, metadata, returnKeys, entity, this.shardingRouter);
    }
}
