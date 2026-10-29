package com.javaorm.core.query;

import com.javaorm.core.ast.ExpressionCondition;
import com.javaorm.core.ast.LogicalOperator;
import com.javaorm.core.ast.SqlExpression;
import com.javaorm.core.operator.PropertyOperator;
import com.javaorm.core.proxy.PropertyPath;
import com.javaorm.core.proxy.RecordingContext;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * HAVING 分组过滤条件构造器。
 */
public class HavingBuilder<T> {

    private final Query<T> query;
    private final Supplier<List<?>> customListSupplier;

    public HavingBuilder(Query<T> query) {
        this(query, null);
    }

    public HavingBuilder(Query<T> query, Supplier<List<?>> customListSupplier) {
        this.query = query;
        this.customListSupplier = customListSupplier;
    }

    public <R> PropertyOperator<HavingBuilder<T>, R> and(SqlExpression<R> expr) {
        query.resolveExpression(expr);
        return new PropertyOperator<>(this, (op, vals) -> {
            ExpressionCondition cond = new ExpressionCondition(expr, op, vals);
            cond.setLogicalOperator(LogicalOperator.AND);
            query.getStatement().addHavingCondition(cond);
        });
    }

    @SuppressWarnings("unchecked")
    public <R> PropertyOperator<HavingBuilder<T>, R> and(String propertyOrColumn) {
        PropertyPath path = RecordingContext.finish();
        if (path != null) {
            SqlExpression<R> expr = (SqlExpression<R>) query.pathToColumnExpression(path);
            return and(expr);
        }
        if (propertyOrColumn == null) {
            throw new IllegalArgumentException("Column or expression cannot be null");
        }
        return and(new com.javaorm.core.ast.RawSqlExpression<R>(propertyOrColumn));
    }

    @SuppressWarnings("unchecked")
    public <R> PropertyOperator<HavingBuilder<T>, R> and(R val) {
        if (val instanceof SqlExpression) {
            return and((SqlExpression<R>) val);
        }
        if (val instanceof String) {
            return and((String) val);
        }
        PropertyPath path = RecordingContext.finish();
        SqlExpression<R> expr = (SqlExpression<R>) query.pathToColumnExpression(path);
        return and(expr);
    }

    public <R> PropertyOperator<HavingBuilder<T>, R> or(SqlExpression<R> expr) {
        query.resolveExpression(expr);
        return new PropertyOperator<>(this, (op, vals) -> {
            ExpressionCondition cond = new ExpressionCondition(expr, op, vals);
            cond.setLogicalOperator(LogicalOperator.OR);
            query.getStatement().addHavingCondition(cond);
        });
    }

    @SuppressWarnings("unchecked")
    public <R> PropertyOperator<HavingBuilder<T>, R> or(String propertyOrColumn) {
        PropertyPath path = RecordingContext.finish();
        if (path != null) {
            SqlExpression<R> expr = (SqlExpression<R>) query.pathToColumnExpression(path);
            return or(expr);
        }
        if (propertyOrColumn == null) {
            throw new IllegalArgumentException("Column or expression cannot be null");
        }
        return or(new com.javaorm.core.ast.RawSqlExpression<R>(propertyOrColumn));
    }

    @SuppressWarnings("unchecked")
    public <R> PropertyOperator<HavingBuilder<T>, R> or(R val) {
        if (val instanceof SqlExpression) {
            return or((SqlExpression<R>) val);
        }
        if (val instanceof String) {
            return or((String) val);
        }
        PropertyPath path = RecordingContext.finish();
        SqlExpression<R> expr = (SqlExpression<R>) query.pathToColumnExpression(path);
        return or(expr);
    }

    // 快捷执行委托
    public <R> OrderByBuilder<HavingBuilder<T>> orderBy(Function<T, R> getter) {
        PropertyPath path = query.recordPath(getter);
        String alias = query.resolveTableAlias(path);
        String colName = query.resolveColumnName(path);
        return new OrderByBuilder<>(this, query.getStatement(), alias, colName);
    }

    public <R> OrderByBuilder<HavingBuilder<T>> orderBy(R val) {
        if (val instanceof SqlExpression) {
            return orderBy((SqlExpression<?>) val);
        }
        PropertyPath path = RecordingContext.finish();
        if (path == null) {
            throw new IllegalStateException("No proxy property accessed before calling orderBy(...)");
        }
        String alias = query.resolveTableAlias(path);
        String colName = query.resolveColumnName(path);
        return new OrderByBuilder<>(this, query.getStatement(), alias, colName);
    }

    public OrderByBuilder<HavingBuilder<T>> orderBy(String propertyOrColumn) {
        PropertyPath path = RecordingContext.finish();
        if (path != null) {
            String alias = query.resolveTableAlias(path);
            String colName = query.resolveColumnName(path);
            return new OrderByBuilder<>(this, query.getStatement(), alias, colName);
        }
        if (propertyOrColumn == null) {
            throw new IllegalArgumentException("Order by column cannot be null");
        }
        com.javaorm.core.ast.ColumnExpression ce = query.stringToColumnExpression(propertyOrColumn);
        return new OrderByBuilder<>(this, query.getStatement(), ce.getTableAlias(), ce.getColumnName());
    }

    public OrderByBuilder<HavingBuilder<T>> orderBy(SqlExpression<?> expr) {
        query.resolveExpression(expr);
        return new OrderByBuilder<>(this, query.getStatement(), expr);
    }

    public HavingBuilder<T> printSql() {
        query.printSql();
        return this;
    }

    public HavingBuilder<T> printSql(boolean print) {
        query.printSql(print);
        return this;
    }

    public com.javaorm.core.dialect.SqlAndParams toSqlAndParams() {
        return query.toSqlAndParams();
    }

    public String toSql() {
        return query.toSql();
    }

    @Override
    public String toString() {
        return toSql();
    }

    public Query<T> union(Query<?> other) {
        return query.union(other);
    }

    public Query<T> unionAll(Query<?> other) {
        return query.unionAll(other);
    }

    public Query<T> page(int pageNum, int pageSize) {
        return query.page(pageNum, pageSize);
    }

    @SuppressWarnings("unchecked")
    public <E> List<E> list() {
        if (customListSupplier != null) {
            return (List<E>) customListSupplier.get();
        }
        return (List<E>) query.list();
    }

    public List<Map<String, Object>> listMaps() {
        return query.listMaps();
    }

    public List<Map<String, Object>> maps() {
        return query.listMaps();
    }

    public <V> V fetchOne(Class<V> targetType) {
        return query.fetchOne(targetType);
    }

    public <V> List<V> fetchList(Class<V> targetType) {
        return query.fetchList(targetType);
    }
}
