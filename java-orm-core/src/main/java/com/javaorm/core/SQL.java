package com.javaorm.core;

import com.javaorm.core.ast.*;
import com.javaorm.core.proxy.PropertyPath;
import com.javaorm.core.proxy.ProxyFactory;
import com.javaorm.core.proxy.RecordingContext;

import java.util.ArrayList;
import java.util.List;

/**
 * SQL 表达式与函数工厂（支持静态导入使用，如 import static com.javaorm.core.SQL.*;）。
 */
public class SQL {

    public static <T> T proxy(Class<T> entityClass) {
        return ProxyFactory.getProxy(entityClass);
    }

    public static <T> T $(Class<T> entityClass) {
        return ProxyFactory.getProxy(entityClass);
    }

    /**
     * 将代理属性调用或动态列字符串转为列引用表达式（支持链式设置别名，如 col(s.getId()).as("myId") 或 col("age") 或 col("t0.age")）
     */
    public static ColumnExpression col(Object prop) {
        return column(prop);
    }

    public static ColumnExpression col(String propertyOrColumn) {
        List<PropertyPath> paths = RecordingContext.finishLast(1);
        if (!paths.isEmpty()) {
            return new ColumnExpression(paths.get(0));
        }
        if (propertyOrColumn == null) return null;
        String str = propertyOrColumn.trim();
        String alias = null;
        String colPart = str;
        String[] parts = str.split(" (?i)as ");
        if (parts.length == 2) {
            colPart = parts[0].trim();
            alias = parts[1].trim();
        } else {
            int spaceIdx = str.lastIndexOf(' ');
            if (spaceIdx > 0 && !str.contains("(")) {
                colPart = str.substring(0, spaceIdx).trim();
                alias = str.substring(spaceIdx + 1).trim();
            }
        }
        String tableAlias = null;
        String colName = colPart;
        if (colPart.contains(".")) {
            int dot = colPart.lastIndexOf('.');
            tableAlias = colPart.substring(0, dot);
            colName = colPart.substring(dot + 1);
        }
        ColumnExpression ce = new ColumnExpression(tableAlias, colName);
        if (alias != null && !alias.isEmpty()) {
            ce.as(alias);
        }
        return ce;
    }

    public static ColumnExpression col(String tableAlias, String propertyOrColumn) {
        return new ColumnExpression(tableAlias, propertyOrColumn);
    }

    public static ColumnExpression column(Object prop) {
        if (prop instanceof ColumnExpression) {
            return (ColumnExpression) prop;
        }
        if (prop instanceof String) {
            return col((String) prop);
        }
        List<PropertyPath> paths = RecordingContext.finishLast(1);
        if (!paths.isEmpty()) {
            return new ColumnExpression(paths.get(0));
        }
        throw new IllegalStateException("No proxy column accessed before calling col(...)");
    }

    public static SqlExpression as(Object prop, String alias) {
        SqlExpression expr = toExpression(prop);
        return expr.as(alias);
    }

    // ==================== 聚合函数 ====================
    public static FunctionExpression count() {
        return new FunctionExpression("COUNT", new RawSqlExpression("1"));
    }

    public static FunctionExpression count(Object prop) {
        return new FunctionExpression("COUNT", toExpression(prop));
    }

    public static FunctionExpression sum(Object prop) {
        return new FunctionExpression("SUM", toExpression(prop));
    }

    public static FunctionExpression avg(Object prop) {
        return new FunctionExpression("AVG", toExpression(prop));
    }

    public static FunctionExpression max(Object prop) {
        return new FunctionExpression("MAX", toExpression(prop));
    }

    public static FunctionExpression min(Object prop) {
        return new FunctionExpression("MIN", toExpression(prop));
    }

    public static FunctionExpression stddev(Object prop) {
        return new FunctionExpression("STDDEV", toExpression(prop));
    }

    public static FunctionExpression variance(Object prop) {
        return new FunctionExpression("VARIANCE", toExpression(prop));
    }

    // ==================== 开窗函数 (Window Functions) ====================
    // 排名函数
    public static WindowFunctionExpression rowNumber() {
        return new WindowFunctionExpression(new FunctionExpression("ROW_NUMBER"));
    }

    public static WindowFunctionExpression rank() {
        return new WindowFunctionExpression(new FunctionExpression("RANK"));
    }

    public static WindowFunctionExpression denseRank() {
        return new WindowFunctionExpression(new FunctionExpression("DENSE_RANK"));
    }

    public static WindowFunctionExpression percentRank() {
        return new WindowFunctionExpression(new FunctionExpression("PERCENT_RANK"));
    }

    public static WindowFunctionExpression cumeDist() {
        return new WindowFunctionExpression(new FunctionExpression("CUME_DIST"));
    }

    public static WindowFunctionExpression ntile(int n) {
        return new WindowFunctionExpression(new FunctionExpression("NTILE", new RawSqlExpression(String.valueOf(n))));
    }

    // 导航/取值函数
    public static WindowFunctionExpression lead(Object prop) {
        return new WindowFunctionExpression(new FunctionExpression("LEAD", toExpression(prop)));
    }

    public static WindowFunctionExpression lead(Object prop, int offset) {
        return new WindowFunctionExpression(new FunctionExpression("LEAD", toExpression(prop), new RawSqlExpression(String.valueOf(offset))));
    }

    public static WindowFunctionExpression lead(Object prop, int offset, Object def) {
        return new WindowFunctionExpression(new FunctionExpression("LEAD", toExpression(prop), new RawSqlExpression(String.valueOf(offset)), toExpression(def)));
    }

