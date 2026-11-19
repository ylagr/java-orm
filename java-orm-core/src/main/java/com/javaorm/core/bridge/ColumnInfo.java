package com.javaorm.core.bridge;

import com.javaorm.core.annotation.AutoFillType;
import com.javaorm.core.annotation.GenerationType;
import com.javaorm.core.converter.ValueConverter;

/**
 * 字段列元数据信息载体。
 * 支持从多个注解处理器/桥接器中提取并合并属性，实现去重与优先级覆盖。
 */
public class ColumnInfo {
    private String columnName;
    private Boolean primaryKey;
    private Boolean generated;
    private GenerationType generationType;
    private Boolean ignored;
    private Boolean version;
    private Boolean logicDelete;
    private Object logicDeleteNormalValue;
    private Object logicDeleteDeletedValue;
    private Boolean tenant;
    private Boolean shardingKey;
    private Boolean autoFill;
    private AutoFillType autoFillType;
    private ValueConverter<?, ?> converter;
    private Boolean nullable;
    private Integer length;
    private Integer precision;
    private Integer scale;
    private String columnDefinition;
    private String comment;
    private String defaultValue;

    /**
     * 将另一个 ColumnInfo 中不为 null 的属性合并到当前实例中（已有值优先，保持高优先级生效）
     */
    public void merge(ColumnInfo other) {
        if (other == null) return;
        if (this.columnName == null) this.columnName = other.columnName;
        if (this.primaryKey == null) this.primaryKey = other.primaryKey;
        if (this.generated == null) this.generated = other.generated;
        if (this.generationType == null) this.generationType = other.generationType;
        if (this.ignored == null) this.ignored = other.ignored;
        if (this.version == null) this.version = other.version;
        if (this.logicDelete == null) this.logicDelete = other.logicDelete;
        if (this.logicDeleteNormalValue == null) this.logicDeleteNormalValue = other.logicDeleteNormalValue;
        if (this.logicDeleteDeletedValue == null) this.logicDeleteDeletedValue = other.logicDeleteDeletedValue;
        if (this.tenant == null) this.tenant = other.tenant;
        if (this.shardingKey == null) this.shardingKey = other.shardingKey;
        if (this.autoFill == null) this.autoFill = other.autoFill;
        if (this.autoFillType == null) this.autoFillType = other.autoFillType;
        if (this.converter == null) this.converter = other.converter;
        if (this.nullable == null) this.nullable = other.nullable;
        if (this.length == null) this.length = other.length;
        if (this.precision == null) this.precision = other.precision;
        if (this.scale == null) this.scale = other.scale;
        if (this.columnDefinition == null) this.columnDefinition = other.columnDefinition;
        if (this.comment == null) this.comment = other.comment;
        if (this.defaultValue == null) this.defaultValue = other.defaultValue;
    }

    public String getColumnName() {
        return columnName;
    }

    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }

    public Boolean getPrimaryKey() {
        return primaryKey;
    }

    public void setPrimaryKey(Boolean primaryKey) {
        this.primaryKey = primaryKey;
    }

    public Boolean getGenerated() {
        return generated;
    }

    public void setGenerated(Boolean generated) {
        this.generated = generated;
    }

    public GenerationType getGenerationType() {
        return generationType;
    }

    public void setGenerationType(GenerationType generationType) {
        this.generationType = generationType;
    }

    public Boolean getIgnored() {
        return ignored;
    }

    public void setIgnored(Boolean ignored) {
        this.ignored = ignored;
    }

    public Boolean getVersion() {
        return version;
    }

    public void setVersion(Boolean version) {
        this.version = version;
    }

    public Boolean getLogicDelete() {
        return logicDelete;
    }

    public void setLogicDelete(Boolean logicDelete) {
        this.logicDelete = logicDelete;
    }

    public Object getLogicDeleteNormalValue() {
        return logicDeleteNormalValue;
    }

    public void setLogicDeleteNormalValue(Object logicDeleteNormalValue) {
        this.logicDeleteNormalValue = logicDeleteNormalValue;
    }

    public Object getLogicDeleteDeletedValue() {
        return logicDeleteDeletedValue;
    }

    public void setLogicDeleteDeletedValue(Object logicDeleteDeletedValue) {
        this.logicDeleteDeletedValue = logicDeleteDeletedValue;
    }

    public Boolean getTenant() {
        return tenant;
    }

    public void setTenant(Boolean tenant) {
        this.tenant = tenant;
    }

    public Boolean getShardingKey() {
        return shardingKey;
    }

    public void setShardingKey(Boolean shardingKey) {
        this.shardingKey = shardingKey;
    }

    public Boolean getAutoFill() {
        return autoFill;
    }

    public void setAutoFill(Boolean autoFill) {
        this.autoFill = autoFill;
    }

    public AutoFillType getAutoFillType() {
        return autoFillType;
    }

    public void setAutoFillType(AutoFillType autoFillType) {
        this.autoFillType = autoFillType;
    }

    public ValueConverter<?, ?> getConverter() {
        return converter;
    }

    public void setConverter(ValueConverter<?, ?> converter) {
        this.converter = converter;
    }

    public Boolean getNullable() {
        return nullable;
    }

    public void setNullable(Boolean nullable) {
        this.nullable = nullable;
    }

    public Integer getLength() {
        return length;
    }

    public void setLength(Integer length) {
        this.length = length;
    }

    public Integer getPrecision() {
        return precision;
    }

    public void setPrecision(Integer precision) {
        this.precision = precision;
    }

    public Integer getScale() {
        return scale;
    }

    public void setScale(Integer scale) {
        this.scale = scale;
    }

    public String getColumnDefinition() {
        return columnDefinition;
    }

    public void setColumnDefinition(String columnDefinition) {
        this.columnDefinition = columnDefinition;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }
}
