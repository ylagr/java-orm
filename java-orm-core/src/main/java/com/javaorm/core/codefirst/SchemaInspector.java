package com.javaorm.core.codefirst;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;
import java.util.TreeSet;

/**
 * 数据库架构检测器（通过 JDBC DatabaseMetaData 安全感知已存在的表与列）。
 */
public class SchemaInspector {

    /**
     * 获取数据库中已存在的所有表名（大小写不敏感 Set）
     */
    public static Set<String> getExistingTables(Connection conn, String schema) throws SQLException {
        Set<String> tables = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        DatabaseMetaData meta = conn.getMetaData();
        String catalog = null;
        try {
            catalog = conn.getCatalog();
        } catch (Exception ignored) {
        }

        try (ResultSet rs = meta.getTables(catalog, schema, "%", new String[]{"TABLE", "BASE TABLE"})) {
            while (rs.next()) {
                String name = rs.getString("TABLE_NAME");
                if (name != null) {
                    tables.add(name);
                }
            }
        }

        if (tables.isEmpty()) {
            try (ResultSet rs = meta.getTables(catalog, schema, "%", null)) {
                while (rs.next()) {
                    String name = rs.getString("TABLE_NAME");
                    if (name != null) {
                        tables.add(name);
                    }
                }
            }
        }

        return tables;
    }

    /**
     * 获取指定表中已存在的所有字段列名（大小写不敏感 Set）
     */
    public static Set<String> getExistingColumns(Connection conn, String schema, String tableName) throws SQLException {
        return getExistingColumnDetails(conn, schema, tableName).keySet();
    }

    /**
     * 获取指定表中已存在的所有字段详细属性（大小写不敏感 Map，包含列名、类型、长度、标度、默认值、可空性等）
     */
    public static java.util.Map<String, PhysicalColumnDesc> getExistingColumnDetails(Connection conn, String schema, String tableName) throws SQLException {
        java.util.Map<String, PhysicalColumnDesc> columns = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        DatabaseMetaData meta = conn.getMetaData();
        String catalog = null;
        try {
            catalog = conn.getCatalog();
        } catch (Exception ignored) {
        }

        // 尝试原名、大写、小写分别查询，兼容不同数据库字典的大小写存储策略
        String[] candidates = new String[]{tableName, tableName.toUpperCase(), tableName.toLowerCase()};
        for (String candidate : candidates) {
            try (ResultSet rs = meta.getColumns(catalog, schema, candidate, "%")) {
                boolean found = false;
                while (rs.next()) {
                    String col = rs.getString("COLUMN_NAME");
                    if (col != null) {
                        int dataType = rs.getInt("DATA_TYPE");
                        String typeName = rs.getString("TYPE_NAME");
                        int columnSize = rs.getInt("COLUMN_SIZE");
                        int decimalDigits = rs.getInt("DECIMAL_DIGITS");
                        int nullable = rs.getInt("NULLABLE");
                        String defaultValue = rs.getString("COLUMN_DEF");
                        String remarks = rs.getString("REMARKS");

                        PhysicalColumnDesc desc = new PhysicalColumnDesc(
                                col, dataType, typeName, columnSize, decimalDigits, nullable, defaultValue, remarks
                        );
                        columns.put(col, desc);
                        found = true;
                    }
                }
                if (found) {
                    break;
                }
            }
        }

        return columns;
    }

    /**
     * 获取指定表中已存在的所有索引名称（大小写不敏感 Set）
     */
    public static Set<String> getExistingIndexes(Connection conn, String schema, String tableName) throws SQLException {
        Set<String> indexes = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        DatabaseMetaData meta = conn.getMetaData();
        String catalog = null;
        try {
            catalog = conn.getCatalog();
        } catch (Exception ignored) {
        }

        String[] candidates = new String[]{tableName, tableName.toUpperCase(), tableName.toLowerCase()};
        for (String candidate : candidates) {
            try (ResultSet rs = meta.getIndexInfo(catalog, schema, candidate, false, true)) {
                boolean found = false;
                while (rs.next()) {
                    String indexName = rs.getString("INDEX_NAME");
                    if (indexName != null) {
                        indexes.add(indexName);
                        found = true;
                    }
                }
                if (found) {
                    break;
                }
            } catch (Exception ignored) {
            }
        }
        return indexes;
    }
}
