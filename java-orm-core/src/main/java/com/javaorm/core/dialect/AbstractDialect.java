package com.javaorm.core.dialect;

import com.javaorm.core.ast.*;
import com.javaorm.core.operator.SqlOperator;

import java.lang.reflect.Method;
import java.util.*;

public abstract class AbstractDialect implements Dialect {

    @Override
    public SqlAndParams buildSelect(SelectStatement stmt) {
        StringBuilder sql = new StringBuilder("SELECT ");
        List<Object> params = new ArrayList<>();

        if (stmt.isDistinct()) {
            sql.append("DISTINCT ");
        }

        // 1. SELECT Columns / Expressions
        if (!stmt.getProjectExpressions().isEmpty()) {
            for (int i = 0; i < stmt.getProjectExpressions().size(); i++) {
                if (i > 0) sql.append(", ");
                SqlExpression expr = stmt.getProjectExpressions().get(i);
                renderExpression(expr, sql, params);
                if (expr.getAlias() != null && !expr.getAlias().isEmpty()) {
                    sql.append(" AS ").append(quote(expr.getAlias()));
                }
            }
        } else if (!stmt.getSelectColumns().isEmpty()) {
            for (int i = 0; i < stmt.getSelectColumns().size(); i++) {
                if (i > 0) sql.append(", ");
                sql.append(stmt.getSelectColumns().get(i));
            }
        } else {
            sql.append(stmt.getRootTableAlias()).append(".*");
        }

        // 2. FROM
        sql.append(" FROM ").append(quote(stmt.getRootTableName())).append(" ").append(stmt.getRootTableAlias());

        // 3. JOINS
        for (TableJoin join : stmt.getJoins()) {
            sql.append(" ").append(join.getJoinType().getSql()).append(" ")
                    .append(quote(join.getTableName())).append(" ").append(join.getTableAlias());
            if (!join.getOnConditions().isEmpty()) {
                sql.append(" ON ");
                renderConditions(join.getOnConditions(), sql, params);
            }
        }

        // 4. WHERE
        if (!stmt.getWhereConditions().isEmpty()) {
            sql.append(" WHERE ");
            renderConditions(stmt.getWhereConditions(), sql, params);
        }

        // 5. GROUP BY
        if (!stmt.getGroupByExpressions().isEmpty()) {
            sql.append(" GROUP BY ");
            for (int i = 0; i < stmt.getGroupByExpressions().size(); i++) {
                if (i > 0) sql.append(", ");
                renderExpression(stmt.getGroupByExpressions().get(i), sql, params);
            }
        }

        // 6. HAVING
        if (!stmt.getHavingConditions().isEmpty()) {
            sql.append(" HAVING ");
            renderConditions(stmt.getHavingConditions(), sql, params);
        }

        // 7. ORDER BY
        if (!stmt.getOrderBys().isEmpty()) {
            sql.append(" ORDER BY ");
            for (int i = 0; i < stmt.getOrderBys().size(); i++) {
                if (i > 0) sql.append(", ");
                OrderBy ob = stmt.getOrderBys().get(i);
                if (ob.getExpression() != null) {
                    renderExpression(ob.getExpression(), sql, params);
                    sql.append(ob.isAscending() ? " ASC" : " DESC");
                } else {
                    if (ob.getTableAlias() != null && !ob.getTableAlias().isEmpty()) {
                        sql.append(renderTablePrefix(ob.getTableAlias()));
                    }
                    sql.append(quote(ob.getColumnName())).append(ob.isAscending() ? " ASC" : " DESC");
                }
            }
        }

        // 8. LIMIT / OFFSET 分页渲染
        renderPagination(sql, params, stmt.getLimit(), stmt.getOffset());

        // 9. UNION / UNION ALL
        if (!stmt.getUnions().isEmpty()) {
            for (UnionClause union : stmt.getUnions()) {
                sql.append(union.isUnionAll() ? " UNION ALL " : " UNION ");
                renderSubQuery(union.getQuery(), sql, params);
            }
        }

        return new SqlAndParams(sql.toString(), params);
    }

    protected void renderPagination(StringBuilder sql, List<Object> params, Integer limit, Integer offset) {
        if (limit != null) {
            sql.append(" LIMIT ?");
            params.add(limit);
            if (offset != null && offset > 0) {
                sql.append(" OFFSET ?");
                params.add(offset);
            }
        }
    }

