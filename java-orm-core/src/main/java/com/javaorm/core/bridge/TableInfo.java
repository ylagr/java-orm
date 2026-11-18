package com.javaorm.core.bridge;

/**
 * 实体表元数据信息载体。
 */
public class TableInfo {
    private String tableName;
    private String schema;

    public TableInfo() {
    }

    public TableInfo(String tableName, String schema) {
        this.tableName = tableName;
        this.schema = schema;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }
}
