package com.javaorm.core.ast;

import java.util.ArrayList;
import java.util.List;

public class InsertStatement implements Statement {
    private Class<?> entityClass;
    private String tableName;
    private final List<String> columns = new ArrayList<>();
    private final List<Object> values = new ArrayList<>();

    public InsertStatement(Class<?> entityClass, String tableName) {
        this.entityClass = entityClass;
        this.tableName = tableName;
    }

    public void addColumnValue(String column, Object value) {
        columns.add(column);
        values.add(value);
    }

    public Class<?> getEntityClass() {
        return entityClass;
    }

    public String getTableName() {
        return tableName;
    }

    public List<String> getColumns() {
        return columns;
    }

    public List<Object> getValues() {
        return values;
    }

    private com.javaorm.core.sharding.TableRouter<?> shardingRouter;
    private com.javaorm.core.sharding.DataSourceRouter<?> dataSourceRouter;
    private String targetDataSource;

    public com.javaorm.core.sharding.TableRouter<?> getShardingRouter() {
        return shardingRouter;
    }

    public void setShardingRouter(com.javaorm.core.sharding.TableRouter<?> shardingRouter) {
        this.shardingRouter = shardingRouter;
    }

    public com.javaorm.core.sharding.DataSourceRouter<?> getDataSourceRouter() {
        return dataSourceRouter;
    }

    public void setDataSourceRouter(com.javaorm.core.sharding.DataSourceRouter<?> dataSourceRouter) {
        this.dataSourceRouter = dataSourceRouter;
    }

    public String getTargetDataSource() {
        return targetDataSource;
    }

    public void setTargetDataSource(String targetDataSource) {
        this.targetDataSource = targetDataSource;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public InsertStatement copyWithTableName(String newTableName) {
        InsertStatement copy = new InsertStatement(this.entityClass, newTableName);
        copy.columns.addAll(this.columns);
        copy.values.addAll(this.values);
        copy.targetDataSource = this.targetDataSource;
        copy.shardingRouter = this.shardingRouter;
        copy.dataSourceRouter = this.dataSourceRouter;
        return copy;
    }
}
