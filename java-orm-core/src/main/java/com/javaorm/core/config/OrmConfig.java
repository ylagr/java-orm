package com.javaorm.core.config;

/**
 * 全局 ORM 配置
 */
public class OrmConfig {

    private NameConversion nameConversion = new UnderlinedNameConversion();
    private boolean printSql = false;
    private String defaultLogicDeleteProperty = null; // 全局逻辑删除字段名（若实体无注解时生效）
    private Object defaultLogicDeleteNormalValue = 0;
    private Object defaultLogicDeleteDeletedValue = 1;

    public NameConversion getNameConversion() {
        return nameConversion;
    }

    public OrmConfig setNameConversion(NameConversion nameConversion) {
        if (nameConversion != null) {
            this.nameConversion = nameConversion;
        }
        return this;
    }

    public boolean isPrintSql() {
        return printSql;
    }

    public OrmConfig setPrintSql(boolean printSql) {
        this.printSql = printSql;
        return this;
    }

    public String getDefaultLogicDeleteProperty() {
        return defaultLogicDeleteProperty;
    }

    public OrmConfig setDefaultLogicDeleteProperty(String defaultLogicDeleteProperty) {
        this.defaultLogicDeleteProperty = defaultLogicDeleteProperty;
        return this;
    }

    public Object getDefaultLogicDeleteNormalValue() {
        return defaultLogicDeleteNormalValue;
    }

    public OrmConfig setDefaultLogicDeleteNormalValue(Object defaultLogicDeleteNormalValue) {
        this.defaultLogicDeleteNormalValue = defaultLogicDeleteNormalValue;
        return this;
    }

    public Object getDefaultLogicDeleteDeletedValue() {
        return defaultLogicDeleteDeletedValue;
    }

    public OrmConfig setDefaultLogicDeleteDeletedValue(Object defaultLogicDeleteDeletedValue) {
        this.defaultLogicDeleteDeletedValue = defaultLogicDeleteDeletedValue;
        return this;
    }
}
