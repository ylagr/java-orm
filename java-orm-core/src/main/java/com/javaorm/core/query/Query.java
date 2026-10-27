package com.javaorm.core.query;

import com.javaorm.core.ast.*;
import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.dialect.Dialect;
import com.javaorm.core.hydration.EntityHydrator;
import com.javaorm.core.hydration.IncludeProcessor;
import com.javaorm.core.metadata.ColumnMetadata;
import com.javaorm.core.metadata.EntityMetadata;
import com.javaorm.core.metadata.EntityMetadataManager;
import com.javaorm.core.metadata.RelationMetadata;
import com.javaorm.core.operator.ConditionAppender;
import com.javaorm.core.operator.PropertyOperator;
import com.javaorm.core.operator.SqlOperator;
import com.javaorm.core.proxy.PropertyPath;
import com.javaorm.core.proxy.ProxyFactory;
import com.javaorm.core.proxy.RecordingContext;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 核心 Fluent 查询构建器。
 */
public class Query<T> {

    private final Class<T> rootEntityClass;
    private final EntityMetadata rootMetadata;
    private final DataSource dataSource;
    private final Dialect dialect;
    private final OrmConfig config;

    private final SelectStatement statement;
    private final Map<String, String> relationJoinAliases = new LinkedHashMap<>();
    private int aliasCounter = 1;

    private final List<PropertyPath> includes = new ArrayList<>();

    public Query(Class<T> rootEntityClass, DataSource dataSource, Dialect dialect, OrmConfig config) {
        this.rootEntityClass = rootEntityClass;
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.config = config;
        this.rootMetadata = EntityMetadataManager.get(rootEntityClass, config);
        this.statement = new SelectStatement(rootEntityClass, rootMetadata.getTableName(), "t0");

        // 默认自动注入逻辑删除过滤条件
        if (rootMetadata.hasLogicDelete()) {
            ColumnMetadata ld = rootMetadata.getLogicDeleteColumn();
            statement.addWhereCondition(new BinaryCondition("t0", ld.getColumnName(), SqlOperator.EQ, ld.getLogicDeleteNormalValue()));
        }
    }

    /**
     * 开启 WHERE 条件构建块
     */
    public WhereBuilder<T> where() {
        return new WhereBuilder<>(this);
    }

    /**
     * 级联子表作用域过滤
     */
    public <TSub> Query<T> whereRelation(Function<T, TSub> relationGetter, Consumer<WhereBuilder<TSub>> subBuilderConsumer) {
        RecordingContext.start(rootEntityClass);
        try {
            T proxy = ProxyFactory.getProxy(rootEntityClass);
            relationGetter.apply(proxy);
            PropertyPath path = RecordingContext.finish();
            if (path != null) {
                String joinAlias = resolveJoinAlias(path);
                // 找到子表元数据
                Class<?> subEntityClass = resolveTargetClass(path);
                if (subEntityClass != null) {
                    @SuppressWarnings("unchecked")
                    Class<TSub> typedSubClass = (Class<TSub>) subEntityClass;
                    WhereBuilder<TSub> subBuilder = new WhereBuilder<>(typedSubClass, this, joinAlias);
                    subBuilderConsumer.accept(subBuilder);
                }
            }
        } finally {
            RecordingContext.clear();
        }
        return this;
    }

    /**
     * 显式关联 JOIN（基于导航关系自动推导 ON 条件）
     */
    public <TJoin> Query<T> leftJoin(Function<T, TJoin> relationGetter) {
        RecordingContext.start(rootEntityClass);
        try {
            T proxy = ProxyFactory.getProxy(rootEntityClass);
            relationGetter.apply(proxy);
            PropertyPath path = RecordingContext.finish();
            if (path != null) {
                resolveJoinAlias(path);
            }
        } finally {
            RecordingContext.clear();
        }
        return this;
    }

    /**
     * 声明显式装配关联对象（避免笛卡尔积，采用 Split IN 查询）
     */
    public Query<T> include(Function<T, ?> relationGetter) {
        RecordingContext.start(rootEntityClass);
        try {
            T proxy = ProxyFactory.getProxy(rootEntityClass);
            relationGetter.apply(proxy);
            PropertyPath path = RecordingContext.finish();
            if (path != null) {
                includes.add(path);
            }
        } finally {
            RecordingContext.clear();
        }
        return this;
    }

    public <R> OrderByBuilder<T> orderBy(Function<T, R> propertyGetter) {
        PropertyPath path = recordPath(propertyGetter);
        String alias = resolveTableAlias(path);
        String colName = resolveColumnName(path);
        return new OrderByBuilder<>(this, alias, colName);
    }

    public Query<T> page(int pageNum, int pageSize) {
        int offset = (Math.max(pageNum, 1) - 1) * pageSize;
        statement.setLimit(pageSize);
        statement.setOffset(offset);
        return this;
    }

    public Query<T> limit(int limit) {
        statement.setLimit(limit);
        return this;
    }

    public Query<T> offset(int offset) {
        statement.setOffset(offset);
        return this;
    }

    public Query<T> distinct() {
        statement.setDistinct(true);
        return this;
    }