    public static WindowFunctionExpression lag(Object prop) {
        return new WindowFunctionExpression(new FunctionExpression("LAG", toExpression(prop)));
    }

    public static WindowFunctionExpression lag(Object prop, int offset) {
        return new WindowFunctionExpression(new FunctionExpression("LAG", toExpression(prop), new RawSqlExpression(String.valueOf(offset))));
    }

    public static WindowFunctionExpression lag(Object prop, int offset, Object def) {
        return new WindowFunctionExpression(new FunctionExpression("LAG", toExpression(prop), new RawSqlExpression(String.valueOf(offset)), toExpression(def)));
    }

    public static WindowFunctionExpression firstValue(Object prop) {
        return new WindowFunctionExpression(new FunctionExpression("FIRST_VALUE", toExpression(prop)));
    }

    public static WindowFunctionExpression lastValue(Object prop) {
        return new WindowFunctionExpression(new FunctionExpression("LAST_VALUE", toExpression(prop)));
    }

    public static WindowFunctionExpression nthValue(Object prop, int n) {
        return new WindowFunctionExpression(new FunctionExpression("NTH_VALUE", toExpression(prop), new RawSqlExpression(String.valueOf(n))));
    }

    // ==================== 日期时间函数 ====================
    public static FunctionExpression year(Object prop) {
        return new FunctionExpression("YEAR", toExpression(prop));
    }

    public static FunctionExpression month(Object prop) {
        return new FunctionExpression("MONTH", toExpression(prop));
    }

    public static FunctionExpression day(Object prop) {
        return new FunctionExpression("DAY", toExpression(prop));
    }

    public static FunctionExpression hour(Object prop) {
        return new FunctionExpression("HOUR", toExpression(prop));
    }

    public static FunctionExpression now() {
        return new FunctionExpression("NOW");
    }

    public static FunctionExpression curDate() {
        return new FunctionExpression("CURDATE");
    }

    // ==================== 字符串函数 ====================
    public static FunctionExpression length(Object prop) {
        return new FunctionExpression("LENGTH", toExpression(prop));
    }

    public static FunctionExpression lower(Object prop) {
        return new FunctionExpression("LOWER", toExpression(prop));
    }

    public static FunctionExpression upper(Object prop) {
        return new FunctionExpression("UPPER", toExpression(prop));
    }

    public static FunctionExpression trim(Object prop) {
        return new FunctionExpression("TRIM", toExpression(prop));
    }

    public static FunctionExpression concat(Object... props) {
        return new FunctionExpression("CONCAT", toExpressions(props));
    }

    public static FunctionExpression subString(Object prop, int start, int len) {
        return new FunctionExpression("SUBSTRING", toExpression(prop), start, len);
    }

    // ==================== 数值函数 ====================
    public static FunctionExpression abs(Object prop) {
        return new FunctionExpression("ABS", toExpression(prop));
    }

    public static FunctionExpression round(Object prop, int scale) {
        return new FunctionExpression("ROUND", toExpression(prop), scale);
    }

    public static FunctionExpression ceil(Object prop) {
        return new FunctionExpression("CEIL", toExpression(prop));
    }

    public static FunctionExpression floor(Object prop) {
        return new FunctionExpression("FLOOR", toExpression(prop));
    }

    // ==================== 空值控制函数 ====================
    public static FunctionExpression ifNull(Object prop, Object def) {
        return new FunctionExpression("IFNULL", toExpression(prop), def);
    }

    public static FunctionExpression coalesce(Object... props) {
        return new FunctionExpression("COALESCE", toExpressions(props));
    }

    // ==================== 子查询与原生片段 ====================
    public static SubQueryExpression subQuery(Object query) {
        return new SubQueryExpression(query);
    }

    // ==================== 原地自增与算术表达式 ====================
    public static IncrementExpression increment(Number step) {
        return new IncrementExpression(step, "+");
    }

    public static IncrementExpression increment() {
        return increment(1);
    }

    public static IncrementExpression decrement(Number step) {
        return new IncrementExpression(step, "-");
    }

    public static IncrementExpression decrement() {
        return decrement(1);
    }

    public static IncrementExpression multiply(Number factor) {
        return new IncrementExpression(factor, "*");
    }

    public static RawSqlExpression raw(String rawSql, Object... params) {
        return new RawSqlExpression(rawSql, params);
    }

    public static SqlExpression toExpression(Object obj) {
        if (obj instanceof SqlExpression) {
            return (SqlExpression) obj;
        }
        List<PropertyPath> paths = RecordingContext.finishLast(1);
        if (!paths.isEmpty()) {
            return new ColumnExpression(paths.get(0));
        }
        if (obj != null) {
            return new RawSqlExpression("?", obj);
        }
        throw new IllegalStateException("Cannot convert expression. Make sure proxy getter was called or pass a SqlExpression.");
    }

    private static Object[] toExpressions(Object... objs) {
        if (objs == null) return new Object[0];
        int proxyCount = 0;
        for (Object obj : objs) {
            if (!(obj instanceof SqlExpression)) {
                proxyCount++;
            }
        }
        List<PropertyPath> paths = RecordingContext.finishLast(proxyCount);
        List<Object> list = new ArrayList<>();
        int pathIdx = 0;
        for (Object obj : objs) {
            if (obj instanceof SqlExpression) {
                list.add(obj);
            } else if (pathIdx < paths.size()) {
                list.add(new ColumnExpression(paths.get(pathIdx++)));
            } else {
                list.add(toExpression(obj));
            }
        }
        return list.toArray();
    }
}
