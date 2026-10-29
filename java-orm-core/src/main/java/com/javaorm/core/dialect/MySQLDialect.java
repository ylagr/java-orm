package com.javaorm.core.dialect;

import com.javaorm.core.ast.SqlExpression;

import java.util.List;

public class MySQLDialect extends AbstractDialect {
    @Override
    public String quote(String identifier) {
        if (identifier == null) return null;
        String clean = identifier;
        if (clean.length() >= 2 && clean.startsWith("`") && clean.endsWith("`")) {
            clean = clean.substring(1, clean.length() - 1);
        }
        return "`" + clean.replace("`", "``") + "`";
    }

    @Override
    protected boolean renderDialectSpecificFunction(String name, List<Object> args, StringBuilder sql, List<Object> params) {
        if (("DATE_FORMAT".equals(name) || "DATE_TO_STRING".equals(name)) && !args.isEmpty()) {
            sql.append("DATE_FORMAT(");
            renderArg(args.get(0), sql, params);
            sql.append(", ");
            String fmt = args.size() > 1 ? String.valueOf(args.get(1)) : "yyyy-MM-dd HH:mm:ss";
            String myFmt = DateFormatPatternConverter.toMySQL(fmt);
            sql.append("'").append(myFmt).append("')");
            return true;
        }
        if (("STR_TO_DATE".equals(name) || "TO_DATE".equals(name) || "STRING_TO_DATE".equals(name)) && !args.isEmpty()) {
            sql.append("STR_TO_DATE(");
            renderArg(args.get(0), sql, params);
            sql.append(", ");
            String fmt = args.size() > 1 ? String.valueOf(args.get(1)) : "yyyy-MM-dd HH:mm:ss";
            String myFmt = DateFormatPatternConverter.toMySQL(fmt);
            sql.append("'").append(myFmt).append("')");
            return true;
        }
        if ("DATE_ADD".equals(name) && args.size() >= 2) {
            String unit = args.size() > 2 ? String.valueOf(args.get(2)).toUpperCase() : "DAY";
            Object amount = args.get(1);
            sql.append("DATE_ADD(");
            renderArg(args.get(0), sql, params);
            sql.append(", INTERVAL ");
            if (amount instanceof Number) {
                sql.append(amount);
            } else {
                renderArg(amount, sql, params);
            }
            sql.append(" ").append(unit).append(")");
            return true;
        }
        if ("DATEDIFF".equals(name) && args.size() >= 2) {
            sql.append("DATEDIFF(");
            renderArg(args.get(0), sql, params);
            sql.append(", ");
            renderArg(args.get(1), sql, params);
            sql.append(")");
            return true;
        }
        if ("NOW".equals(name) || "CURRENT_TIMESTAMP".equals(name)) {
            sql.append("NOW()");
            return true;
        }
        if ("CURDATE".equals(name) || "CURRENT_DATE".equals(name)) {
            sql.append("CURDATE()");
            return true;
        }
        if ("CURTIME".equals(name) || "CURRENT_TIME".equals(name)) {
            sql.append("CURTIME()");
            return true;
        }
        if ("GROUP_CONCAT".equals(name) && !args.isEmpty()) {
            sql.append("GROUP_CONCAT(");
            renderArg(args.get(0), sql, params);
            if (args.size() > 1) {
                sql.append(" SEPARATOR ");
                Object sep = args.get(1);
                if (sep instanceof SqlExpression) {
                    renderArg(sep, sql, params);
                } else {
                    sql.append("'").append(String.valueOf(sep).replace("'", "''")).append("'");
                }
            }
            sql.append(")");
            return true;
        }
        if (("TRUNCATE".equals(name) || "TRUNC".equals(name)) && !args.isEmpty()) {
            sql.append("TRUNCATE(");
            renderArg(args.get(0), sql, params);
            sql.append(", ");
            renderArg(args.size() > 1 ? args.get(1) : 0, sql, params);
            sql.append(")");
            return true;
        }
        if ("DATE_SUB".equals(name) && args.size() >= 2) {
            String unit = args.size() > 2 ? String.valueOf(args.get(2)).toUpperCase() : "DAY";
            Object amount = args.get(1);
            sql.append("DATE_SUB(");
            renderArg(args.get(0), sql, params);
            sql.append(", INTERVAL ");
            if (amount instanceof Number) {
                sql.append(amount);
            } else {
                renderArg(amount, sql, params);
            }
            sql.append(" ").append(unit).append(")");
            return true;
        }
        if ("LAST_DAY".equals(name) && !args.isEmpty()) {
            sql.append("LAST_DAY(");
            renderArg(args.get(0), sql, params);
            sql.append(")");
            return true;
        }
        if ("NVL".equals(name) && args.size() >= 2) {
            sql.append("IFNULL(");
            renderArg(args.get(0), sql, params);
            sql.append(", ");
            renderArg(args.get(1), sql, params);
            sql.append(")");
            return true;
        }
        if ("STRPOS".equals(name) && args.size() >= 2) {
            sql.append("INSTR(");
            renderArg(args.get(0), sql, params);
            sql.append(", ");
            renderArg(args.get(1), sql, params);
            sql.append(")");
            return true;
        }
        if ("LOCATE".equals(name) && args.size() >= 2) {
            sql.append("LOCATE(");
            renderArg(args.get(0), sql, params);
            sql.append(", ");
            renderArg(args.get(1), sql, params);
            sql.append(")");
            return true;
        }
        if (("IIF".equals(name) || "IF".equals(name)) && args.size() >= 3) {
            sql.append("IF(");
            renderArg(args.get(0), sql, params);
            sql.append(", ");
            renderArg(args.get(1), sql, params);
            sql.append(", ");
            renderArg(args.get(2), sql, params);
            sql.append(")");
            return true;
        }
        if ("CAST".equals(name) && args.size() >= 2) {
            String type = String.valueOf(args.get(1)).toUpperCase();
            if ("VARCHAR".equals(type) || "TEXT".equals(type) || "STRING".equals(type)) type = "CHAR";
            else if ("INT".equals(type) || "INTEGER".equals(type) || "BIGINT".equals(type)) type = "SIGNED";
            else if ("FLOAT".equals(type) || "DOUBLE".equals(type)) type = "DECIMAL(18,4)";
            sql.append("CAST(");
            renderArg(args.get(0), sql, params);
            sql.append(" AS ").append(type).append(")");
            return true;
        }
        return super.renderDialectSpecificFunction(name, args, sql, params);
    }
}
