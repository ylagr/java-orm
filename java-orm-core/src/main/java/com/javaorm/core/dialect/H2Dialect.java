package com.javaorm.core.dialect;

import com.javaorm.core.ast.LockMode;
import com.javaorm.core.ast.UpsertStatement;

import java.util.ArrayList;
import java.util.List;

public class H2Dialect extends AbstractDialect {

    @Override
    protected void renderLockMode(StringBuilder sql, LockMode lockMode) {
        if (lockMode == null || lockMode == LockMode.NONE) {
            return;
        }
        // H2 标准支持 FOR UPDATE，对于 NOWAIT / SKIP LOCKED 安全降级为 FOR UPDATE
        sql.append(" FOR UPDATE");
    }

    @Override
    public SqlAndParams buildUpsert(UpsertStatement stmt) {
        StringBuilder sql = new StringBuilder();
        List<Object> params = new ArrayList<>();

        if (stmt.isDoNothing()) {
            sql.append("MERGE INTO ").append(quote(stmt.getTableName())).append(" (");
            for (int i = 0; i < stmt.getConflictColumns().size(); i++) {
                if (i > 0) sql.append(", ");
                sql.append(quote(stmt.getConflictColumns().get(i)));
            }
            sql.append(") KEY (");
            for (int i = 0; i < stmt.getConflictColumns().size(); i++) {
                if (i > 0) sql.append(", ");
                sql.append(quote(stmt.getConflictColumns().get(i)));
            }
            sql.append(") VALUES (");
            for (int i = 0; i < stmt.getConflictColumns().size(); i++) {
                if (i > 0) sql.append(", ");
                sql.append("?");
                String col = stmt.getConflictColumns().get(i);
                int idx = stmt.getColumns().indexOf(col);
                params.add(idx >= 0 ? stmt.getValues().get(idx) : null);
            }
            sql.append(")");
            return new SqlAndParams(sql.toString(), params);
        }

        sql.append("MERGE INTO ").append(quote(stmt.getTableName())).append(" (");
        for (int i = 0; i < stmt.getColumns().size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append(quote(stmt.getColumns().get(i)));
        }
        sql.append(") KEY (");
        for (int i = 0; i < stmt.getConflictColumns().size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append(quote(stmt.getConflictColumns().get(i)));
        }
        sql.append(") VALUES (");
        for (int i = 0; i < stmt.getColumns().size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append("?");
            params.add(stmt.getValues().get(i));
        }
        sql.append(")");

        return new SqlAndParams(sql.toString(), params);
    }
}
