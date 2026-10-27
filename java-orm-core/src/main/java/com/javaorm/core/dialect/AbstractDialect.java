package com.javaorm.core.dialect;

import com.javaorm.core.ast.*;
import com.javaorm.core.operator.SqlOperator;

import java.util.*;

public abstract class AbstractDialect implements Dialect {

    @Override
    public SqlAndParams buildSelect(SelectStatement stmt) {
        StringBuilder sql = new StringBuilder("SELECT ");
        List<Object> params = new ArrayList<>();

        if (stmt.isDistinct()) {
            sql.append("DISTINCT ");
        }

        // 1. SELECT Columns
        if (stmt.getSelectColumns().isEmpty()) {
            sql.append(stmt.getRootTableAlias()).append(".*");
        } else {
            for (int i = 0; i < stmt.getSelectColumns().size(); i++) {
                if (i > 0) sql.append(", ");
                sql.append(stmt.getSelectColumns().get(i));
            }
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

        // 5. ORDER BY
        if (!stmt.getOrderBys().isEmpty()) {
            sql.append(" ORDER BY ");
            for (int i = 0; i < stmt.getOrderBys().size(); i++) {
                if (i > 0) sql.append(", ");
                OrderBy ob = stmt.getOrderBys().get(i);
                if (ob.getTableAlias() != null && !ob.getTableAlias().isEmpty()) {
                    sql.append(ob.getTableAlias()).append(".");
                }
                sql.append(quote(ob.getColumnName())).append(ob.isAscending() ? " ASC" : " DESC");
            }
        }

        // 6. LIMIT / OFFSET 分页渲染
        renderPagination(sql, params, stmt.getLimit(), stmt.getOffset());

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
            sql.append(quote(entry.getKey())).append(" = ?");
            params.add(entry.getValue());
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

    protected void renderSingleCondition(Condition cond, StringBuilder sql, List<Object> params) {
        if (cond instanceof BinaryCondition) {
            BinaryCondition bc = (BinaryCondition) cond;
            if (bc.getTableAlias() != null && !bc.getTableAlias().isEmpty()) {
                sql.append(bc.getTableAlias()).append(".");
            }
            sql.append(quote(bc.getColumnName())).append(" ");

            SqlOperator op = bc.getOperator();
            sql.append(op.getSql());

            if (op == SqlOperator.IS_NULL || op == SqlOperator.IS_NOT_NULL) {
                // 不需要参数
            } else if (op == SqlOperator.BETWEEN) {
                sql.append(" ? AND ?");
                if (bc.getValues() != null && bc.getValues().length >= 2) {
                    params.add(bc.getValues()[0]);
                    params.add(bc.getValues()[1]);
                }
            } else if (op == SqlOperator.IN || op == SqlOperator.NOT_IN) {
                sql.append(" (");
                Object val = (bc.getValues() != null && bc.getValues().length > 0) ? bc.getValues()[0] : null;
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
                } else if (bc.getValues() != null) {
                    for (int k = 0; k < bc.getValues().length; k++) {
                        if (k > 0) sql.append(", ");
                        sql.append("?");
                        params.add(bc.getValues()[k]);
                    }
                }
                sql.append(")");
            } else {
                sql.append(" ?");
                if (bc.getValues() != null && bc.getValues().length > 0) {
                    params.add(bc.getValues()[0]);
                }
            }
        } else if (cond instanceof ColumnEqualityCondition) {
            ColumnEqualityCondition cec = (ColumnEqualityCondition) cond;
            if (cec.getLeftTableAlias() != null && !cec.getLeftTableAlias().isEmpty()) {
                sql.append(cec.getLeftTableAlias()).append(".");
            }
            sql.append(quote(cec.getLeftColumn())).append(" = ");
            if (cec.getRightTableAlias() != null && !cec.getRightTableAlias().isEmpty()) {
                sql.append(cec.getRightTableAlias()).append(".");
            }
            sql.append(quote(cec.getRightColumn()));
        } else if (cond instanceof GroupCondition) {
            GroupCondition gc = (GroupCondition) cond;
            sql.append("(");
            renderConditions(gc.getChildren(), sql, params);
            sql.append(")");
        }
    }
}
