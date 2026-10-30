package com.javaorm.core.mutation;

import com.javaorm.core.ast.BinaryCondition;
import com.javaorm.core.ast.Condition;
import com.javaorm.core.ast.DeleteStatement;
import com.javaorm.core.ast.LogicalOperator;
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

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class DeleteBuilder<T> {

    private static final Logger log = LoggerFactory.getLogger(DeleteBuilder.class);

    private final Class<T> entityClass;
    private final EntityMetadata metadata;
    private final DataSource dataSource;
    private final Dialect dialect;
    private final OrmConfig config;
    private Boolean printSqlOverride = null;
    private final List<Condition> whereConditions = new ArrayList<>();
    private boolean logicDeleteDisabled = false;
    private final Query<T> queryHelper;

    public DeleteBuilder(Class<T> entityClass, DataSource dataSource, Dialect dialect, OrmConfig config) {
        this.entityClass = entityClass;
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.config = config;
        this.metadata = EntityMetadataManager.get(entityClass, config);
        this.queryHelper = new Query<>(entityClass, dataSource, dialect, config, false);
        RecordingContext.registerAlias(entityClass, metadata.getTableName());
    }

    public T proxy() {
        return ProxyFactory.getProxy(entityClass);
    }

    public DeleteBuilder<T> printSql() {
        this.printSqlOverride = config.isPrintSql();
        return this;
    }

    public DeleteBuilder<T> printSql(boolean print) {
        this.printSqlOverride = print;
        return this;
    }

    public DeleteBuilder<T> disableLogicDelete() {
        this.logicDeleteDisabled = true;
        return this;
    }

    public SqlAndParams toSqlAndParams() {
        return buildSqlAndParams(dialect);
    }

    public SqlAndParams buildSqlAndParams(Dialect targetDialect) {
        ColumnMetadata tc = metadata.hasTenant() ? metadata.getTenantColumn() : null;
        Object tenantId = (tc != null && config != null) ? config.getCurrentTenantId() : (tc != null ? com.javaorm.core.tenant.TenantContext.getTenantId() : null);
        if (tc != null && tenantId == null && config != null && config.isTenantStrict()) {
            throw new IllegalStateException("Tenant context is required for multi-tenant entity: " + entityClass.getName());
        }

        if (metadata.hasLogicDelete() && !logicDeleteDisabled) {
            ColumnMetadata ld = metadata.getLogicDeleteColumn();
            com.javaorm.core.ast.UpdateStatement updateStmt = new com.javaorm.core.ast.UpdateStatement(entityClass, metadata.getTableName());
            updateStmt.addSet(ld.getColumnName(), ld.getLogicDeleteDeletedValue());
            for (Condition c : whereConditions) {
                updateStmt.addWhereCondition(c);
            }
            updateStmt.addSystemCondition(new BinaryCondition(null, ld.getColumnName(), SqlOperator.EQ, ld.getLogicDeleteNormalValue()));
            if (tc != null && tenantId != null) {
                updateStmt.addSystemCondition(new BinaryCondition(null, tc.getColumnName(), SqlOperator.EQ, tenantId));
            }
            return (targetDialect != null ? targetDialect : dialect).buildUpdate(updateStmt);
        }
        DeleteStatement stmt = new DeleteStatement(entityClass, metadata.getTableName());
        for (Condition c : whereConditions) {
            stmt.addWhereCondition(c);
        }
        if (tc != null && tenantId != null) {
            stmt.addSystemCondition(new BinaryCondition(null, tc.getColumnName(), SqlOperator.EQ, tenantId));
        }
        return (targetDialect != null ? targetDialect : dialect).buildDelete(stmt);
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
        return new WhereBuilder<>(entityClass, queryHelper, null, whereConditions::add, null, this::execute, this::toSqlAndParams, val -> {
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

    public int execute() {
        try {
            // 如果存在逻辑删除，自动重写为 UPDATE t SET is_deleted = 1 WHERE ...
            if (metadata.hasLogicDelete() && !logicDeleteDisabled) {
                ColumnMetadata ld = metadata.getLogicDeleteColumn();
                return performLogicDelete(ld);
            }

            // 物理删除
            DeleteStatement stmt = new DeleteStatement(entityClass, metadata.getTableName());
            for (Condition c : whereConditions) {
                stmt.addWhereCondition(c);
            }
            if (metadata.hasTenant()) {
                ColumnMetadata tc = metadata.getTenantColumn();
                Object tenantId = config != null ? config.getCurrentTenantId() : com.javaorm.core.tenant.TenantContext.getTenantId();
                if (tenantId != null) {
                    stmt.addSystemCondition(new BinaryCondition(null, tc.getColumnName(), SqlOperator.EQ, tenantId));
                } else if (config != null && config.isTenantStrict()) {
                    throw new IllegalStateException("Tenant context is required for multi-tenant entity: " + entityClass.getName());
                }
            }

            SqlAndParams sqlAndParams = dialect.buildDelete(stmt);
            boolean shouldPrint = (printSqlOverride != null) ? printSqlOverride : config.isPrintSql();
            if (shouldPrint) {
                log.info("==>  Preparing: {}", sqlAndParams.getSql());
                log.info("==> Parameters: {}", sqlAndParams.getParams());
            }

            Connection conn = null;
            boolean isTx = false;
            try {
                conn = ConnectionUtils.getConnection(dataSource, config);
                isTx = ConnectionUtils.isTransactionConnection(conn);
                try (PreparedStatement ps = conn.prepareStatement(sqlAndParams.getSql())) {
                    for (int i = 0; i < sqlAndParams.getParams().size(); i++) {
                        ps.setObject(i + 1, sqlAndParams.getParams().get(i));
                    }
                    return ps.executeUpdate();
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to execute delete statement", e);
            } finally {
                if (!isTx) ConnectionUtils.closeConnection(conn);
            }
        } finally {
            Query.resetAliasSeq();
        }
    }

    private int performLogicDelete(ColumnMetadata ld) {
        return executeLogicDeleteUpdate(ld);
    }

    private int executeLogicDeleteUpdate(ColumnMetadata ld) {
        com.javaorm.core.ast.UpdateStatement updateStmt = new com.javaorm.core.ast.UpdateStatement(entityClass, metadata.getTableName());
        updateStmt.addSet(ld.getColumnName(), ld.getLogicDeleteDeletedValue());

        // 叠加用户传入的 where 条件
        for (Condition c : whereConditions) {
            updateStmt.addWhereCondition(c);
        }
        // 系统条件：且仅删除未删除状态的数据，并注入租户隔离
        updateStmt.addSystemCondition(new BinaryCondition(null, ld.getColumnName(), SqlOperator.EQ, ld.getLogicDeleteNormalValue()));
        if (metadata.hasTenant()) {
            ColumnMetadata tc = metadata.getTenantColumn();
            Object tenantId = config != null ? config.getCurrentTenantId() : com.javaorm.core.tenant.TenantContext.getTenantId();
            if (tenantId != null) {
                updateStmt.addSystemCondition(new BinaryCondition(null, tc.getColumnName(), SqlOperator.EQ, tenantId));
            } else if (config != null && config.isTenantStrict()) {
                throw new IllegalStateException("Tenant context is required for multi-tenant entity: " + entityClass.getName());
            }
        }

        SqlAndParams sqlAndParams = dialect.buildUpdate(updateStmt);
        boolean shouldPrint = (printSqlOverride != null) ? printSqlOverride : config.isPrintSql();
        if (shouldPrint) {
            log.info("==> [LogicDelete] Preparing: {}", sqlAndParams.getSql());
            log.info("==> [LogicDelete] Parameters: {}", sqlAndParams.getParams());
        }

        Connection conn = null;
        boolean isTx = false;
        try {
            conn = ConnectionUtils.getConnection(dataSource, config);
            isTx = ConnectionUtils.isTransactionConnection(conn);
            try (PreparedStatement ps = conn.prepareStatement(sqlAndParams.getSql())) {
                for (int i = 0; i < sqlAndParams.getParams().size(); i++) {
                    ps.setObject(i + 1, sqlAndParams.getParams().get(i));
                }
                return ps.executeUpdate();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute logic delete statement", e);
        } finally {
            if (!isTx) ConnectionUtils.closeConnection(conn);
        }
    }

    public static <E> int deleteEntity(E entity, DataSource dataSource, Dialect dialect, OrmConfig config) {
        if (entity == null) return 0;
        @SuppressWarnings("unchecked")
        Class<E> clazz = (Class<E>) entity.getClass();
        EntityMetadata meta = EntityMetadataManager.get(clazz, config);

        if (meta.getIdColumn() == null) {
            throw new IllegalStateException("Entity " + clazz.getName() + " does not have a primary key for delete");
        }

        Object idVal = meta.getIdColumn().getValue(entity);
        return deleteById(clazz, idVal, dataSource, dialect, config);
    }

    public static <E> int deleteById(Class<E> clazz, Object idVal, DataSource dataSource, Dialect dialect, OrmConfig config) {
        if (idVal == null) {
            throw new IllegalArgumentException("Primary key value is null for delete");
        }
        EntityMetadata meta = EntityMetadataManager.get(clazz, config);
        DeleteBuilder<E> builder = new DeleteBuilder<>(clazz, dataSource, dialect, config);
        builder.whereConditions.add(new BinaryCondition(null, meta.getIdColumn().getColumnName(), SqlOperator.EQ, idVal));
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

        public DeleteWhereBuilder(Class<T> entityClass, Query<?> rootQuery, Consumer<Condition> receiver, DeleteBuilder<T> deleteBuilder) {
            super(entityClass, rootQuery, null, receiver);
            this.deleteBuilder = deleteBuilder;
        }

        public int execute() {
            return deleteBuilder.execute();
        }
    }
}