    // 执行方法
    public List<T> list() {
        try (Connection conn = dataSource.getConnection()) {
            List<T> result = EntityHydrator.queryList(conn, statement, rootMetadata, dialect, config);
            if (!includes.isEmpty() && !result.isEmpty()) {
                IncludeProcessor.processIncludes(conn, result, rootMetadata, includes, dialect, config);
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute query list", e);
        }
    }

    public T single() {
        statement.setLimit(1);
        List<T> list = list();
        return list.isEmpty() ? null : list.get(0);
    }

    public T firstOrNull() {
        return single();
    }

    public long count() {
        SelectStatement countStmt = new SelectStatement(rootEntityClass, rootMetadata.getTableName(), "t0");
        countStmt.addSelectColumn("COUNT(1)");
        for (TableJoin join : statement.getJoins()) {
            countStmt.addJoin(join);
        }
        for (Condition cond : statement.getWhereConditions()) {
            countStmt.addWhereCondition(cond);
        }

        try (Connection conn = dataSource.getConnection()) {
            return EntityHydrator.queryCount(conn, countStmt, dialect, config);
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute count query", e);
        }
    }

    // 内部路径解析器
    public <R> PropertyPath recordPath(Function<T, R> getter) {
        RecordingContext.start(rootEntityClass);
        try {
            T proxy = ProxyFactory.getProxy(rootEntityClass);
            getter.apply(proxy);
            return RecordingContext.finish();
        } finally {
            RecordingContext.clear();
        }
    }

    public String resolveTableAlias(PropertyPath path) {
        if (!path.hasRelations()) {
            return statement.getRootTableAlias();
        }
        return resolveJoinAlias(path);
    }

    public String resolveColumnName(PropertyPath path) {
        Class<?> targetClass = resolveTargetClass(path);
        EntityMetadata targetMeta = EntityMetadataManager.get(targetClass, config);
        ColumnMetadata col = targetMeta.getColumnByProperty(path.getTerminalProperty());
        if (col != null) {
            return col.getColumnName();
        }
        return config.getNameConversion().convert(path.getTerminalProperty());
    }

    public String resolveJoinAlias(PropertyPath path) {
        String currentAlias = statement.getRootTableAlias();
        Class<?> currentClass = rootEntityClass;
        EntityMetadata currentMeta = rootMetadata;

        StringBuilder pathKey = new StringBuilder();
        for (String step : path.getRelationSteps()) {
            if (pathKey.length() > 0) pathKey.append(".");
            pathKey.append(step);

            String existingAlias = relationJoinAliases.get(pathKey.toString());
            if (existingAlias != null) {
                currentAlias = existingAlias;
                RelationMetadata rel = currentMeta.getRelation(step);
                currentClass = rel.getTargetEntityClass();
                currentMeta = EntityMetadataManager.get(currentClass, config);
                continue;
            }

            RelationMetadata rel = currentMeta.getRelation(step);
            if (rel == null) {
                throw new IllegalStateException("Relation property '" + step + "' not found on " + currentClass.getName());
            }

            String nextAlias = "t" + (aliasCounter++);
            Class<?> targetClass = rel.getTargetEntityClass();
            EntityMetadata targetMeta = EntityMetadataManager.get(targetClass, config);

            TableJoin join = new TableJoin(TableJoin.JoinType.LEFT_JOIN, targetMeta.getTableName(), nextAlias);

            // 构建 ON 条件
            ColumnMetadata selfCol = currentMeta.getColumnByProperty(rel.getSelfProperty());
            ColumnMetadata targetCol = targetMeta.getColumnByProperty(rel.getTargetProperty());

            String selfColName = selfCol != null ? selfCol.getColumnName() : config.getNameConversion().convert(rel.getSelfProperty());
            String targetColName = targetCol != null ? targetCol.getColumnName() : config.getNameConversion().convert(rel.getTargetProperty());

            // 联表 ON t0.teacher_id = t1.id
            join.addOnCondition(new BinaryCondition(currentAlias, selfColName, SqlOperator.EQ) {
                // 自定义跨表字段比较
            });
            // 默认简单跨表赋值
            join.getOnConditions().clear();
            join.addOnCondition(new ColumnEqualityCondition(currentAlias, selfColName, nextAlias, targetColName));

            // 子表逻辑删除自动过滤
            if (targetMeta.hasLogicDelete()) {
                ColumnMetadata ld = targetMeta.getLogicDeleteColumn();
                join.addOnCondition(new BinaryCondition(nextAlias, ld.getColumnName(), SqlOperator.EQ, ld.getLogicDeleteNormalValue()));
            }

            statement.addJoin(join);
            relationJoinAliases.put(pathKey.toString(), nextAlias);

            currentAlias = nextAlias;
            currentClass = targetClass;
            currentMeta = targetMeta;
        }

        return currentAlias;
    }

    private Class<?> resolveTargetClass(PropertyPath path) {
        Class<?> current = rootEntityClass;
        EntityMetadata currentMeta = rootMetadata;
        for (String step : path.getRelationSteps()) {
            RelationMetadata rel = currentMeta.getRelation(step);
            if (rel == null) break;
            current = rel.getTargetEntityClass();
            currentMeta = EntityMetadataManager.get(current, config);
        }
        return current;
    }

    public SelectStatement getStatement() { return statement; }
    public OrmConfig getConfig() { return config; }
    public Dialect getDialect() { return dialect; }
}
