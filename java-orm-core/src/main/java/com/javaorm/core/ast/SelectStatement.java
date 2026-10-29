package com.javaorm.core.ast;

import java.util.ArrayList;
import java.util.List;

public class SelectStatement {
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
    private Integer limit;
    private Integer offset;
    private boolean distinct;

    public SelectStatement(Class<?> rootEntityClass, String rootTableName, String rootTableAlias) {
        this.rootEntityClass = rootEntityClass;
        this.rootTableName = rootTableName;
        this.rootTableAlias = rootTableAlias;
    }

    public void addSelectColumn(String col) { selectColumns.add(col); }
    public void addProjectExpression(SqlExpression<?> expr) { projectExpressions.add(expr); }
    public void addJoin(TableJoin join) { joins.add(join); }
    public void addSystemCondition(Condition condition) { systemConditions.add(condition); }
    public void addWhereCondition(Condition condition) { whereConditions.add(condition); }
    public void addGroupByExpression(SqlExpression<?> expr) { groupByExpressions.add(expr); }
    public void addHavingCondition(Condition condition) { havingConditions.add(condition); }
    public void addOrderBy(OrderBy orderBy) { orderBys.add(orderBy); }
    public void addUnion(UnionClause union) { unions.add(union); }

    public Class<?> getRootEntityClass() { return rootEntityClass; }
    public void setRootEntityClass(Class<?> rootEntityClass) { this.rootEntityClass = rootEntityClass; }
    public String getRootTableName() { return rootTableName; }
    public void setRootTableName(String rootTableName) { this.rootTableName = rootTableName; }
    public String getRootTableAlias() { return rootTableAlias; }
    public void setRootTableAlias(String rootTableAlias) { this.rootTableAlias = rootTableAlias; }

    public List<String> getSelectColumns() { return selectColumns; }
    public List<SqlExpression<?>> getProjectExpressions() { return projectExpressions; }
    public List<TableJoin> getJoins() { return joins; }
    public List<Condition> getSystemConditions() { return systemConditions; }
    public List<Condition> getWhereConditions() { return whereConditions; }
    public List<SqlExpression<?>> getGroupByExpressions() { return groupByExpressions; }
    public List<Condition> getHavingConditions() { return havingConditions; }
    public List<OrderBy> getOrderBys() { return orderBys; }
    public List<UnionClause> getUnions() { return unions; }

    public Integer getLimit() { return limit; }
    public void setLimit(Integer limit) { this.limit = limit; }
    public Integer getOffset() { return offset; }
    public void setOffset(Integer offset) { this.offset = offset; }
    public boolean isDistinct() { return distinct; }
    public void setDistinct(boolean distinct) { this.distinct = distinct; }
}
