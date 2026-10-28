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
    public String quote(String identifier) {
        if (identifier == null) return null;
        if (identifier.startsWith("\"") && identifier.endsWith("\"")) return identifier;
        return "\"" + identifier + "\"";
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
        if ("NOW".equals(name)) {
            sql.append("SYSDATE");
            return true;
        }
        if ("CURDATE".equals(name)) {
            sql.append("TRUNC(SYSDATE)");
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
