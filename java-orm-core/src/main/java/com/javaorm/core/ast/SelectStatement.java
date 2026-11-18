package com.javaorm.core.ast;

import java.util.ArrayList;
import java.util.List;

public class SelectStatement implements Statement {
    private Class<?> rootEntityClass;
    private String rootTableName;
    private String rootTableAlias = "t0";
    private final List<String> selectColumns = new ArrayList<>();
    private final List<SqlExpression<?>> projectExpressions = new ArrayList<>();
    private final List<TableJoin> joins = new ArrayList<>();
    private final List<Condition> systemConditions = new ArrayList<>();
    private final List<Condition> whereConditions = new ArrayList<>();
    private final List<SqlExpression<?>> groupByExpressions = new ArrayList<>();
    private final List<Condition> havingConditions = new ArrayList<>();
    private final List<OrderBy> orderBys = new ArrayList<>();
    private final List<UnionClause> unions = new ArrayList<>();
    private final List<CteClause> ctes = new ArrayList<>();
    private Integer limit;
    private Integer offset;
    private boolean distinct;
    private com.javaorm.core.sharding.TableRouter<?> shardingRouter;
    private com.javaorm.core.sharding.DataSourceRouter<?> dataSourceRouter;
    private String targetDataSource;
    private Boolean allowFullShardingScan;

    public SelectStatement(Class<?> rootEntityClass, String rootTableName, String rootTableAlias) {
        this.rootEntityClass = rootEntityClass;
        this.rootTableName = rootTableName;
        this.rootTableAlias = rootTableAlias;
    }

    public void addSelectColumn(String col) {
        selectColumns.add(col);
    }

    public void addProjectExpression(SqlExpression<?> expr) {
        projectExpressions.add(expr);
    }

    public void addJoin(TableJoin join) {
        joins.add(join);
    }

    public void addSystemCondition(Condition condition) {
        systemConditions.add(condition);
    }

    public void addWhereCondition(Condition condition) {
        whereConditions.add(condition);
    }

    public void addGroupByExpression(SqlExpression<?> expr) {
        groupByExpressions.add(expr);
    }

    public void addHavingCondition(Condition condition) {
        havingConditions.add(condition);
    }

    public void addOrderBy(OrderBy orderBy) {
        orderBys.add(orderBy);
    }

    public void addUnion(UnionClause union) {
        unions.add(union);
    }

    public void addCte(CteClause cte) {
        if (cte != null) ctes.add(cte);
    }

    @Override
    public Class<?> getEntityClass() {
        return rootEntityClass;
    }

    public Class<?> getRootEntityClass() {
        return rootEntityClass;
    }

    public void setRootEntityClass(Class<?> rootEntityClass) {
        this.rootEntityClass = rootEntityClass;
    }

    @Override
    public String getTableName() {
        return rootTableName;
    }

    public String getRootTableName() {
        return rootTableName;
    }

    public void setRootTableName(String rootTableName) {
        this.rootTableName = rootTableName;
    }

    public String getRootTableAlias() {
        return rootTableAlias;
    }

    public void setRootTableAlias(String rootTableAlias) {
        this.rootTableAlias = rootTableAlias;
    }

    public List<String> getSelectColumns() {
        return selectColumns;
    }

    public List<SqlExpression<?>> getProjectExpressions() {
        return projectExpressions;
    }

    public List<TableJoin> getJoins() {
        return joins;
    }

    public List<Condition> getSystemConditions() {
        return systemConditions;
    }

    public List<Condition> getWhereConditions() {
        return whereConditions;
    }

    public List<SqlExpression<?>> getGroupByExpressions() {
        return groupByExpressions;
    }

    public List<Condition> getHavingConditions() {
        return havingConditions;
    }

    public List<OrderBy> getOrderBys() {
        return orderBys;
    }

    public List<UnionClause> getUnions() {
        return unions;
    }

    public List<CteClause> getCtes() {
        return ctes;
    }

    public boolean isRecursive() {
        for (CteClause c : ctes) {
            if (c.isRecursive()) return true;
        }
        return false;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    public Integer getOffset() {
        return offset;
    }

    public void setOffset(Integer offset) {
        this.offset = offset;
    }

    public boolean isDistinct() {
        return distinct;
    }

    public void setDistinct(boolean distinct) {
        this.distinct = distinct;
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

    public SelectStatement copyWithTableName(String newTableName) {
        SelectStatement copy = new SelectStatement(this.rootEntityClass, newTableName, this.rootTableAlias);
        copy.selectColumns.addAll(this.selectColumns);
        copy.projectExpressions.addAll(this.projectExpressions);
        copy.joins.addAll(this.joins);
        copy.systemConditions.addAll(this.systemConditions);
        copy.whereConditions.addAll(this.whereConditions);
        copy.groupByExpressions.addAll(this.groupByExpressions);
        copy.havingConditions.addAll(this.havingConditions);
        copy.orderBys.addAll(this.orderBys);
        copy.unions.addAll(this.unions);
        copy.ctes.addAll(this.ctes);
        copy.limit = this.limit;
        copy.offset = this.offset;
        copy.distinct = this.distinct;
        copy.shardingRouter = this.shardingRouter;
        copy.dataSourceRouter = this.dataSourceRouter;
        copy.targetDataSource = this.targetDataSource;
        copy.allowFullShardingScan = this.allowFullShardingScan;
        return copy;
    }
}
