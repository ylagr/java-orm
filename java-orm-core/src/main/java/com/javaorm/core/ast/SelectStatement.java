package com.javaorm.core.ast;

import java.util.ArrayList;
import java.util.List;

public class SelectStatement {
    private Class<?> rootEntityClass;
    private String rootTableName;
    private String rootTableAlias = "t0";
    private final List<String> selectColumns = new ArrayList<>();
    private final List<TableJoin> joins = new ArrayList<>();
    private final List<Condition> whereConditions = new ArrayList<>();
    private final List<OrderBy> orderBys = new ArrayList<>();
    private Integer limit;
    private Integer offset;
    private boolean distinct;

    public SelectStatement(Class<?> rootEntityClass, String rootTableName, String rootTableAlias) {
        this.rootEntityClass = rootEntityClass;
        this.rootTableName = rootTableName;
        this.rootTableAlias = rootTableAlias;
    }

    public void addSelectColumn(String col) { selectColumns.add(col); }
    public void addJoin(TableJoin join) { joins.add(join); }
    public void addWhereCondition(Condition condition) { whereConditions.add(condition); }
    public void addOrderBy(OrderBy orderBy) { orderBys.add(orderBy); }

    public Class<?> getRootEntityClass() { return rootEntityClass; }
    public String getRootTableName() { return rootTableName; }
    public String getRootTableAlias() { return rootTableAlias; }
    public List<String> getSelectColumns() { return selectColumns; }
    public List<TableJoin> getJoins() { return joins; }
    public List<Condition> getWhereConditions() { return whereConditions; }
    public List<OrderBy> getOrderBys() { return orderBys; }
    public Integer getLimit() { return limit; }
    public void setLimit(Integer limit) { this.limit = limit; }
    public Integer getOffset() { return offset; }
    public void setOffset(Integer offset) { this.offset = offset; }
    public boolean isDistinct() { return distinct; }
    public void setDistinct(boolean distinct) { this.distinct = distinct; }
}
