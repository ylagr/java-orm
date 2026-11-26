package com.javaorm.core.mutation;

import com.javaorm.core.ast.UpsertStatement;
import com.javaorm.core.metadata.ColumnMetadata;
import com.javaorm.core.metadata.EntityMetadata;
import com.javaorm.core.metadata.IndexMetadata;
import com.javaorm.core.proxy.PropertyPath;
import com.javaorm.core.proxy.RecordingContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * UPSERT 冲突判定与更新列策略对象（由 {@link UpsertBuilder} 与 {@link com.javaorm.core.batch.BatchUpsertBuilder} 组合复用）。
 *
 * @param <T> 实体类型
 */
public final class UpsertSpec<T> {

    private final List<String> conflictColumns = new ArrayList<>();
    private final List<String> updateColumns = new ArrayList<>();
    private final Set<String> ignoreColumns = new HashSet<>();
    private boolean doNothing = false;

    public void addConflictColumns(EntityMetadata metadata, String... columns) {
        if (columns == null) {
            return;
        }
        for (String col : columns) {
            if (col != null && !col.trim().isEmpty()) {
                String resolved = resolveColumnName(metadata, col.trim());
                if (!conflictColumns.contains(resolved)) {
                    conflictColumns.add(resolved);
                }
            }
        }
    }

    public void addConflictColumns(Class<T> entityClass, EntityMetadata metadata, Consumer<T> proxyAction) {
        addResolvedColumns(conflictColumns, entityClass, metadata, proxyAction);
    }

    public void addUpdateColumns(EntityMetadata metadata, String... columns) {
        if (columns == null) {
            return;
        }
        for (String col : columns) {
            if (col != null && !col.trim().isEmpty()) {
                String resolved = resolveColumnName(metadata, col.trim());
                if (!updateColumns.contains(resolved)) {
                    updateColumns.add(resolved);
                }
            }
        }
    }

    public void addUpdateColumns(Class<T> entityClass, EntityMetadata metadata, Consumer<T> proxyAction) {
        addResolvedColumns(updateColumns, entityClass, metadata, proxyAction);
    }

    public void addIgnoreColumns(EntityMetadata metadata, String... columns) {
        if (columns == null) {
            return;
        }
        for (String col : columns) {
            if (col != null && !col.trim().isEmpty()) {
                ignoreColumns.add(resolveColumnName(metadata, col.trim()));
            }
        }
    }

    public void setDoNothing(boolean doNothing) {
        this.doNothing = doNothing;
    }

    public boolean isDoNothing() {
        return doNothing;
    }

    public boolean containsConflictColumn(String columnName) {
        return conflictColumns.contains(columnName);
    }

    public void applyTo(UpsertStatement stmt, EntityMetadata metadata, Class<?> entityClass, Collection<String> candidateColumns) {
        stmt.setDoNothing(doNothing);
        List<String> finalConflictCols = resolveConflictColumns(metadata, entityClass);
        stmt.addConflictColumns(finalConflictCols);

        if (!doNothing) {
            stmt.addUpdateColumns(resolveUpdateColumns(metadata, candidateColumns, finalConflictCols));
        }
    }

    private List<String> resolveConflictColumns(EntityMetadata metadata, Class<?> entityClass) {
        List<String> finalConflictCols = new ArrayList<>(conflictColumns);
        if (finalConflictCols.isEmpty()) {
            if (metadata.getIdColumn() != null) {
                finalConflictCols.add(metadata.getIdColumn().getColumnName());
            } else if (!metadata.getIndexes().isEmpty()) {
                for (IndexMetadata idx : metadata.getIndexes()) {
                    if (idx.isUnique() && !idx.getColumns().isEmpty()) {
                        finalConflictCols.addAll(idx.getColumns());
                        break;
                    }
                }
            }
        }
        if (finalConflictCols.isEmpty()) {
            String name = entityClass != null ? entityClass.getName() : "unknown";
            throw new IllegalStateException("UPSERT requires a primary key or explicit onConflict columns for entity: " + name);
        }
        return finalConflictCols;
    }

    private List<String> resolveUpdateColumns(EntityMetadata metadata,
                                              Collection<String> candidateColumns,
                                              List<String> finalConflictCols) {
        if (!updateColumns.isEmpty()) {
            return new ArrayList<>(updateColumns);
        }
        List<String> finalUpdateCols = new ArrayList<>();
        for (String colName : candidateColumns) {
            if (finalConflictCols.contains(colName)) {
                continue;
            }
            if (metadata.getIdColumn() != null && colName.equalsIgnoreCase(metadata.getIdColumn().getColumnName())) {
                continue;
            }
            if (ignoreColumns.contains(colName) || isDefaultCreationAuditColumn(colName)) {
                continue;
            }
            finalUpdateCols.add(colName);
        }
        return finalUpdateCols;
    }

    private void addResolvedColumns(List<String> target, Class<T> entityClass, EntityMetadata metadata, Consumer<T> proxyAction) {
        for (PropertyPath path : RecordingContext.recordPaths(entityClass, proxyAction)) {
            String resolved = resolveColumnName(metadata, path.getTerminalProperty());
            if (!target.contains(resolved)) {
                target.add(resolved);
            }
        }
    }

    private static String resolveColumnName(EntityMetadata metadata, String propOrCol) {
        if (metadata != null) {
            ColumnMetadata col = metadata.getColumnByProperty(propOrCol);
            if (col != null) return col.getColumnName();
            col = metadata.getColumnByDbName(propOrCol);
            if (col != null) return col.getColumnName();
        }
        return propOrCol;
    }

    private static boolean isDefaultCreationAuditColumn(String colName) {
        if (colName == null) return false;
        String lower = colName.toLowerCase();
        return lower.equals("create_time") || lower.equals("createtime") || lower.equals("created_at")
                || lower.equals("gmt_create") || lower.equals("create_by") || lower.equals("createby") || lower.equals("creator");
    }
}
