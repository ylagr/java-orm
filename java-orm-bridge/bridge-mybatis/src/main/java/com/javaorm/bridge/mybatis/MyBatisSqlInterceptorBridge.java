package com.javaorm.bridge.mybatis;

import com.javaorm.core.dialect.Dialect;
import com.javaorm.core.dialect.SqlAndParams;
import com.javaorm.core.interceptor.SqlInterceptor;
import com.javaorm.core.metadata.EntityMetadata;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * 原生 MyBatis 拦截器与 JavaORM SqlInterceptor 双向桥接器。
 * <p>
 * 1. MyBatis -> JavaORM：将原生的 MyBatis {@link Interceptor}（如自定义 StatementHandler/Executor 插件）
 *    桥接为 JavaORM {@link SqlInterceptor}，挂载到 {@code OrmConfig} 中参与 JavaORM SQL 改写；
 * 2. JavaORM -> MyBatis：将 JavaORM {@link SqlInterceptor} 桥接为标准 MyBatis {@link Interceptor}，
 *    注册到 MyBatis {@link Configuration} 中，在 StatementHandler.prepare 阶段改写 SQL 并在 Executor 阶段统计耗时。
 * </p>
 */
@Intercepts({
        @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class}),
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class})
})
public class MyBatisSqlInterceptorBridge implements SqlInterceptor, Interceptor {

    private static final Configuration DEFAULT_CONFIGURATION = new Configuration();
    private static final Method PREPARE_METHOD;
    private static final Method EXECUTOR_QUERY_METHOD;
    private static final Method EXECUTOR_UPDATE_METHOD;
    private static final Field BOUND_SQL_FIELD;

    static {
        Method prepare = null;
        Method query = null;
        Method update = null;
        Field sqlField = null;
        try {
            prepare = StatementHandler.class.getMethod("prepare", Connection.class, Integer.class);
            query = Executor.class.getMethod("query", MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class);
            update = Executor.class.getMethod("update", MappedStatement.class, Object.class);
            sqlField = BoundSql.class.getDeclaredField("sql");
            sqlField.setAccessible(true);
        } catch (Throwable ignored) {
        }
        PREPARE_METHOD = prepare;
        EXECUTOR_QUERY_METHOD = query;
        EXECUTOR_UPDATE_METHOD = update;
        BOUND_SQL_FIELD = sqlField;
    }

    private final Interceptor mybatisInterceptor;
    private final SqlInterceptor sqlInterceptor;
    private final Configuration configuration;
    private final Dialect dialect;
    private int order = 0;

    public MyBatisSqlInterceptorBridge() {
        this.mybatisInterceptor = null;
        this.sqlInterceptor = null;
        this.configuration = DEFAULT_CONFIGURATION;
        this.dialect = null;
    }

    public MyBatisSqlInterceptorBridge(Interceptor mybatisInterceptor) {
        this(mybatisInterceptor, null);
    }

    public MyBatisSqlInterceptorBridge(Interceptor mybatisInterceptor, Configuration configuration) {
        this.mybatisInterceptor = mybatisInterceptor;
        this.sqlInterceptor = mybatisInterceptor instanceof SqlInterceptor ? (SqlInterceptor) mybatisInterceptor : null;
        this.configuration = configuration != null ? configuration : DEFAULT_CONFIGURATION;
        this.dialect = null;
    }

    public MyBatisSqlInterceptorBridge(SqlInterceptor sqlInterceptor) {
        this(sqlInterceptor, null);
    }

