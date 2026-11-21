package com.javaorm.core.bridge;

import com.javaorm.core.annotation.RelationType;

/**
 * 关联关系/导航属性元数据载体（桥接模型）。
 * 承载来自 JavaORM 注解、JPA 注解、EasyQuery 注解等的统一关系拓扑描述。
 */
public class RelationInfo {
    private RelationType relationType = RelationType.MANY_TO_ONE;
    private Class<?> targetEntityClass;
    private String selfProperty;
    private String targetProperty;

    // 多对多 (ManyToMany) 中间表映射支持
    private Class<?> mappingClass;
    private String selfMappingProperty;
    private String targetMappingProperty;

    // 额外关联过滤条件
    private String extraFilter;

    public RelationInfo() {
    }

    public RelationInfo(RelationType relationType, Class<?> targetEntityClass, String selfProperty, String targetProperty) {
        this.relationType = relationType;
        this.targetEntityClass = targetEntityClass;
        this.selfProperty = selfProperty;
        this.targetProperty = targetProperty;
    }

    public RelationType getRelationType() {
        return relationType;
    }

    public void setRelationType(RelationType relationType) {
        this.relationType = relationType;
    }

    public Class<?> getTargetEntityClass() {
        return targetEntityClass;
    }

    public void setTargetEntityClass(Class<?> targetEntityClass) {
        this.targetEntityClass = targetEntityClass;
    }

    public String getSelfProperty() {
        return selfProperty;
    }

    public void setSelfProperty(String selfProperty) {
        this.selfProperty = selfProperty;
    }

    public String getTargetProperty() {
        return targetProperty;
    }

    public void setTargetProperty(String targetProperty) {
        this.targetProperty = targetProperty;
    }

    public Class<?> getMappingClass() {
        return mappingClass;
    }

    public void setMappingClass(Class<?> mappingClass) {
        this.mappingClass = mappingClass;
    }

    public String getSelfMappingProperty() {
        return selfMappingProperty;
    }

    public void setSelfMappingProperty(String selfMappingProperty) {
        this.selfMappingProperty = selfMappingProperty;
    }

    public String getTargetMappingProperty() {
        return targetMappingProperty;
    }

    public void setTargetMappingProperty(String targetMappingProperty) {
        this.targetMappingProperty = targetMappingProperty;
    }

    public String getExtraFilter() {
        return extraFilter;
    }

    public void setExtraFilter(String extraFilter) {
        this.extraFilter = extraFilter;
    }
}
