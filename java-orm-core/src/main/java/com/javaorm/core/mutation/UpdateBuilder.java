package com.javaorm.core.mutation;

import com.javaorm.core.ast.BinaryCondition;
import com.javaorm.core.ast.Condition;
import com.javaorm.core.ast.LogicalOperator;
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

import com.javaorm.core.query.Query;
import com.javaorm.core.query.WhereBuilder;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.function.Consumer;
import java.util.function.Function;

public class UpdateBuilder<T> {

    private static final Logger log = LoggerFactory.getLogger(UpdateBuilder.class);

    private final Class<T> entityClass;
    private final EntityMetadata metadata;
    private final DataSource dataSource;
    private final Dialect dialect;
    private final OrmConfig config;
    private final UpdateStatement statement;
    private final Query<T> queryHelper;

    public UpdateBuilder(Class<T> entityClass, DataSource dataSource, Dialect dialect, OrmConfig config) {
        this.entityClass = entityClass;
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.config = config;
        this.metadata = EntityMetadataManager.get(entityClass, config);
        this.statement = new UpdateStatement(entityClass, metadata.getTableName());
        this.queryHelper = new Query<>(entityClass, dataSource, dialect, config);
        RecordingContext.registerAlias(entityClass, metadata.getTableName());

        // 自动注入逻辑删除保护（避免误更新已删除记录）
        if (metadata.hasLogicDelete()) {
            ColumnMetadata ld = metadata.getLogicDeleteColumn();
            statement.addWhereCondition(new BinaryCondition(null, ld.getColumnName(), SqlOperator.EQ, ld.getLogicDeleteNormalValue()));
        }
    }

    public T proxy() {
        return ProxyFactory.getProxy(entityClass);
    }

    public UpdateBuilder<T> set(Object propVal, Object value) {
        PropertyPath path = RecordingContext.finishCurrent();
        if (path == null) {
            path = RecordingContext.finish();
        }
        if (path == null) {
            throw new IllegalStateException("No proxy property accessed before calling set(...)! E.g. set(s.getAge(), 20)");
        }
        ColumnMetadata col = metadata.getColumnByProperty(path.getTerminalProperty());
        String colName = col != null ? col.getColumnName() : config.getNameConversion().convert(path.getTerminalProperty());
        statement.addSet(colName, value);
        return this;
    }

    public <R> UpdateBuilder<T> set(Function<T, R> propertyGetter, Object value) {
        PropertyPath path = recordPath(propertyGetter);
        ColumnMetadata col = metadata.getColumnByProperty(path.getTerminalProperty());
        String colName = col != null ? col.getColumnName() : config.getNameConversion().convert(path.getTerminalProperty());
        statement.addSet(colName, value);
        return this;
    }

    public WhereBuilder<T> where() {
        return new WhereBuilder<>(entityClass, queryHelper, null, statement::addWhereCondition, this::execute);
    }

    public UpdateBuilder<T> where(Consumer<WhereBuilder<T>> consumer) {
        consumer.accept(where());
        return this;
    }

    public UpdateBuilder<T> where(WhereBuilder.BiWhereConsumer<T> consumer) {
        WhereBuilder<T> w = where();
        consumer.accept(w, proxy());
        return this;
    }

    public int execute() {
        if (statement.getSetValues().isEmpty()) {
            return 0;
        }

        SqlAndParams sqlAndParams = dialect.buildUpdate(statement);
        if (config.isPrintSql()) {
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
            throw new RuntimeException("Failed to execute update statement", e);
        }
    }

    public static <E> int updateEntity(E entity, DataSource dataSource, Dialect dialect, OrmConfig config, boolean selective) {
        if (entity == null) return 0;
        @SuppressWarnings("unchecked")
        Class<E> clazz = (Class<E>) entity.getClass();
        EntityMetadata meta = EntityMetadataManager.get(clazz, config);

        if (meta.getIdColumn() == null) {
            throw new IllegalStateException("Entity " + clazz.getName() + " does not have a primary key for update");
        }

        Object idVal = meta.getIdColumn().getValue(entity);
        if (idVal == null) {
            throw new IllegalArgumentException("Primary key value is null for entity update");
        }

        UpdateBuilder<E> builder = new UpdateBuilder<>(clazz, dataSource, dialect, config);
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
            RecordingContext.clear();
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
