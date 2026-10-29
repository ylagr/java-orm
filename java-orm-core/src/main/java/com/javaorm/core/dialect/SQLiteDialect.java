package com.javaorm.core.dialect;

import com.javaorm.core.function.BuiltinSqlFunction;

/**
 * SQLite 数据库方言实现。
 * 通过内置注册机制支持 SQLite 专属语法、时间函数、字符串运算与类型转换。
 */
public class SQLiteDialect extends AbstractDialect {

    public SQLiteDialect() {
        super();
        registerSQLiteFunctions();
    }

    protected void registerSQLiteFunctions() {
        registerFunction(BuiltinSqlFunction.NOW, (fn, args, sql, params, d) -> sql.append("datetime('now', 'localtime')"));
        registerFunction("CURRENT_TIMESTAMP", getFunctionRenderer(BuiltinSqlFunction.NOW));

        registerFunction(BuiltinSqlFunction.CURDATE, (fn, args, sql, params, d) -> sql.append("date('now', 'localtime')"));
        registerFunction("CURRENT_DATE", getFunctionRenderer(BuiltinSqlFunction.CURDATE));

        registerFunction(BuiltinSqlFunction.CURTIME, (fn, args, sql, params, d) -> sql.append("time('now', 'localtime')"));
        registerFunction("CURRENT_TIME", getFunctionRenderer(BuiltinSqlFunction.CURTIME));

        registerFunction(BuiltinSqlFunction.DATE_FORMAT, (fn, args, sql, params, d) -> {
            if (args.isEmpty()) return;
            sql.append("strftime('");
            String fmt = args.size() > 1 ? String.valueOf(args.get(1)) : "yyyy-MM-dd HH:mm:ss";
            String sqFmt = DateFormatPatternConverter.toSQLite(fmt);
            sql.append(sqFmt).append("', ");
            d.renderArg(args.get(0), sql, params);
            sql.append(")");
        });
        registerFunction("DATE_TO_STRING", getFunctionRenderer(BuiltinSqlFunction.DATE_FORMAT));

        registerFunction(BuiltinSqlFunction.STR_TO_DATE, (fn, args, sql, params, d) -> {
            if (args.isEmpty()) return;
            sql.append("datetime(");
            d.renderArg(args.get(0), sql, params);
            sql.append(")");
        });
        registerFunction("TO_DATE", getFunctionRenderer(BuiltinSqlFunction.STR_TO_DATE));
        registerFunction("STRING_TO_DATE", getFunctionRenderer(BuiltinSqlFunction.STR_TO_DATE));

        registerFunction(BuiltinSqlFunction.DATE_ADD, (fn, args, sql, params, d) -> {
            if (args.size() < 2) return;
            String unit = args.size() > 2 ? String.valueOf(args.get(2)).toUpperCase() : "DAY";
            String unitSuffix;
            if ("MONTH".equals(unit)) unitSuffix = " months";
            else if ("YEAR".equals(unit)) unitSuffix = " years";
            else if ("HOUR".equals(unit)) unitSuffix = " hours";
            else if ("MINUTE".equals(unit)) unitSuffix = " minutes";
            else if ("SECOND".equals(unit)) unitSuffix = " seconds";
            else unitSuffix = " days";

            sql.append("datetime(");
            d.renderArg(args.get(0), sql, params);
            sql.append(", (");
            if (args.get(1) instanceof Number) {
                sql.append(args.get(1));
            } else {
                d.renderArg(args.get(1), sql, params);
            }
            sql.append(" || '").append(unitSuffix).append("'))");
        });

        registerFunction(BuiltinSqlFunction.DATE_SUB, (fn, args, sql, params, d) -> {
            if (args.size() < 2) return;
            String unit = args.size() > 2 ? String.valueOf(args.get(2)).toUpperCase() : "DAY";
            String unitSuffix;
            if ("MONTH".equals(unit)) unitSuffix = " months";
            else if ("YEAR".equals(unit)) unitSuffix = " years";
            else if ("HOUR".equals(unit)) unitSuffix = " hours";
            else if ("MINUTE".equals(unit)) unitSuffix = " minutes";
            else if ("SECOND".equals(unit)) unitSuffix = " seconds";
            else unitSuffix = " days";

            sql.append("datetime(");
            d.renderArg(args.get(0), sql, params);
            sql.append(", ('-' || ");
            if (args.get(1) instanceof Number) {
                sql.append(args.get(1));
            } else {
                d.renderArg(args.get(1), sql, params);
            }
            sql.append(" || '").append(unitSuffix).append("'))");
        });

        registerFunction(BuiltinSqlFunction.DATEDIFF, (fn, args, sql, params, d) -> {
            if (args.size() < 2) return;
            sql.append("CAST(julianday(");
            d.renderArg(args.get(0), sql, params);
            sql.append(") - julianday(");
            d.renderArg(args.get(1), sql, params);
            sql.append(") AS INT)");
        });

        registerFunction(BuiltinSqlFunction.DAYOFWEEK, (fn, args, sql, params, d) -> {
            if (args.isEmpty()) return;
            sql.append("(CAST(strftime('%w', ");
            d.renderArg(args.get(0), sql, params);
            sql.append(") AS INT) + 1)");
        });

        registerFunction(BuiltinSqlFunction.DAYOFYEAR, (fn, args, sql, params, d) -> {
            if (args.isEmpty()) return;
            sql.append("CAST(strftime('%j', ");
            d.renderArg(args.get(0), sql, params);
            sql.append(") AS INT)");
        });

        registerFunction(BuiltinSqlFunction.CHAR_LENGTH, (fn, args, sql, params, d) -> {
            if (args.isEmpty()) return;
            sql.append("length(");
            d.renderArg(args.get(0), sql, params);
            sql.append(")");
        });

        registerFunction(BuiltinSqlFunction.CONCAT, (fn, args, sql, params, d) -> {
            if (args.isEmpty()) return;
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) sql.append(" || ");
                d.renderArg(args.get(i), sql, params);
            }
        });

        registerFunction(BuiltinSqlFunction.CONCAT_WS, (fn, args, sql, params, d) -> {
            if (args.size() < 2) return;
            Object sep = args.get(0);
            for (int i = 1; i < args.size(); i++) {
                if (i > 1) {
                    sql.append(" || ");
                    d.renderArg(sep, sql, params);
                    sql.append(" || ");
                }
                d.renderArg(args.get(i), sql, params);
            }
        });

        registerFunction(BuiltinSqlFunction.GROUP_CONCAT, (fn, args, sql, params, d) -> {
            if (args.isEmpty()) return;
            sql.append("group_concat(");
            d.renderArg(args.get(0), sql, params);
            if (args.size() > 1) {
                sql.append(", ");
                d.renderArg(args.get(1), sql, params);
            }
            sql.append(")");
        });

        registerFunction(BuiltinSqlFunction.TRUNCATE, (fn, args, sql, params, d) -> {
            if (args.isEmpty()) return;
            if (args.size() > 1) {
                sql.append("ROUND(");
                d.renderArg(args.get(0), sql, params);
                sql.append(", ");
                d.renderArg(args.get(1), sql, params);
                sql.append(")");
            } else {
                sql.append("CAST(");
                d.renderArg(args.get(0), sql, params);
                sql.append(" AS INT)");
            }
        });
        registerFunction(BuiltinSqlFunction.TRUNC, getFunctionRenderer(BuiltinSqlFunction.TRUNCATE));

        registerFunction(BuiltinSqlFunction.YEAR, (fn, args, sql, params, d) -> {
            if (args.isEmpty()) return;
            sql.append("CAST(strftime('%Y', ");
            d.renderArg(args.get(0), sql, params);
            sql.append(") AS INT)");
        });

        registerFunction(BuiltinSqlFunction.MONTH, (fn, args, sql, params, d) -> {
            if (args.isEmpty()) return;
            sql.append("CAST(strftime('%m', ");
            d.renderArg(args.get(0), sql, params);
            sql.append(") AS INT)");
        });

        registerFunction(BuiltinSqlFunction.DAY, (fn, args, sql, params, d) -> {
            if (args.isEmpty()) return;
            sql.append("CAST(strftime('%d', ");
            d.renderArg(args.get(0), sql, params);
            sql.append(") AS INT)");
        });

        registerFunction(BuiltinSqlFunction.HOUR, (fn, args, sql, params, d) -> {
            if (args.isEmpty()) return;
            sql.append("CAST(strftime('%H', ");
            d.renderArg(args.get(0), sql, params);
            sql.append(") AS INT)");
        });

        registerFunction(BuiltinSqlFunction.MINUTE, (fn, args, sql, params, d) -> {
            if (args.isEmpty()) return;
            sql.append("CAST(strftime('%M', ");
            d.renderArg(args.get(0), sql, params);
            sql.append(") AS INT)");
        });

        registerFunction(BuiltinSqlFunction.SECOND, (fn, args, sql, params, d) -> {
            if (args.isEmpty()) return;
            sql.append("CAST(strftime('%S', ");
            d.renderArg(args.get(0), sql, params);
            sql.append(") AS INT)");
        });

        registerFunction(BuiltinSqlFunction.SUBSTRING, (fn, args, sql, params, d) -> {
            if (args.size() < 2) return;
            sql.append("substr(");
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) sql.append(", ");
                d.renderArg(args.get(i), sql, params);
            }
            sql.append(")");
        });
        registerFunction("SUBSTR", getFunctionRenderer(BuiltinSqlFunction.SUBSTRING));

        registerFunction(BuiltinSqlFunction.LEFT, (fn, args, sql, params, d) -> {
            if (args.size() < 2) return;
            sql.append("substr(");
            d.renderArg(args.get(0), sql, params);
            sql.append(", 1, ");
            d.renderArg(args.get(1), sql, params);
            sql.append(")");
        });

        registerFunction(BuiltinSqlFunction.RIGHT, (fn, args, sql, params, d) -> {
            if (args.size() < 2) return;
            sql.append("substr(");
            d.renderArg(args.get(0), sql, params);
            sql.append(", -(");
            d.renderArg(args.get(1), sql, params);
            sql.append("))");
        });

        registerFunction(BuiltinSqlFunction.MOD, (fn, args, sql, params, d) -> {
            if (args.size() < 2) return;
            sql.append("(");
            d.renderArg(args.get(0), sql, params);
            sql.append(" % ");
            d.renderArg(args.get(1), sql, params);
            sql.append(")");
        });

        registerFunction(BuiltinSqlFunction.GREATEST, (fn, args, sql, params, d) -> {
            if (args.isEmpty()) return;
            sql.append("max(");
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) sql.append(", ");
                d.renderArg(args.get(i), sql, params);
            }
            sql.append(")");
        });

        registerFunction(BuiltinSqlFunction.LEAST, (fn, args, sql, params, d) -> {
            if (args.isEmpty()) return;
            sql.append("min(");
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) sql.append(", ");
                d.renderArg(args.get(i), sql, params);
            }
            sql.append(")");
        });

        registerFunction(BuiltinSqlFunction.IFNULL, (fn, args, sql, params, d) -> {
            if (args.size() < 2) return;
            sql.append("ifnull(");
            d.renderArg(args.get(0), sql, params);
            sql.append(", ");
            d.renderArg(args.get(1), sql, params);
            sql.append(")");
        });
        registerFunction(BuiltinSqlFunction.NVL, getFunctionRenderer(BuiltinSqlFunction.IFNULL));

        registerFunction(BuiltinSqlFunction.CEILING, (fn, args, sql, params, d) -> {
            if (args.isEmpty()) return;
            sql.append("ceil(");
            d.renderArg(args.get(0), sql, params);
            sql.append(")");
        });
        registerFunction(BuiltinSqlFunction.CEIL, getFunctionRenderer(BuiltinSqlFunction.CEILING));

        registerFunction(BuiltinSqlFunction.REPEAT, (fn, args, sql, params, d) -> {
            if (args.size() < 2) return;
            sql.append("replace(hex(zeroblob(");
            d.renderArg(args.get(1), sql, params);
            sql.append(")), '00', ");
            d.renderArg(args.get(0), sql, params);
            sql.append(")");
        });

        registerFunction(BuiltinSqlFunction.LAST_DAY, (fn, args, sql, params, d) -> {
            if (args.isEmpty()) return;
            sql.append("date(");
            d.renderArg(args.get(0), sql, params);
            sql.append(", 'start of month', '+1 month', '-1 day')");
        });

        registerFunction(BuiltinSqlFunction.LOCATE, (fn, args, sql, params, d) -> {
            if (args.size() < 2) return;
            sql.append("instr(");
            d.renderArg(args.get(1), sql, params);
            sql.append(", ");
            d.renderArg(args.get(0), sql, params);
            sql.append(")");
        });

        registerFunction(BuiltinSqlFunction.IIF, (fn, args, sql, params, d) -> {
            if (args.size() < 3) return;
            sql.append("iif(");
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
            if ("CHAR".equals(type) || "VARCHAR".equals(type) || "STRING".equals(type)) type = "TEXT";
            else if ("SIGNED".equals(type) || "INTEGER".equals(type)) type = "INT";
            else if ("DECIMAL".equals(type) || "DOUBLE".equals(type) || "FLOAT".equals(type)) type = "REAL";
            sql.append("CAST(");
            d.renderArg(args.get(0), sql, params);
            sql.append(" AS ").append(type).append(")");
        });
    }
}
