package com.javaorm.core.mutation;

import com.javaorm.core.ast.BinaryCondition;
import com.javaorm.core.ast.Condition;
import com.javaorm.core.ast.LogicalOperator;
import com.javaorm.core.ast.SqlExpression;
import com.javaorm.core.ast.UpdateStatement;
import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.dialect.Dialect;
import com.javaorm.core.dialect.SqlAndParams;
import com.javaorm.core.metadata.ColumnMetadata;
import com.javaorm.core.metadata.EntityMetadata;
import com.javaorm.core.metadata.EntityMetadataManager;
import com.javaorm.core.operator.PropertyOperator;
import com.javaorm.core.operator.SqlOperator;
import com.javaorm.core.proxy.PropertyPath;
import com.javaorm.core.proxy.ProxyFactory;
import com.javaorm.core.proxy.RecordingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.javaorm.core.connection.ConnectionUtils;
import com.javaorm.core.query.Query;
import com.javaorm.core.query.WhereBuilder;
import com.javaorm.core.sql.SqlEnv;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.function.Consumer;
import java.util.function.Function;

public class UpdateBuilder<T> {

    private static final Logger log = LoggerFactory.getLogger(UpdateBuilder.class);

    private final SqlEnv env;
    private final DataSource dataSource;
    private final Dialect dialect;
    private final OrmConfig config;
    private final Class<T> entityClass;
    private final EntityMetadata metadata;
    private Boolean printSqlOverride = null;
    private final UpdateStatement statement;
    private final Query<T> queryHelper;

