package com.javaorm.core.mutation;

import com.javaorm.core.ast.BinaryCondition;
import com.javaorm.core.ast.Condition;
import com.javaorm.core.ast.SqlExpression;
import com.javaorm.core.ast.UpdateStatement;
import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.dialect.Dialect;
import com.javaorm.core.dialect.SqlAndParams;
import com.javaorm.core.metadata.ColumnMetadata;
import com.javaorm.core.metadata.EntityMetadata;
import com.javaorm.core.metadata.EntityMetadataManager;
import com.javaorm.core.operator.SqlOperator;
import com.javaorm.core.proxy.PropertyPath;
import com.javaorm.core.proxy.ProxyFactory;
import com.javaorm.core.proxy.RecordingContext;
import com.javaorm.core.query.SelectBuilder;
import com.javaorm.core.query.WhereBuilder;
import com.javaorm.core.sql.SqlEnv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.function.Consumer;
import java.util.function.Function;

public class UpdateBuilder<T> {

    private static final Logger log = LoggerFactory.getLogger(UpdateBuilder.class);

    private final SqlEnv env;
    private final Class<T> entityClass;
    private final EntityMetadata metadata;
    private Boolean printSqlOverride = null;
    private final UpdateStatement statement;
    private final SelectBuilder<T> queryHelper;

    public UpdateBuilder(SqlEnv env, Class<T> entityClass) {
        this.env = env != null ? env : new SqlEnv(null, null, null);
        this.entityClass = entityClass;
        this.metadata = EntityMetadataManager.get(entityClass, this.env.getConfig());
        this.statement = new UpdateStatement(entityClass, metadata != null ? metadata.getTableName() : null);
        this.queryHelper = new SelectBuilder<>(this.env, entityClass, false);
        if (metadata != null) {
            RecordingContext.registerAlias(entityClass, metadata.getTableName());
        }

        // 自动注入逻辑删除保护（避免误更新已删除记录）
        if (metadata != null && metadata.hasLogicDelete()) {
            ColumnMetadata ld = metadata.getLogicDeleteColumn();
            statement.addSystemCondition(new BinaryCondition(null, ld.getColumnName(), SqlOperator.EQ, ld.getLogicDeleteNormalValue()));
        }

        // 自动注入多租户隔离保护
        if (metadata != null && metadata.hasTenant()) {
            ColumnMetadata tc = metadata.getTenantColumn();
            Object tenantId = this.env.getConfig() != null ? this.env.getConfig().getCurrentTenantId() : com.javaorm.core.tenant.TenantContext.getTenantId();
            if (tenantId != null) {
                statement.addSystemCondition(new BinaryCondition(null, tc.getColumnName(), SqlOperator.EQ, tenantId));
            } else if (this.env.getConfig() != null && this.env.getConfig().isTenantStrict()) {
                throw new IllegalStateException("Tenant context is required for multi-tenant entity: " + entityClass.getName());
            }
        }
    }

    public UpdateBuilder(Class<T> entityClass, DataSource dataSource, Dialect dialect, OrmConfig config) {
        this(new SqlEnv(dataSource, dialect, config), entityClass);
    }

    public UpdateBuilder<T> sharding(com.javaorm.core.sharding.TableRouter<?> router) {
        if (statement != null) {
            statement.setShardingRouter(router);
        }
        return this;
    }

    public UpdateBuilder<T> sharding(Class<? extends com.javaorm.core.sharding.TableRouter<?>> routerClass) {
        if (routerClass != null) {
            try {
                sharding(routerClass.getDeclaredConstructor().newInstance());
            } catch (Exception e) {
                throw new RuntimeException("Failed to instantiate router " + routerClass.getName(), e);
            }
        }
        return this;
    }

    public UpdateBuilder<T> tableRouter(com.javaorm.core.sharding.TableRouter<?> router) {
        return sharding(router);
    }

    public UpdateBuilder<T> tableRouter(Class<? extends com.javaorm.core.sharding.TableRouter<?>> routerClass) {
        return sharding(routerClass);
    }

    public UpdateBuilder<T> dataSource(String name) {
        if (statement != null) {
            statement.setTargetDataSource(name);
        }
        return this;
    }

    public UpdateBuilder<T> dataSource(com.javaorm.core.sharding.DataSourceRouter<?> router) {
        if (statement != null) {
            statement.setDataSourceRouter(router);
        }
        return this;
    }

