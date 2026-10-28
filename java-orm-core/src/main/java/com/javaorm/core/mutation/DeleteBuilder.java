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

    public SqlAndParams toSqlAndParams() {
        if (metadata.hasLogicDelete()) {
            ColumnMetadata ld = metadata.getLogicDeleteColumn();
            com.javaorm.core.ast.UpdateStatement updateStmt = new com.javaorm.core.ast.UpdateStatement(entityClass, metadata.getTableName());
            updateStmt.addSet(ld.getColumnName(), ld.getLogicDeleteDeletedValue());
            for (Condition c : whereConditions) {
                updateStmt.addWhereCondition(c);
            }
            updateStmt.addWhereCondition(new BinaryCondition(null, ld.getColumnName(), SqlOperator.EQ, ld.getLogicDeleteNormalValue()));
            return dialect.buildUpdate(updateStmt);
        }
        DeleteStatement stmt = new DeleteStatement(entityClass, metadata.getTableName());
        for (Condition c : whereConditions) {
            stmt.addWhereCondition(c);
        }
        return dialect.buildDelete(stmt);
    }

    public String toSql() {
        return toSqlAndParams().getSql();
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

    public DeleteBuilder<T> where(WhereBuilder.BiWhereConsumer<T> consumer) {
        WhereBuilder<T> w = where();
        consumer.accept(w, proxy());
        return this;
    }

    public int execute() {
        try {
            // 如果存在逻辑删除，自动重写为 UPDATE t SET is_deleted = 1 WHERE ...
            if (metadata.hasLogicDelete()) {
                ColumnMetadata ld = metadata.getLogicDeleteColumn();
                return performLogicDelete(ld);
            }

            // 物理删除
            DeleteStatement stmt = new DeleteStatement(entityClass, metadata.getTableName());
            for (Condition c : whereConditions) {
                stmt.addWhereCondition(c);
            }

            SqlAndParams sqlAndParams = dialect.buildDelete(stmt);
            boolean shouldPrint = (printSqlOverride != null) ? printSqlOverride : config.isPrintSql();
            if (shouldPrint) {
                log.info("==>  Preparing: {}", sqlAndParams.getSql());
                log.info("==> Parameters: {}", sqlAndParams.getParams());
            }

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sqlAndParams.getSql())) {

                for (int i = 0; i < sqlAndParams.getParams().size(); i++) {
                    ps.setObject(i + 1, sqlAndParams.getParams().get(i));
                }

                return ps.executeUpdate();
            } catch (Exception e) {
                throw new RuntimeException("Failed to execute delete statement", e);
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
        // 且仅删除未删除状态的数据
        updateStmt.addWhereCondition(new BinaryCondition(null, ld.getColumnName(), SqlOperator.EQ, ld.getLogicDeleteNormalValue()));

        SqlAndParams sqlAndParams = dialect.buildUpdate(updateStmt);
        boolean shouldPrint = (printSqlOverride != null) ? printSqlOverride : config.isPrintSql();
        if (shouldPrint) {
            log.info("==> [LogicDelete] Preparing: {}", sqlAndParams.getSql());
            log.info("==> [LogicDelete] Parameters: {}", sqlAndParams.getParams());
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlAndParams.getSql())) {

            for (int i = 0; i < sqlAndParams.getParams().size(); i++) {
                ps.setObject(i + 1, sqlAndParams.getParams().get(i));
            }

            return ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute logic delete statement", e);
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