    public UpdateBuilder(SqlEnv env, Class<T> entityClass) {
        this.env = env != null ? env : new SqlEnv(null, null, null);
        this.dataSource = this.env.getDataSource();
        this.dialect = this.env.getDialect();
        this.config = this.env.getConfig();
        this.entityClass = entityClass;
        this.metadata = EntityMetadataManager.get(entityClass, this.config);
        this.statement = new UpdateStatement(entityClass, metadata != null ? metadata.getTableName() : null);
        this.queryHelper = new Query<>(this.env, entityClass, false);
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
            Object tenantId = this.config != null ? this.config.getCurrentTenantId() : com.javaorm.core.tenant.TenantContext.getTenantId();
            if (tenantId != null) {
                statement.addSystemCondition(new BinaryCondition(null, tc.getColumnName(), SqlOperator.EQ, tenantId));
            } else if (this.config != null && this.config.isTenantStrict()) {
                throw new IllegalStateException("Tenant context is required for multi-tenant entity: " + entityClass.getName());
            }
        }
    }

    public UpdateBuilder(Class<T> entityClass, DataSource dataSource, Dialect dialect, OrmConfig config) {
        this(new SqlEnv(dataSource, dialect, config), entityClass);
    }

    public SqlEnv getEnv() {
        return env;
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public Dialect getDialect() {
        return dialect;
    }

    public OrmConfig getConfig() {
        return config;
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
        this.printSqlOverride = config.isPrintSql();
        return this;
    }

    public UpdateBuilder<T> printSql(boolean print) {
        this.printSqlOverride = print;
        return this;
    }

    public SqlAndParams toSqlAndParams() {
        return getDialect().buildUpdate(statement);
    }

    public String toSql() {
        return toSqlAndParams().getSql();
    }

    public String toSQL() {
        return toSql();
    }

    public String toSql(Dialect customDialect) {
        return (customDialect != null ? customDialect : dialect).buildUpdate(statement).getSql();
    }

    public String toSQL(Dialect customDialect) {
        return toSql(customDialect);
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
            String colName = col != null ? col.getColumnName() : config.getNameConversion().convert(path.getTerminalProperty());
            if (value instanceof SqlExpression) {
                queryHelper.resolveExpression((SqlExpression) value);
            }
            statement.addSet(colName, value);
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
            } else if (config != null && config.getNameConversion() != null) {
                colName = config.getNameConversion().convert(propertyOrColumn);
            }
        }
        if (value instanceof SqlExpression) {
            queryHelper.resolveExpression((SqlExpression) value);
        }
        statement.addSet(colName, value);
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
        String colName = col != null ? col.getColumnName() : config.getNameConversion().convert(path.getTerminalProperty());
        if (value instanceof SqlExpression) {
            queryHelper.resolveExpression((SqlExpression) value);
        }
        statement.addSet(colName, value);
        return this;
    }

    public <R> UpdateBuilder<T> set(Function<T, R> propertyGetter, Object value) {
        PropertyPath path = recordPath(propertyGetter);
        ColumnMetadata col = metadata.getColumnByProperty(path.getTerminalProperty());
        String colName = col != null ? col.getColumnName() : config.getNameConversion().convert(path.getTerminalProperty());
        if (value instanceof SqlExpression) {
            queryHelper.resolveExpression((SqlExpression) value);
        }
        statement.addSet(colName, value);
        return this;
    }

    public WhereBuilder<T> where() {
        return new WhereBuilder<>(env, statement, entityClass, null, statement::addWhereCondition, queryHelper, null, this::execute, this::toSqlAndParams, val -> {
            if (val == null) this.printSql();
            else this.printSql(val);
        });
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
        where().and(idProp).eq(id);
        return this;
    }

    public UpdateBuilder<T> whereByIds(java.util.Collection<?> ids) {
        return whereByIds(true, ids);
    }

    public UpdateBuilder<T> whereByIds(boolean condition, java.util.Collection<?> ids) {
        if (!condition) return this;
        ColumnMetadata idCol = metadata != null ? metadata.getIdColumn() : null;
        String idProp = idCol != null ? idCol.getPropertyName() : "id";
        where().and(idProp).in((java.util.Collection) ids);
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
        for (ColumnMetadata col : metadata.getColumns()) {
            if (col.isPrimaryKey() || col.isIgnored()) {
                continue;
            }
            Object val = col.getValue(entity);
            if (selective && val == null) {
                continue;
            }
            statement.addSet(col.getColumnName(), val);
        }
        return this;
    }

    public int executeRows() {
        return execute();
    }

    public int execute() {
        if (statement.getSetValues().isEmpty()) {
            return 0;
        }
        if (printSqlOverride != null) {
            env.setPrintSql(printSqlOverride);
        }
        return env.executeUpdate(statement);
    }

    public static <E> int updateEntity(E entity, DataSource dataSource, Dialect dialect, OrmConfig config, boolean selective) {
        return updateEntity(entity, new SqlEnv(dataSource, dialect, config), selective);
    }

    public static <E> int updateEntity(E entity, SqlEnv env, boolean selective) {
        if (entity == null) return 0;
        @SuppressWarnings("unchecked")
        Class<E> clazz = (Class<E>) entity.getClass();
        EntityMetadata meta = EntityMetadataManager.get(clazz, env.getConfig());

        if (meta.getIdColumn() == null) {
            throw new IllegalStateException("Entity " + clazz.getName() + " does not have a primary key for update");
        }

        Object idVal = meta.getIdColumn().getValue(entity);
        if (idVal == null) {
            throw new IllegalArgumentException("Primary key value is null for entity update");
        }

        UpdateBuilder<E> builder = new UpdateBuilder<>(env, clazz);
        for (ColumnMetadata col : meta.getColumns()) {
            if (col.isPrimaryKey() || col.isIgnored()) {
                continue;
            }
            Object val = col.getValue(entity);
            if (selective && val == null) {
                continue;
            }
            builder.statement.addSet(col.getColumnName(), val);
        }

        builder.statement.addWhereCondition(new BinaryCondition(null, meta.getIdColumn().getColumnName(), SqlOperator.EQ, idVal));
        return builder.execute();
    }

    private <R> PropertyPath recordPath(Function<T, R> getter) {
        RecordingContext.start(entityClass);
        try {
            T proxy = ProxyFactory.getProxy(entityClass);
            getter.apply(proxy);
            return RecordingContext.finish();
        } finally {
            RecordingContext.clearRecording();
        }
    }

    public static class UpdateWhereBuilder<T> extends WhereBuilder<T> {
        private final UpdateBuilder<T> updateBuilder;

        public UpdateWhereBuilder(Class<T> entityClass, Query<?> rootQuery, Consumer<Condition> receiver, UpdateBuilder<T> updateBuilder) {
            super(entityClass, rootQuery, null, receiver);
            this.updateBuilder = updateBuilder;
        }

        public int execute() {
            return updateBuilder.execute();
        }
    }
}