    public UpdateBuilder<T> dataSource(Class<? extends com.javaorm.core.sharding.DataSourceRouter<?>> routerClass) {
        if (routerClass != null) {
            try {
                dataSource(routerClass.getDeclaredConstructor().newInstance());
            } catch (Exception e) {
                throw new RuntimeException("Failed to instantiate DataSourceRouter " + routerClass.getName(), e);
            }
        }
        return this;
    }

    public UpdateBuilder<T> sharding(com.javaorm.core.sharding.DataSourceRouter<?> router) {
        return dataSource(router);
    }

    public UpdateBuilder<T> allowFullShardingScan(boolean allow) {
        if (statement != null) {
            statement.setAllowFullShardingScan(allow);
        }
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

    public EntityMetadata getMetadata() {
        return metadata;
    }

    public Class<T> getEntityClass() {
        return entityClass;
    }

    public UpdateStatement getStatement() {
        return statement;
    }

    public T proxy() {
        return ProxyFactory.getProxy(entityClass);
    }

    public UpdateBuilder<T> printSql() {
        this.printSqlOverride = getConfig() != null ? getConfig().isPrintSql() : false;
        return this;
    }

    public UpdateBuilder<T> printSql(boolean print) {
        this.printSqlOverride = print;
        return this;
    }

    public SqlAndParams toSqlAndParams() {
        return toSqlAndParams(null);
    }

    public SqlAndParams toSqlAndParams(Dialect customDialect) {
        UpdateStatement targetStmt = statement;
        if (boundEntity != null && statement.getSetValues().isEmpty()) {
            EntityUpdatePlan<T> plan = prepareEntityUpdate(boundEntity, env, boundSelective, false);
            if (plan == null) {
                return new SqlAndParams("", java.util.Collections.emptyList());
            }
            targetStmt = plan.statement;
        }
        return (customDialect != null ? customDialect : getDialect()).buildUpdate(targetStmt);
    }

    public String toSql() {
        return toSqlAndParams().getSql();
    }

    public String toSql(Dialect customDialect) {
        return toSqlAndParams(customDialect).getSql();
    }

    @Override
    public String toString() {
        return toSql();
    }

    public UpdateBuilder<T> set(String propertyOrColumn, Object value) {
        PropertyPath path = RecordingContext.finishCurrent(entityClass);
        if (path == null) {
            path = RecordingContext.finish(entityClass);
        }
        if (path != null) {
            ColumnMetadata col = metadata.getColumnByProperty(path.getTerminalProperty());
            String colName = col != null ? col.getColumnName() : (getConfig() != null && getConfig().getNameConversion() != null ? getConfig().getNameConversion().convert(path.getTerminalProperty()) : path.getTerminalProperty());
            if (value instanceof SqlExpression) {
                queryHelper.resolveExpression((SqlExpression) value);
            }
            Object finalVal = (col != null && !(value instanceof SqlExpression)) ? col.serializeParam(value) : value;
            statement.addSet(colName, finalVal);
            return this;
        }
        if (propertyOrColumn == null) {
            throw new IllegalArgumentException("Set column or property name cannot be null");
        }
        String colName = propertyOrColumn;
        ColumnMetadata col = metadata.getColumnByProperty(propertyOrColumn);
        if (col != null) {
            colName = col.getColumnName();
        } else {
            ColumnMetadata dbCol = metadata.getColumnByDbName(propertyOrColumn);
            if (dbCol != null) {
                colName = dbCol.getColumnName();
                col = dbCol;
            } else if (getConfig() != null && getConfig().getNameConversion() != null) {
                colName = getConfig().getNameConversion().convert(propertyOrColumn);
                col = metadata.getColumnByDbName(colName);
            }
        }
        if (value instanceof SqlExpression) {
            queryHelper.resolveExpression((SqlExpression) value);
        }
        Object finalVal = (col != null && !(value instanceof SqlExpression)) ? col.serializeParam(value) : value;
        statement.addSet(colName, finalVal);
        return this;
    }

    public UpdateBuilder<T> set(boolean condition, Object propVal, Object value) {
        if (condition) {
            set(propVal, value);
        } else {
            RecordingContext.finish(entityClass);
        }
        return this;
    }

    public UpdateBuilder<T> set(Object propVal, Object value) {
        PropertyPath path = RecordingContext.finishCurrent(entityClass);
        if (path == null) {
            path = RecordingContext.finish(entityClass);
        }
        if (path == null) {
            if (propVal instanceof String) {
                return set((String) propVal, value);
            }
            throw new IllegalStateException("No proxy property accessed before calling set(...)! E.g. set(s.getAge(), 20)");
        }
        ColumnMetadata col = metadata.getColumnByProperty(path.getTerminalProperty());
        String colName = col != null ? col.getColumnName() : (getConfig() != null && getConfig().getNameConversion() != null ? getConfig().getNameConversion().convert(path.getTerminalProperty()) : path.getTerminalProperty());
        if (value instanceof SqlExpression) {
            queryHelper.resolveExpression((SqlExpression) value);
        }
        Object finalVal = (col != null && !(value instanceof SqlExpression)) ? col.serializeParam(value) : value;
        statement.addSet(colName, finalVal);
        return this;
    }

    public <R> UpdateBuilder<T> set(Function<T, R> propertyGetter, Object value) {
        PropertyPath path = recordPath(propertyGetter);
        ColumnMetadata col = metadata.getColumnByProperty(path.getTerminalProperty());
        String colName = col != null ? col.getColumnName() : (getConfig() != null && getConfig().getNameConversion() != null ? getConfig().getNameConversion().convert(path.getTerminalProperty()) : path.getTerminalProperty());
        if (value instanceof SqlExpression) {
            queryHelper.resolveExpression((SqlExpression) value);
        }
        Object finalVal = (col != null && !(value instanceof SqlExpression)) ? col.serializeParam(value) : value;
        statement.addSet(colName, finalVal);
        return this;
    }

    public WhereBuilder<T> where() {
        return new WhereBuilder<>(env, statement, entityClass, null, statement::addWhereCondition, queryHelper, null, this::execute, this::toSqlAndParams, val -> {
            if (val == null) this.printSql();
            else this.printSql(val);
        }).setSqlAndParamsDialectFunction(this::toSqlAndParams);
    }

    public UpdateBuilder<T> where(Consumer<WhereBuilder<T>> consumer) {
        consumer.accept(where());
        return this;
    }

    public UpdateBuilder<T> where(boolean condition, Consumer<WhereBuilder<T>> consumer) {
        if (condition && consumer != null) {
            consumer.accept(where());
        }
        return this;
    }

    public UpdateBuilder<T> where(WhereBuilder.BiWhereConsumer<T> consumer) {
        WhereBuilder<T> w = where();
        consumer.accept(w, proxy());
        return this;
    }

    public UpdateBuilder<T> where(boolean condition, WhereBuilder.BiWhereConsumer<T> consumer) {
        if (condition && consumer != null) {
            WhereBuilder<T> w = where();
            consumer.accept(w, proxy());
        }
        return this;
    }

    public UpdateBuilder<T> whereById(Object id) {
        return whereById(true, id);
    }

    public UpdateBuilder<T> whereById(boolean condition, Object id) {
        if (!condition) return this;
        ColumnMetadata idCol = metadata != null ? metadata.getIdColumn() : null;
        String idProp = idCol != null ? idCol.getPropertyName() : "id";
        where().eq(idProp, id);
        return this;
    }

    public UpdateBuilder<T> whereByIds(java.util.Collection<?> ids) {
        return whereByIds(true, ids);
    }

    public UpdateBuilder<T> whereByIds(boolean condition, java.util.Collection<?> ids) {
        if (!condition) return this;
        ColumnMetadata idCol = metadata != null ? metadata.getIdColumn() : null;
        String idProp = idCol != null ? idCol.getPropertyName() : "id";
        where().in(idProp, (java.util.Collection) ids);
        return this;
    }

    public final UpdateBuilder<T> whereByIds(Object... ids) {
        if (ids != null && ids.length == 1 && ids[0] instanceof java.util.Collection) {
            return whereByIds((java.util.Collection<?>) ids[0]);
        }
        return whereByIds(ids != null ? java.util.Arrays.asList(ids) : java.util.Collections.emptyList());
    }

    public final UpdateBuilder<T> whereByIds(boolean condition, Object... ids) {
        if (!condition) return this;
        return whereByIds(ids);
    }

    public UpdateBuilder<T> disableLogicDelete() {
        statement.getSystemConditions().removeIf(c -> {
            if (c instanceof BinaryCondition) {
                BinaryCondition bc = (BinaryCondition) c;
                ColumnMetadata ld = metadata != null ? metadata.getLogicDeleteColumn() : null;
                return ld != null && ld.getColumnName().equalsIgnoreCase(bc.getColumnName());
            }
            return false;
        });
        return this;
    }

    public UpdateBuilder<T> setColumns(T entity) {
        return setColumns(entity, true);
    }

    public UpdateBuilder<T> setColumns(T entity, boolean selective) {
        if (entity == null) return this;
        if (env != null && env.getConfig() != null && env.getConfig().getAutoFillHandler() != null && metadata != null) {
            env.getConfig().getAutoFillHandler().updateFill(entity, metadata);
        }
        com.javaorm.core.track.TrackManager tm = (env != null && env.getConfig() != null) ? env.getConfig().getTrackManager() : null;
        com.javaorm.core.track.EntitySnapshot snapshot = tm != null ? tm.getSnapshot(entity) : null;
        java.util.Set<String> dirtyCols = snapshot != null ? snapshot.getDirtyColumnNames() : null;

        for (ColumnMetadata col : metadata.getColumns()) {
            if (col.isPrimaryKey() || col.isIgnored()) {
                continue;
            }
            if (snapshot != null) {
                if (!dirtyCols.contains(col.getColumnName())) {
                    continue;
                }
            } else {
                Object val = col.getValue(entity);
                if (selective && val == null) {
                    continue;
                }
            }
            Object val = col.getValue(entity);
            statement.addSet(col.getColumnName(), val);
        }
        return this;
    }

    public int executeRows() {
        return execute();
    }

    private T boundEntity;
    private boolean boundSelective = true;

    @SuppressWarnings("unchecked")
    public static <E> UpdateBuilder<E> forEntity(SqlEnv env, E entity, boolean selective) {
        if (entity == null) {
            throw new IllegalArgumentException("Entity must not be null");
        }
        UpdateBuilder<E> builder = new UpdateBuilder<>(env, (Class<E>) entity.getClass());
        builder.boundEntity = entity;
        builder.boundSelective = selective;
        return builder;
    }

    public int exec() {
        return execute();
    }

    public int execute() {
        if (printSqlOverride != null) {
            env.setPrintSql(printSqlOverride);
        }
        if (boundEntity != null && statement.getSetValues().isEmpty()) {
            try {
                return updateEntity(boundEntity, env, boundSelective);
            } finally {
                SelectBuilder.resetAliasSeq();
            }
        }
        if (statement.getSetValues().isEmpty()) {
            return 0;
        }
        return env.executeUpdate(statement);
    }

    public static <E> int updateEntity(E entity, DataSource dataSource, Dialect dialect, OrmConfig config, boolean selective) {
        return updateEntity(entity, new SqlEnv(dataSource, dialect, config), selective);
    }

    private static final class EntityUpdatePlan<E> {
        final Class<E> entityClass;
        final Object idVal;
        final UpdateStatement statement;
        final ColumnMetadata versionCol;
        final Object currentVersion;
        final Object nextVersion;
        final com.javaorm.core.track.EntitySnapshot snapshot;

        EntityUpdatePlan(Class<E> entityClass, Object idVal, UpdateStatement statement,
                         ColumnMetadata versionCol, Object currentVersion, Object nextVersion,
                         com.javaorm.core.track.EntitySnapshot snapshot) {
            this.entityClass = entityClass;
            this.idVal = idVal;
            this.statement = statement;
            this.versionCol = versionCol;
            this.currentVersion = currentVersion;
            this.nextVersion = nextVersion;
            this.snapshot = snapshot;
        }
    }

    private static <E> EntityUpdatePlan<E> prepareEntityUpdate(E entity, SqlEnv env, boolean selective, boolean triggerAutoFill) {
        if (entity == null) return null;
        @SuppressWarnings("unchecked")
        Class<E> clazz = (Class<E>) entity.getClass();
        EntityMetadata meta = EntityMetadataManager.get(clazz, env.getConfig());

        if (triggerAutoFill && env.getConfig() != null && env.getConfig().getAutoFillHandler() != null) {
            env.getConfig().getAutoFillHandler().updateFill(entity, meta);
        }

        if (meta.getIdColumn() == null) {
            throw new IllegalStateException("Entity " + clazz.getName() + " does not have a primary key for update");
        }

        Object idVal = meta.getIdColumn().getValue(entity);
        if (idVal == null) {
            throw new IllegalArgumentException("Primary key value is null for entity update");
        }

        // 实体快照变脏检测 (Dirty Checking)
        com.javaorm.core.track.TrackManager trackManager = env.getConfig() != null ? env.getConfig().getTrackManager() : null;
        com.javaorm.core.track.EntitySnapshot snapshot = trackManager != null ? trackManager.getSnapshot(entity) : null;
        java.util.Set<String> dirtyColumns = snapshot != null ? snapshot.getDirtyColumnNames() : null;

        if (snapshot != null && (dirtyColumns == null || dirtyColumns.isEmpty())) {
            return null;
        }

        UpdateBuilder<E> builder = new UpdateBuilder<>(env, clazz);
        ColumnMetadata versionCol = meta.getVersionColumn();
        Object currentVersion = null;
        Object nextVersion = null;

        for (ColumnMetadata col : meta.getColumns()) {
            if (col.isPrimaryKey() || col.isIgnored() || col.isVersion()) {
                continue;
            }
            if (snapshot != null) {
                if (!dirtyColumns.contains(col.getColumnName())) {
                    continue;
                }
            } else if (selective && col.getValue(entity) == null) {
                continue;
            }
            builder.statement.addSet(col.getColumnName(), col.getValue(entity));
        }

        if (versionCol != null) {
            currentVersion = versionCol.getValue(entity);
            if (currentVersion == null) {
                currentVersion = 1;
            }
            if (currentVersion instanceof Number) {
                long nv = ((Number) currentVersion).longValue() + 1;
                Class<?> propType = versionCol.getPropertyType();
                if (propType == Integer.class || propType == int.class) {
                    nextVersion = (int) nv;
                } else if (propType == Long.class || propType == long.class) {
                    nextVersion = nv;
                } else if (propType == Short.class || propType == short.class) {
                    nextVersion = (short) nv;
                } else {
                    nextVersion = nv;
                }
            } else if (currentVersion instanceof java.time.LocalDateTime) {
                nextVersion = java.time.LocalDateTime.now();
            } else if (currentVersion instanceof java.util.Date) {
                nextVersion = new java.util.Date();
            } else {
                nextVersion = 1;
            }

            builder.statement.addSet(versionCol.getColumnName(), nextVersion);
            builder.statement.addWhereCondition(new BinaryCondition(null, versionCol.getColumnName(), SqlOperator.EQ, currentVersion));
        }

        builder.statement.addWhereCondition(new BinaryCondition(null, meta.getIdColumn().getColumnName(), SqlOperator.EQ, idVal));
        return new EntityUpdatePlan<>(clazz, idVal, builder.statement, versionCol, currentVersion, nextVersion, snapshot);
    }

    public static <E> int updateEntity(E entity, SqlEnv env, boolean selective) {
        EntityUpdatePlan<E> plan = prepareEntityUpdate(entity, env, selective, true);
        if (plan == null || plan.statement.getSetValues().isEmpty()) {
            return 0;
        }
        int affected = env.executeUpdate(plan.statement);
        if (plan.versionCol != null) {
            if (affected == 0) {
                throw new com.javaorm.core.exception.OptimisticLockException(plan.entityClass, plan.idVal, plan.currentVersion);
            }
            plan.versionCol.setValue(entity, plan.nextVersion);
        }
        if (plan.snapshot != null && affected > 0) {
            plan.snapshot.updateSnapshot();
        }
        return affected;
    }

    private <R> PropertyPath recordPath(Function<T, R> getter) {
        return RecordingContext.recordPath(entityClass, getter);
    }

    public static class UpdateWhereBuilder<T> extends WhereBuilder<T> {
        private final UpdateBuilder<T> updateBuilder;

        public UpdateWhereBuilder(Class<T> entityClass, SelectBuilder<?> rootQuery, Consumer<Condition> receiver, UpdateBuilder<T> updateBuilder) {
            super(entityClass, rootQuery, null, receiver);
            this.updateBuilder = updateBuilder;
        }

        public int execute() {
            return updateBuilder.execute();
        }
    }
}