    @Override
    public SqlAndParams buildUpdate(UpdateStatement stmt) {
        StringBuilder sql = new StringBuilder("UPDATE ");
        List<Object> params = new ArrayList<>();

        sql.append(quote(stmt.getTableName())).append(" SET ");
        int i = 0;
        for (Map.Entry<String, Object> entry : stmt.getSetValues().entrySet()) {
            if (i++ > 0) sql.append(", ");
            String colName = quote(entry.getKey());
            Object val = entry.getValue();
            if (val instanceof IncrementExpression) {
                IncrementExpression inc = (IncrementExpression) val;
                sql.append(colName).append(" = ").append(colName).append(" ").append(inc.getOperator()).append(" ?");
                params.add(inc.getStep());
            } else if (isQuery(val)) {
                sql.append(colName).append(" = (");
                renderSubQuery(val, sql, params);
                sql.append(")");
            } else if (val instanceof SqlExpression) {
                sql.append(colName).append(" = ");
                renderExpression((SqlExpression) val, sql, params);
            } else {
                sql.append(colName).append(" = ?");
                params.add(val);
            }
        }

        if (!stmt.getWhereConditions().isEmpty()) {
            sql.append(" WHERE ");
            renderConditions(stmt.getWhereConditions(), sql, params);
        }

        return new SqlAndParams(sql.toString(), params);
    }

    @Override
    public SqlAndParams buildDelete(DeleteStatement stmt) {
        StringBuilder sql = new StringBuilder("DELETE FROM ");
        List<Object> params = new ArrayList<>();

        sql.append(quote(stmt.getTableName()));

        if (!stmt.getWhereConditions().isEmpty()) {
            sql.append(" WHERE ");
            renderConditions(stmt.getWhereConditions(), sql, params);
        }

        return new SqlAndParams(sql.toString(), params);
    }

    @Override
    public SqlAndParams buildInsert(InsertStatement stmt) {
        StringBuilder sql = new StringBuilder("INSERT INTO ");
        List<Object> params = new ArrayList<>();

        sql.append(quote(stmt.getTableName())).append(" (");
        for (int i = 0; i < stmt.getColumns().size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append(quote(stmt.getColumns().get(i)));
        }
        sql.append(") VALUES (");
        for (int i = 0; i < stmt.getValues().size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append("?");
            params.add(stmt.getValues().get(i));
        }
        sql.append(")");

        return new SqlAndParams(sql.toString(), params);
    }

    protected void renderConditions(List<Condition> conditions, StringBuilder sql, List<Object> params) {
        for (int i = 0; i < conditions.size(); i++) {
            Condition cond = conditions.get(i);
            if (i > 0) {
                sql.append(" ").append(cond.getLogicalOperator().name()).append(" ");
            }
            renderSingleCondition(cond, sql, params);
        }
    }

    protected String renderTablePrefix(String alias) {
        if (alias == null || alias.isEmpty()) {
            return "";
        }
        if (alias.matches("^t\\d+$") || "tt".equalsIgnoreCase(alias)) {
            return alias + ".";
        }
        return quote(alias) + ".";
    }

