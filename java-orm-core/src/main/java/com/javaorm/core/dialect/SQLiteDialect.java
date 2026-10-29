package com.javaorm.core.dialect;

import java.util.List;

public class SQLiteDialect extends AbstractDialect {

    @Override
    public String quote(String identifier) {
        if (identifier == null) return null;
        if (identifier.startsWith("\"") && identifier.endsWith("\"")) return identifier;
        return "\"" + identifier + "\"";
    }

    @Override
    protected boolean renderDialectSpecificFunction(String name, List<Object> args, StringBuilder sql, List<Object> params) {
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
        if ("NOW".equals(name)) {
            sql.append("datetime('now', 'localtime')");
            return true;
        }
        if ("CURDATE".equals(name)) {
            sql.append("date('now', 'localtime')");
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
        return false;
    }
}
