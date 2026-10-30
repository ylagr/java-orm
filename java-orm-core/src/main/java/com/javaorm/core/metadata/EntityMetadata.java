package com.javaorm.core.metadata;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class EntityMetadata {
    private Class<?> entityClass;
    private String tableName;
    private String schema;
    private ColumnMetadata idColumn;
    private final Map<String, ColumnMetadata> propertyColumns = new LinkedHashMap<>();
    private final Map<String, ColumnMetadata> columnToProperty = new LinkedHashMap<>();
    private final Map<String, RelationMetadata> relations = new LinkedHashMap<>();
    private ColumnMetadata logicDeleteColumn;
    private ColumnMetadata tenantColumn;

    public EntityMetadata(Class<?> entityClass) {
        this.entityClass = entityClass;
    }

    public void addColumn(ColumnMetadata column) {
        propertyColumns.put(column.getPropertyName(), column);
        if (column.getColumnName() != null) {
            columnToProperty.put(column.getColumnName().toLowerCase(), column);
        }
        if (column.isPrimaryKey()) {
            this.idColumn = column;
        }
        if (column.isLogicDelete()) {
            this.logicDeleteColumn = column;
        }
        if (column.isTenant()) {
            this.tenantColumn = column;
        }
    }

    public void addRelation(RelationMetadata relation) {
        relations.put(relation.getPropertyName(), relation);
    }

    public ColumnMetadata getColumnByProperty(String propertyName) {
        return propertyColumns.get(propertyName);
    }

    public ColumnMetadata getColumnByDbName(String dbColumnName) {
        if (dbColumnName == null) return null;
        return columnToProperty.get(dbColumnName.toLowerCase());
    }

    public RelationMetadata getRelation(String propertyName) {
        return relations.get(propertyName);
    }

    public Collection<ColumnMetadata> getColumns() {
        return propertyColumns.values();
    }

    public Collection<RelationMetadata> getRelations() {
        return relations.values();
    }

    private final Map<String, ValueObjectMetadata> valueObjects = new LinkedHashMap<>();

    public void addValueObject(ValueObjectMetadata vo) {
        valueObjects.put(vo.getFullPropertyName(), vo);
        valueObjects.put(vo.getPropertyName(), vo);
    }

    public ValueObjectMetadata getValueObject(String propertyName) {
        if (propertyName == null) return null;
        return valueObjects.get(propertyName);
    }

    public Collection<ValueObjectMetadata> getValueObjects() {
        return valueObjects.values();
    }

    public boolean hasValueObjects() {
        return !valueObjects.isEmpty();
    }

    // Getters and Setters
    public Class<?> getEntityClass() {
        return entityClass;
    }

    public void setEntityClass(Class<?> entityClass) {
        this.entityClass = entityClass;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public ColumnMetadata getIdColumn() {
        return idColumn;
    }

    public void setIdColumn(ColumnMetadata idColumn) {
        this.idColumn = idColumn;
    }

    public boolean hasLogicDelete() {
        return logicDeleteColumn != null;
    }

    public ColumnMetadata getLogicDeleteColumn() {
        return logicDeleteColumn;
    }

    public boolean hasTenant() {
        return tenantColumn != null;
    }

    public ColumnMetadata getTenantColumn() {
        return tenantColumn;
    }

    private com.javaorm.core.fastbean.FastBeanCreator<?> beanCreator;

    public com.javaorm.core.fastbean.FastBeanCreator<?> getBeanCreator() {
        return beanCreator;
    }

    public void setBeanCreator(com.javaorm.core.fastbean.FastBeanCreator<?> beanCreator) {
        this.beanCreator = beanCreator;
    }

    @SuppressWarnings("unchecked")
    public <T> T newInstance() {
        if (beanCreator != null) {
            return (T) beanCreator.create();
        }
        try {
            return (T) entityClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate entity " + entityClass.getName(), e);
        }
    }
}
