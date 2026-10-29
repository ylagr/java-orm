package com.javaorm.core.dialect;

import com.javaorm.core.ast.SelectStatement;

import java.util.List;

/**
 * Oracle 数据库方言实现。
 * 支持标准双引号标识符转义、Oracle 12c+ 原生分页语法（OFFSET ... ROWS FETCH NEXT ... ROWS ONLY）、
 * DUAL 虚拟表支持、SYSDATE/NVL/|| 拼接等 Oracle 专属函数映射。
 */
public class OracleDialect extends AbstractDialect {

    private final boolean oracle11;

    public OracleDialect() {
        this(false);
    }

    public OracleDialect(boolean oracle11) {
        this.oracle11 = oracle11;
    }

    public boolean isOracle11() {
        return oracle11;
    }


    @Override
    public SqlAndParams buildSelect(SelectStatement stmt) {
        if (stmt.getRootTableName() == null || stmt.getRootTableName().isEmpty()) {
            stmt.setRootTableName("DUAL");
        }
        if (oracle11 && (stmt.getLimit() != null || (stmt.getOffset() != null && stmt.getOffset() > 0))) {
            return buildOracle11Select(stmt);
        }
        return super.buildSelect(stmt);
    }

    private SqlAndParams buildOracle11Select(SelectStatement stmt) {
        Integer limit = stmt.getLimit();
        Integer offset = stmt.getOffset();
        stmt.setLimit(null);
        stmt.setOffset(null);
        SqlAndParams inner;
        try {
            inner = super.buildSelect(stmt);
        } finally {
            stmt.setLimit(limit);
            stmt.setOffset(offset);
        }

        StringBuilder sql = new StringBuilder();
        java.util.List<Object> params = new java.util.ArrayList<>(inner.getParams());

        int off = (offset != null && offset > 0) ? offset : 0;
        if (off > 0) {
            sql.append("SELECT * FROM (SELECT tt.*, ROWNUM AS \"row_num\" FROM (")
               .append(inner.getSql())
               .append(") tt");
            if (limit != null) {
                sql.append(" WHERE ROWNUM <= ?");
                params.add(off + limit);
            }
            sql.append(") WHERE \"row_num\" > ?");
            params.add(off);
        } else if (limit != null) {
            sql.append("SELECT * FROM (")
               .append(inner.getSql())
               .append(") WHERE ROWNUM <= ?");
            params.add(limit);
        } else {
            return inner;
        }
        return new SqlAndParams(sql.toString(), params);
    }

