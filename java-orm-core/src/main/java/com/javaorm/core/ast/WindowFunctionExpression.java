package com.javaorm.core.ast;

import com.javaorm.core.SQL;
import com.javaorm.core.proxy.PropertyPath;
import com.javaorm.core.proxy.RecordingContext;

import java.util.ArrayList;
import java.util.List;

/**
 * SQL 开窗函数表达式（Window Function）。
 * 例如：ROW_NUMBER() OVER (PARTITION BY teacher_id ORDER BY age DESC) AS "rn"
 */
public class WindowFunctionExpression implements SqlExpression {

    private final SqlExpression functionExpr;
    private final List<SqlExpression> partitionBys = new ArrayList<>();
    private final List<OrderBy> orderBys = new ArrayList<>();
    private String frameClause;
    private String alias;

    public WindowFunctionExpression(SqlExpression functionExpr) {
        this.functionExpr = functionExpr;
    }

    public WindowFunctionExpression partitionBy(Object... props) {
        if (props != null && props.length > 0) {
            int proxyCount = 0;
            for (Object prop : props) {
                if (!(prop instanceof SqlExpression)) {
                    proxyCount++;
                }
            }
            List<PropertyPath> paths = RecordingContext.finishLast(proxyCount);
            int pathIdx = 0;
            for (Object prop : props) {
                if (prop instanceof SqlExpression) {
                    partitionBys.add((SqlExpression) prop);
                } else if (pathIdx < paths.size()) {
                    partitionBys.add(new ColumnExpression(paths.get(pathIdx++)));
                } else {
                    partitionBys.add(SQL.toExpression(prop));
                }
            }
        }
        return this;
    }

    public WindowOrderByBuilder orderBy(Object prop) {
        return new WindowOrderByBuilder(this, SQL.toExpression(prop));
    }

    public WindowFunctionExpression rowsBetween(String frameClause) {
        this.frameClause = frameClause;
        return this;
    }

    public WindowFunctionExpression rowsBetween(long preceding, long following) {
        String pStr = (preceding == Long.MIN_VALUE) ? "UNBOUNDED PRECEDING" : (preceding + " PRECEDING");
        String fStr;
        if (following == Long.MAX_VALUE) {
            fStr = "UNBOUNDED FOLLOWING";
        } else if (following == 0) {
            fStr = "CURRENT ROW";
        } else if (following > 0) {
            fStr = following + " FOLLOWING";
        } else {
            fStr = (-following) + " PRECEDING";
        }
        this.frameClause = "ROWS BETWEEN " + pStr + " AND " + fStr;
        return this;
    }

    public WindowFunctionExpression rowsUnboundedPreceding() {
        this.frameClause = "ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW";
        return this;
    }

    public WindowFunctionExpression rowsCurrentRow() {
        this.frameClause = "ROWS CURRENT ROW";
        return this;
    }

    public void addOrderBy(OrderBy orderBy) {
        this.orderBys.add(orderBy);
    }

    @Override
    public WindowFunctionExpression as(String alias) {
        this.alias = alias;
        return this;
    }

    @Override
    public String getAlias() {
        return alias;
    }

    @Override
    public WindowFunctionExpression over() {
        return this;
    }

    public SqlExpression getFunctionExpr() {
        return functionExpr;
    }

    public List<SqlExpression> getPartitionBys() {
        return partitionBys;
    }

    public List<OrderBy> getOrderBys() {
        return orderBys;
    }

    public String getFrameClause() {
        return frameClause;
    }
}