    public MyBatisSqlInterceptorBridge(SqlInterceptor sqlInterceptor, Dialect dialect) {
        this.mybatisInterceptor = null;
        this.sqlInterceptor = sqlInterceptor;
        this.configuration = DEFAULT_CONFIGURATION;
        this.dialect = dialect;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    @Override
    public int getOrder() {
        return sqlInterceptor != null ? sqlInterceptor.getOrder() : order;
    }

    @Override
    public SqlAndParams beforeQuery(SqlAndParams sqlAndParams, EntityMetadata metadata, Dialect dialect) {
        return invokeBefore(sqlAndParams, metadata, dialect, true);
    }

    @Override
    public SqlAndParams beforeUpdate(SqlAndParams sqlAndParams, EntityMetadata metadata, Dialect dialect) {
        return invokeBefore(sqlAndParams, metadata, dialect, false);
    }

    @Override
    public void afterExecution(String sql, List<Object> params, long elapsedMillis, Throwable throwable) {
        if (sqlInterceptor != null) {
            sqlInterceptor.afterExecution(sql, params, elapsedMillis, throwable);
        }
    }

    private SqlAndParams invokeBefore(SqlAndParams sqlAndParams, EntityMetadata metadata, Dialect dialect, boolean isQuery) {
        if (sqlAndParams == null || sqlAndParams.getSql() == null) {
            return sqlAndParams;
        }
        if (sqlInterceptor != null) {
            return isQuery
                    ? sqlInterceptor.beforeQuery(sqlAndParams, metadata, dialect)
                    : sqlInterceptor.beforeUpdate(sqlAndParams, metadata, dialect);
        }
        if (mybatisInterceptor == null) {
            return sqlAndParams;
        }

        BoundSql boundSql = new BoundSql(configuration, sqlAndParams.getSql(), Collections.<ParameterMapping>emptyList(), sqlAndParams.getParams());
        try {
            if (targetsStatementHandler(mybatisInterceptor) && PREPARE_METHOD != null) {
                MappedStatement ms = buildSyntheticMappedStatement(boundSql, isQuery);
                StatementHandler sh = createSyntheticStatementHandler(boundSql, ms);
                mybatisInterceptor.intercept(new Invocation(sh, PREPARE_METHOD, new Object[]{null, null}));
            } else if (targetsExecutor(mybatisInterceptor)) {
                MappedStatement ms = buildSyntheticMappedStatement(boundSql, isQuery);
                Executor executor = createSyntheticExecutor();
                if (isQuery && EXECUTOR_QUERY_METHOD != null) {
                    mybatisInterceptor.intercept(new Invocation(executor, EXECUTOR_QUERY_METHOD,
                            new Object[]{ms, sqlAndParams.getParams(), RowBounds.DEFAULT, null}));
                } else if (!isQuery && EXECUTOR_UPDATE_METHOD != null) {
                    mybatisInterceptor.intercept(new Invocation(executor, EXECUTOR_UPDATE_METHOD,
                            new Object[]{ms, sqlAndParams.getParams()}));
                }
            }
        } catch (Throwable ignored) {
            return sqlAndParams;
        }

        String updatedSql = boundSql.getSql();
        if (updatedSql != null && !updatedSql.equals(sqlAndParams.getSql())) {
            return new SqlAndParams(updatedSql, sqlAndParams.getParams());
        }
        return sqlAndParams;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if (mybatisInterceptor != null) {
            return mybatisInterceptor.intercept(invocation);
        }
        if (sqlInterceptor == null) {
            return invocation.proceed();
        }

        Object target = invocation.getTarget();
        if (target instanceof StatementHandler) {
            StatementHandler sh = (StatementHandler) target;
            BoundSql boundSql = sh.getBoundSql();
            if (boundSql != null && boundSql.getSql() != null) {
                boolean isQuery = isSelectStatement(sh, boundSql.getSql());
                List<Object> params = extractBoundSqlParams(boundSql);
                SqlAndParams input = new SqlAndParams(boundSql.getSql(), params);
                SqlAndParams output = isQuery
                        ? sqlInterceptor.beforeQuery(input, null, dialect)
                        : sqlInterceptor.beforeUpdate(input, null, dialect);
                if (output != null && output.getSql() != null && !output.getSql().equals(boundSql.getSql())) {
                    setBoundSql(boundSql, output.getSql());
                }
            }
            return invocation.proceed();
        }

        if (target instanceof Executor) {
            Object[] args = invocation.getArgs();
            MappedStatement ms = args != null && args.length > 0 && args[0] instanceof MappedStatement
                    ? (MappedStatement) args[0] : null;
            Object paramObj = args != null && args.length > 1 ? args[1] : null;
            BoundSql boundSql = ms != null ? ms.getBoundSql(paramObj) : null;
            String sql = boundSql != null ? boundSql.getSql() : null;
            List<Object> params = boundSql != null ? extractBoundSqlParams(boundSql) : Collections.emptyList();

            long start = System.currentTimeMillis();
            Throwable error = null;
            try {
                return invocation.proceed();
            } catch (Throwable t) {
                error = t;
                throw t;
            } finally {
                long elapsed = System.currentTimeMillis() - start;
                sqlInterceptor.afterExecution(sql, params, elapsed, error);
            }
        }

        return invocation.proceed();
    }

    @Override
    public Object plugin(Object target) {
        if (mybatisInterceptor != null) {
            return mybatisInterceptor.plugin(target);
        }
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        if (mybatisInterceptor != null) {
            mybatisInterceptor.setProperties(properties);
        }
    }

    private MappedStatement buildSyntheticMappedStatement(BoundSql boundSql, boolean isQuery) {
        SqlSource sqlSource = parameterObject -> boundSql;
        return new MappedStatement.Builder(
                configuration,
                "javaorm.bridge.synthetic." + (isQuery ? "query" : "update"),
                sqlSource,
                isQuery ? SqlCommandType.SELECT : SqlCommandType.UPDATE
        ).build();
    }

    private StatementHandler createSyntheticStatementHandler(BoundSql boundSql, MappedStatement ms) {
        return (StatementHandler) Proxy.newProxyInstance(
                StatementHandler.class.getClassLoader(),
                new Class<?>[]{StatementHandler.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getBoundSql".equals(name)) {
                        return boundSql;
                    }
                    if ("getMappedStatement".equals(name)) {
                        return ms;
                    }
                    if ("prepare".equals(name)) {
                        return null;
                    }
                    return null;
                }
        );
    }

