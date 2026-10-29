package com.javaorm.core.ast;

/**
 * 开窗函数排序列构建器。
 * 支持链式指定 .asc() / .desc()，以及继续添加后续排序列或开窗帧范围（ROWS BETWEEN）。
 *
 * @param <T> 开窗计算结果的 Java 映射强类型
 */
public class WindowOrderByBuilder<T> implements SqlExpression<T> {

    private final WindowFunctionExpression<T> windowExpr;
    private final SqlExpression<?> orderExpr;
    private boolean committed = false;

    public WindowOrderByBuilder(WindowFunctionExpression<T> windowExpr, SqlExpression<?> orderExpr) {
        this.windowExpr = windowExpr;
        this.orderExpr = orderExpr;
    }

    public synchronized void commitDefault() {
        if (!committed) {
            windowExpr.addOrderBy(new OrderBy(orderExpr, true));
            committed = true;
        }
    }

    public WindowFunctionExpression<T> asc() {
        if (!committed) {
            windowExpr.addOrderBy(new OrderBy(orderExpr, true));
            committed = true;
        }
        return windowExpr;
    }

    public WindowFunctionExpression<T> desc() {
        if (!committed) {
            windowExpr.addOrderBy(new OrderBy(orderExpr, false));
            committed = true;
        }
        return windowExpr;
    }

    public WindowOrderByBuilder<T> orderBy(Object prop) {
        commitDefault();
        return windowExpr.orderBy(prop);
    }

    public WindowFunctionExpression<T> partitionBy(Object... props) {
        commitDefault();
        return windowExpr.partitionBy(props);
    }

    public WindowFunctionExpression<T> rowsBetween(String frameClause) {
        commitDefault();
        return windowExpr.rowsBetween(frameClause);
    }

    public WindowFunctionExpression<T> rowsBetween(long preceding, long following) {
        commitDefault();
        return windowExpr.rowsBetween(preceding, following);
    }

    public WindowFunctionExpression<T> rowsUnboundedPreceding() {
        commitDefault();
        return windowExpr.rowsUnboundedPreceding();
    }

    public WindowFunctionExpression<T> rowsCurrentRow() {
        commitDefault();
        return windowExpr.rowsCurrentRow();
    }

    @Override
    public String getAlias() {
        commitDefault();
        return windowExpr.getAlias();
    }

    @Override
    public WindowFunctionExpression<T> as(String alias) {
        commitDefault();
        return windowExpr.as(alias);
    }

    @Override
    public WindowFunctionExpression<T> over() {
        commitDefault();
        return windowExpr;
    }

    public WindowFunctionExpression<T> getWindowExpr() {
        commitDefault();
        return windowExpr;
    }
}
