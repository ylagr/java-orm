package com.javaorm.core.ast;

import java.util.ArrayList;
import java.util.List;

public class TableJoin {
    public enum JoinType {
        LEFT_JOIN("LEFT JOIN"),
        INNER_JOIN("INNER JOIN"),
        RIGHT_JOIN("RIGHT JOIN");

        private final String sql;

        JoinType(String sql) {
            this.sql = sql;
        }

        public String getSql() {
            return sql;
        }
    }

    private JoinType joinType = JoinType.LEFT_JOIN;
    private String tableName;
    private String tableAlias;
    private final List<Condition> onConditions = new ArrayList<>();

    public TableJoin(JoinType joinType, String tableName, String tableAlias) {
        this.joinType = joinType;
        this.tableName = tableName;
        this.tableAlias = tableAlias;
    }

    public void addOnCondition(Condition condition) {
        onConditions.add(condition);
    }

    public JoinType getJoinType() {
        return joinType;
    }

    public String getTableName() {
        return tableName;
    }

    public String getTableAlias() {
        return tableAlias;
    }

    public List<Condition> getOnConditions() {
        return onConditions;
    }
}
