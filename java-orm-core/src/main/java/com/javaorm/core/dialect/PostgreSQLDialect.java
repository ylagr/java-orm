package com.javaorm.core.dialect;

import java.util.List;

public class PostgreSQLDialect extends AbstractDialect {

    @Override
    public String quote(String identifier) {
        if (identifier == null) return null;
        if (identifier.startsWith("\"") && identifier.endsWith("\"")) return identifier;
        return "\"" + identifier + "\"";
    }

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
        if ("CURDATE".equals(name)) {
            sql.append("CURRENT_DATE");
            return true;
        }
        if ("NOW".equals(name)) {
            sql.append("CURRENT_TIMESTAMP");
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
        return false;
    }
}
