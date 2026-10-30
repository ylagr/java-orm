package com.javaorm.core.ast;

import java.util.ArrayList;
import java.util.List;

public class DeleteStatement implements Statement {
    private Class<?> entityClass;
    private String tableName;
    private final List<Condition> systemConditions = new ArrayList<>();
    private final List<Condition> whereConditions = new ArrayList<>();

    public DeleteStatement(Class<?> entityClass, String tableName) {
        this.entityClass = entityClass;
        this.tableName = tableName;
    }

    public void addSystemCondition(Condition condition) {
        systemConditions.add(condition);
    }

    public void addWhereCondition(Condition condition) {
        whereConditions.add(condition);
    }

    public Class<?> getEntityClass() {
        return entityClass;
    }

    public String getTableName() {
        return tableName;
    }

    public List<Condition> getSystemConditions() {
        return systemConditions;
    }

    public List<Condition> getWhereConditions() {
        return whereConditions;
    }
}
