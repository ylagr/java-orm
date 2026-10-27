package com.javaorm.core.ast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class UpdateStatement {
    private Class<?> entityClass;
    private String tableName;
    private final Map<String, Object> setValues = new LinkedHashMap<>();
    private final List<Condition> whereConditions = new ArrayList<>();

    public UpdateStatement(Class<?> entityClass, String tableName) {
        this.entityClass = entityClass;
        this.tableName = tableName;
    }

    public void addSet(String column, Object value) { setValues.put(column, value); }
    public void addWhereCondition(Condition condition) { whereConditions.add(condition); }

    public Class<?> getEntityClass() { return entityClass; }
    public String getTableName() { return tableName; }
    public Map<String, Object> getSetValues() { return setValues; }
    public List<Condition> getWhereConditions() { return whereConditions; }
}
