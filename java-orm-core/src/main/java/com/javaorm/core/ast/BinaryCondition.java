package com.javaorm.core.ast;

import com.javaorm.core.operator.SqlOperator;
import com.javaorm.core.proxy.PropertyPath;

public class BinaryCondition implements Condition {
    private LogicalOperator logicalOperator = LogicalOperator.AND;
    private String tableAlias;
    private String columnName;
    private SqlOperator operator;
    private Object[] values;
    private PropertyPath path;

    public BinaryCondition(String tableAlias, String columnName, SqlOperator operator, Object... values) {
        this.tableAlias = tableAlias;
        this.columnName = columnName;
        this.operator = operator;
        this.values = values;
    }

    public BinaryCondition(PropertyPath path, SqlOperator operator, Object... values) {
        this.path = path;
        this.columnName = path != null ? path.getTerminalProperty() : null;
        this.operator = operator;
        this.values = values;
    }

    @Override
    public LogicalOperator getLogicalOperator() {
        return logicalOperator;
    }

    @Override
    public void setLogicalOperator(LogicalOperator logicalOperator) {
        this.logicalOperator = logicalOperator;
    }

    public String getTableAlias() {
        return tableAlias;
    }

    public void setTableAlias(String tableAlias) {
        this.tableAlias = tableAlias;
    }

    public String getColumnName() {
        return columnName;
    }

    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }

    public SqlOperator getOperator() {
        return operator;
    }

    public void setOperator(SqlOperator operator) {
        this.operator = operator;
    }

    public Object[] getValues() {
        return values;
    }

    public void setValues(Object[] values) {
        this.values = values;
    }

    public PropertyPath getPath() {
        return path;
    }

    public void setPath(PropertyPath path) {
        this.path = path;
    }
}
