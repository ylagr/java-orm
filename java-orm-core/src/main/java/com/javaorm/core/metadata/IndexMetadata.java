package com.javaorm.core.metadata;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 索引元数据描述符（用于 Code-First 自动建索引与多框架索引注解桥接）
 */
public class IndexMetadata {

    /**
     * 索引名称，如 idx_user_age_status
     */
    private String name;

    /**
     * 索引包含的列名列表（按复合索引列顺序排列）
     */
    private List<String> columns = new ArrayList<>();

    /**
     * 是否为唯一索引（UNIQUE INDEX）
     */
    private boolean unique = false;

    public IndexMetadata() {
    }

    public IndexMetadata(String name, List<String> columns, boolean unique) {
        this.name = name;
        if (columns != null) {
            this.columns.addAll(columns);
        }
        this.unique = unique;
    }

    public IndexMetadata(String name, boolean unique, String... columns) {
        this.name = name;
        this.unique = unique;
        if (columns != null) {
            this.columns.addAll(Arrays.asList(columns));
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getColumns() {
        return columns;
    }

    public void setColumns(List<String> columns) {
        this.columns = columns != null ? columns : new ArrayList<String>();
    }

    public void addColumn(String column) {
        if (column != null && !column.trim().isEmpty()) {
            this.columns.add(column.trim());
        }
    }

    public boolean isUnique() {
        return unique;
    }

    public void setUnique(boolean unique) {
        this.unique = unique;
    }

    @Override
    public String toString() {
        return "IndexMetadata{" +
                "name='" + name + '\'' +
                ", columns=" + columns +
                ", unique=" + unique +
                '}';
    }
}
