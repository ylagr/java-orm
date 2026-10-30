package com.javaorm.core.mutation;

import com.javaorm.core.ast.BinaryCondition;
import com.javaorm.core.ast.Condition;
import com.javaorm.core.ast.DeleteStatement;
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
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class DeleteBuilder<T> {

    private static final Logger log = LoggerFactory.getLogger(DeleteBuilder.class);

    private final SqlEnv env;
    private final Class<T> entityClass;
    private final EntityMetadata metadata;
    private Boolean printSqlOverride = null;
    private final DeleteStatement statement;
    private boolean logicDeleteDisabled = false;
    private final SelectBuilder<T> queryHelper;

    public DeleteBuilder(SqlEnv env, Class<T> entityClass) {
        this.env = env != null ? env : new SqlEnv(null, null, null);
        this.entityClass = entityClass;
        this.metadata = EntityMetadataManager.get(entityClass, this.env.getConfig());
        this.statement = new DeleteStatement(entityClass, metadata != null ? metadata.getTableName() : null);
        this.queryHelper = new SelectBuilder<>(this.env, entityClass, false);
        if (metadata != null) {
            RecordingContext.registerAlias(entityClass, metadata.getTableName());
        }
    }

    public DeleteBuilder(Class<T> entityClass, DataSource dataSource, Dialect dialect, OrmConfig config) {
        this(new SqlEnv(dataSource, dialect, config), entityClass);
    }

    public SqlEnv getEnv() {
        return env;
    }

    public DeleteStatement getStatement() {
        return statement;
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

    public List<Condition> getWhereConditions() {
        return statement.getWhereConditions();
    }

    public T proxy() {
        return ProxyFactory.getProxy(entityClass);
    }

    public DeleteBuilder<T> printSql() {
        this.printSqlOverride = env.getConfig().isPrintSql();
        env.setPrintSql(this.printSqlOverride);
        return this;
    }

    public DeleteBuilder<T> printSql(boolean print) {
        this.printSqlOverride = print;
        env.setPrintSql(print);
        return this;
    }

    public DeleteBuilder<T> disableLogicDelete() {
        this.logicDeleteDisabled = true;
        return this;
    }

    public SqlAndParams toSqlAndParams() {
        return buildSqlAndParams(env.getDialect());
    }

    public SqlAndParams buildSqlAndParams(Dialect targetDialect) {
        Dialect effDialect = targetDialect != null ? targetDialect : env.getDialect();
        ColumnMetadata tc = metadata.hasTenant() ? metadata.getTenantColumn() : null;
        Object tenantId = (tc != null && env.getConfig() != null) ? env.getConfig().getCurrentTenantId() : (tc != null ? com.javaorm.core.tenant.TenantContext.getTenantId() : null);
        if (tc != null && tenantId == null && env.getConfig() != null && env.getConfig().isTenantStrict()) {
            throw new IllegalStateException("Tenant context is required for multi-tenant entity: " + entityClass.getName());
        }

        if (metadata.hasLogicDelete() && !logicDeleteDisabled) {
            ColumnMetadata ld = metadata.getLogicDeleteColumn();
            com.javaorm.core.ast.UpdateStatement updateStmt = new com.javaorm.core.ast.UpdateStatement(entityClass, metadata.getTableName());
            updateStmt.addSet(ld.getColumnName(), ld.getLogicDeleteDeletedValue());
            for (Condition c : statement.getWhereConditions()) {
                updateStmt.addWhereCondition(c);
            }
            updateStmt.addSystemCondition(new BinaryCondition(null, ld.getColumnName(), SqlOperator.EQ, ld.getLogicDeleteNormalValue()));
            if (tc != null && tenantId != null) {
                updateStmt.addSystemCondition(new BinaryCondition(null, tc.getColumnName(), SqlOperator.EQ, tenantId));
            }
            return effDialect.buildUpdate(updateStmt);
        }
        DeleteStatement stmt = new DeleteStatement(entityClass, metadata.getTableName());
        for (Condition c : statement.getWhereConditions()) {
            stmt.addWhereCondition(c);
        }
        if (tc != null && tenantId != null) {
            stmt.addSystemCondition(new BinaryCondition(null, tc.getColumnName(), SqlOperator.EQ, tenantId));
        }
        return effDialect.buildDelete(stmt);
    }

    public String toSql() {
        return toSqlAndParams().getSql();
    }

    public String toSQL() {
        return toSql();
    }

    public String toSql(Dialect customDialect) {
        return buildSqlAndParams(customDialect).getSql();
    }

    public String toSQL(Dialect customDialect) {
        return toSql(customDialect);
    }

    @Override
    public String toString() {
        return toSql();
    }

    public WhereBuilder<T> where() {
        return new WhereBuilder<>(env, statement, entityClass, null, statement::addWhereCondition, queryHelper, null, this::execute, this::toSqlAndParams, val -> {
            if (val == null) this.printSql();
            else this.printSql(val);
        });
    }

    public DeleteBuilder<T> where(Consumer<WhereBuilder<T>> consumer) {
        consumer.accept(where());
        return this;
    }

    public DeleteBuilder<T> where(boolean condition, Consumer<WhereBuilder<T>> consumer) {
        if (condition && consumer != null) {
            consumer.accept(where());
        }
        return this;
    }

    public DeleteBuilder<T> where(WhereBuilder.BiWhereConsumer<T> consumer) {
        WhereBuilder<T> w = where();
        consumer.accept(w, proxy());
        return this;
    }

    public DeleteBuilder<T> where(boolean condition, WhereBuilder.BiWhereConsumer<T> consumer) {
        if (condition && consumer != null) {
            WhereBuilder<T> w = where();
            consumer.accept(w, proxy());
        }
        return this;
    }

    public DeleteBuilder<T> whereById(Object id) {
        return whereById(true, id);
    }

    public DeleteBuilder<T> whereById(boolean condition, Object id) {
        if (!condition) return this;
        ColumnMetadata idCol = metadata != null ? metadata.getIdColumn() : null;
        String idProp = idCol != null ? idCol.getPropertyName() : "id";
        where().and(idProp).eq(id);
        return this;
    }

    public DeleteBuilder<T> whereByIds(java.util.Collection<?> ids) {
        return whereByIds(true, ids);
    }

    public DeleteBuilder<T> whereByIds(boolean condition, java.util.Collection<?> ids) {
        if (!condition) return this;
        ColumnMetadata idCol = metadata != null ? metadata.getIdColumn() : null;
        String idProp = idCol != null ? idCol.getPropertyName() : "id";
        where().and(idProp).in((java.util.Collection) ids);
        return this;
    }

    public final DeleteBuilder<T> whereByIds(Object... ids) {
        if (ids != null && ids.length == 1 && ids[0] instanceof java.util.Collection) {
            return whereByIds((java.util.Collection<?>) ids[0]);
        }
        return whereByIds(ids != null ? java.util.Arrays.asList(ids) : java.util.Collections.emptyList());
    }

    public final DeleteBuilder<T> whereByIds(boolean condition, Object... ids) {
        if (!condition) return this;
        return whereByIds(ids);
    }

    public int executeRows() {
        return execute();
    }

    public int exec() {
        return execute();
    }

    public int execute() {
        try {
            if (printSqlOverride != null) {
                env.setPrintSql(printSqlOverride);
            }
            // 如果存在逻辑删除，自动重写为 UPDATE t SET is_deleted = 1 WHERE ...
            if (metadata != null && metadata.hasLogicDelete() && !logicDeleteDisabled) {
                return env.executeLogicDelete(metadata, entityClass, statement.getWhereConditions());
            }

            // 物理删除
            if (metadata != null && metadata.hasTenant()) {
                ColumnMetadata tc = metadata.getTenantColumn();
                Object tenantId = env.getConfig() != null ? env.getConfig().getCurrentTenantId() : com.javaorm.core.tenant.TenantContext.getTenantId();
                if (tenantId != null) {
                    statement.addSystemCondition(new BinaryCondition(null, tc.getColumnName(), SqlOperator.EQ, tenantId));
                } else if (env.getConfig() != null && env.getConfig().isTenantStrict()) {
                    throw new IllegalStateException("Tenant context is required for multi-tenant entity: " + entityClass.getName());
                }
            }

            return env.executeDelete(statement);
        } finally {
            SelectBuilder.resetAliasSeq();
        }
    }

    public static <E> int deleteEntity(E entity, DataSource dataSource, Dialect dialect, OrmConfig config) {
        return deleteEntity(entity, new SqlEnv(dataSource, dialect, config));
    }

    public static <E> int deleteEntity(E entity, SqlEnv env) {
        if (entity == null) return 0;
        @SuppressWarnings("unchecked")
        Class<E> clazz = (Class<E>) entity.getClass();
        EntityMetadata meta = EntityMetadataManager.get(clazz, env.getConfig());

        if (meta.getIdColumn() == null) {
            throw new IllegalStateException("Entity " + clazz.getName() + " does not have a primary key for delete");
        }

        Object idVal = meta.getIdColumn().getValue(entity);
        return deleteById(clazz, idVal, env);
    }

    public static <E> int deleteById(Class<E> clazz, Object idVal, DataSource dataSource, Dialect dialect, OrmConfig config) {
        return deleteById(clazz, idVal, new SqlEnv(dataSource, dialect, config));
    }

    public static <E> int deleteById(Class<E> clazz, Object idVal, SqlEnv env) {
        if (idVal == null) {
            throw new IllegalArgumentException("Primary key value is null for delete");
        }
        EntityMetadata meta = EntityMetadataManager.get(clazz, env.getConfig());
        DeleteBuilder<E> builder = new DeleteBuilder<>(env, clazz);
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

    public static class DeleteWhereBuilder<T> extends WhereBuilder<T> {
        private final DeleteBuilder<T> deleteBuilder;

        public DeleteWhereBuilder(Class<T> entityClass, SelectBuilder<?> rootQuery, Consumer<Condition> receiver, DeleteBuilder<T> deleteBuilder) {
            super(entityClass, rootQuery, null, receiver);
            this.deleteBuilder = deleteBuilder;
        }

        public int execute() {
            return deleteBuilder.execute();
        }
    }
}
