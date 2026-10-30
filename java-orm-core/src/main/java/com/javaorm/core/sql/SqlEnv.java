package com.javaorm.core.sql;

import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.dialect.Dialect;
import com.javaorm.core.metadata.EntityMetadata;
import com.javaorm.core.metadata.EntityMetadataManager;
import com.javaorm.core.proxy.ProxyFactory;
import com.javaorm.core.proxy.RecordingContext;
import com.javaorm.core.query.Cte;

import javax.sql.DataSource;
import java.util.*;

/**
 * SQL 声明式环境上下文（SqlEnv）。
 * 在闭包内统一声明表变量（e.table(...)），并按原生 SQL 语序流畅装配 SELECT、UPDATE、DELETE。
 */
public class SqlEnv {

    private final DataSource dataSource;
    private final Dialect dialect;
    private final OrmConfig config;

    private int aliasSequence = 0;
    private final List<SqlTableInfo> registeredTables = new ArrayList<>();
    private final Map<Object, SqlTableInfo> proxyMap = new IdentityHashMap<>();
    private final Map<Class<?>, SqlTableInfo> classMap = new HashMap<>();
    private final Map<String, SqlTableInfo> aliasMap = new HashMap<>();

    private Object currentOperation;

    public SqlEnv(DataSource dataSource, Dialect dialect, OrmConfig config) {
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.config = config;
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public Dialect getDialect() {
        return dialect;
    }

    public OrmConfig getConfig() {
        return config;
    }

    public String nextAlias() {
        return "t" + (aliasSequence++);
    }

    // ========== 1. 声明与注册表（table） ==========

    /**
     * 声明实体表，自动分配别名（t0, t1...），返回实体代理
     */
    public <T> T table(Class<T> entityClass) {
        return table(entityClass, nextAlias());
    }

    /**
     * 声明实体表并显式指定别名，返回实体代理
     */
    public <T> T table(Class<T> entityClass, String alias) {
        if (entityClass == null) {
            throw new IllegalArgumentException("Entity class cannot be null");
        }
        String effectiveAlias = (alias != null && !alias.trim().isEmpty()) ? alias.trim() : nextAlias();
        T proxy = ProxyFactory.createProxy(entityClass, effectiveAlias);
        EntityMetadata meta = EntityMetadataManager.get(entityClass, config);
        String tableName = meta != null ? meta.getTableName() : entityClass.getSimpleName();

        SqlTableInfo info = new SqlTableInfo(proxy, entityClass, null, effectiveAlias, tableName);
        registerTable(info);
        RecordingContext.registerProxyAlias(proxy, effectiveAlias);
        RecordingContext.registerAlias(entityClass, effectiveAlias);
        return proxy;
    }

    /**
     * 声明 CTE 临时表，返回带别名的 CTE 实例
     */
    public <R> Cte<R> table(Cte<R> cte) {
        String alias = cte.getEffectiveAlias() != null ? cte.getEffectiveAlias() : nextAlias();
        return table(cte, alias);
    }

    /**
     * 声明 CTE 临时表并显式指定别名
     */
    public <R> Cte<R> table(Cte<R> cte, String alias) {
        if (cte == null) {
            throw new IllegalArgumentException("CTE cannot be null");
        }
        String effectiveAlias = (alias != null && !alias.trim().isEmpty()) ? alias.trim() : nextAlias();
        Cte<R> aliasedCte = cte.as(effectiveAlias);
        SqlTableInfo info = new SqlTableInfo(aliasedCte, cte.getEntityClass(), aliasedCte, effectiveAlias, cte.getName());
        registerTable(info);
        return aliasedCte;
    }

    private void registerTable(SqlTableInfo info) {
        registeredTables.add(info);
        if (info.getProxy() != null) {
            proxyMap.put(info.getProxy(), info);
        }
        if (info.getEntityClass() != null && !classMap.containsKey(info.getEntityClass())) {
            classMap.put(info.getEntityClass(), info);
        }
        if (info.getAlias() != null) {
            aliasMap.put(info.getAlias().toLowerCase(), info);
        }
    }

    public SqlTableInfo findTableInfo(Object target) {
        if (target == null) return null;
        SqlTableInfo info = proxyMap.get(target);
        if (info != null) return info;
        if (target instanceof Class) {
            return classMap.get(target);
        }
        if (target instanceof Cte) {
            Cte<?> cte = (Cte<?>) target;
            if (cte.getEffectiveAlias() != null) {
                return aliasMap.get(cte.getEffectiveAlias().toLowerCase());
            }
        }
        if (target instanceof String) {
            return aliasMap.get(((String) target).toLowerCase());
        }
        return null;
    }

    public SqlTableInfo resolveTable(Object target) {
        SqlTableInfo info = findTableInfo(target);
        if (info != null) {
            return info;
        }
        if (target instanceof Class) {
            @SuppressWarnings("unchecked")
            Class<?> clazz = (Class<?>) target;
            String alias = nextAlias();
            EntityMetadata meta = EntityMetadataManager.get(clazz, config);
            String tableName = meta != null ? meta.getTableName() : clazz.getSimpleName();
            SqlTableInfo dynamicallyCreated = new SqlTableInfo(null, clazz, null, alias, tableName);
            registerTable(dynamicallyCreated);
            return dynamicallyCreated;
        }
        if (target instanceof Cte) {
            Cte<?> cte = (Cte<?>) target;
            String alias = cte.getEffectiveAlias() != null ? cte.getEffectiveAlias() : nextAlias();
            SqlTableInfo dynamicallyCreated = new SqlTableInfo(cte, cte.getEntityClass(), cte, alias, cte.getName());
            registerTable(dynamicallyCreated);
            return dynamicallyCreated;
        }
        throw new IllegalArgumentException("Cannot resolve table from object: " + target);
    }

    public SqlTableInfo getFirstRegisteredTable() {
        return !registeredTables.isEmpty() ? registeredTables.get(0) : null;
    }

    // ========== 2. SELECT 管道入口 ==========

    public SqlSelectStream select(Object... expressions) {
        SqlSelectStream stream = new SqlSelectStream(this, null, expressions);
        this.currentOperation = stream;
        return stream;
    }

    public <DTO> SqlSelectStream select(Class<DTO> dtoClass, Object... expressions) {
        SqlSelectStream stream = new SqlSelectStream(this, dtoClass, expressions);
        this.currentOperation = stream;
        return stream;
    }

    // ========== 3. UPDATE 管道入口 ==========

    public SqlUpdateStream update(Object targetTable) {
        SqlTableInfo info = resolveTable(targetTable);
        SqlUpdateStream stream = new SqlUpdateStream(this, info);
        this.currentOperation = stream;
        return stream;
    }

    // ========== 4. DELETE 管道入口 ==========

    public SqlDeleteStream delete(Object targetTable) {
        SqlTableInfo info = resolveTable(targetTable);
        SqlDeleteStream stream = new SqlDeleteStream(this, info);
        this.currentOperation = stream;
        return stream;
    }

    // ========== 5. 输出统一执行门面 ==========

    public SqlExecution toExecution() {
        return new SqlExecution(this, currentOperation);
    }
}
