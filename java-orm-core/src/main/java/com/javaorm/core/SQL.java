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

    public static FunctionExpression countDistinct(Object prop) {
        return new FunctionExpression("COUNT", toExpression(prop)).distinct();
    }

    public static FunctionExpression sum(Object prop) {
        return new FunctionExpression("SUM", toExpression(prop));
    }

    public static FunctionExpression sumDistinct(Object prop) {
        return new FunctionExpression("SUM", toExpression(prop)).distinct();
    }

    public static FunctionExpression avg(Object prop) {
        return new FunctionExpression("AVG", toExpression(prop));
    }

    public static FunctionExpression avgDistinct(Object prop) {
        return new FunctionExpression("AVG", toExpression(prop)).distinct();
    }

    public static FunctionExpression max(Object prop) {
        return new FunctionExpression("MAX", toExpression(prop));
    }

    public static FunctionExpression min(Object prop) {
        return new FunctionExpression("MIN", toExpression(prop));
    }

    public static FunctionExpression groupConcat(Object prop) {
        return new FunctionExpression("GROUP_CONCAT", toExpression(prop));
    }

    public static FunctionExpression groupConcat(Object prop, String separator) {
        return new FunctionExpression("GROUP_CONCAT", toExpression(prop), separator);
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
    public static FunctionExpression dateFormat(Object prop, String pattern) {
        return new FunctionExpression("DATE_FORMAT", toExpression(prop), pattern);
    }

    public static FunctionExpression dateFormat(Object prop) {
        return dateFormat(prop, "yyyy-MM-dd HH:mm:ss");
    }

    public static FunctionExpression dateToString(Object prop, String pattern) {
        return dateFormat(prop, pattern);
    }

    public static FunctionExpression dateToString(Object prop) {
        return dateFormat(prop);
    }

    public static FunctionExpression strToDate(Object prop, String pattern) {
        return new FunctionExpression("STR_TO_DATE", toExpression(prop), pattern);
    }

    public static FunctionExpression stringToDate(Object prop, String pattern) {
        return strToDate(prop, pattern);
    }

    public static FunctionExpression toDate(Object prop, String pattern) {
        return strToDate(prop, pattern);
    }

    public static FunctionExpression toDate(Object prop) {
        return strToDate(prop, "yyyy-MM-dd HH:mm:ss");
    }

    public static FunctionExpression dateAdd(Object prop, Object amount, DateUnit unit) {
        return new FunctionExpression("DATE_ADD", toExpression(prop), amount, unit.name());
    }

    public static FunctionExpression dateAdd(Object prop, Object amount, String unit) {
        return new FunctionExpression("DATE_ADD", toExpression(prop), amount, unit);
    }

    public static FunctionExpression dateAdd(Object prop, Object amount) {
        return dateAdd(prop, amount, DateUnit.DAY);
    }

    public static FunctionExpression dateAddDays(Object prop, Object days) {
        return dateAdd(prop, days, DateUnit.DAY);
    }

    public static FunctionExpression dateAddMonths(Object prop, Object months) {
        return dateAdd(prop, months, DateUnit.MONTH);
    }

    public static FunctionExpression dateAddYears(Object prop, Object years) {
        return dateAdd(prop, years, DateUnit.YEAR);
    }

    public static FunctionExpression dateAddHours(Object prop, Object hours) {
        return dateAdd(prop, hours, DateUnit.HOUR);
    }

    public static FunctionExpression dateAddMinutes(Object prop, Object minutes) {
        return dateAdd(prop, minutes, DateUnit.MINUTE);
    }

    public static FunctionExpression dateAddSeconds(Object prop, Object seconds) {
        return dateAdd(prop, seconds, DateUnit.SECOND);
    }

    public static FunctionExpression dateSub(Object prop, Object amount, DateUnit unit) {
        return new FunctionExpression("DATE_SUB", toExpression(prop), amount, unit.name());
    }

    public static FunctionExpression dateSub(Object prop, Object amount, String unit) {
        return new FunctionExpression("DATE_SUB", toExpression(prop), amount, unit);
    }

    public static FunctionExpression dateSub(Object prop, Object amount) {
        return dateSub(prop, amount, DateUnit.DAY);
    }

    public static FunctionExpression dateSubDays(Object prop, Object days) {
        return dateSub(prop, days, DateUnit.DAY);
    }

    public static FunctionExpression dateSubMonths(Object prop, Object months) {
        return dateSub(prop, months, DateUnit.MONTH);
    }

    public static FunctionExpression dateSubYears(Object prop, Object years) {
        return dateSub(prop, years, DateUnit.YEAR);
    }

    public static FunctionExpression dateSubHours(Object prop, Object hours) {
        return dateSub(prop, hours, DateUnit.HOUR);
    }

    public static FunctionExpression dateSubMinutes(Object prop, Object minutes) {
        return dateSub(prop, minutes, DateUnit.MINUTE);
    }

    public static FunctionExpression dateSubSeconds(Object prop, Object seconds) {
        return dateSub(prop, seconds, DateUnit.SECOND);
    }

    public static FunctionExpression lastDay(Object prop) {
        return new FunctionExpression("LAST_DAY", toExpression(prop));
    }

    public static FunctionExpression dateDiff(Object d1, Object d2) {
        return new FunctionExpression("DATEDIFF", toExpressions(d1, d2));
    }

    public static FunctionExpression dayOfWeek(Object prop) {
        return new FunctionExpression("DAYOFWEEK", toExpression(prop));
    }

    public static FunctionExpression dayOfYear(Object prop) {
        return new FunctionExpression("DAYOFYEAR", toExpression(prop));
    }

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

    public static FunctionExpression minute(Object prop) {
        return new FunctionExpression("MINUTE", toExpression(prop));
    }

    public static FunctionExpression second(Object prop) {
        return new FunctionExpression("SECOND", toExpression(prop));
    }

    public static FunctionExpression now() {
        return new FunctionExpression("NOW");
    }

    public static FunctionExpression currentTimestamp() {
        return now();
    }

    public static FunctionExpression curDate() {
        return new FunctionExpression("CURDATE");
    }

    public static FunctionExpression currentDate() {
        return curDate();
    }

    public static FunctionExpression curTime() {
        return new FunctionExpression("CURTIME");
    }

    public static FunctionExpression currentTime() {
        return curTime();
    }

    // ==================== 字符串函数 ====================
    public static FunctionExpression length(Object prop) {
        return new FunctionExpression("LENGTH", toExpression(prop));
    }

    public static FunctionExpression charLength(Object prop) {
        return new FunctionExpression("CHAR_LENGTH", toExpression(prop));
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

    public static FunctionExpression ltrim(Object prop) {
        return new FunctionExpression("LTRIM", toExpression(prop));
    }

    public static FunctionExpression rtrim(Object prop) {
        return new FunctionExpression("RTRIM", toExpression(prop));
    }

    public static FunctionExpression concat(Object... props) {
        return new FunctionExpression("CONCAT", toExpressions(props));
    }

    public static FunctionExpression concatWs(Object separator, Object... props) {
        Object[] all = new Object[props.length + 1];
        all[0] = separator;
        System.arraycopy(props, 0, all, 1, props.length);
        return new FunctionExpression("CONCAT_WS", toExpressions(all));
    }

    public static FunctionExpression subString(Object prop, int start, int len) {
        return new FunctionExpression("SUBSTRING", toExpression(prop), start, len);
    }

    public static FunctionExpression subString(Object prop, int start) {
        return new FunctionExpression("SUBSTRING", toExpression(prop), start);
    }

    public static FunctionExpression substring(Object prop, int start, int len) {
        return subString(prop, start, len);
    }

    public static FunctionExpression substring(Object prop, int start) {
        return subString(prop, start);
    }

    public static FunctionExpression substr(Object prop, int start, int len) {
        return subString(prop, start, len);
    }

    public static FunctionExpression substr(Object prop, int start) {
        return subString(prop, start);
    }

    public static FunctionExpression replace(Object prop, Object target, Object replacement) {
        return new FunctionExpression("REPLACE", toExpressions(prop, target, replacement));
    }

    public static FunctionExpression left(Object prop, int len) {
        return new FunctionExpression("LEFT", toExpression(prop), len);
    }

    public static FunctionExpression right(Object prop, int len) {
        return new FunctionExpression("RIGHT", toExpression(prop), len);
    }

    public static FunctionExpression instr(Object str, Object substr) {
        return new FunctionExpression("INSTR", toExpressions(str, substr));
    }

    public static FunctionExpression locate(Object substr, Object str) {
        return new FunctionExpression("LOCATE", toExpressions(substr, str));
    }

    public static FunctionExpression md5(Object prop) {
        return new FunctionExpression("MD5", toExpression(prop));
    }

    public static FunctionExpression lpad(Object prop, int len, Object pad) {
        return new FunctionExpression("LPAD", toExpressions(prop, len, pad));
    }

    public static FunctionExpression rpad(Object prop, int len, Object pad) {
        return new FunctionExpression("RPAD", toExpressions(prop, len, pad));
    }

    public static FunctionExpression reverse(Object prop) {
        return new FunctionExpression("REVERSE", toExpression(prop));
    }

    public static FunctionExpression repeat(Object prop, int count) {
        return new FunctionExpression("REPEAT", toExpression(prop), count);
    }

    public static FunctionExpression ascii(Object prop) {
        return new FunctionExpression("ASCII", toExpression(prop));
    }

    // ==================== 数值函数 ====================
    public static FunctionExpression abs(Object prop) {
        return new FunctionExpression("ABS", toExpression(prop));
    }

    public static FunctionExpression round(Object prop, int scale) {
        return new FunctionExpression("ROUND", toExpression(prop), scale);
    }

    public static FunctionExpression round(Object prop) {
        return round(prop, 0);
    }

    public static FunctionExpression ceil(Object prop) {
        return new FunctionExpression("CEIL", toExpression(prop));
    }

    public static FunctionExpression ceiling(Object prop) {
        return ceil(prop);
    }

    public static FunctionExpression floor(Object prop) {
        return new FunctionExpression("FLOOR", toExpression(prop));
    }

    public static FunctionExpression mod(Object prop, Object divisor) {
        return new FunctionExpression("MOD", toExpressions(prop, divisor));
    }

    public static FunctionExpression sqrt(Object prop) {
        return new FunctionExpression("SQRT", toExpression(prop));
    }

    public static FunctionExpression power(Object prop, Object exponent) {
        return new FunctionExpression("POWER", toExpressions(prop, exponent));
    }

    public static FunctionExpression sign(Object prop) {
        return new FunctionExpression("SIGN", toExpression(prop));
    }

    public static FunctionExpression truncate(Object prop, int scale) {
        return new FunctionExpression("TRUNCATE", toExpression(prop), scale);
    }

    public static FunctionExpression trunc(Object prop, int scale) {
        return truncate(prop, scale);
    }

    public static FunctionExpression trunc(Object prop) {
        return truncate(prop, 0);
    }

    public static FunctionExpression exp(Object prop) {
        return new FunctionExpression("EXP", toExpression(prop));
    }

    public static FunctionExpression ln(Object prop) {
        return new FunctionExpression("LN", toExpression(prop));
    }

    public static FunctionExpression log(Object prop) {
        return ln(prop);
    }

    public static FunctionExpression log(Object base, Object prop) {
        return new FunctionExpression("LOG", toExpressions(base, prop));
    }

    public static FunctionExpression pi() {
        return new FunctionExpression("PI");
    }

    public static FunctionExpression sin(Object prop) {
        return new FunctionExpression("SIN", toExpression(prop));
    }

    public static FunctionExpression cos(Object prop) {
        return new FunctionExpression("COS", toExpression(prop));
    }

    public static FunctionExpression tan(Object prop) {
        return new FunctionExpression("TAN", toExpression(prop));
    }

    public static FunctionExpression asin(Object prop) {
        return new FunctionExpression("ASIN", toExpression(prop));
    }

    public static FunctionExpression acos(Object prop) {
        return new FunctionExpression("ACOS", toExpression(prop));
    }

    public static FunctionExpression atan(Object prop) {
        return new FunctionExpression("ATAN", toExpression(prop));
    }

    public static FunctionExpression degrees(Object prop) {
        return new FunctionExpression("DEGREES", toExpression(prop));
    }

    public static FunctionExpression radians(Object prop) {
        return new FunctionExpression("RADIANS", toExpression(prop));
    }

    // ==================== 空值控制与条件分支 ====================
    public static FunctionExpression ifNull(Object prop, Object def) {
        return new FunctionExpression("IFNULL", toExpression(prop), def);
    }

    public static FunctionExpression nvl(Object prop, Object def) {
        return ifNull(prop, def);
    }

    public static FunctionExpression coalesce(Object... props) {
        return new FunctionExpression("COALESCE", toExpressions(props));
    }

    public static FunctionExpression nullIf(Object expr1, Object expr2) {
        return new FunctionExpression("NULLIF", toExpressions(expr1, expr2));
    }

    public static FunctionExpression greatest(Object... props) {
        return new FunctionExpression("GREATEST", toExpressions(props));
    }

    public static FunctionExpression least(Object... props) {
        return new FunctionExpression("LEAST", toExpressions(props));
    }

    public static CaseWhenBuilder caseWhen() {
        return new CaseWhenBuilder();
    }

    public static CaseWhenBuilder caseWhen(Condition condition, Object thenVal) {
        return new CaseWhenBuilder(condition, thenVal);
    }

    public static CaseWhenBuilder caseWhen(Object prop, com.javaorm.core.operator.SqlOperator op, Object val, Object thenVal) {
        return new CaseWhenBuilder(prop, op, val, thenVal);
    }

    public static FunctionExpression iif(Object condition, Object trueVal, Object falseVal) {
        return new FunctionExpression("IIF", toExpressions(condition, trueVal, falseVal));
    }

    public static FunctionExpression ifExpr(Object condition, Object trueVal, Object falseVal) {
        return iif(condition, trueVal, falseVal);
    }

    public static FunctionExpression cast(Object prop, String targetType) {
        return new FunctionExpression("CAST", toExpression(prop), targetType);
    }

    public static FunctionExpression castAsChar(Object prop) {
        return cast(prop, "CHAR");
    }

    public static FunctionExpression castAsInt(Object prop) {
        return cast(prop, "SIGNED");
    }

    public static FunctionExpression castAsDecimal(Object prop, int precision, int scale) {
        return cast(prop, "DECIMAL(" + precision + "," + scale + ")");
    }

    public static FunctionExpression castAsDate(Object prop) {
        return cast(prop, "DATE");
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
            if (!(obj instanceof SqlExpression) && isPotentialProxy(obj)) {
                proxyCount++;
            }
        }
        List<PropertyPath> paths = RecordingContext.finishLast(proxyCount);
        List<Object> list = new ArrayList<>();
        int pathIdx = 0;
        for (Object obj : objs) {
            if (obj instanceof SqlExpression) {
                list.add(obj);
            } else if (isPotentialProxy(obj) && pathIdx < paths.size()) {
                list.add(new ColumnExpression(paths.get(pathIdx++)));
            } else if (obj != null) {
                list.add(new RawSqlExpression("?", obj));
            } else {
                list.add(toExpression(obj));
            }
        }
        return list.toArray();
    }

    private static boolean isPotentialProxy(Object obj) {
        if (obj == null) return true;
        if (obj instanceof Number && ((Number) obj).longValue() == 0) return true;
        if (obj instanceof Boolean && !((Boolean) obj)) return true;
        return false;
    }
}
