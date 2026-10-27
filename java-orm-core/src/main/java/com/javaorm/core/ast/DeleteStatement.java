package com.javaorm.core.ast;

import java.util.ArrayList;
import java.util.List;

public class DeleteStatement {
    private Class<?> entityClass;
    private String tableName;
    private final List<Condition> whereConditions = new ArrayList<>();

    public DeleteStatement(Class<?> entityClass, String tableName) {
        this.entityClass = entityClass;
        this.tableName = tableName;
    }

    public void addWhereCondition(Condition condition) { whereConditions.add(condition); }

    public Class<?> getEntityClass() { return entityClass; }
    public String getTableName() { return tableName; }
    public List<Condition> getWhereConditions() { return whereConditions; }
}
