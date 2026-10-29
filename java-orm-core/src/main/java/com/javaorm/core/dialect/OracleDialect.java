package com.javaorm.core.dialect;

import com.javaorm.core.ast.CteClause;
import com.javaorm.core.ast.SelectStatement;
import com.javaorm.core.function.BuiltinSqlFunction;
import com.javaorm.core.function.FunctionRenderer;

import java.util.ArrayList;
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
        super();
        this.oracle11 = oracle11;
        registerOracleFunctions();
    }

    public boolean isOracle11() {
        return oracle11;
    }

    @Override
    public boolean supportsRecursiveKeyword() {
        return false;
    }

    @Override
    public SqlAndParams buildSelect(SelectStatement stmt) {
        return buildSelect(stmt, true);
    }

    @Override
    public SqlAndParams buildSelect(SelectStatement stmt, boolean renderCte) {
        if (stmt.getRootTableName() == null || stmt.getRootTableName().isEmpty()) {
            stmt.setRootTableName("DUAL");
        }
        if (oracle11 && (stmt.getLimit() != null || (stmt.getOffset() != null && stmt.getOffset() > 0))) {
            return buildOracle11Select(stmt);
        }
        return super.buildSelect(stmt, renderCte);
    }

    private SqlAndParams buildOracle11Select(SelectStatement stmt) {
        Integer limit = stmt.getLimit();
        Integer offset = stmt.getOffset();
        stmt.setLimit(null);
        stmt.setOffset(null);
        SqlAndParams inner;
        try {
            inner = super.buildSelect(stmt, false);
        } finally {
            stmt.setLimit(limit);
            stmt.setOffset(offset);
        }

        StringBuilder sql = new StringBuilder();
        List<Object> params = new ArrayList<>();

        if (!stmt.getCtes().isEmpty()) {
            List<CteClause> allCtes = collectAllCtes(stmt);
            sql.append("WITH ");
            for (int i = 0; i < allCtes.size(); i++) {
                if (i > 0) sql.append(", ");
                CteClause cte = allCtes.get(i);
                sql.append(quote(cte.getName()));
                if (cte.getColumnNames() != null && !cte.getColumnNames().isEmpty()) {
                    sql.append(" (");
                    for (int c = 0; c < cte.getColumnNames().size(); c++) {
                        if (c > 0) sql.append(", ");
                        sql.append(quote(cte.getColumnNames().get(c)));
                    }
                    sql.append(")");
                }
                sql.append(" AS (");
                renderCteSubQuery(cte.getQuery(), sql, params);
                sql.append(")");
            }
            sql.append(" ");
        }

        params.addAll(inner.getParams());

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

    protected void registerOracleFunctions() {
        registerFunction(BuiltinSqlFunction.NOW, (fn, args, sql, params, d) -> sql.append("SYSDATE"));
        registerFunction("CURRENT_TIMESTAMP", getFunctionRenderer(BuiltinSqlFunction.NOW));

        registerFunction(BuiltinSqlFunction.CURDATE, (fn, args, sql, params, d) -> sql.append("TRUNC(SYSDATE)"));
        registerFunction("CURRENT_DATE", getFunctionRenderer(BuiltinSqlFunction.CURDATE));

        registerFunction(BuiltinSqlFunction.CURTIME, (fn, args, sql, params, d) -> sql.append("TO_CHAR(SYSDATE, 'HH24:MI:SS')"));
        registerFunction("CURRENT_TIME", getFunctionRenderer(BuiltinSqlFunction.CURTIME));

        registerFunction(BuiltinSqlFunction.DATE_FORMAT, (fn, args, sql, params, d) -> {
            if (args.isEmpty()) return;
            sql.append("TO_CHAR(");
            d.renderArg(args.get(0), sql, params);
            sql.append(", ");
            String fmt = args.size() > 1 ? String.valueOf(args.get(1)) : "yyyy-MM-dd HH:mm:ss";
            String oraFmt = DateFormatPatternConverter.toOracle(fmt);
            sql.append("'").append(oraFmt).append("')");
        });
        registerFunction("DATE_TO_STRING", getFunctionRenderer(BuiltinSqlFunction.DATE_FORMAT));

        registerFunction(BuiltinSqlFunction.STR_TO_DATE, (fn, args, sql, params, d) -> {
            if (args.isEmpty()) return;
            sql.append("TO_TIMESTAMP(");
            d.renderArg(args.get(0), sql, params);
            sql.append(", ");
            String fmt = args.size() > 1 ? String.valueOf(args.get(1)) : "yyyy-MM-dd HH:mm:ss";
            String oraFmt = DateFormatPatternConverter.toOracle(fmt);
            sql.append("'").append(oraFmt).append("')");
        });
        registerFunction("TO_DATE", getFunctionRenderer(BuiltinSqlFunction.STR_TO_DATE));
        registerFunction("STRING_TO_DATE", getFunctionRenderer(BuiltinSqlFunction.STR_TO_DATE));

        registerFunction(BuiltinSqlFunction.DATE_ADD, (fn, args, sql, params, d) -> {
            if (args.size() < 2) return;
            String unit = args.size() > 2 ? String.valueOf(args.get(2)).toUpperCase() : "DAY";
            Object amount = args.get(1);
            if ("MONTH".equals(unit)) {
                sql.append("ADD_MONTHS(");
                d.renderArg(args.get(0), sql, params);
                sql.append(", ");
                if (amount instanceof Number) {
                    sql.append(amount);
                } else {
                    d.renderArg(amount, sql, params);
                }
                sql.append(")");
            } else if ("YEAR".equals(unit)) {
                sql.append("ADD_MONTHS(");
                d.renderArg(args.get(0), sql, params);
                sql.append(", (");
                if (amount instanceof Number) {
                    sql.append(amount);
                } else {
                    d.renderArg(amount, sql, params);
                }
                sql.append(" * 12))");
            } else if ("HOUR".equals(unit)) {
                sql.append("(");
                d.renderArg(args.get(0), sql, params);
                sql.append(" + (");
                if (amount instanceof Number) {
                    sql.append(amount);
                } else {
                    d.renderArg(amount, sql, params);
                }
                sql.append(" / 24))");
            } else if ("MINUTE".equals(unit)) {
                sql.append("(");
                d.renderArg(args.get(0), sql, params);
                sql.append(" + (");
                if (amount instanceof Number) {
                    sql.append(amount);
                } else {
                    d.renderArg(amount, sql, params);
                }
                sql.append(" / 1440))");
            } else if ("SECOND".equals(unit)) {
                sql.append("(");
                d.renderArg(args.get(0), sql, params);
                sql.append(" + (");
                if (amount instanceof Number) {
                    sql.append(amount);
                } else {
                    d.renderArg(amount, sql, params);
                }
                sql.append(" / 86400))");
            } else {
                sql.append("(");
                d.renderArg(args.get(0), sql, params);
                sql.append(" + ");
                if (amount instanceof Number) {
                    sql.append(amount);
                } else {
                    d.renderArg(amount, sql, params);
                }
                sql.append(")");
            }
        });

        registerFunction(BuiltinSqlFunction.DATE_SUB, (fn, args, sql, params, d) -> {
            if (args.size() < 2) return;
            String unit = args.size() > 2 ? String.valueOf(args.get(2)).toUpperCase() : "DAY";
            Object amount = args.get(1);
            if ("MONTH".equals(unit)) {
                sql.append("ADD_MONTHS(");
                d.renderArg(args.get(0), sql, params);
                sql.append(", -(");
                if (amount instanceof Number) {
                    sql.append(amount);
                } else {
                    d.renderArg(amount, sql, params);
                }
                sql.append("))");
            } else if ("YEAR".equals(unit)) {
                sql.append("ADD_MONTHS(");
                d.renderArg(args.get(0), sql, params);
                sql.append(", -(");
                if (amount instanceof Number) {
                    sql.append(amount);
                } else {
                    d.renderArg(amount, sql, params);
                }
                sql.append(" * 12))");
            } else if ("HOUR".equals(unit)) {
                sql.append("(");
                d.renderArg(args.get(0), sql, params);
                sql.append(" - (");
                if (amount instanceof Number) {
                    sql.append(amount);
                } else {
                    d.renderArg(amount, sql, params);
                }
                sql.append(" / 24))");
            } else if ("MINUTE".equals(unit)) {
                sql.append("(");
                d.renderArg(args.get(0), sql, params);
                sql.append(" - (");
                if (amount instanceof Number) {
                    sql.append(amount);
                } else {
                    d.renderArg(amount, sql, params);
                }
                sql.append(" / 1440))");
            } else if ("SECOND".equals(unit)) {
                sql.append("(");
                d.renderArg(args.get(0), sql, params);
                sql.append(" - (");
                if (amount instanceof Number) {
                    sql.append(amount);
                } else {
                    d.renderArg(amount, sql, params);
                }
                sql.append(" / 86400))");
            } else {
                sql.append("(");
                d.renderArg(args.get(0), sql, params);
                sql.append(" - ");
                if (amount instanceof Number) {
                    sql.append(amount);
                } else {
                    d.renderArg(amount, sql, params);
                }
                sql.append(")");
            }
        });

        registerFunction(BuiltinSqlFunction.DATEDIFF, (fn, args, sql, params, d) -> {
            if (args.size() < 2) return;
            sql.append("(TRUNC(");
            d.renderArg(args.get(0), sql, params);
            sql.append(") - TRUNC(");
            d.renderArg(args.get(1), sql, params);
            sql.append("))");
        });

        registerFunction(BuiltinSqlFunction.DAYOFWEEK, (fn, args, sql, params, d) -> {
            if (args.isEmpty()) return;
            sql.append("TO_CHAR(");
            d.renderArg(args.get(0), sql, params);
            sql.append(", 'D')");
        });

        registerFunction(BuiltinSqlFunction.DAYOFYEAR, (fn, args, sql, params, d) -> {
            if (args.isEmpty()) return;
            sql.append("TO_CHAR(");
            d.renderArg(args.get(0), sql, params);
            sql.append(", 'DDD')");
        });

        registerFunction(BuiltinSqlFunction.IFNULL, (fn, args, sql, params, d) -> {
            if (args.size() < 2) return;
            sql.append("NVL(");
            d.renderArg(args.get(0), sql, params);
            sql.append(", ");
            d.renderArg(args.get(1), sql, params);
            sql.append(")");
        });
        registerFunction(BuiltinSqlFunction.NVL, getFunctionRenderer(BuiltinSqlFunction.IFNULL));

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
            sql.append("LISTAGG(");
            d.renderArg(args.get(0), sql, params);
            if (args.size() > 1) {
                sql.append(", ");
                d.renderArg(args.get(1), sql, params);
            } else {
                sql.append(", ','");
            }
            sql.append(") WITHIN GROUP (ORDER BY ");
            d.renderArg(args.get(0), sql, params);
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

        registerFunction(BuiltinSqlFunction.CHAR_LENGTH, (fn, args, sql, params, d) -> {
            if (args.isEmpty()) return;
            sql.append("LENGTH(");
            d.renderArg(args.get(0), sql, params);
            sql.append(")");
        });

        FunctionRenderer datePartRenderer = (fn, args, sql, params, d) -> {
            if (args.isEmpty()) return;
            sql.append("EXTRACT(").append(fn.getName().toUpperCase()).append(" FROM ");
            d.renderArg(args.get(0), sql, params);
            sql.append(")");
        };
        registerFunction(BuiltinSqlFunction.YEAR, datePartRenderer);
        registerFunction(BuiltinSqlFunction.MONTH, datePartRenderer);
        registerFunction(BuiltinSqlFunction.DAY, datePartRenderer);

        FunctionRenderer timePartRenderer = (fn, args, sql, params, d) -> {
            if (args.isEmpty()) return;
            sql.append("EXTRACT(").append(fn.getName().toUpperCase()).append(" FROM CAST(");
            d.renderArg(args.get(0), sql, params);
            sql.append(" AS TIMESTAMP))");
        };
        registerFunction(BuiltinSqlFunction.HOUR, timePartRenderer);
        registerFunction(BuiltinSqlFunction.MINUTE, timePartRenderer);
        registerFunction(BuiltinSqlFunction.SECOND, timePartRenderer);

        registerFunction(BuiltinSqlFunction.LEFT, (fn, args, sql, params, d) -> {
            if (args.size() < 2) return;
            sql.append("SUBSTR(");
            d.renderArg(args.get(0), sql, params);
            sql.append(", 1, ");
            d.renderArg(args.get(1), sql, params);
            sql.append(")");
        });

        registerFunction(BuiltinSqlFunction.RIGHT, (fn, args, sql, params, d) -> {
            if (args.size() < 2) return;
            sql.append("SUBSTR(");
            d.renderArg(args.get(0), sql, params);
            sql.append(", -(");
            d.renderArg(args.get(1), sql, params);
            sql.append("))");
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

        registerFunction(BuiltinSqlFunction.CEILING, (fn, args, sql, params, d) -> {
            if (args.isEmpty()) return;
            sql.append("CEIL(");
            d.renderArg(args.get(0), sql, params);
            sql.append(")");
        });
        registerFunction(BuiltinSqlFunction.CEIL, getFunctionRenderer(BuiltinSqlFunction.CEILING));

        registerFunction(BuiltinSqlFunction.REPEAT, (fn, args, sql, params, d) -> {
            if (args.size() < 2) return;
            sql.append("RPAD(");
            d.renderArg(args.get(0), sql, params);
            sql.append(", (LENGTH(");
            d.renderArg(args.get(0), sql, params);
            sql.append(") * ");
            d.renderArg(args.get(1), sql, params);
            sql.append("), ");
            d.renderArg(args.get(0), sql, params);
            sql.append(")");
        });

        registerFunction(BuiltinSqlFunction.LAST_DAY, (fn, args, sql, params, d) -> {
            if (args.isEmpty()) return;
            sql.append("LAST_DAY(");
            d.renderArg(args.get(0), sql, params);
            sql.append(")");
        });

        registerFunction(BuiltinSqlFunction.LOCATE, (fn, args, sql, params, d) -> {
            if (args.size() < 2) return;
            sql.append("INSTR(");
            d.renderArg(args.get(1), sql, params);
            sql.append(", ");
            d.renderArg(args.get(0), sql, params);
            sql.append(")");
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
            if ("CHAR".equals(type) || "VARCHAR".equals(type) || "TEXT".equals(type) || "STRING".equals(type)) type = "VARCHAR2(4000)";
            else if ("SIGNED".equals(type) || "INT".equals(type) || "INTEGER".equals(type) || "BIGINT".equals(type)) type = "NUMBER";
            sql.append("CAST(");
            d.renderArg(args.get(0), sql, params);
            sql.append(" AS ").append(type).append(")");
        });
    }
}
