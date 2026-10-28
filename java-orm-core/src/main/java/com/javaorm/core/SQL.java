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
     * 将代理属性调用转为列引用表达式（支持链式设置别名，如 col(s.getId()).as("myId")）
     */
    public static ColumnExpression col(Object prop) {
        return column(prop);
    }

    public static ColumnExpression column(Object prop) {
        if (prop instanceof ColumnExpression) {
            return (ColumnExpression) prop;
        }
        PropertyPath path = RecordingContext.finishCurrent();
        if (path == null) {
            path = RecordingContext.finish();
        }
        if (path != null) {
            return new ColumnExpression(path);
        }
        throw new IllegalStateException("No proxy column accessed before calling col(...)");
    }

    public static SqlExpression as(Object prop, String alias) {
        SqlExpression expr = toExpression(prop);
        return expr.as(alias);
    }

    // 聚合函数
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

    // 日期时间函数
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

    // 字符串函数
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

    // 数值函数
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

    // 空值控制函数
    public static FunctionExpression ifNull(Object prop, Object def) {
        return new FunctionExpression("IFNULL", toExpression(prop), def);
    }

    public static FunctionExpression coalesce(Object... props) {
        return new FunctionExpression("COALESCE", toExpressions(props));
    }

    // 子查询与原生片段
    public static SubQueryExpression subQuery(Object query) {
        return new SubQueryExpression(query);
    }

    public static RawSqlExpression raw(String rawSql, Object... params) {
        return new RawSqlExpression(rawSql, params);
    }

    public static SqlExpression toExpression(Object obj) {
        if (obj instanceof SqlExpression) {
            return (SqlExpression) obj;
        }
        PropertyPath path = RecordingContext.finishCurrent();
        if (path == null) {
            path = RecordingContext.finish();
        }
        if (path != null) {
            return new ColumnExpression(path);
        }
        if (obj != null) {
            return new RawSqlExpression("?", obj);
        }
        throw new IllegalStateException("Cannot convert expression. Make sure proxy getter was called or pass a SqlExpression.");
    }

    private static Object[] toExpressions(Object... objs) {
        if (objs == null) return new Object[0];
        List<Object> list = new ArrayList<>();
        for (Object obj : objs) {
            list.add(toExpression(obj));
        }
        return list.toArray();
    }
}
