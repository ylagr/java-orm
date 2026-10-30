package com.javaorm.core.sql;

import com.javaorm.core.ast.*;
import com.javaorm.core.dialect.SqlAndParams;
import com.javaorm.core.operator.SqlOperator;
import com.javaorm.core.proxy.PropertyPath;
import com.javaorm.core.proxy.RecordingContext;
import com.javaorm.core.query.Query;
import com.javaorm.core.query.SelectBuilder;
import com.javaorm.core.query.WhereBuilder;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * JOIN 子句构建节点。
 * 支持天然双属性关联：.on(child.getParentId(), parent.getId())，亦支持 lambda 高级配置。
 *
 * @param <T> 查询结果类型强类型约束
 */
public class SqlJoinClause<T> implements Query<T> {

    private final SqlSelectStream<T> selectStream;
    private final TableJoin tableJoin;
    private final SqlTableInfo targetInfo;
    private boolean onConfigured = false;

    public SqlJoinClause(SqlSelectStream<T> selectStream, TableJoin tableJoin, SqlTableInfo targetInfo) {
        this.selectStream = selectStream;
        this.tableJoin = tableJoin;
        this.targetInfo = targetInfo;
    }

    public TableJoin getTableJoin() {
        return tableJoin;
    }

    /**
     * 声明等值关联条件：.on(child.getParentId(), parent.getId())
     */
    public SqlSelectStream<T> on(Object leftProp, Object rightProp) {
        List<PropertyPath> paths = RecordingContext.finishAll();
        if (paths.size() >= 2) {
            PropertyPath p1 = paths.get(paths.size() - 2);
            PropertyPath p2 = paths.get(paths.size() - 1);
            ColumnExpression col1 = pathToColumnExpression(p1);
            ColumnExpression col2 = pathToColumnExpression(p2);
            tableJoin.addOnCondition(new ColumnEqualityCondition(
                    col1.getTableAlias(), col1.getColumnName(),
                    col2.getTableAlias(), col2.getColumnName()
            ));
        } else if (!paths.isEmpty()) {
            PropertyPath p = paths.get(0);
            ColumnExpression col1 = pathToColumnExpression(p);
            tableJoin.addOnCondition(new BinaryCondition(col1.getTableAlias(), col1.getColumnName(), SqlOperator.EQ, rightProp));
        }
        this.onConfigured = true;
        return selectStream;
    }

    /**
     * 作用域 Lambda 关联条件配置
     */
    public SqlSelectStream<T> on(Consumer<WhereBuilder<?>> onConsumer) {
        if (onConsumer != null) {
            WhereBuilder<?> wb = new WhereBuilder<>(targetInfo.getEntityClass(), selectStream.getQuery(),
                    targetInfo.getAlias(), tableJoin::addOnCondition);
            onConsumer.accept(wb);
        }
        this.onConfigured = true;
        return selectStream;
    }

    private ColumnExpression pathToColumnExpression(PropertyPath path) {
        SelectBuilder<?> query = selectStream.getQuery();
        String alias = path.getExplicitAlias() != null ? path.getExplicitAlias() : query.resolveTableAlias(path);
        String colName = query.resolveColumnName(path);
        ColumnExpression ce = new ColumnExpression(alias, colName);
        ce.setPath(path);
        return ce;
    }

    // ========== 委托至 SqlSelectStream，支持省略 .on() 时直接继续链式调用 ==========

    public SqlJoinClause<T> leftJoin(Object targetTable) {
        return selectStream.leftJoin(targetTable);
    }

    public SqlJoinClause<T> leftjoin(Object targetTable) {
        return selectStream.leftjoin(targetTable);
    }

    public SqlJoinClause<T> innerJoin(Object targetTable) {
        return selectStream.innerJoin(targetTable);
    }

    public SqlJoinClause<T> innerjoin(Object targetTable) {
        return selectStream.innerjoin(targetTable);
    }

    public SqlJoinClause<T> rightJoin(Object targetTable) {
        return selectStream.rightJoin(targetTable);
    }

    public SqlJoinClause<T> rightjoin(Object targetTable) {
        return selectStream.rightjoin(targetTable);
    }

    public WhereBuilder<T> where() {
        return selectStream.where();
    }

    public SqlSelectStream<T> where(Consumer<WhereBuilder<T>> consumer) {
        return selectStream.where(consumer);
    }

    public SqlSelectStream<T> orderBy(Object val) {
        return selectStream.orderBy(val);
    }

    public SqlSelectStream<T> orderby(Object val) {
        return selectStream.orderby(val);
    }

    public SqlSelectStream<T> orderByAsc(Object val) {
        return selectStream.orderByAsc(val);
    }

    public SqlSelectStream<T> orderByDesc(Object val) {
        return selectStream.orderByDesc(val);
    }

    // ========== TQuery<T> 接口委托 ==========

    @Override
    public Class<T> getResultClass() {
        return selectStream.getResultClass();
    }

    @Override
    public SelectStatement getStatement() {
        return selectStream.getStatement();
    }

    @Override
    public SqlEnv getEnv() {
        return selectStream.getEnv();
    }

    @Override
    public List<T> toList() {
        return selectStream.toList();
    }

    @Override
    public <R> List<R> toList(Class<R> targetClass) {
        return selectStream.toList(targetClass);
    }

    @Override
    public T firstOrNull() {
        return selectStream.firstOrNull();
    }

    @Override
    public <R> R firstOrNull(Class<R> targetClass) {
        return selectStream.firstOrNull(targetClass);
    }

    @Override
    public List<Map<String, Object>> listMaps() {
        return selectStream.listMaps();
    }

    @Override
    public long count() {
        return selectStream.count();
    }

    @Override
    public String toSql() {
        return selectStream.toSql();
    }

    @Override
    public SqlAndParams toSqlAndParams() {
        return selectStream.toSqlAndParams();
    }
}
