package com.javaorm.core.sql;

import com.javaorm.core.query.Cte;

/**
 * SqlEnv 中已注册表/视图/CTE 的元信息载体。
 *
 * @param <T> 实体类型
 */
public class SqlTableInfo<T> {

    private final Object proxy;
    private final Class<T> entityClass;
    private final Cte<T> cte;
    private final String alias;
    private final String tableName;

    public SqlTableInfo(Object proxy, Class<T> entityClass, Cte<T> cte, String alias, String tableName) {
        this.proxy = proxy;
        this.entityClass = entityClass;
        this.cte = cte;
        this.alias = alias;
        this.tableName = tableName;
    }

    public Object getProxy() {
        return proxy;
    }

    public Class<T> getEntityClass() {
        return entityClass;
    }

    public Cte<T> getCte() {
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
