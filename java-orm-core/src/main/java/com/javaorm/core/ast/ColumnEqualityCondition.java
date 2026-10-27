package com.javaorm.core.ast;

/**
 * 跨表列等值连接条件（例如 t0.teacher_id = t1.id）
 */
public class ColumnEqualityCondition implements Condition {

    private LogicalOperator logicalOperator = LogicalOperator.AND;
    private final String leftTableAlias;
    private final String leftColumn;
    private final String rightTableAlias;
    private final String rightColumn;

    public ColumnEqualityCondition(String leftTableAlias, String leftColumn, String rightTableAlias, String rightColumn) {
        this.leftTableAlias = leftTableAlias;
        this.leftColumn = leftColumn;
        this.rightTableAlias = rightTableAlias;
        this.rightColumn = rightColumn;
    }

    @Override
    public LogicalOperator getLogicalOperator() { return logicalOperator; }
    @Override
    public void setLogicalOperator(LogicalOperator logicalOperator) { this.logicalOperator = logicalOperator; }

    public String getLeftTableAlias() { return leftTableAlias; }
    public String getLeftColumn() { return leftColumn; }
    public String getRightTableAlias() { return rightTableAlias; }
    public String getRightColumn() { return rightColumn; }
}
