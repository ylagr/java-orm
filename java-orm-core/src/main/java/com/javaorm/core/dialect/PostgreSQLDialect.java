package com.javaorm.core.dialect;

import com.javaorm.core.function.BuiltinSqlFunction;
import com.javaorm.core.function.FunctionRenderer;

/**
 * PostgreSQL 数据库方言实现。
 * 通过内置注册机制支持 PostgreSQL 专属语法、时间函数及类型转换映射。
 */
public class PostgreSQLDialect extends AbstractDialect {

    public PostgreSQLDialect() {
        super();
        registerPostgreSQLFunctions();
    }

    protected void registerPostgreSQLFunctions() {
        registerFunction(BuiltinSqlFunction.IFNULL, (fn, args, sql, params, d) -> {
            if (args.size() < 2) return;
            sql.append("COALESCE(");
            d.renderArg(args.get(0), sql, params);
            sql.append(", ");
            d.renderArg(args.get(1), sql, params);
            sql.append(")");
        });
        registerFunction(BuiltinSqlFunction.NVL, getFunctionRenderer(BuiltinSqlFunction.IFNULL));

        registerFunction(BuiltinSqlFunction.CURDATE, (fn, args, sql, params, d) -> sql.append("CURRENT_DATE"));
        registerFunction("CURRENT_DATE", getFunctionRenderer(BuiltinSqlFunction.CURDATE));

        registerFunction(BuiltinSqlFunction.CURTIME, (fn, args, sql, params, d) -> sql.append("CURRENT_TIME"));
        registerFunction("CURRENT_TIME", getFunctionRenderer(BuiltinSqlFunction.CURTIME));

        registerFunction(BuiltinSqlFunction.NOW, (fn, args, sql, params, d) -> sql.append("CURRENT_TIMESTAMP"));
        registerFunction("CURRENT_TIMESTAMP", getFunctionRenderer(BuiltinSqlFunction.NOW));

        registerFunction(BuiltinSqlFunction.DATE_FORMAT, (fn, args, sql, params, d) -> {
            if (args.isEmpty()) return;
            sql.append("TO_CHAR(");
            d.renderArg(args.get(0), sql, params);
            sql.append(", ");
            String fmt = args.size() > 1 ? String.valueOf(args.get(1)) : "yyyy-MM-dd HH:mm:ss";
            String pgFmt = DateFormatPatternConverter.toPostgreSQL(fmt);
            sql.append("'").append(pgFmt).append("')");
        });
        registerFunction("DATE_TO_STRING", getFunctionRenderer(BuiltinSqlFunction.DATE_FORMAT));

        registerFunction(BuiltinSqlFunction.STR_TO_DATE, (fn, args, sql, params, d) -> {
            if (args.isEmpty()) return;
            sql.append("TO_TIMESTAMP(");
            d.renderArg(args.get(0), sql, params);
            sql.append(", ");
            String fmt = args.size() > 1 ? String.valueOf(args.get(1)) : "yyyy-MM-dd HH:mm:ss";
            String pgFmt = DateFormatPatternConverter.toPostgreSQL(fmt);
            sql.append("'").append(pgFmt).append("')");
        });
        registerFunction("TO_DATE", getFunctionRenderer(BuiltinSqlFunction.STR_TO_DATE));
        registerFunction("STRING_TO_DATE", getFunctionRenderer(BuiltinSqlFunction.STR_TO_DATE));

        registerFunction(BuiltinSqlFunction.DATE_ADD, (fn, args, sql, params, d) -> {
            if (args.size() < 2) return;
            String unit = args.size() > 2 ? String.valueOf(args.get(2)).toUpperCase() : "DAY";
            Object amount = args.get(1);
            sql.append("(");
            d.renderArg(args.get(0), sql, params);
            sql.append(" + (");
            if (amount instanceof Number) {
                sql.append(amount);
            } else {
                d.renderArg(amount, sql, params);
            }
            sql.append(" * INTERVAL '1 ").append(unit).append("'))");
        });

        registerFunction(BuiltinSqlFunction.DATE_SUB, (fn, args, sql, params, d) -> {
            if (args.size() < 2) return;
            String unit = args.size() > 2 ? String.valueOf(args.get(2)).toUpperCase() : "DAY";
            Object amount = args.get(1);
            sql.append("(");
            d.renderArg(args.get(0), sql, params);
            sql.append(" - (");
            if (amount instanceof Number) {
                sql.append(amount);
            } else {
                d.renderArg(amount, sql, params);
            }
            sql.append(" * INTERVAL '1 ").append(unit).append("'))");
        });

        registerFunction(BuiltinSqlFunction.DATEDIFF, (fn, args, sql, params, d) -> {
            if (args.size() < 2) return;
            sql.append("(CAST(");
            d.renderArg(args.get(0), sql, params);
            sql.append(" AS DATE) - CAST(");
            d.renderArg(args.get(1), sql, params);
            sql.append(" AS DATE))");
        });

        registerFunction(BuiltinSqlFunction.DAYOFWEEK, (fn, args, sql, params, d) -> {
            if (args.isEmpty()) return;
            sql.append("(EXTRACT(DOW FROM ");
            d.renderArg(args.get(0), sql, params);
            sql.append(") + 1)");
        });

        registerFunction(BuiltinSqlFunction.DAYOFYEAR, (fn, args, sql, params, d) -> {
            if (args.isEmpty()) return;
            sql.append("EXTRACT(DOY FROM ");
            d.renderArg(args.get(0), sql, params);
            sql.append(")");
        });

        FunctionRenderer extractRenderer = (fn, args, sql, params, d) -> {
            if (args.isEmpty()) return;
            sql.append("EXTRACT(").append(fn.getName().toUpperCase()).append(" FROM ");
            d.renderArg(args.get(0), sql, params);
            sql.append(")");
        };
        registerFunction(BuiltinSqlFunction.YEAR, extractRenderer);
        registerFunction(BuiltinSqlFunction.MONTH, extractRenderer);
        registerFunction(BuiltinSqlFunction.DAY, extractRenderer);
        registerFunction(BuiltinSqlFunction.HOUR, extractRenderer);
        registerFunction(BuiltinSqlFunction.MINUTE, extractRenderer);
        registerFunction(BuiltinSqlFunction.SECOND, extractRenderer);

        registerFunction(BuiltinSqlFunction.INSTR, (fn, args, sql, params, d) -> {
            if (args.size() < 2) return;
            sql.append("STRPOS(");
            d.renderArg(args.get(0), sql, params);
            sql.append(", ");
            d.renderArg(args.get(1), sql, params);
            sql.append(")");
        });
        registerFunction("STRPOS", getFunctionRenderer(BuiltinSqlFunction.INSTR));

        registerFunction(BuiltinSqlFunction.LOCATE, (fn, args, sql, params, d) -> {
            if (args.size() < 2) return;
            sql.append("STRPOS(");
            d.renderArg(args.get(1), sql, params);
            sql.append(", ");
            d.renderArg(args.get(0), sql, params);
            sql.append(")");
        });

        registerFunction(BuiltinSqlFunction.SUBSTRING, (fn, args, sql, params, d) -> {
            if (args.size() < 2) return;
            sql.append("SUBSTR(");
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) sql.append(", ");
                d.renderArg(args.get(i), sql, params);
            }
            sql.append(")");
        });
        registerFunction("SUBSTR", getFunctionRenderer(BuiltinSqlFunction.SUBSTRING));

        registerFunction(BuiltinSqlFunction.GROUP_CONCAT, (fn, args, sql, params, d) -> {
            if (args.isEmpty()) return;
            sql.append("STRING_AGG(CAST(");
            d.renderArg(args.get(0), sql, params);
            sql.append(" AS TEXT), ");
            if (args.size() > 1) {
                d.renderArg(args.get(1), sql, params);
            } else {
                sql.append("','");
            }
            sql.append(")");
        });

        registerFunction(BuiltinSqlFunction.TRUNCATE, (fn, args, sql, params, d) -> {
            if (args.isEmpty()) return;
            sql.append("TRUNC(");
            d.renderArg(args.get(0), sql, params);
            if (args.size() > 1) {
                sql.append(", ");
                d.renderArg(args.get(1), sql, params);
            }
            sql.append(")");
        });
        registerFunction(BuiltinSqlFunction.TRUNC, getFunctionRenderer(BuiltinSqlFunction.TRUNCATE));

        registerFunction(BuiltinSqlFunction.LAST_DAY, (fn, args, sql, params, d) -> {
            if (args.isEmpty()) return;
            sql.append("CAST(DATE_TRUNC('month', ");
            d.renderArg(args.get(0), sql, params);
            sql.append(") + INTERVAL '1 month - 1 day' AS DATE)");
        });

        registerFunction(BuiltinSqlFunction.IIF, (fn, args, sql, params, d) -> {
            if (args.size() < 3) return;
            sql.append("(CASE WHEN ");
            d.renderArg(args.get(0), sql, params);
            sql.append(" THEN ");
            d.renderArg(args.get(1), sql, params);
            sql.append(" ELSE ");
            d.renderArg(args.get(2), sql, params);
            sql.append(" END)");
        });
        registerFunction(BuiltinSqlFunction.IF, getFunctionRenderer(BuiltinSqlFunction.IIF));

        registerFunction(BuiltinSqlFunction.CAST, (fn, args, sql, params, d) -> {
            if (args.size() < 2) return;
            String type = String.valueOf(args.get(1)).toUpperCase();
            if ("CHAR".equals(type) || "STRING".equals(type)) type = "TEXT";
            else if ("SIGNED".equals(type) || "INT".equals(type)) type = "INTEGER";
            sql.append("CAST(");
            d.renderArg(args.get(0), sql, params);
            sql.append(" AS ").append(type).append(")");
        });
    }
}
