package com.javaorm.core.sql;

import com.javaorm.core.ast.BinaryCondition;
import com.javaorm.core.ast.ColumnEqualityCondition;
import com.javaorm.core.ast.ColumnExpression;
import com.javaorm.core.ast.TableJoin;
import com.javaorm.core.operator.SqlOperator;
import com.javaorm.core.proxy.PropertyPath;
import com.javaorm.core.proxy.RecordingContext;
import com.javaorm.core.query.Query;
import com.javaorm.core.query.WhereBuilder;

import java.util.List;
import java.util.function.Consumer;

/**
 * JOIN 子句构建节点。
 * 支持天然双属性关联：.on(child.getParentId(), parent.getId())，亦支持 lambda 高级配置。
 */
public class SqlJoinClause {

    private final SqlSelectStream selectStream;
    private final TableJoin tableJoin;
    private final SqlTableInfo targetInfo;
    private boolean onConfigured = false;

    public SqlJoinClause(SqlSelectStream selectStream, TableJoin tableJoin, SqlTableInfo targetInfo) {
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
    public SqlSelectStream on(Object leftProp, Object rightProp) {
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
    public SqlSelectStream on(Consumer<WhereBuilder<?>> onConsumer) {
        if (onConsumer != null) {
            WhereBuilder<?> wb = new WhereBuilder<>(targetInfo.getEntityClass(), selectStream.getQuery(),
                    targetInfo.getAlias(), tableJoin::addOnCondition);
            onConsumer.accept(wb);
        }
        this.onConfigured = true;
        return selectStream;
    }

    private ColumnExpression pathToColumnExpression(PropertyPath path) {
        Query<?> query = selectStream.getQuery();
        String alias = path.getExplicitAlias() != null ? path.getExplicitAlias() : query.resolveTableAlias(path);
        String colName = query.resolveColumnName(path);
        ColumnExpression ce = new ColumnExpression(alias, colName);
        ce.setPath(path);
        return ce;
    }

    // ========== 委托至 SqlSelectStream，支持省略 .on() 时直接继续链式调用 ==========

    public SqlJoinClause leftJoin(Object targetTable) {
        return selectStream.leftJoin(targetTable);
    }

    public SqlJoinClause leftjoin(Object targetTable) {
        return selectStream.leftjoin(targetTable);
    }

    public SqlJoinClause innerJoin(Object targetTable) {
        return selectStream.innerJoin(targetTable);
    }

    public SqlJoinClause innerjoin(Object targetTable) {
        return selectStream.innerjoin(targetTable);
    }

    public SqlJoinClause rightJoin(Object targetTable) {
        return selectStream.rightJoin(targetTable);
    }

    public SqlJoinClause rightjoin(Object targetTable) {
        return selectStream.rightjoin(targetTable);
    }

    public <T> WhereBuilder<T> where() {
        return selectStream.where();
    }

    public SqlSelectStream where(Consumer<WhereBuilder<?>> consumer) {
        return selectStream.where(consumer);
    }

    public SqlSelectStream orderBy(Object val) {
        return selectStream.orderBy(val);
    }

    public SqlSelectStream orderby(Object val) {
        return selectStream.orderby(val);
    }

    public SqlSelectStream orderByAsc(Object val) {
        return selectStream.orderByAsc(val);
    }

    public SqlSelectStream orderByDesc(Object val) {
        return selectStream.orderByDesc(val);
    }
}
