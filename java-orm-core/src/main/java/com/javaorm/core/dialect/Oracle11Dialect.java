package com.javaorm.core.dialect;

/**
 * Oracle 11g 方言实现，默认启用经典 ROWNUM 嵌套分页。
 */
public class Oracle11Dialect extends OracleDialect {

    public Oracle11Dialect() {
        super(true);
    }
}
