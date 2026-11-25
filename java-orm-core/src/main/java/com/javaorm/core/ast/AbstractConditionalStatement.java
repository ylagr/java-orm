package com.javaorm.core.ast;

import com.javaorm.core.sharding.DataSourceRouter;
import com.javaorm.core.sharding.TableRouter;

import java.util.ArrayList;
import java.util.List;

/**
 * 带条件（WHERE / 系统条件）与分片路由信息的语句公共基类。
 */
public abstract class AbstractConditionalStatement implements Statement {

    protected final Class<?> entityClass;
    protected String tableName;
    protected final List<Condition> systemConditions = new ArrayList<>();
    protected final List<Condition> whereConditions = new ArrayList<>();

    protected TableRouter<?> shardingRouter;
    protected DataSourceRouter<?> dataSourceRouter;
    protected String targetDataSource;
    protected Boolean allowFullShardingScan;

    protected AbstractConditionalStatement(Class<?> entityClass, String tableName) {
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

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public List<Condition> getSystemConditions() {
        return systemConditions;
    }

    public List<Condition> getWhereConditions() {
        return whereConditions;
    }

    public TableRouter<?> getShardingRouter() {
        return shardingRouter;
    }

    public void setShardingRouter(TableRouter<?> shardingRouter) {
        this.shardingRouter = shardingRouter;
    }

    public String getTargetDataSource() {
        return targetDataSource;
    }

    public void setTargetDataSource(String targetDataSource) {
        this.targetDataSource = targetDataSource;
    }

    public DataSourceRouter<?> getDataSourceRouter() {
        return dataSourceRouter;
    }

    public void setDataSourceRouter(DataSourceRouter<?> dataSourceRouter) {
        this.dataSourceRouter = dataSourceRouter;
    }

    public Boolean getAllowFullShardingScan() {
        return allowFullShardingScan;
    }

    public void setAllowFullShardingScan(Boolean allowFullShardingScan) {
        this.allowFullShardingScan = allowFullShardingScan;
    }

    protected void copyConditionalBaseTo(AbstractConditionalStatement target) {
        target.systemConditions.addAll(this.systemConditions);
        target.whereConditions.addAll(this.whereConditions);
        target.shardingRouter = this.shardingRouter;
        target.dataSourceRouter = this.dataSourceRouter;
        target.targetDataSource = this.targetDataSource;
        target.allowFullShardingScan = this.allowFullShardingScan;
    }
}