    private Executor createSyntheticExecutor() {
        return (Executor) Proxy.newProxyInstance(
                Executor.class.getClassLoader(),
                new Class<?>[]{Executor.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("query".equals(name)) {
                        return Collections.emptyList();
                    }
                    if ("update".equals(name)) {
                        return 0;
                    }
                    return null;
                }
        );
    }

    private static boolean targetsStatementHandler(Interceptor interceptor) {
        Intercepts intercepts = interceptor.getClass().getAnnotation(Intercepts.class);
        if (intercepts == null) {
            return true;
        }
        for (Signature sig : intercepts.value()) {
            if (StatementHandler.class.isAssignableFrom(sig.type())) {
                return true;
            }
        }
        return false;
    }

    private static boolean targetsExecutor(Interceptor interceptor) {
        Intercepts intercepts = interceptor.getClass().getAnnotation(Intercepts.class);
        if (intercepts == null) {
            return false;
        }
        for (Signature sig : intercepts.value()) {
            if (Executor.class.isAssignableFrom(sig.type())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSelectStatement(StatementHandler sh, String sql) {
        try {
            MetaObject meta = SystemMetaObject.forObject(sh);
            Object msObj = meta.hasGetter("mappedStatement") ? meta.getValue("mappedStatement")
                    : (meta.hasGetter("delegate.mappedStatement") ? meta.getValue("delegate.mappedStatement") : null);
            if (msObj instanceof MappedStatement) {
                return ((MappedStatement) msObj).getSqlCommandType() == SqlCommandType.SELECT;
            }
        } catch (Throwable ignored) {
        }
        return sql.trim().toUpperCase().startsWith("SELECT");
    }

    private static List<Object> extractBoundSqlParams(BoundSql boundSql) {
        Object paramObj = boundSql.getParameterObject();
        if (paramObj == null) {
            return Collections.emptyList();
        }
        List<ParameterMapping> mappings = boundSql.getParameterMappings();
        if (mappings == null || mappings.isEmpty()) {
            return Collections.singletonList(paramObj);
        }
        List<Object> params = new ArrayList<>(mappings.size());
        MetaObject meta = SystemMetaObject.forObject(paramObj);
        for (ParameterMapping pm : mappings) {
            String prop = pm.getProperty();
            if (boundSql.hasAdditionalParameter(prop)) {
                params.add(boundSql.getAdditionalParameter(prop));
            } else if (meta.hasGetter(prop)) {
                params.add(meta.getValue(prop));
            } else {
                params.add(paramObj);
            }
        }
        return params;
    }

    private static void setBoundSql(BoundSql boundSql, String newSql) {
        try {
            if (BOUND_SQL_FIELD != null) {
                BOUND_SQL_FIELD.set(boundSql, newSql);
            } else {
                SystemMetaObject.forObject(boundSql).setValue("sql", newSql);
            }
        } catch (Throwable ignored) {
        }
    }
}