    protected void renderSingleCondition(Condition cond, StringBuilder sql, List<Object> params) {
        if (cond instanceof BinaryCondition) {
            BinaryCondition bc = (BinaryCondition) cond;
            if (bc.getTableAlias() != null && !bc.getTableAlias().isEmpty()) {
                sql.append(renderTablePrefix(bc.getTableAlias()));
            }
            sql.append(quote(bc.getColumnName())).append(" ");
            SqlOperator op = bc.getOperator();
            sql.append(op.getSql());
            renderOperatorValues(op, bc.getValues(), sql, params);
        } else if (cond instanceof ExpressionCondition) {
            ExpressionCondition ec = (ExpressionCondition) cond;
            renderExpression(ec.getExpression(), sql, params);
            if (ec.getOperator() != null) {
                sql.append(" ").append(ec.getOperator().getSql());
                renderOperatorValues(ec.getOperator(), ec.getValues(), sql, params);
            }
        } else if (cond instanceof ColumnEqualityCondition) {
            ColumnEqualityCondition cec = (ColumnEqualityCondition) cond;
            if (cec.getLeftTableAlias() != null && !cec.getLeftTableAlias().isEmpty()) {
                sql.append(renderTablePrefix(cec.getLeftTableAlias()));
            }
            sql.append(quote(cec.getLeftColumn())).append(" = ");
            if (cec.getRightTableAlias() != null && !cec.getRightTableAlias().isEmpty()) {
                sql.append(renderTablePrefix(cec.getRightTableAlias()));
            }
            sql.append(quote(cec.getRightColumn()));
        } else if (cond instanceof ExistsCondition) {
            ExistsCondition ec = (ExistsCondition) cond;
            sql.append(ec.isNotExists() ? "NOT EXISTS (" : "EXISTS (");
            renderSubQuery(ec.getSubQuery(), sql, params);
            sql.append(")");
        } else if (cond instanceof GroupCondition) {
            GroupCondition gc = (GroupCondition) cond;
            sql.append("(");
            renderConditions(gc.getChildren(), sql, params);
            sql.append(")");
        }
    }

    protected void renderOperatorValues(SqlOperator op, Object[] values, StringBuilder sql, List<Object> params) {
        if (op == SqlOperator.IS_NULL || op == SqlOperator.IS_NOT_NULL) {
            return;
        }
        if (op == SqlOperator.BETWEEN) {
            sql.append(" ? AND ?");
            if (values != null && values.length >= 2) {
                params.add(values[0]);
                params.add(values[1]);
            }
            return;
        }
        if (op == SqlOperator.IN || op == SqlOperator.NOT_IN) {
            sql.append(" (");
            Object val = (values != null && values.length > 0) ? values[0] : null;
            if (val instanceof Collection) {
                Collection<?> coll = (Collection<?>) val;
                int idx = 0;
                for (Object item : coll) {
                    if (idx++ > 0) sql.append(", ");
                    sql.append("?");
                    params.add(item);
                }
                if (idx == 0) {
                    sql.append("NULL");
                }
            } else if (isQuery(val)) {
                renderSubQuery(val, sql, params);
            } else if (val instanceof SubQueryExpression) {
                renderSubQuery(((SubQueryExpression) val).getQuery(), sql, params);
            } else if (values != null) {
                for (int k = 0; k < values.length; k++) {
                    if (k > 0) sql.append(", ");
                    sql.append("?");
                    params.add(values[k]);
                }
            }
            sql.append(")");
            return;
        }

        // 普通比较 = / > / < 等，右边可能是子查询或列表达式
        Object val = (values != null && values.length > 0) ? values[0] : null;
        if (isQuery(val)) {
            sql.append(" (");
            renderSubQuery(val, sql, params);
            sql.append(")");
        } else if (val instanceof SqlExpression) {
            sql.append(" ");
            renderExpression((SqlExpression) val, sql, params);
        } else {
            sql.append(" ?");
            if (val != null) {
                params.add(val);
            }
        }
    }

    public void renderExpression(SqlExpression expr, StringBuilder sql, List<Object> params) {
        if (expr instanceof ColumnExpression) {
            ColumnExpression ce = (ColumnExpression) expr;
            if (ce.getTableAlias() != null && !ce.getTableAlias().isEmpty()) {
                sql.append(renderTablePrefix(ce.getTableAlias()));
            }
            sql.append(quote(ce.getColumnName()));
        } else if (expr instanceof WindowOrderByBuilder) {
            renderWindowFunction(((WindowOrderByBuilder) expr).getWindowExpr(), sql, params);
        } else if (expr instanceof WindowFunctionExpression) {
            renderWindowFunction((WindowFunctionExpression) expr, sql, params);
        } else if (expr instanceof FunctionExpression) {
            renderFunction((FunctionExpression) expr, sql, params);
        } else if (expr instanceof CaseWhenBuilder) {
            renderCaseWhen((CaseWhenBuilder) expr, sql, params);
        } else if (expr instanceof SubQueryExpression) {
            sql.append("(");
            renderSubQuery(((SubQueryExpression) expr).getQuery(), sql, params);
            sql.append(")");
        } else if (expr instanceof RawSqlExpression) {
            RawSqlExpression raw = (RawSqlExpression) expr;
            sql.append(raw.getRawSql());
            if (raw.getParams() != null) {
                Collections.addAll(params, raw.getParams());
            }
        }
    }

