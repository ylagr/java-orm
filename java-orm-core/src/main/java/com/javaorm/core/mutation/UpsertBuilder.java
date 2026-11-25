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
public class UpsertBuilder<T> {

    private static final Logger log = LoggerFactory.getLogger(UpsertBuilder.class);

    private final SqlEnv env;
    private final T entity;
    private final Class<T> entityClass;
    private final EntityMetadata metadata;

    private final List<String> conflictColumns = new ArrayList<>();
    private final List<String> updateColumns = new ArrayList<>();
    private final Set<String> ignoreColumns = new HashSet<>();
    private boolean doNothing = false;
    private Boolean printSqlOverride = null;

    private com.javaorm.core.sharding.TableRouter<?> shardingRouter;
    private String targetDataSource;
    private com.javaorm.core.sharding.DataSourceRouter<?> dataSourceRouter;

    @SuppressWarnings("unchecked")
    public UpsertBuilder(SqlEnv env, T entity) {
        this.env = env != null ? env : new SqlEnv(null, null, null);
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
        if (columns != null) {
            for (String col : columns) {
                if (col != null && !col.trim().isEmpty()) {
                    String resolved = resolveColumnName(col.trim());
                    if (!this.conflictColumns.contains(resolved)) {
                        this.conflictColumns.add(resolved);
                    }
                }
            }
        }
        return this;
    }

    /**
     * 通过强类型 Getter 代理指定冲突判定的属性
     */
    public UpsertBuilder<T> onConflict(Consumer<T> proxyAction) {
        if (proxyAction != null && entityClass != null) {
            T proxy = ProxyFactory.getProxy(entityClass);
            proxyAction.accept(proxy);
            PropertyPath path = RecordingContext.finishCurrent(entityClass);
            if (path == null) {
                path = RecordingContext.finish(entityClass);
            }
            if (path != null) {
                String resolved = resolveColumnName(path.getTerminalProperty());
                if (!this.conflictColumns.contains(resolved)) {
                    this.conflictColumns.add(resolved);
                }
            }
        }
        return this;
    }

    /**
     * 显式指定发生冲突时仅更新的列名
     */
    public UpsertBuilder<T> updateColumns(String... columns) {
        if (columns != null) {
            for (String col : columns) {
                if (col != null && !col.trim().isEmpty()) {
                    String resolved = resolveColumnName(col.trim());
                    if (!this.updateColumns.contains(resolved)) {
                        this.updateColumns.add(resolved);
                    }
                }
            }
        }
        return this;
    }

    /**
     * 通过强类型 Getter 代理显式指定发生冲突时仅更新的属性
     */
    public UpsertBuilder<T> updateColumns(Consumer<T> proxyAction) {
        if (proxyAction != null && entityClass != null) {
            T proxy = ProxyFactory.getProxy(entityClass);
            proxyAction.accept(proxy);
            PropertyPath path = RecordingContext.finishCurrent(entityClass);
            if (path == null) {
                path = RecordingContext.finish(entityClass);
            }
            if (path != null) {
                String resolved = resolveColumnName(path.getTerminalProperty());
                if (!this.updateColumns.contains(resolved)) {
                    this.updateColumns.add(resolved);
                }
            }
        }
        return this;
    }

    /**
     * 忽略更新的列名（例如创建时间、创建人等）
     */
    public UpsertBuilder<T> ignoreColumns(String... columns) {
        if (columns != null) {
            for (String col : columns) {
                if (col != null && !col.trim().isEmpty()) {
                    this.ignoreColumns.add(resolveColumnName(col.trim()));
                }
            }
        }
        return this;
    }

    /**
     * 存在冲突时不做任何更新操作（DO NOTHING）
     */
    public UpsertBuilder<T> doNothing() {
        this.doNothing = true;
        return this;
    }

    public UpsertBuilder<T> printSql() {
        this.printSqlOverride = getConfig() != null ? getConfig().isPrintSql() : false;
        return this;
    }

    public UpsertBuilder<T> printSql(boolean print) {
        this.printSqlOverride = print;
        return this;
    }

    public UpsertBuilder<T> sharding(com.javaorm.core.sharding.TableRouter<?> router) {
        this.shardingRouter = router;
        return this;
    }

    public UpsertBuilder<T> dataSource(String name) {
        this.targetDataSource = name;
        return this;
    }

    public UpsertBuilder<T> dataSource(com.javaorm.core.sharding.DataSourceRouter<?> router) {
        this.dataSourceRouter = router;
        return this;
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
        stmt.setDoNothing(this.doNothing);

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
                if (this.conflictColumns.contains(col.getColumnName())) {
                    stmt.addColumnValue(col.getColumnName(), col.serializeParam(val));
                }
                continue;
            }

            stmt.addColumnValue(col.getColumnName(), col.serializeParam(val));
        }

        // 解析冲突判定列 conflictColumns
        List<String> finalConflictCols = new ArrayList<>(this.conflictColumns);
        if (finalConflictCols.isEmpty()) {
            if (metadata.getIdColumn() != null) {
                finalConflictCols.add(metadata.getIdColumn().getColumnName());
            } else if (!metadata.getIndexes().isEmpty()) {
                for (com.javaorm.core.metadata.IndexMetadata idx : metadata.getIndexes()) {
                    if (idx.isUnique() && !idx.getColumns().isEmpty()) {
                        finalConflictCols.addAll(idx.getColumns());
                        break;
                    }
                }
            }
        }
        if (finalConflictCols.isEmpty()) {
            throw new IllegalStateException("UPSERT requires a primary key or explicit onConflict columns for entity: " + entityClass.getName());
        }
        stmt.addConflictColumns(finalConflictCols);

        // 解析更新列 updateColumns
        if (!this.doNothing) {
            List<String> finalUpdateCols = new ArrayList<>(this.updateColumns);
            if (finalUpdateCols.isEmpty()) {
                for (String colName : stmt.getColumns()) {
                    if (finalConflictCols.contains(colName)) {
                        continue;
                    }
                    if (metadata.getIdColumn() != null && colName.equalsIgnoreCase(metadata.getIdColumn().getColumnName())) {
                        continue;
                    }
                    if (ignoreColumns.contains(colName)) {
                        continue;
                    }
                    // 默认跳过常见创建时间/创建人字段
                    if (isDefaultCreationAuditColumn(colName)) {
                        continue;
                    }
                    finalUpdateCols.add(colName);
                }
            }
            stmt.addUpdateColumns(finalUpdateCols);
        }

        return stmt;
    }

    private boolean isDefaultCreationAuditColumn(String colName) {
        if (colName == null) return false;
        String lower = colName.toLowerCase();
        return lower.equals("create_time") || lower.equals("createtime") || lower.equals("created_at")
                || lower.equals("gmt_create") || lower.equals("create_by") || lower.equals("createby") || lower.equals("creator");
    }

    private String resolveColumnName(String propOrCol) {
        if (metadata != null) {
            ColumnMetadata col = metadata.getColumnByProperty(propOrCol);
            if (col != null) return col.getColumnName();
            col = metadata.getColumnByDbName(propOrCol);
            if (col != null) return col.getColumnName();
        }
        return propOrCol;
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

    public int executeRows() {
        return execute(true);
    }

    public int executeRows(boolean returnKeys) {
        return execute(returnKeys);
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

        return env.executeUpsert(null, stmt, metadata, returnKeys, entity, this.shardingRouter);
    }
}
