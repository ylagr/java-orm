package com.javaorm.core.sql;

import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.dialect.Dialect;
import com.javaorm.core.executor.ExecutionContext;
import com.javaorm.core.sharding.DataSourceRouter;
import com.javaorm.core.sharding.TableRouter;

import javax.sql.DataSource;

/**
 * 语句构建器抽象基类（统一 SqlEnv 持有、分片/数据源路由配置与 SQL 日志开关）。
 *
 * @param <SELF> 具体构建器自引用类型
 */
public abstract class AbstractStatementBuilder<SELF extends AbstractStatementBuilder<SELF>> {

    protected final SqlEnv env;
    protected Boolean printSqlOverride = null;

    protected AbstractStatementBuilder(SqlEnv env) {
        this.env = env != null ? env : new SqlEnv(null, null, null);
    }

    @SuppressWarnings("unchecked")
    protected final SELF self() {
        return (SELF) this;
    }

    public SqlEnv getEnv() {
        return env;
    }

    public DataSource getDataSource() {
        return env.getDataSource();
    }

    public Dialect getDialect() {
        return env.getDialect();
    }

    public OrmConfig getConfig() {
        return env.getConfig();
    }

    public ExecutionContext getExecutionContext() {
        ExecutionContext ctx = env.getExecutionContext();
        return printSqlOverride != null ? ctx.withPrintSql(printSqlOverride) : ctx;
    }

    public SELF printSql() {
        OrmConfig cfg = getConfig();
        this.printSqlOverride = cfg != null && cfg.isPrintSql();
        return self();
    }

    public SELF printSql(boolean print) {
        this.printSqlOverride = print;
        return self();
    }

    public SELF sharding(TableRouter<?> router) {
        applyTableRouter(router);
        return self();
    }

    public SELF sharding(Class<? extends TableRouter<?>> routerClass) {
        if (routerClass != null) {
            try {
                sharding(routerClass.getDeclaredConstructor().newInstance());
            } catch (Exception e) {
                throw new RuntimeException("Failed to instantiate router " + routerClass.getName(), e);
            }
        }
        return self();
    }

    public SELF tableRouter(TableRouter<?> router) {
        return sharding(router);
    }

    public SELF tableRouter(Class<? extends TableRouter<?>> routerClass) {
        return sharding(routerClass);
    }

    public SELF dataSource(String name) {
        applyTargetDataSource(name);
        return self();
    }

    public SELF dataSource(DataSourceRouter<?> router) {
        applyDataSourceRouter(router);
        return self();
    }

    public SELF dataSource(Class<? extends DataSourceRouter<?>> routerClass) {
        if (routerClass != null) {
            try {
                dataSource(routerClass.getDeclaredConstructor().newInstance());
            } catch (Exception e) {
                throw new RuntimeException("Failed to instantiate DataSourceRouter " + routerClass.getName(), e);
            }
        }
        return self();
    }

    public SELF sharding(DataSourceRouter<?> router) {
        return dataSource(router);
    }

    public SELF allowFullShardingScan(boolean allow) {
        applyAllowFullShardingScan(allow);
        return self();
    }

    protected abstract void applyTableRouter(TableRouter<?> router);

    protected abstract void applyTargetDataSource(String name);

    protected abstract void applyDataSourceRouter(DataSourceRouter<?> router);

    protected void applyAllowFullShardingScan(boolean allow) {
        throw new UnsupportedOperationException("allowFullShardingScan is not supported on " + getClass().getSimpleName());
    }
}
