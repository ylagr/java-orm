package com.javaorm.core.dialect;

import java.util.List;

public class PostgreSQLDialect extends AbstractDialect {


    @Override
    protected boolean renderDialectSpecificFunction(String name, List<Object> args, StringBuilder sql, List<Object> params) {
        if ("IFNULL".equals(name) && args.size() >= 2) {
            sql.append("COALESCE(");
            renderArg(args.get(0), sql, params);
            sql.append(", ");
            renderArg(args.get(1), sql, params);
            sql.append(")");
            return true;
        }
        if ("CURDATE".equals(name) || "CURRENT_DATE".equals(name)) {
            sql.append("CURRENT_DATE");
            return true;
        }
        if ("CURTIME".equals(name) || "CURRENT_TIME".equals(name)) {
            sql.append("CURRENT_TIME");
            return true;
        }
        if ("NOW".equals(name) || "CURRENT_TIMESTAMP".equals(name)) {
            sql.append("CURRENT_TIMESTAMP");
            return true;
        }
        if (("DATE_FORMAT".equals(name) || "DATE_TO_STRING".equals(name)) && !args.isEmpty()) {
            sql.append("TO_CHAR(");
            renderArg(args.get(0), sql, params);
            sql.append(", ");
            String fmt = args.size() > 1 ? String.valueOf(args.get(1)) : "yyyy-MM-dd HH:mm:ss";
            String pgFmt = DateFormatPatternConverter.toPostgreSQL(fmt);
            sql.append("'").append(pgFmt).append("')");
            return true;
        }
        if (("STR_TO_DATE".equals(name) || "TO_DATE".equals(name) || "STRING_TO_DATE".equals(name)) && !args.isEmpty()) {
            sql.append("TO_TIMESTAMP(");
            renderArg(args.get(0), sql, params);
            sql.append(", ");
            String fmt = args.size() > 1 ? String.valueOf(args.get(1)) : "yyyy-MM-dd HH:mm:ss";
            String pgFmt = DateFormatPatternConverter.toPostgreSQL(fmt);
            sql.append("'").append(pgFmt).append("')");
            return true;
        }
        if ("DATE_ADD".equals(name) && args.size() >= 2) {
            String unit = args.size() > 2 ? String.valueOf(args.get(2)).toUpperCase() : "DAY";
            Object amount = args.get(1);
            sql.append("(");
            renderArg(args.get(0), sql, params);
            sql.append(" + (");
            if (amount instanceof Number) {
                sql.append(amount);
            } else {
                renderArg(amount, sql, params);
            }
            sql.append(" * INTERVAL '1 ").append(unit).append("'))");
            return true;
        }
        if ("DATEDIFF".equals(name) && args.size() >= 2) {
            sql.append("(CAST(");
            renderArg(args.get(0), sql, params);
            sql.append(" AS DATE) - CAST(");
            renderArg(args.get(1), sql, params);
            sql.append(" AS DATE))");
            return true;
        }
        if ("DAYOFWEEK".equals(name) && !args.isEmpty()) {
            sql.append("(EXTRACT(DOW FROM ");
            renderArg(args.get(0), sql, params);
            sql.append(") + 1)");
            return true;
        }
        if ("DAYOFYEAR".equals(name) && !args.isEmpty()) {
            sql.append("EXTRACT(DOY FROM ");
            renderArg(args.get(0), sql, params);
            sql.append(")");
            return true;
        }
        if (("YEAR".equals(name) || "MONTH".equals(name) || "DAY".equals(name) ||
             "HOUR".equals(name) || "MINUTE".equals(name) || "SECOND".equals(name)) && !args.isEmpty()) {
            sql.append("EXTRACT(").append(name).append(" FROM ");
            renderArg(args.get(0), sql, params);
            sql.append(")");
            return true;
        }
        if ("INSTR".equals(name) && args.size() >= 2) {
            sql.append("STRPOS(");
            renderArg(args.get(0), sql, params);
            sql.append(", ");
            renderArg(args.get(1), sql, params);
            sql.append(")");
            return true;
        }
        if ("SUBSTRING".equals(name) && args.size() >= 2) {
            sql.append("SUBSTR(");
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) sql.append(", ");
                renderArg(args.get(i), sql, params);
            }
            sql.append(")");
            return true;
        }
        if ("GROUP_CONCAT".equals(name) && !args.isEmpty()) {
            sql.append("STRING_AGG(CAST(");
            renderArg(args.get(0), sql, params);
            sql.append(" AS TEXT), ");
            if (args.size() > 1) {
                renderArg(args.get(1), sql, params);
            } else {
                sql.append("','");
            }
            sql.append(")");
            return true;
        }
        if (("TRUNCATE".equals(name) || "TRUNC".equals(name)) && !args.isEmpty()) {
            sql.append("TRUNC(");
            renderArg(args.get(0), sql, params);
            if (args.size() > 1) {
                sql.append(", ");
                renderArg(args.get(1), sql, params);
            }
            sql.append(")");
            return true;
        }
        return super.renderDialectSpecificFunction(name, args, sql, params);
    }
}
