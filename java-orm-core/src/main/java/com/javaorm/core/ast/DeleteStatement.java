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

    private com.javaorm.core.sharding.TableRouter<?> shardingRouter;
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

    public Boolean getAllowFullShardingScan() {
        return allowFullShardingScan;
    }

    public void setAllowFullShardingScan(Boolean allowFullShardingScan) {
        this.allowFullShardingScan = allowFullShardingScan;
    }

    public DeleteStatement copyWithTableName(String newTableName) {
        DeleteStatement copy = new DeleteStatement(this.entityClass, newTableName);
        copy.systemConditions.addAll(this.systemConditions);
        copy.whereConditions.addAll(this.whereConditions);
        copy.shardingRouter = this.shardingRouter;
        copy.allowFullShardingScan = this.allowFullShardingScan;
        return copy;
    }
}
