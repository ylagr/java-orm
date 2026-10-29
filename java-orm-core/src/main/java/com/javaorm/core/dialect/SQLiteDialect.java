package com.javaorm.core.dialect;

import java.util.List;

public class SQLiteDialect extends AbstractDialect {


    @Override
    protected boolean renderDialectSpecificFunction(String name, List<Object> args, StringBuilder sql, List<Object> params) {
        if ("NOW".equals(name) || "CURRENT_TIMESTAMP".equals(name)) {
            sql.append("datetime('now', 'localtime')");
            return true;
        }
        if ("CURDATE".equals(name) || "CURRENT_DATE".equals(name)) {
            sql.append("date('now', 'localtime')");
            return true;
        }
        if ("CURTIME".equals(name) || "CURRENT_TIME".equals(name)) {
            sql.append("time('now', 'localtime')");
            return true;
        }
        if (("DATE_FORMAT".equals(name) || "DATE_TO_STRING".equals(name)) && !args.isEmpty()) {
            sql.append("strftime('");
            String fmt = args.size() > 1 ? String.valueOf(args.get(1)) : "yyyy-MM-dd HH:mm:ss";
            String sqFmt = DateFormatPatternConverter.toSQLite(fmt);
            sql.append(sqFmt).append("', ");
            renderArg(args.get(0), sql, params);
            sql.append(")");
            return true;
        }
        if (("STR_TO_DATE".equals(name) || "TO_DATE".equals(name) || "STRING_TO_DATE".equals(name)) && !args.isEmpty()) {
            sql.append("datetime(");
            renderArg(args.get(0), sql, params);
            sql.append(")");
            return true;
        }
        if ("DATE_ADD".equals(name) && args.size() >= 2) {
            String unit = args.size() > 2 ? String.valueOf(args.get(2)).toUpperCase() : "DAY";
            String unitSuffix;
            if ("MONTH".equals(unit)) unitSuffix = " months";
            else if ("YEAR".equals(unit)) unitSuffix = " years";
            else if ("HOUR".equals(unit)) unitSuffix = " hours";
            else if ("MINUTE".equals(unit)) unitSuffix = " minutes";
            else if ("SECOND".equals(unit)) unitSuffix = " seconds";
            else unitSuffix = " days";

            sql.append("datetime(");
            renderArg(args.get(0), sql, params);
            sql.append(", (");
            if (args.get(1) instanceof Number) {
                sql.append(args.get(1));
            } else {
                renderArg(args.get(1), sql, params);
            }
            sql.append(" || '").append(unitSuffix).append("'))");
            return true;
        }
        if ("DATEDIFF".equals(name) && args.size() >= 2) {
            sql.append("CAST(julianday(");
            renderArg(args.get(0), sql, params);
            sql.append(") - julianday(");
            renderArg(args.get(1), sql, params);
            sql.append(") AS INT)");
            return true;
        }
        if ("DAYOFWEEK".equals(name) && !args.isEmpty()) {
            sql.append("(CAST(strftime('%w', ");
            renderArg(args.get(0), sql, params);
            sql.append(") AS INT) + 1)");
            return true;
        }
        if ("DAYOFYEAR".equals(name) && !args.isEmpty()) {
            sql.append("CAST(strftime('%j', ");
            renderArg(args.get(0), sql, params);
            sql.append(") AS INT)");
            return true;
        }
        if ("CHAR_LENGTH".equals(name) && !args.isEmpty()) {
            sql.append("length(");
            renderArg(args.get(0), sql, params);
            sql.append(")");
            return true;
        }
        if ("CONCAT_WS".equals(name) && args.size() >= 2) {
            Object sep = args.get(0);
            for (int i = 1; i < args.size(); i++) {
                if (i > 1) {
                    sql.append(" || ");
                    renderArg(sep, sql, params);
                    sql.append(" || ");
                }
                renderArg(args.get(i), sql, params);
            }
            return true;
        }
        if ("GROUP_CONCAT".equals(name) && !args.isEmpty()) {
            sql.append("group_concat(");
            renderArg(args.get(0), sql, params);
            if (args.size() > 1) {
                sql.append(", ");
                renderArg(args.get(1), sql, params);
            }
            sql.append(")");
            return true;
        }
        if (("TRUNCATE".equals(name) || "TRUNC".equals(name)) && !args.isEmpty()) {
            if (args.size() > 1) {
                sql.append("ROUND(");
                renderArg(args.get(0), sql, params);
                sql.append(", ");
                renderArg(args.get(1), sql, params);
                sql.append(")");
            } else {
                sql.append("CAST(");
                renderArg(args.get(0), sql, params);
                sql.append(" AS INT)");
            }
            return true;
        }
        if ("YEAR".equals(name) && !args.isEmpty()) {
            sql.append("CAST(strftime('%Y', ");
            renderArg(args.get(0), sql, params);
            sql.append(") AS INT)");
            return true;
        }
        if ("MONTH".equals(name) && !args.isEmpty()) {
            sql.append("CAST(strftime('%m', ");
            renderArg(args.get(0), sql, params);
            sql.append(") AS INT)");
            return true;
        }
        if ("DAY".equals(name) && !args.isEmpty()) {
            sql.append("CAST(strftime('%d', ");
            renderArg(args.get(0), sql, params);
            sql.append(") AS INT)");
            return true;
        }
        if ("HOUR".equals(name) && !args.isEmpty()) {
            sql.append("CAST(strftime('%H', ");
            renderArg(args.get(0), sql, params);
            sql.append(") AS INT)");
            return true;
        }
        if ("MINUTE".equals(name) && !args.isEmpty()) {
            sql.append("CAST(strftime('%M', ");
            renderArg(args.get(0), sql, params);
            sql.append(") AS INT)");
            return true;
        }
        if ("SECOND".equals(name) && !args.isEmpty()) {
            sql.append("CAST(strftime('%S', ");
            renderArg(args.get(0), sql, params);
            sql.append(") AS INT)");
            return true;
        }
        if ("CONCAT".equals(name) && !args.isEmpty()) {
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) sql.append(" || ");
                renderArg(args.get(i), sql, params);
            }
            return true;
        }
        if ("SUBSTRING".equals(name) && args.size() >= 2) {
            sql.append("substr(");
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) sql.append(", ");
                renderArg(args.get(i), sql, params);
            }
            sql.append(")");
            return true;
        }
        if ("LEFT".equals(name) && args.size() >= 2) {
            sql.append("substr(");
            renderArg(args.get(0), sql, params);
            sql.append(", 1, ");
            renderArg(args.get(1), sql, params);
            sql.append(")");
            return true;
        }
        if ("RIGHT".equals(name) && args.size() >= 2) {
            sql.append("substr(");
            renderArg(args.get(0), sql, params);
            sql.append(", -(");
            renderArg(args.get(1), sql, params);
            sql.append("))");
            return true;
        }
        if ("MOD".equals(name) && args.size() >= 2) {
            sql.append("(");
            renderArg(args.get(0), sql, params);
            sql.append(" % ");
            renderArg(args.get(1), sql, params);
            sql.append(")");
            return true;
        }
        return super.renderDialectSpecificFunction(name, args, sql, params);
    }
}
