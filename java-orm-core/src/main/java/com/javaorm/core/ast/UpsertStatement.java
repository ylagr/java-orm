package com.javaorm.core.ast;

import java.util.ArrayList;
import java.util.List;

/**
 * 跨方言 UPSERT（存在即更新，不存在即插入）抽象语句。
 */
public class UpsertStatement extends InsertStatement {

    private final List<String> conflictColumns = new ArrayList<>();
    private final List<String> updateColumns = new ArrayList<>();
    private boolean doNothing = false;

    public UpsertStatement(Class<?> entityClass, String tableName) {
        super(entityClass, tableName);
    }

    public List<String> getConflictColumns() {
        return conflictColumns;
    }

    public void addConflictColumn(String col) {
        if (col != null && !conflictColumns.contains(col)) {
            conflictColumns.add(col);
        }
    }

    public void addConflictColumns(List<String> cols) {
        if (cols != null) {
            for (String col : cols) {
                addConflictColumn(col);
            }
        }
    }

    public List<String> getUpdateColumns() {
        return updateColumns;
    }

    public void addUpdateColumn(String col) {
        if (col != null && !updateColumns.contains(col)) {
            updateColumns.add(col);
        }
    }

    public void addUpdateColumns(List<String> cols) {
        if (cols != null) {
            for (String col : cols) {
                addUpdateColumn(col);
            }
        }
    }

    public boolean isDoNothing() {
        return doNothing;
    }

    public void setDoNothing(boolean doNothing) {
        this.doNothing = doNothing;
    }
}