    @Override
    protected void renderPagination(StringBuilder sql, List<Object> params, Integer limit, Integer offset) {
        if (oracle11) {
            return;
        }
        if (limit != null) {
            int off = (offset != null && offset > 0) ? offset : 0;
            sql.append(" OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
            params.add(off);
            params.add(limit);
        } else if (offset != null && offset > 0) {
            sql.append(" OFFSET ? ROWS");
            params.add(offset);
        }
    }

    @Override
    protected boolean renderDialectSpecificFunction(String name, List<Object> args, StringBuilder sql, List<Object> params) {
        if ("NOW".equals(name) || "CURRENT_TIMESTAMP".equals(name)) {
            sql.append("SYSDATE");
            return true;
        }
        if ("CURDATE".equals(name) || "CURRENT_DATE".equals(name)) {
            sql.append("TRUNC(SYSDATE)");
            return true;
        }
        if ("CURTIME".equals(name) || "CURRENT_TIME".equals(name)) {
            sql.append("TO_CHAR(SYSDATE, 'HH24:MI:SS')");
            return true;
        }
        if (("DATE_FORMAT".equals(name) || "DATE_TO_STRING".equals(name)) && !args.isEmpty()) {
            sql.append("TO_CHAR(");
            renderArg(args.get(0), sql, params);
            sql.append(", ");
            String fmt = args.size() > 1 ? String.valueOf(args.get(1)) : "yyyy-MM-dd HH:mm:ss";
            String oraFmt = DateFormatPatternConverter.toOracle(fmt);
            sql.append("'").append(oraFmt).append("')");
            return true;
        }
        if (("STR_TO_DATE".equals(name) || "TO_DATE".equals(name) || "STRING_TO_DATE".equals(name)) && !args.isEmpty()) {
            sql.append("TO_TIMESTAMP(");
            renderArg(args.get(0), sql, params);
            sql.append(", ");
            String fmt = args.size() > 1 ? String.valueOf(args.get(1)) : "yyyy-MM-dd HH:mm:ss";
            String oraFmt = DateFormatPatternConverter.toOracle(fmt);
            sql.append("'").append(oraFmt).append("')");
            return true;
        }
        if ("DATE_ADD".equals(name) && args.size() >= 2) {
            String unit = args.size() > 2 ? String.valueOf(args.get(2)).toUpperCase() : "DAY";
            Object amount = args.get(1);
            if ("MONTH".equals(unit)) {
                sql.append("ADD_MONTHS(");
                renderArg(args.get(0), sql, params);
                sql.append(", ");
                if (amount instanceof Number) {
                    sql.append(amount);
                } else {
                    renderArg(amount, sql, params);
                }
                sql.append(")");
            } else if ("YEAR".equals(unit)) {
                sql.append("ADD_MONTHS(");
                renderArg(args.get(0), sql, params);
                sql.append(", (");
                if (amount instanceof Number) {
                    sql.append(amount);
                } else {
                    renderArg(amount, sql, params);
                }
                sql.append(" * 12))");
            } else if ("HOUR".equals(unit)) {
                sql.append("(");
                renderArg(args.get(0), sql, params);
                sql.append(" + (");
                if (amount instanceof Number) {
                    sql.append(amount);
                } else {
                    renderArg(amount, sql, params);
                }
                sql.append(" / 24))");
            } else if ("MINUTE".equals(unit)) {
                sql.append("(");
                renderArg(args.get(0), sql, params);
                sql.append(" + (");
                if (amount instanceof Number) {
                    sql.append(amount);
                } else {
                    renderArg(amount, sql, params);
                }
                sql.append(" / 1440))");
            } else if ("SECOND".equals(unit)) {
                sql.append("(");
                renderArg(args.get(0), sql, params);
                sql.append(" + (");
                if (amount instanceof Number) {
                    sql.append(amount);
                } else {
                    renderArg(amount, sql, params);
                }
                sql.append(" / 86400))");
            } else {
                // DAY
                sql.append("(");
                renderArg(args.get(0), sql, params);
                sql.append(" + ");
                if (amount instanceof Number) {
                    sql.append(amount);
                } else {
                    renderArg(amount, sql, params);
                }
                sql.append(")");
            }
            return true;
        }
        if ("DATEDIFF".equals(name) && args.size() >= 2) {
            sql.append("(TRUNC(");
            renderArg(args.get(0), sql, params);
            sql.append(") - TRUNC(");
            renderArg(args.get(1), sql, params);
            sql.append("))");
            return true;
        }
        if ("DAYOFWEEK".equals(name) && !args.isEmpty()) {
            sql.append("TO_CHAR(");
            renderArg(args.get(0), sql, params);
            sql.append(", 'D')");
            return true;
        }
        if ("DAYOFYEAR".equals(name) && !args.isEmpty()) {
            sql.append("TO_CHAR(");
            renderArg(args.get(0), sql, params);
            sql.append(", 'DDD')");
            return true;
        }
        if ("IFNULL".equals(name) && args.size() >= 2) {
            sql.append("NVL(");
            renderArg(args.get(0), sql, params);
            sql.append(", ");
            renderArg(args.get(1), sql, params);
            sql.append(")");
            return true;
        }
        if ("CONCAT".equals(name) && !args.isEmpty()) {
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) sql.append(" || ");
                renderArg(args.get(i), sql, params);
            }
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
            sql.append("LISTAGG(");
            renderArg(args.get(0), sql, params);
            if (args.size() > 1) {
                sql.append(", ");
                renderArg(args.get(1), sql, params);
            } else {
                sql.append(", ','");
            }
            sql.append(") WITHIN GROUP (ORDER BY ");
            renderArg(args.get(0), sql, params);
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
        if ("CHAR_LENGTH".equals(name) && !args.isEmpty()) {
            sql.append("LENGTH(");
            renderArg(args.get(0), sql, params);
            sql.append(")");
            return true;
        }
        if ("YEAR".equals(name) && !args.isEmpty()) {
            sql.append("EXTRACT(YEAR FROM ");
            renderArg(args.get(0), sql, params);
            sql.append(")");
            return true;
        }
        if ("MONTH".equals(name) && !args.isEmpty()) {
            sql.append("EXTRACT(MONTH FROM ");
            renderArg(args.get(0), sql, params);
            sql.append(")");
            return true;
        }
        if ("DAY".equals(name) && !args.isEmpty()) {
            sql.append("EXTRACT(DAY FROM ");
            renderArg(args.get(0), sql, params);
            sql.append(")");
            return true;
        }
        if (("HOUR".equals(name) || "MINUTE".equals(name) || "SECOND".equals(name)) && !args.isEmpty()) {
            sql.append("EXTRACT(").append(name).append(" FROM CAST(");
            renderArg(args.get(0), sql, params);
            sql.append(" AS TIMESTAMP))");
            return true;
        }
        if ("LEFT".equals(name) && args.size() >= 2) {
            sql.append("SUBSTR(");
            renderArg(args.get(0), sql, params);
            sql.append(", 1, ");
            renderArg(args.get(1), sql, params);
            sql.append(")");
            return true;
        }
        if ("RIGHT".equals(name) && args.size() >= 2) {
            sql.append("SUBSTR(");
            renderArg(args.get(0), sql, params);
            sql.append(", -(");
            renderArg(args.get(1), sql, params);
            sql.append("))");
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
        return super.renderDialectSpecificFunction(name, args, sql, params);
    }
}
