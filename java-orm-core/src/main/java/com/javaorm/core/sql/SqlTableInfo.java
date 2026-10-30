package com.javaorm.core.sql;

import com.javaorm.core.query.Cte;

/**
 * SqlEnv 中已注册表/视图/CTE 的元信息载体。
 */
public class SqlTableInfo {

    private final Object proxy;
    private final Class<?> entityClass;
    private final Cte<?> cte;
    private final String alias;
    private final String tableName;

    public SqlTableInfo(Object proxy, Class<?> entityClass, Cte<?> cte, String alias, String tableName) {
        this.proxy = proxy;
        this.entityClass = entityClass;
        this.cte = cte;
        this.alias = alias;
        this.tableName = tableName;
    }

    public Object getProxy() {
        return proxy;
    }

    public Class<?> getEntityClass() {
        return entityClass;
    }

    public Cte<?> getCte() {
        return cte;
    }

    public String getAlias() {
        return alias;
    }

    public String getTableName() {
        return tableName;
    }

    public boolean isCte() {
        return cte != null;
    }
}
