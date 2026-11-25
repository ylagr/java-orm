package com.javaorm.core.ast;

import java.util.ArrayList;
import java.util.List;

public class SelectStatement extends AbstractConditionalStatement {
    private String rootTableAlias = "t0";
    private final List<String> selectColumns = new ArrayList<>();
    private final List<SqlExpression<?>> projectExpressions = new ArrayList<>();
    private final List<TableJoin> joins = new ArrayList<>();
    private final List<SqlExpression<?>> groupByExpressions = new ArrayList<>();
    private final List<Condition> havingConditions = new ArrayList<>();
    private final List<OrderBy> orderBys = new ArrayList<>();
    private final List<UnionClause> unions = new ArrayList<>();
    private final List<CteClause> ctes = new ArrayList<>();
    private Integer limit;
    private Integer offset;
    private boolean distinct;
    private LockMode lockMode = LockMode.NONE;
    private boolean tracking = false;
    private com.javaorm.core.datasource.RouteRole routeRole = com.javaorm.core.datasource.RouteRole.AUTO;
    private com.javaorm.core.config.OrmConfig config;

    public SelectStatement(Class<?> rootEntityClass, String rootTableName, String rootTableAlias) {
        super(rootEntityClass, rootTableName);
        this.rootTableAlias = rootTableAlias;
    }

    public com.javaorm.core.config.OrmConfig getConfig() {
        return config;
    }

    public void setConfig(com.javaorm.core.config.OrmConfig config) {
        this.config = config;
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

    public Class<?> getRootEntityClass() {
        return entityClass;
    }

    public void setRootEntityClass(Class<?> rootEntityClass) {
        this.entityClass = rootEntityClass;
    }

    public String getRootTableName() {
        return tableName;
    }

    public void setRootTableName(String rootTableName) {
        this.tableName = rootTableName;
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

    public SelectStatement copyWithTableName(String newTableName) {
        SelectStatement copy = new SelectStatement(this.entityClass, newTableName, this.rootTableAlias);
        copy.selectColumns.addAll(this.selectColumns);
        copy.projectExpressions.addAll(this.projectExpressions);
        copy.joins.addAll(this.joins);
        copy.groupByExpressions.addAll(this.groupByExpressions);
        copy.havingConditions.addAll(this.havingConditions);
        copy.orderBys.addAll(this.orderBys);
        copy.unions.addAll(this.unions);
        copy.ctes.addAll(this.ctes);
        copy.limit = this.limit;
        copy.offset = this.offset;
        copy.distinct = this.distinct;
        copy.lockMode = this.lockMode;
        copy.tracking = this.tracking;
        copy.routeRole = this.routeRole;
        copy.config = this.config;
        copyConditionalBaseTo(copy);
        return copy;
    }

    public LockMode getLockMode() {
        return lockMode;
    }

    public void setLockMode(LockMode lockMode) {
        this.lockMode = lockMode != null ? lockMode : LockMode.NONE;
    }

    public boolean isTracking() {
        return tracking;
    }

    public void setTracking(boolean tracking) {
        this.tracking = tracking;
    }

    public com.javaorm.core.datasource.RouteRole getRouteRole() {
        return routeRole;
    }

    public void setRouteRole(com.javaorm.core.datasource.RouteRole routeRole) {
        this.routeRole = routeRole != null ? routeRole : com.javaorm.core.datasource.RouteRole.AUTO;
    }
}