    public void renderWindowFunction(WindowFunctionExpression wfe, StringBuilder sql, List<Object> params) {
        renderExpression(wfe.getFunctionExpr(), sql, params);
        sql.append(" OVER (");
        boolean hasPartition = false;
        if (!wfe.getPartitionBys().isEmpty()) {
            sql.append("PARTITION BY ");
            for (int i = 0; i < wfe.getPartitionBys().size(); i++) {
                if (i > 0) sql.append(", ");
                renderExpression(wfe.getPartitionBys().get(i), sql, params);
            }
            hasPartition = true;
        }
        if (!wfe.getOrderBys().isEmpty()) {
            if (hasPartition) {
                sql.append(" ");
            }
            sql.append("ORDER BY ");
            for (int i = 0; i < wfe.getOrderBys().size(); i++) {
                if (i > 0) sql.append(", ");
                OrderBy ob = wfe.getOrderBys().get(i);
                if (ob.getExpression() != null) {
                    renderExpression(ob.getExpression(), sql, params);
                } else {
                    if (ob.getTableAlias() != null && !ob.getTableAlias().isEmpty()) {
                        sql.append(ob.getTableAlias()).append(".");
                    }
                    sql.append(quote(ob.getColumnName()));
                }
                sql.append(ob.isAscending() ? " ASC" : " DESC");
            }
        }
        if (wfe.getFrameClause() != null && !wfe.getFrameClause().isEmpty()) {
            if (hasPartition || !wfe.getOrderBys().isEmpty()) {
                sql.append(" ");
            }
            String frame = wfe.getFrameClause().trim();
            if (!frame.toUpperCase().startsWith("ROWS") && !frame.toUpperCase().startsWith("RANGE")) {
                sql.append("ROWS BETWEEN ").append(frame);
            } else {
                sql.append(frame);
            }
        }
        sql.append(")");
    }

    public void renderFunction(FunctionExpression fn, StringBuilder sql, List<Object> params) {
        String name = fn.getFunctionName().toUpperCase();
        List<Object> args = fn.getArguments();

        if (renderDialectSpecificFunction(name, args, sql, params)) {
            return;
        }

        sql.append(name).append("(");
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) sql.append(", ");
            Object arg = args.get(i);
            renderArg(arg, sql, params);
        }
        sql.append(")");
    }

    protected void renderArg(Object arg, StringBuilder sql, List<Object> params) {
        if (arg instanceof SqlExpression) {
            renderExpression((SqlExpression) arg, sql, params);
        } else {
            sql.append("?");
            params.add(arg);
        }
    }

    protected boolean renderDialectSpecificFunction(String name, List<Object> args, StringBuilder sql, List<Object> params) {
        return false;
    }

    public void renderCaseWhen(CaseWhenBuilder builder, StringBuilder sql, List<Object> params) {
        sql.append("CASE");
        for (CaseWhenBuilder.WhenBranch branch : builder.getBranches()) {
            sql.append(" WHEN ");
            renderSingleCondition(branch.getCondition(), sql, params);
            sql.append(" THEN ");
            renderArg(branch.getThenVal(), sql, params);
        }
        if (builder.getElseVal() != null) {
            sql.append(" ELSE ");
            renderArg(builder.getElseVal(), sql, params);
        }
        sql.append(" END");
    }

    public void renderSubQuery(Object queryObj, StringBuilder sql, List<Object> params) {
        if (queryObj == null) return;
        try {
            Method getStatement = queryObj.getClass().getMethod("getStatement");
            SelectStatement subStmt = (SelectStatement) getStatement.invoke(queryObj);
            SqlAndParams subSqlAndParams = buildSelect(subStmt);
            sql.append(subSqlAndParams.getSql());
            params.addAll(subSqlAndParams.getParams());
        } catch (Exception e) {
            throw new RuntimeException("Failed to render subquery", e);
        }
    }

    private boolean isQuery(Object obj) {
        if (obj == null) return false;
        try {
            obj.getClass().getMethod("getStatement");
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}
