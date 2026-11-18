package com.javaorm.core.ast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class UpdateStatement implements Statement {
    private Class<?> entityClass;
    private String tableName;
    private final Map<String, Object> setValues = new LinkedHashMap<>();
    private final List<Condition> systemConditions = new ArrayList<>();
    private final List<Condition> whereConditions = new ArrayList<>();

    public UpdateStatement(Class<?> entityClass, String tableName) {
        this.entityClass = entityClass;
        this.tableName = tableName;
    }

    public void addSet(String column, Object value) {
        setValues.put(column, value);
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

    public Map<String, Object> getSetValues() {
        return setValues;
    }

    public List<Condition> getSystemConditions() {
        return systemConditions;
    }

    public List<Condition> getWhereConditions() {
        return whereConditions;
    }

    private com.javaorm.core.sharding.TableRouter<?> shardingRouter;
    private com.javaorm.core.sharding.DataSourceRouter<?> dataSourceRouter;
    private String targetDataSource;
    private Boolean allowFullShardingScan;

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public com.javaorm.core.sharding.TableRouter<?> getShardingRouter() {
        return shardingRouter;
    }

    public void setShardingRouter(com.javaorm.core.sharding.TableRouter<?> shardingRouter) {
        this.shardingRouter = shardingRouter;
    }

    public String getTargetDataSource() {
        return targetDataSource;
    }

    public void setTargetDataSource(String targetDataSource) {
        this.targetDataSource = targetDataSource;
    }

    public com.javaorm.core.sharding.DataSourceRouter<?> getDataSourceRouter() {
        return dataSourceRouter;
    }

    public void setDataSourceRouter(com.javaorm.core.sharding.DataSourceRouter<?> dataSourceRouter) {
        this.dataSourceRouter = dataSourceRouter;
    }

    public Boolean getAllowFullShardingScan() {
        return allowFullShardingScan;
    }

    public void setAllowFullShardingScan(Boolean allowFullShardingScan) {
        this.allowFullShardingScan = allowFullShardingScan;
    }

    public UpdateStatement copyWithTableName(String newTableName) {
        UpdateStatement copy = new UpdateStatement(this.entityClass, newTableName);
        copy.setValues.putAll(this.setValues);
        copy.systemConditions.addAll(this.systemConditions);
        copy.whereConditions.addAll(this.whereConditions);
        copy.shardingRouter = this.shardingRouter;
        copy.dataSourceRouter = this.dataSourceRouter;
        copy.targetDataSource = this.targetDataSource;
        copy.allowFullShardingScan = this.allowFullShardingScan;
        return copy;
    }
}
