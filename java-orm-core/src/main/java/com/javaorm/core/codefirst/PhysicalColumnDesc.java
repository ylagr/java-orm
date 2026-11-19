package com.javaorm.core.codefirst;

/**
 * 物理数据库列元数据描述符（用于检测已有列的长度、类型、默认值等变更）。
 */
public class PhysicalColumnDesc {

    private String columnName;
    private int dataType;       // java.sql.Types
    private String typeName;    // 数据库类型名称，如 VARCHAR, INT, DECIMAL
    private int columnSize;     // 字段长度 / 精度
    private int decimalDigits;  // 标度 / 小数位数
    private int nullable;       // DatabaseMetaData.columnNullable (1) / columnNoNulls (0)
    private String defaultValue;// 默认值定义
    private String remarks;     // 字段注释

    public PhysicalColumnDesc() {
    }

    public PhysicalColumnDesc(String columnName, int dataType, String typeName,
                              int columnSize, int decimalDigits, int nullable,
                              String defaultValue, String remarks) {
        this.columnName = columnName;
        this.dataType = dataType;
        this.typeName = typeName;
        this.columnSize = columnSize;
        this.decimalDigits = decimalDigits;
        this.nullable = nullable;
        this.defaultValue = defaultValue;
        this.remarks = remarks;
    }

    public String getColumnName() {
        return columnName;
    }

    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }

    public int getDataType() {
        return dataType;
    }

    public void setDataType(int dataType) {
        this.dataType = dataType;
    }

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    public int getColumnSize() {
        return columnSize;
    }

    public void setColumnSize(int columnSize) {
        this.columnSize = columnSize;
    }

    public int getDecimalDigits() {
        return decimalDigits;
    }

    public void setDecimalDigits(int decimalDigits) {
        this.decimalDigits = decimalDigits;
    }

    public int getNullable() {
        return nullable;
    }

    public void setNullable(int nullable) {
        this.nullable = nullable;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }
}
