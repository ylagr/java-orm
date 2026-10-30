package com.javaorm.core;

import com.javaorm.core.ast.*;
import com.javaorm.core.function.BuiltinSqlFunction;
import com.javaorm.core.function.SqlFunction;
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
    @SuppressWarnings("unchecked")
    public static <T> ColumnExpression<T> col(T prop) {
        return column(prop);
    }

    public static <T> ColumnExpression<T> col(String propertyOrColumn) {
        List<PropertyPath> paths = RecordingContext.finishLast(1);
        if (!paths.isEmpty()) {
            return new ColumnExpression<>(paths.get(0));
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
        ColumnExpression<T> ce = new ColumnExpression<>(tableAlias, colName);
        if (alias != null && !alias.isEmpty()) {
            ce.as(alias);
        }
        return ce;
    }

    public static <T> ColumnExpression<T> col(String tableAlias, String propertyOrColumn) {
        return new ColumnExpression<>(tableAlias, propertyOrColumn);
    }

    @SuppressWarnings("unchecked")
    public static <T> ColumnExpression<T> column(T prop) {
        if (prop instanceof ColumnExpression) {
            return (ColumnExpression<T>) prop;
        }
        if (prop instanceof String) {
            return col((String) prop);
        }
        List<PropertyPath> paths = RecordingContext.finishLast(1);
        if (!paths.isEmpty()) {
            return new ColumnExpression<>(paths.get(0));
        }
        throw new IllegalStateException("No proxy column accessed before calling col(...)");
    }

    public static <T> SqlExpression<T> as(T prop, String alias) {
        SqlExpression<T> expr = toExpression(prop);
        return expr.as(alias);
    }

    // ==================== 通用/自定义函数调用入口 ====================
    /**
     * 自定义或内置 SQL 函数调用（支持传入 SqlFunction 接口实现或内置 BuiltinSqlFunction 枚举）。
     */
    public static <T> FunctionExpression<T> func(SqlFunction function, Object... args) {
        return new FunctionExpression<>(function, toExpressions(args));
    }

    /**
     * 自定义 SQL 函数调用（根据字符串函数名称自动解析或创建）。
     */
    public static <T> FunctionExpression<T> func(String functionName, Object... args) {
        return new FunctionExpression<>(functionName, toExpressions(args));
    }

    // ==================== 聚合函数 ====================
    public static FunctionExpression<Long> count() {
        return new FunctionExpression<>(BuiltinSqlFunction.COUNT, new RawSqlExpression<Long>("1"));
    }

    public static <T> FunctionExpression<Long> count(SqlExpression<T> expr) {
        return new FunctionExpression<>(BuiltinSqlFunction.COUNT, expr);
    }

    public static <T> FunctionExpression<Long> count(T prop) {
        return new FunctionExpression<>(BuiltinSqlFunction.COUNT, toExpression(prop));
    }

    public static <T> FunctionExpression<Long> countx() {
        return new FunctionExpression<>(BuiltinSqlFunction.COUNT, toExpression("*"));
    }

    public static <T> FunctionExpression<Long> count1() {
        return new FunctionExpression<>(BuiltinSqlFunction.COUNT, new RawSqlExpression<Long>("1"));
    }

    public static <T> FunctionExpression<Long> countDistinct(SqlExpression<T> expr) {
        return new FunctionExpression<Long>(BuiltinSqlFunction.COUNT, expr).distinct();
    }

    public static <T> FunctionExpression<Long> countDistinct(T prop) {
        return new FunctionExpression<Long>(BuiltinSqlFunction.COUNT, toExpression(prop)).distinct();
    }

    public static <T> FunctionExpression<T> sum(SqlExpression<T> expr) {
        return new FunctionExpression<>(BuiltinSqlFunction.SUM, expr);
    }

    public static <T> FunctionExpression<T> sum(T prop) {
        return new FunctionExpression<>(BuiltinSqlFunction.SUM, toExpression(prop));
    }

    public static <T> FunctionExpression<T> sumDistinct(SqlExpression<T> expr) {
        return new FunctionExpression<T>(BuiltinSqlFunction.SUM, expr).distinct();
    }

    public static <T> FunctionExpression<T> sumDistinct(T prop) {
        return new FunctionExpression<T>(BuiltinSqlFunction.SUM, toExpression(prop)).distinct();
    }

    public static <T> FunctionExpression<Double> avg(SqlExpression<T> expr) {
        return new FunctionExpression<>(BuiltinSqlFunction.AVG, expr);
    }

    public static <T> FunctionExpression<Double> avg(T prop) {
        return new FunctionExpression<>(BuiltinSqlFunction.AVG, toExpression(prop));
    }

    public static <T> FunctionExpression<Double> avgDistinct(SqlExpression<T> expr) {
        return new FunctionExpression<Double>(BuiltinSqlFunction.AVG, expr).distinct();
    }

    public static <T> FunctionExpression<Double> avgDistinct(T prop) {
        return new FunctionExpression<Double>(BuiltinSqlFunction.AVG, toExpression(prop)).distinct();
    }

    public static <T> FunctionExpression<T> max(SqlExpression<T> expr) {
        return new FunctionExpression<>(BuiltinSqlFunction.MAX, expr);
    }

    public static <T> FunctionExpression<T> max(T prop) {
        return new FunctionExpression<>(BuiltinSqlFunction.MAX, toExpression(prop));
    }

    public static <T> FunctionExpression<T> min(SqlExpression<T> expr) {
        return new FunctionExpression<>(BuiltinSqlFunction.MIN, expr);
    }

    public static <T> FunctionExpression<T> min(T prop) {
        return new FunctionExpression<>(BuiltinSqlFunction.MIN, toExpression(prop));
    }

    public static <T> FunctionExpression<String> groupConcat(SqlExpression<T> expr) {
        return new FunctionExpression<>(BuiltinSqlFunction.GROUP_CONCAT, expr);
    }

    public static <T> FunctionExpression<String> groupConcat(T prop) {
        return new FunctionExpression<>(BuiltinSqlFunction.GROUP_CONCAT, toExpression(prop));
    }

    public static <T> FunctionExpression<String> groupConcat(SqlExpression<T> expr, String separator) {
        return new FunctionExpression<>(BuiltinSqlFunction.GROUP_CONCAT, expr, separator);
    }

    public static <T> FunctionExpression<String> groupConcat(T prop, String separator) {
        return new FunctionExpression<>(BuiltinSqlFunction.GROUP_CONCAT, toExpression(prop), separator);
    }

    public static <T> FunctionExpression<Double> stddev(SqlExpression<T> expr) {
        return new FunctionExpression<>(BuiltinSqlFunction.STDDEV, expr);
    }

    public static <T> FunctionExpression<Double> stddev(T prop) {
        return new FunctionExpression<>(BuiltinSqlFunction.STDDEV, toExpression(prop));
    }

    public static <T> FunctionExpression<Double> variance(SqlExpression<T> expr) {
        return new FunctionExpression<>(BuiltinSqlFunction.VARIANCE, expr);
    }

    public static <T> FunctionExpression<Double> variance(T prop) {
        return new FunctionExpression<>(BuiltinSqlFunction.VARIANCE, toExpression(prop));
    }

    // ==================== 开窗函数 (Window Functions) ====================
    // 排名函数
    public static WindowFunctionExpression<Long> rowNumber() {
        return new WindowFunctionExpression<>(new FunctionExpression<Long>(BuiltinSqlFunction.ROW_NUMBER));
    }

    public static WindowFunctionExpression<Long> rank() {
        return new WindowFunctionExpression<>(new FunctionExpression<Long>(BuiltinSqlFunction.RANK));
    }

    public static WindowFunctionExpression<Long> denseRank() {
        return new WindowFunctionExpression<>(new FunctionExpression<Long>(BuiltinSqlFunction.DENSE_RANK));
    }

    public static WindowFunctionExpression<Double> percentRank() {
        return new WindowFunctionExpression<>(new FunctionExpression<Double>(BuiltinSqlFunction.PERCENT_RANK));
    }

    public static WindowFunctionExpression<Double> cumeDist() {
        return new WindowFunctionExpression<>(new FunctionExpression<Double>(BuiltinSqlFunction.CUME_DIST));
    }

    public static WindowFunctionExpression<Long> ntile(int n) {
        return new WindowFunctionExpression<>(new FunctionExpression<Long>(BuiltinSqlFunction.NTILE, new RawSqlExpression<Integer>(String.valueOf(n))));
    }

    // 导航/取值函数
    public static <T> WindowFunctionExpression<T> lead(SqlExpression<T> expr) {
        return new WindowFunctionExpression<>(new FunctionExpression<T>(BuiltinSqlFunction.LEAD, expr));
    }

    public static <T> WindowFunctionExpression<T> lead(T prop) {
        return new WindowFunctionExpression<>(new FunctionExpression<T>(BuiltinSqlFunction.LEAD, toExpression(prop)));
    }

    public static <T> WindowFunctionExpression<T> lead(SqlExpression<T> expr, int offset) {
        return new WindowFunctionExpression<>(new FunctionExpression<T>(BuiltinSqlFunction.LEAD, expr, new RawSqlExpression<Integer>(String.valueOf(offset))));
    }

    public static <T> WindowFunctionExpression<T> lead(T prop, int offset) {
        return new WindowFunctionExpression<>(new FunctionExpression<T>(BuiltinSqlFunction.LEAD, toExpression(prop), new RawSqlExpression<Integer>(String.valueOf(offset))));
    }

    public static <T> WindowFunctionExpression<T> lead(SqlExpression<T> expr, int offset, T def) {
        return new WindowFunctionExpression<>(new FunctionExpression<T>(BuiltinSqlFunction.LEAD, expr, new RawSqlExpression<Integer>(String.valueOf(offset)), toExpression(def)));
    }

    public static <T> WindowFunctionExpression<T> lead(T prop, int offset, T def) {
        return new WindowFunctionExpression<>(new FunctionExpression<T>(BuiltinSqlFunction.LEAD, toExpression(prop), new RawSqlExpression<Integer>(String.valueOf(offset)), toExpression(def)));
    }

    public static <T> WindowFunctionExpression<T> lag(SqlExpression<T> expr) {
        return new WindowFunctionExpression<>(new FunctionExpression<T>(BuiltinSqlFunction.LAG, expr));
    }

    public static <T> WindowFunctionExpression<T> lag(T prop) {
        return new WindowFunctionExpression<>(new FunctionExpression<T>(BuiltinSqlFunction.LAG, toExpression(prop)));
    }

    public static <T> WindowFunctionExpression<T> lag(SqlExpression<T> expr, int offset) {
        return new WindowFunctionExpression<>(new FunctionExpression<T>(BuiltinSqlFunction.LAG, expr, new RawSqlExpression<Integer>(String.valueOf(offset))));
    }

    public static <T> WindowFunctionExpression<T> lag(T prop, int offset) {
        return new WindowFunctionExpression<>(new FunctionExpression<T>(BuiltinSqlFunction.LAG, toExpression(prop), new RawSqlExpression<Integer>(String.valueOf(offset))));
    }

    public static <T> WindowFunctionExpression<T> lag(SqlExpression<T> expr, int offset, T def) {
        return new WindowFunctionExpression<>(new FunctionExpression<T>(BuiltinSqlFunction.LAG, expr, new RawSqlExpression<Integer>(String.valueOf(offset)), toExpression(def)));
    }

    public static <T> WindowFunctionExpression<T> lag(T prop, int offset, T def) {
        return new WindowFunctionExpression<>(new FunctionExpression<T>(BuiltinSqlFunction.LAG, toExpression(prop), new RawSqlExpression<Integer>(String.valueOf(offset)), toExpression(def)));
    }

    public static <T> WindowFunctionExpression<T> firstValue(SqlExpression<T> expr) {
        return new WindowFunctionExpression<>(new FunctionExpression<T>(BuiltinSqlFunction.FIRST_VALUE, expr));
    }

    public static <T> WindowFunctionExpression<T> firstValue(T prop) {
        return new WindowFunctionExpression<>(new FunctionExpression<T>(BuiltinSqlFunction.FIRST_VALUE, toExpression(prop)));
    }

    public static <T> WindowFunctionExpression<T> lastValue(SqlExpression<T> expr) {
        return new WindowFunctionExpression<>(new FunctionExpression<T>(BuiltinSqlFunction.LAST_VALUE, expr));
    }

    public static <T> WindowFunctionExpression<T> lastValue(T prop) {
        return new WindowFunctionExpression<>(new FunctionExpression<T>(BuiltinSqlFunction.LAST_VALUE, toExpression(prop)));
    }

    public static <T> WindowFunctionExpression<T> nthValue(SqlExpression<T> expr, int n) {
        return new WindowFunctionExpression<>(new FunctionExpression<T>(BuiltinSqlFunction.NTH_VALUE, expr, new RawSqlExpression<Integer>(String.valueOf(n))));
    }

    public static <T> WindowFunctionExpression<T> nthValue(T prop, int n) {
        return new WindowFunctionExpression<>(new FunctionExpression<T>(BuiltinSqlFunction.NTH_VALUE, toExpression(prop), new RawSqlExpression<Integer>(String.valueOf(n))));
    }

    // ==================== 日期时间函数 ====================
    public static FunctionExpression<String> dateFormat(Object prop, String pattern) {
        return new FunctionExpression<>(BuiltinSqlFunction.DATE_FORMAT, toExpression(prop), pattern);
    }

    public static FunctionExpression<String> dateFormat(Object prop) {
        return dateFormat(prop, "yyyy-MM-dd HH:mm:ss");
    }

    public static FunctionExpression<String> dateToString(Object prop, String pattern) {
        return dateFormat(prop, pattern);
    }

    public static FunctionExpression<String> dateToString(Object prop) {
        return dateFormat(prop);
    }

    public static FunctionExpression<java.util.Date> strToDate(Object prop, String pattern) {
        return new FunctionExpression<>(BuiltinSqlFunction.STR_TO_DATE, toExpression(prop), pattern);
    }

    public static FunctionExpression<java.util.Date> stringToDate(Object prop, String pattern) {
        return strToDate(prop, pattern);
    }

    public static FunctionExpression<java.util.Date> toDate(Object prop, String pattern) {
        return strToDate(prop, pattern);
    }

    public static FunctionExpression<java.util.Date> toDate(Object prop) {
        return strToDate(prop, "yyyy-MM-dd HH:mm:ss");
    }

    public static <T> FunctionExpression<T> dateAdd(T prop, Object amount, DateUnit unit) {
        return new FunctionExpression<>(BuiltinSqlFunction.DATE_ADD, toExpression(prop), amount, unit.name());
    }

    public static <T> FunctionExpression<T> dateAdd(T prop, Object amount, String unit) {
        return new FunctionExpression<>(BuiltinSqlFunction.DATE_ADD, toExpression(prop), amount, unit);
    }

    public static <T> FunctionExpression<T> dateAdd(T prop, Object amount) {
        return dateAdd(prop, amount, DateUnit.DAY);
    }

    public static <T> FunctionExpression<T> dateAddDays(T prop, Object days) {
        return dateAdd(prop, days, DateUnit.DAY);
    }

    public static <T> FunctionExpression<T> dateAddMonths(T prop, Object months) {
        return dateAdd(prop, months, DateUnit.MONTH);
    }

    public static <T> FunctionExpression<T> dateAddYears(T prop, Object years) {
        return dateAdd(prop, years, DateUnit.YEAR);
    }

    public static <T> FunctionExpression<T> dateAddHours(T prop, Object hours) {
        return dateAdd(prop, hours, DateUnit.HOUR);
    }

    public static <T> FunctionExpression<T> dateAddMinutes(T prop, Object minutes) {
        return dateAdd(prop, minutes, DateUnit.MINUTE);
    }

    public static <T> FunctionExpression<T> dateAddSeconds(T prop, Object seconds) {
        return dateAdd(prop, seconds, DateUnit.SECOND);
    }

    public static <T> FunctionExpression<T> dateSub(T prop, Object amount, DateUnit unit) {
        return new FunctionExpression<>(BuiltinSqlFunction.DATE_SUB, toExpression(prop), amount, unit.name());
    }

    public static <T> FunctionExpression<T> dateSub(T prop, Object amount, String unit) {
        return new FunctionExpression<>(BuiltinSqlFunction.DATE_SUB, toExpression(prop), amount, unit);
    }

    public static <T> FunctionExpression<T> dateSub(T prop, Object amount) {
        return dateSub(prop, amount, DateUnit.DAY);
    }

    public static <T> FunctionExpression<T> dateSubDays(T prop, Object days) {
        return dateSub(prop, days, DateUnit.DAY);
    }

    public static <T> FunctionExpression<T> dateSubMonths(T prop, Object months) {
        return dateSub(prop, months, DateUnit.MONTH);
    }

    public static <T> FunctionExpression<T> dateSubYears(T prop, Object years) {
        return dateSub(prop, years, DateUnit.YEAR);
    }

    public static <T> FunctionExpression<T> dateSubHours(T prop, Object hours) {
        return dateSub(prop, hours, DateUnit.HOUR);
    }

    public static <T> FunctionExpression<T> dateSubMinutes(T prop, Object minutes) {
        return dateSub(prop, minutes, DateUnit.MINUTE);
    }

    public static <T> FunctionExpression<T> dateSubSeconds(T prop, Object seconds) {
        return dateSub(prop, seconds, DateUnit.SECOND);
    }

    public static <T> FunctionExpression<T> lastDay(T prop) {
        return new FunctionExpression<>(BuiltinSqlFunction.LAST_DAY, toExpression(prop));
    }

    public static FunctionExpression<Integer> dateDiff(Object d1, Object d2) {
        return new FunctionExpression<>(BuiltinSqlFunction.DATEDIFF, toExpressions(d1, d2));
    }

    public static FunctionExpression<Integer> dayOfWeek(Object prop) {
        return new FunctionExpression<>(BuiltinSqlFunction.DAYOFWEEK, toExpression(prop));
    }

    public static FunctionExpression<Integer> dayOfYear(Object prop) {
        return new FunctionExpression<>(BuiltinSqlFunction.DAYOFYEAR, toExpression(prop));
    }

    public static FunctionExpression<Integer> year(Object prop) {
        return new FunctionExpression<>(BuiltinSqlFunction.YEAR, toExpression(prop));
    }

    public static FunctionExpression<Integer> month(Object prop) {
        return new FunctionExpression<>(BuiltinSqlFunction.MONTH, toExpression(prop));
    }

    public static FunctionExpression<Integer> day(Object prop) {
        return new FunctionExpression<>(BuiltinSqlFunction.DAY, toExpression(prop));
    }

    public static FunctionExpression<Integer> hour(Object prop) {
        return new FunctionExpression<>(BuiltinSqlFunction.HOUR, toExpression(prop));
    }

    public static FunctionExpression<Integer> minute(Object prop) {
        return new FunctionExpression<>(BuiltinSqlFunction.MINUTE, toExpression(prop));
    }

    public static FunctionExpression<Integer> second(Object prop) {
        return new FunctionExpression<>(BuiltinSqlFunction.SECOND, toExpression(prop));
    }

    public static FunctionExpression<java.util.Date> now() {
        return new FunctionExpression<>(BuiltinSqlFunction.NOW);
    }

    public static FunctionExpression<java.util.Date> currentTimestamp() {
        return now();
    }

    public static FunctionExpression<java.util.Date> curDate() {
        return new FunctionExpression<>(BuiltinSqlFunction.CURDATE);
    }

    public static FunctionExpression<java.util.Date> currentDate() {
        return curDate();
    }

    public static FunctionExpression<String> curTime() {
        return new FunctionExpression<>(BuiltinSqlFunction.CURTIME);
    }

    public static FunctionExpression<String> currentTime() {
        return curTime();
    }

    // ==================== 字符串函数 ====================
    public static FunctionExpression<Integer> length(Object prop) {
        return new FunctionExpression<>(BuiltinSqlFunction.LENGTH, toExpression(prop));
    }

    public static FunctionExpression<Integer> charLength(Object prop) {
        return new FunctionExpression<>(BuiltinSqlFunction.CHAR_LENGTH, toExpression(prop));
    }

    public static FunctionExpression<String> lower(Object prop) {
        return new FunctionExpression<>(BuiltinSqlFunction.LOWER, toExpression(prop));
    }

    public static FunctionExpression<String> upper(Object prop) {
        return new FunctionExpression<>(BuiltinSqlFunction.UPPER, toExpression(prop));
    }

    public static FunctionExpression<String> trim(Object prop) {
        return new FunctionExpression<>(BuiltinSqlFunction.TRIM, toExpression(prop));
    }

    public static FunctionExpression<String> ltrim(Object prop) {
        return new FunctionExpression<>(BuiltinSqlFunction.LTRIM, toExpression(prop));
    }

    public static FunctionExpression<String> rtrim(Object prop) {
        return new FunctionExpression<>(BuiltinSqlFunction.RTRIM, toExpression(prop));
    }

    public static FunctionExpression<String> concat(Object... props) {
        return new FunctionExpression<>(BuiltinSqlFunction.CONCAT, toExpressions(props));
    }

    public static FunctionExpression<String> concatWs(Object separator, Object... props) {
        Object[] all = new Object[props.length + 1];
        all[0] = separator;
        System.arraycopy(props, 0, all, 1, props.length);
        return new FunctionExpression<>(BuiltinSqlFunction.CONCAT_WS, toExpressions(all));
    }

    public static FunctionExpression<String> subString(Object prop, int start, int len) {
        return new FunctionExpression<>(BuiltinSqlFunction.SUBSTRING, toExpression(prop), start, len);
    }

    public static FunctionExpression<String> subString(Object prop, int start) {
        return new FunctionExpression<>(BuiltinSqlFunction.SUBSTRING, toExpression(prop), start);
    }

    public static FunctionExpression<String> substring(Object prop, int start, int len) {
        return subString(prop, start, len);
    }

    public static FunctionExpression<String> substring(Object prop, int start) {
        return subString(prop, start);
    }

    public static FunctionExpression<String> substr(Object prop, int start, int len) {
        return subString(prop, start, len);
    }

    public static FunctionExpression<String> substr(Object prop, int start) {
        return subString(prop, start);
    }

    public static FunctionExpression<String> replace(Object prop, Object target, Object replacement) {
        return new FunctionExpression<>(BuiltinSqlFunction.REPLACE, toExpressions(prop, target, replacement));
    }

    public static FunctionExpression<String> left(Object prop, int len) {
        return new FunctionExpression<>(BuiltinSqlFunction.LEFT, toExpression(prop), len);
    }

    public static FunctionExpression<String> right(Object prop, int len) {
        return new FunctionExpression<>(BuiltinSqlFunction.RIGHT, toExpression(prop), len);
    }

    public static FunctionExpression<Integer> instr(Object str, Object substr) {
        return new FunctionExpression<>(BuiltinSqlFunction.INSTR, toExpressions(str, substr));
    }

    public static FunctionExpression<Integer> locate(Object substr, Object str) {
        return new FunctionExpression<>(BuiltinSqlFunction.LOCATE, toExpressions(substr, str));
    }

    public static FunctionExpression<String> md5(Object prop) {
        return new FunctionExpression<>(BuiltinSqlFunction.MD5, toExpression(prop));
    }

    public static FunctionExpression<String> lpad(Object prop, int len, Object pad) {
        return new FunctionExpression<>(BuiltinSqlFunction.LPAD, toExpressions(prop, len, pad));
    }

    public static FunctionExpression<String> rpad(Object prop, int len, Object pad) {
        return new FunctionExpression<>(BuiltinSqlFunction.RPAD, toExpressions(prop, len, pad));
    }

    public static FunctionExpression<String> reverse(Object prop) {
        return new FunctionExpression<>(BuiltinSqlFunction.REVERSE, toExpression(prop));
    }

    public static FunctionExpression<String> repeat(Object prop, int count) {
        return new FunctionExpression<>(BuiltinSqlFunction.REPEAT, toExpression(prop), count);
    }

    public static FunctionExpression<Integer> ascii(Object prop) {
        return new FunctionExpression<>(BuiltinSqlFunction.ASCII, toExpression(prop));
    }

    // ==================== 数值函数 ====================
    public static <T> FunctionExpression<T> abs(T prop) {
        return new FunctionExpression<>(BuiltinSqlFunction.ABS, toExpression(prop));
    }

    public static <T> FunctionExpression<T> round(T prop, int scale) {
        return new FunctionExpression<>(BuiltinSqlFunction.ROUND, toExpression(prop), scale);
    }

    public static <T> FunctionExpression<T> round(T prop) {
        return round(prop, 0);
    }

    public static <T> FunctionExpression<T> ceil(T prop) {
        return new FunctionExpression<>(BuiltinSqlFunction.CEIL, toExpression(prop));
    }

    public static <T> FunctionExpression<T> ceiling(T prop) {
        return ceil(prop);
    }

    public static <T> FunctionExpression<T> floor(T prop) {
        return new FunctionExpression<>(BuiltinSqlFunction.FLOOR, toExpression(prop));
    }

    public static <T> FunctionExpression<T> mod(T prop, Object divisor) {
        return new FunctionExpression<>(BuiltinSqlFunction.MOD, toExpressions(prop, divisor));
    }

    public static FunctionExpression<Double> sqrt(Object prop) {
        return new FunctionExpression<>(BuiltinSqlFunction.SQRT, toExpression(prop));
    }

    public static FunctionExpression<Double> power(Object prop, Object exponent) {
        return new FunctionExpression<>(BuiltinSqlFunction.POWER, toExpressions(prop, exponent));
    }

    public static FunctionExpression<Integer> sign(Object prop) {
        return new FunctionExpression<>(BuiltinSqlFunction.SIGN, toExpression(prop));
    }

    public static <T> FunctionExpression<T> truncate(T prop, int scale) {
        return new FunctionExpression<>(BuiltinSqlFunction.TRUNCATE, toExpression(prop), scale);
    }

    public static <T> FunctionExpression<T> trunc(T prop, int scale) {
        return truncate(prop, scale);
    }

    public static <T> FunctionExpression<T> trunc(T prop) {
        return truncate(prop, 0);
    }

    public static FunctionExpression<Double> exp(Object prop) {
        return new FunctionExpression<>(BuiltinSqlFunction.EXP, toExpression(prop));
    }

    public static FunctionExpression<Double> ln(Object prop) {
        return new FunctionExpression<>(BuiltinSqlFunction.LN, toExpression(prop));
    }

    public static FunctionExpression<Double> log(Object prop) {
        return ln(prop);
    }

    public static FunctionExpression<Double> log(Object base, Object prop) {
        return new FunctionExpression<>(BuiltinSqlFunction.LOG, toExpressions(base, prop));
    }

    public static FunctionExpression<Double> pi() {
        return new FunctionExpression<>(BuiltinSqlFunction.PI);
    }

    public static FunctionExpression<Double> sin(Object prop) {
        return new FunctionExpression<>(BuiltinSqlFunction.SIN, toExpression(prop));
    }

    public static FunctionExpression<Double> cos(Object prop) {
        return new FunctionExpression<>(BuiltinSqlFunction.COS, toExpression(prop));
    }

    public static FunctionExpression<Double> tan(Object prop) {
        return new FunctionExpression<>(BuiltinSqlFunction.TAN, toExpression(prop));
    }

    public static FunctionExpression<Double> asin(Object prop) {
        return new FunctionExpression<>(BuiltinSqlFunction.ASIN, toExpression(prop));
    }

    public static FunctionExpression<Double> acos(Object prop) {
        return new FunctionExpression<>(BuiltinSqlFunction.ACOS, toExpression(prop));
    }

    public static FunctionExpression<Double> atan(Object prop) {
        return new FunctionExpression<>(BuiltinSqlFunction.ATAN, toExpression(prop));
    }

    public static FunctionExpression<Double> degrees(Object prop) {
        return new FunctionExpression<>(BuiltinSqlFunction.DEGREES, toExpression(prop));
    }

    public static FunctionExpression<Double> radians(Object prop) {
        return new FunctionExpression<>(BuiltinSqlFunction.RADIANS, toExpression(prop));
    }

    // ==================== 空值控制与条件分支 ====================
    public static <T> FunctionExpression<T> ifNull(T prop, T def) {
        return new FunctionExpression<>(BuiltinSqlFunction.IFNULL, toExpression(prop), def);
    }

    public static <T> FunctionExpression<T> nvl(T prop, T def) {
        return ifNull(prop, def);
    }

    @SafeVarargs
    public static <T> FunctionExpression<T> coalesce(T... props) {
        return new FunctionExpression<>(BuiltinSqlFunction.COALESCE, toExpressions((Object[]) props));
    }

    public static <T> FunctionExpression<T> nullIf(T expr1, T expr2) {
        return new FunctionExpression<>(BuiltinSqlFunction.NULLIF, toExpressions(expr1, expr2));
    }

    @SafeVarargs
    public static <T> FunctionExpression<T> greatest(T... props) {
        return new FunctionExpression<>(BuiltinSqlFunction.GREATEST, toExpressions((Object[]) props));
    }

    @SafeVarargs
    public static <T> FunctionExpression<T> least(T... props) {
        return new FunctionExpression<>(BuiltinSqlFunction.LEAST, toExpressions((Object[]) props));
    }

    public static <T> CaseWhenBuilder<T> caseWhen() {
        return new CaseWhenBuilder<>();
    }

    public static <T> CaseWhenBuilder<T> caseWhen(com.javaorm.core.ast.Condition condition, T thenVal) {
        return new CaseWhenBuilder<>(condition, thenVal);
    }

    public static <T> CaseWhenBuilder<T> caseWhen(Object prop, com.javaorm.core.operator.SqlOperator op, Object val, T thenVal) {
        return new CaseWhenBuilder<>(prop, op, val, thenVal);
    }

    public static <T> FunctionExpression<T> iif(Object condition, T trueVal, T falseVal) {
        return new FunctionExpression<>(BuiltinSqlFunction.IIF, toExpressions(condition, trueVal, falseVal));
    }

    public static <T> FunctionExpression<T> ifExpr(Object condition, T trueVal, T falseVal) {
        return iif(condition, trueVal, falseVal);
    }

    public static <T> FunctionExpression<T> cast(Object prop, String targetType) {
        return new FunctionExpression<>(BuiltinSqlFunction.CAST, toExpression(prop), targetType);
    }

    public static FunctionExpression<String> castAsChar(Object prop) {
        return cast(prop, "CHAR");
    }

    public static FunctionExpression<Long> castAsInt(Object prop) {
        return cast(prop, "SIGNED");
    }

    public static FunctionExpression<java.math.BigDecimal> castAsDecimal(Object prop, int precision, int scale) {
        return cast(prop, "DECIMAL(" + precision + "," + scale + ")");
    }

    public static FunctionExpression<java.util.Date> castAsDate(Object prop) {
        return cast(prop, "DATE");
    }

    // ==================== 子查询与原生片段 ====================
    public static <T> SubQueryExpression<T> subQuery(Object query) {
        return new SubQueryExpression<>(query);
    }

    // ==================== 通用表表达式（CTE） ====================
    public static <T> com.javaorm.core.query.Cte<T> cte(String name, com.javaorm.core.query.Query<T> query) {
        return new com.javaorm.core.query.Cte<>(name, query.getRootEntityClass(), query, false);
    }

    public static <T> com.javaorm.core.query.Cte<T> cte(String name, com.javaorm.core.query.WhereBuilder<T> whereBuilder) {
        return new com.javaorm.core.query.Cte<>(name, whereBuilder.getEntityClass(), whereBuilder, false);
    }

    public static <T> com.javaorm.core.query.Cte<T> cte(String name, Class<T> cteClass, Object query) {
        return new com.javaorm.core.query.Cte<>(name, cteClass, query, false);
    }

    public static com.javaorm.core.query.Cte<?> cte(String name, Object query) {
        return new com.javaorm.core.query.Cte<>(name, null, query, false);
    }

    public static com.javaorm.core.query.Cte<?> dynamicCte(String name, Object query) {
        return new com.javaorm.core.query.Cte<>(name, null, query, false);
    }

    public static com.javaorm.core.query.Cte<?> dynamicCte(String name, Object query, boolean recursive) {
        return new com.javaorm.core.query.Cte<>(name, null, query, recursive);
    }

    public static com.javaorm.core.query.Cte<?> dynamicCte(String name, Object query, boolean recursive, List<String> columnNames) {
        return new com.javaorm.core.query.Cte<>(name, null, query, recursive, columnNames);
    }

    public static <T> com.javaorm.core.query.Cte<T> cteRecursive(String name, com.javaorm.core.query.Query<T> query) {
        return new com.javaorm.core.query.Cte<>(name, query.getRootEntityClass(), query, true);
    }

    public static <T> com.javaorm.core.query.Cte<T> cteRecursive(String name, com.javaorm.core.query.WhereBuilder<T> whereBuilder) {
        return new com.javaorm.core.query.Cte<>(name, whereBuilder.getEntityClass(), whereBuilder, true);
    }

    public static <T> com.javaorm.core.query.Cte<T> cteRecursive(String name, Class<T> cteClass, Object query) {
        return new com.javaorm.core.query.Cte<>(name, cteClass, query, true);
    }

    public static <T> com.javaorm.core.query.Cte<T> cteRecursive(String name, Class<T> cteClass, java.util.function.Function<com.javaorm.core.query.Cte<T>, Object> queryBuilder) {
        com.javaorm.core.query.Cte<T> cte = new com.javaorm.core.query.Cte<>(name, cteClass, null, true);
        Object query = queryBuilder.apply(cte);
        cte.setQuery(query);
        return cte;
    }

    public static com.javaorm.core.query.Cte<?> cteRecursive(String name, Object query) {
        return new com.javaorm.core.query.Cte<>(name, null, query, true);
    }

    public static com.javaorm.core.query.Cte<?> cteRecursive(String name, java.util.function.Function<com.javaorm.core.query.Cte<?>, Object> queryBuilder) {
        com.javaorm.core.query.Cte<?> cte = new com.javaorm.core.query.Cte<>(name, null, null, true);
        Object query = queryBuilder.apply(cte);
        cte.setQuery(query);
        return cte;
    }

    public static com.javaorm.core.query.Cte<?> dynamicCteRecursive(String name, java.util.function.Function<com.javaorm.core.query.Cte<?>, Object> queryBuilder) {
        return cteRecursive(name, queryBuilder);
    }

    // ==================== 原地自增与算术表达式 ====================
    public static <T extends Number> IncrementExpression<T> increment(T step) {
        return new IncrementExpression<>(step, "+");
    }

    public static IncrementExpression<Integer> increment() {
        return increment(1);
    }

    public static <T extends Number> IncrementExpression<T> decrement(T step) {
        return new IncrementExpression<>(step, "-");
    }

    public static IncrementExpression<Integer> decrement() {
        return decrement(1);
    }

    public static <T extends Number> IncrementExpression<T> multiply(T factor) {
        return new IncrementExpression<>(factor, "*");
    }

    public static <T> RawSqlExpression<T> raw(String rawSql, Object... params) {
        return new RawSqlExpression<>(rawSql, params);
    }

    @SuppressWarnings("unchecked")
    public static <T> SqlExpression<T> toExpression(Object obj) {
        if (obj instanceof SqlExpression) {
            return (SqlExpression<T>) obj;
        }
        if (obj instanceof String && "*".equals(((String) obj).trim())) {
            return new RawSqlExpression<T>("*");
        }
        List<PropertyPath> paths = RecordingContext.finishLast(1);
        if (!paths.isEmpty()) {
            return new ColumnExpression<T>(paths.get(0));
        }
        if (obj != null) {
            return new RawSqlExpression<T>("?", obj);
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
                list.add(new ColumnExpression<>(paths.get(pathIdx++)));
            } else if (obj != null) {
                list.add(new RawSqlExpression<>("?", obj));
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
