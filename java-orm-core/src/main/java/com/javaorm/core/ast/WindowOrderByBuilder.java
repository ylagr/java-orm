package com.javaorm.core.ast;

/**
 * 开窗函数排序列构建器。
 * 支持链式指定 .asc() / .desc()，以及继续添加后续排序列或开窗帧范围（ROWS BETWEEN）。
 */
public class WindowOrderByBuilder implements SqlExpression {

    private final WindowFunctionExpression windowExpr;
    private final SqlExpression orderExpr;
    private boolean committed = false;

    public WindowOrderByBuilder(WindowFunctionExpression windowExpr, SqlExpression orderExpr) {
        this.windowExpr = windowExpr;
        this.orderExpr = orderExpr;
    }

    public synchronized void commitDefault() {
        if (!committed) {
            windowExpr.addOrderBy(new OrderBy(orderExpr, true));
            committed = true;
        }
    }

    public WindowFunctionExpression asc() {
        if (!committed) {
            windowExpr.addOrderBy(new OrderBy(orderExpr, true));
            committed = true;
        }
        return windowExpr;
    }

    public WindowFunctionExpression desc() {
        if (!committed) {
            windowExpr.addOrderBy(new OrderBy(orderExpr, false));
            committed = true;
        }
        return windowExpr;
    }

    public WindowOrderByBuilder orderBy(Object prop) {
        commitDefault();
        return windowExpr.orderBy(prop);
    }

    public WindowFunctionExpression partitionBy(Object... props) {
        commitDefault();
        return windowExpr.partitionBy(props);
    }

    public WindowFunctionExpression rowsBetween(String frameClause) {
        commitDefault();
        return windowExpr.rowsBetween(frameClause);
    }

    public WindowFunctionExpression rowsBetween(long preceding, long following) {
        commitDefault();
        return windowExpr.rowsBetween(preceding, following);
    }

    public WindowFunctionExpression rowsUnboundedPreceding() {
        commitDefault();
        return windowExpr.rowsUnboundedPreceding();
    }

    public WindowFunctionExpression rowsCurrentRow() {
        commitDefault();
        return windowExpr.rowsCurrentRow();
    }

    @Override
    public String getAlias() {
        commitDefault();
        return windowExpr.getAlias();
    }

    @Override
    public WindowFunctionExpression as(String alias) {
        commitDefault();
        return windowExpr.as(alias);
    }

    @Override
    public WindowFunctionExpression over() {
        commitDefault();
        return windowExpr;
    }

    public WindowFunctionExpression getWindowExpr() {
        commitDefault();
        return windowExpr;
    }
}
