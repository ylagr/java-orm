package com.javaorm.core.dialect;

import com.javaorm.core.ast.SqlExpression;
import com.javaorm.core.function.BuiltinSqlFunction;

public class MySQLDialect extends AbstractDialect {

    public MySQLDialect() {
        super();
        registerMySQLFunctions();
    }

    @Override
    protected String doQuote(String identifier) {
        String clean = identifier;
        if (clean.length() >= 2 && clean.startsWith("`") && clean.endsWith("`")) {
            clean = clean.substring(1, clean.length() - 1);
        }
        return "`" + clean.replace("`", "``") + "`";
    }

    protected void registerMySQLFunctions() {
        registerFunction(BuiltinSqlFunction.DATE_FORMAT, (fn, args, sql, params, d) -> {
            if (args.isEmpty()) return;
            sql.append("DATE_FORMAT(");
            d.renderArg(args.get(0), sql, params);
            sql.append(", ");
            String fmt = args.size() > 1 ? String.valueOf(args.get(1)) : "yyyy-MM-dd HH:mm:ss";
            String myFmt = DateFormatPatternConverter.toMySQL(fmt);
            sql.append("'").append(myFmt).append("')");
        });
        registerFunction("DATE_TO_STRING", getFunctionRenderer(BuiltinSqlFunction.DATE_FORMAT));

        registerFunction(BuiltinSqlFunction.STR_TO_DATE, (fn, args, sql, params, d) -> {
            if (args.isEmpty()) return;
            sql.append("STR_TO_DATE(");
            d.renderArg(args.get(0), sql, params);
            sql.append(", ");
            String fmt = args.size() > 1 ? String.valueOf(args.get(1)) : "yyyy-MM-dd HH:mm:ss";
            String myFmt = DateFormatPatternConverter.toMySQL(fmt);
            sql.append("'").append(myFmt).append("')");
        });
        registerFunction("TO_DATE", getFunctionRenderer(BuiltinSqlFunction.STR_TO_DATE));
        registerFunction("STRING_TO_DATE", getFunctionRenderer(BuiltinSqlFunction.STR_TO_DATE));

        registerFunction(BuiltinSqlFunction.DATE_ADD, (fn, args, sql, params, d) -> {
            if (args.size() < 2) return;
            String unit = args.size() > 2 ? String.valueOf(args.get(2)).toUpperCase() : "DAY";
            Object amount = args.get(1);
            sql.append("DATE_ADD(");
            d.renderArg(args.get(0), sql, params);
            sql.append(", INTERVAL ");
            if (amount instanceof Number) {
                sql.append(amount);
            } else {
                d.renderArg(amount, sql, params);
            }
            sql.append(" ").append(unit).append(")");
        });

        registerFunction(BuiltinSqlFunction.DATE_SUB, (fn, args, sql, params, d) -> {
            if (args.size() < 2) return;
            String unit = args.size() > 2 ? String.valueOf(args.get(2)).toUpperCase() : "DAY";
            Object amount = args.get(1);
            sql.append("DATE_SUB(");
            d.renderArg(args.get(0), sql, params);
            sql.append(", INTERVAL ");
            if (amount instanceof Number) {
                sql.append(amount);
            } else {
                d.renderArg(amount, sql, params);
            }
            sql.append(" ").append(unit).append(")");
        });

        registerFunction(BuiltinSqlFunction.DATEDIFF, (fn, args, sql, params, d) -> {
            if (args.size() < 2) return;
            sql.append("DATEDIFF(");
            d.renderArg(args.get(0), sql, params);
            sql.append(", ");
            d.renderArg(args.get(1), sql, params);
            sql.append(")");
        });

        registerFunction(BuiltinSqlFunction.NOW, (fn, args, sql, params, d) -> sql.append("NOW()"));
        registerFunction("CURRENT_TIMESTAMP", getFunctionRenderer(BuiltinSqlFunction.NOW));

        registerFunction(BuiltinSqlFunction.CURDATE, (fn, args, sql, params, d) -> sql.append("CURDATE()"));
        registerFunction("CURRENT_DATE", getFunctionRenderer(BuiltinSqlFunction.CURDATE));

        registerFunction(BuiltinSqlFunction.CURTIME, (fn, args, sql, params, d) -> sql.append("CURTIME()"));
        registerFunction("CURRENT_TIME", getFunctionRenderer(BuiltinSqlFunction.CURTIME));

        registerFunction(BuiltinSqlFunction.GROUP_CONCAT, (fn, args, sql, params, d) -> {
            if (args.isEmpty()) return;
            sql.append("GROUP_CONCAT(");
            d.renderArg(args.get(0), sql, params);
            if (args.size() > 1) {
                sql.append(" SEPARATOR ");
                Object sep = args.get(1);
                if (sep instanceof SqlExpression) {
                    d.renderArg(sep, sql, params);
                } else {
                    sql.append("'").append(String.valueOf(sep).replace("'", "''")).append("'");
                }
            }
            sql.append(")");
        });

        registerFunction(BuiltinSqlFunction.TRUNCATE, (fn, args, sql, params, d) -> {
            if (args.isEmpty()) return;
            sql.append("TRUNCATE(");
            d.renderArg(args.get(0), sql, params);
            sql.append(", ");
            d.renderArg(args.size() > 1 ? args.get(1) : 0, sql, params);
            sql.append(")");
        });
        registerFunction(BuiltinSqlFunction.TRUNC, getFunctionRenderer(BuiltinSqlFunction.TRUNCATE));

        registerFunction(BuiltinSqlFunction.LAST_DAY, (fn, args, sql, params, d) -> {
            if (args.isEmpty()) return;
            sql.append("LAST_DAY(");
            d.renderArg(args.get(0), sql, params);
            sql.append(")");
        });

        registerFunction(BuiltinSqlFunction.NVL, (fn, args, sql, params, d) -> {
            if (args.size() < 2) return;
            sql.append("IFNULL(");
            d.renderArg(args.get(0), sql, params);
            sql.append(", ");
            d.renderArg(args.get(1), sql, params);
            sql.append(")");
        });

        registerFunction("STRPOS", (fn, args, sql, params, d) -> {
            if (args.size() < 2) return;
            sql.append("INSTR(");
            d.renderArg(args.get(0), sql, params);
            sql.append(", ");
            d.renderArg(args.get(1), sql, params);
            sql.append(")");
        });

        registerFunction(BuiltinSqlFunction.LOCATE, (fn, args, sql, params, d) -> {
            if (args.size() < 2) return;
            sql.append("LOCATE(");
            d.renderArg(args.get(0), sql, params);
            sql.append(", ");
            d.renderArg(args.get(1), sql, params);
            sql.append(")");
        });

        registerFunction(BuiltinSqlFunction.IIF, (fn, args, sql, params, d) -> {
            if (args.size() < 3) return;
            sql.append("IF(");
            d.renderArg(args.get(0), sql, params);
            sql.append(", ");
            d.renderArg(args.get(1), sql, params);
            sql.append(", ");
            d.renderArg(args.get(2), sql, params);
            sql.append(")");
        });
        registerFunction(BuiltinSqlFunction.IF, getFunctionRenderer(BuiltinSqlFunction.IIF));

        registerFunction(BuiltinSqlFunction.CAST, (fn, args, sql, params, d) -> {
            if (args.size() < 2) return;
            String type = String.valueOf(args.get(1)).toUpperCase();
            if ("VARCHAR".equals(type) || "TEXT".equals(type) || "STRING".equals(type)) type = "CHAR";
            else if ("INT".equals(type) || "INTEGER".equals(type) || "BIGINT".equals(type)) type = "SIGNED";
            else if ("FLOAT".equals(type) || "DOUBLE".equals(type)) type = "DECIMAL(18,4)";
            sql.append("CAST(");
            d.renderArg(args.get(0), sql, params);
            sql.append(" AS ").append(type).append(")");
        });
    }
}
