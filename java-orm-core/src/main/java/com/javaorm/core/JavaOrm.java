package com.javaorm.core;

import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.dialect.*;
import com.javaorm.core.mutation.DeleteBuilder;
import com.javaorm.core.mutation.InsertBuilder;
import com.javaorm.core.mutation.UpdateBuilder;
import com.javaorm.core.query.*;
import com.javaorm.core.sql.SqlEnv;
import com.javaorm.core.sql.SqlExecution;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * JavaOrm 核心门面与统一操作入口。
 */
public class JavaOrm {

    private final DataSource dataSource;
    private final Dialect dialect;
    private final OrmConfig config;

    public JavaOrm(DataSource dataSource) {
        this(dataSource, null, new OrmConfig());
    }

    public JavaOrm(DataSource dataSource, Dialect dialect, OrmConfig config) {
        if (dataSource == null) {
            throw new IllegalArgumentException("DataSource must not be null");
        }
        this.dataSource = dataSource;
        this.config = config != null ? config : new OrmConfig();
        this.dialect = dialect != null ? dialect : detectDialect(dataSource);
    }

    public static JavaOrm create(DataSource dataSource) {
        return new JavaOrm(dataSource, null, new OrmConfig());
    }

    public static JavaOrm create(DataSource dataSource, Dialect dialect) {
        return new JavaOrm(dataSource, dialect, new OrmConfig());
    }

    public static JavaOrm create(DataSource dataSource, Dialect dialect, OrmConfig config) {
        return new JavaOrm(dataSource, dialect, config);
    }

    /**
     * 开启编程式事务执行（无返回值）
     */
    public void transaction(java.util.function.Consumer<JavaOrm> action) {
        transactionResult(orm -> {
            action.accept(orm);
            return null;
        });
    }

    /**
     * 开启编程式事务执行（带返回值）
     */
    public <R> R transactionResult(java.util.function.Function<JavaOrm, R> action) {
        java.sql.Connection existing = com.javaorm.core.connection.TransactionContext.getConnection();
        if (existing != null) {
            return action.apply(this);
        }
        try (java.sql.Connection conn = dataSource.getConnection()) {
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            com.javaorm.core.connection.TransactionContext.setConnection(conn);
            try {
                R result = action.apply(this);
                conn.commit();
                return result;
            } catch (Throwable t) {
                try {
                    conn.rollback();
                } catch (Exception ignored) {}
                if (t instanceof RuntimeException) {
                    throw (RuntimeException) t;
                }
                throw new RuntimeException("Transaction rolled back due to error", t);
            } finally {
                com.javaorm.core.connection.TransactionContext.clear();
                try {
                    conn.setAutoCommit(autoCommit);
                } catch (Exception ignored) {}
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Transaction execution failed", e);
        }
    }

    /**
     * 获取指定实体的录制代理单例对象
     */
    public <T> T proxy(Class<T> entityClass) {
        return com.javaorm.core.proxy.ProxyFactory.getProxy(entityClass);
    }

    public <T> T proxy(Cte<T> cte) {
        return cte.proxy();
    }

    /**
     * 快捷静态方法：获取实体的录制代理单例对象
     */
    public static <T> T $(Class<T> entityClass) {
        return com.javaorm.core.proxy.ProxyFactory.getProxy(entityClass);
    }

    public static <T> T $(Cte<T> cte) {
        return cte.proxy();
    }

    /**
     * 开启流式查询：orm.select(Student.class).where().and(s -> s.getAge()).gt(18)...
     */
    public <T> Query<T> select(Class<T> entityClass) {
        return env().createQuery(entityClass);
    }

    /**
     * 开启流式查询别名（等价于 select(entityClass)）
     */
    public <T> Query<T> queryable(Class<T> entityClass) {
        return select(entityClass);
    }

    /**
     * 开启 CTE 强类型流式查询：orm.select(cte).where()...
     */
    public <T> Query<T> select(Cte<T> cte) {
        return env().createQuery(cte);
    }

    /**
     * 定义强类型或动态 CTE
     */
    public <T> Cte<T> cte(String name, Query<T> query) {
        return new Cte<>(name, query.getRootEntityClass(), query, false);
    }

    public <T> Cte<T> cte(String name, WhereBuilder<T> whereBuilder) {
        return new Cte<>(name, whereBuilder.getEntityClass(), whereBuilder, false);
    }

    public <T> Cte<T> cte(String name, Class<T> cteClass, Object query) {
        return new Cte<>(name, cteClass, query, false);
    }

    public Cte<?> cte(String name, Object query) {
        return new Cte<>(name, null, query, false);
    }

    public Cte<?> dynamicCte(String name, Object query) {
        return new Cte<>(name, null, query, false);
    }

    public Cte<?> dynamicCte(String name, Object query, boolean recursive) {
        return new Cte<>(name, null, query, recursive);
    }

    public Cte<?> dynamicCte(String name, Object query, boolean recursive, List<String> columnNames) {
        return new Cte<>(name, null, query, recursive, columnNames);
    }

    public <T> Cte<T> cteRecursive(String name, Query<T> query) {
        return new Cte<>(name, query.getRootEntityClass(), query, true);
    }

    public <T> Cte<T> cteRecursive(String name, WhereBuilder<T> whereBuilder) {
        return new Cte<>(name, whereBuilder.getEntityClass(), whereBuilder, true);
    }

    public <T> Cte<T> cteRecursive(String name, Class<T> cteClass, Object query) {
        return new Cte<>(name, cteClass, query, true);
    }

    public <T> Cte<T> cteRecursive(String name, Class<T> cteClass, java.util.function.Function<Cte<T>, Object> queryBuilder) {
        Cte<T> cte = new Cte<>(name, cteClass, null, true);
        Object query = queryBuilder.apply(cte);
        cte.setQuery(query);
        return cte;
    }

    public Cte<?> cteRecursive(String name, Object query) {
        return new Cte<>(name, null, query, true);
    }

    public Cte<?> cteRecursive(String name, java.util.function.Function<Cte<?>, Object> queryBuilder) {
        Cte<?> cte = new Cte<>(name, null, null, true);
        Object query = queryBuilder.apply(cte);
        cte.setQuery(query);
        return cte;
    }

    public Cte<?> dynamicCteRecursive(String name, java.util.function.Function<Cte<?>, Object> queryBuilder) {
        return cteRecursive(name, queryBuilder);
    }

    /**
     * 获取统一的 SQL 声明式环境上下文
     */
    public SqlEnv env() {
        return new SqlEnv(dataSource, dialect, config);
    }

    /**
     * 以 WITH 子句起始构建主查询
     */
    public WithStage with(Cte<?> cte) {
        return new WithStage(env()).with(cte);
    }

    public WithStage with(String name, Object query) {
        return new WithStage(env()).with(name, query);
    }

    public WithStage withRecursive(String name, Object query) {
        return new WithStage(env()).withRecursive(name, query);
    }

    /**
     * 开启自定义字段投影查询：orm.select(count(s.getId()), s.getTeacherId()).from(Student.class)...
     */
    public FromStageQuery select(Object expr1, Object... moreExpressions) {
        Object[] all = new Object[1 + (moreExpressions != null ? moreExpressions.length : 0)];
        all[0] = expr1;
        if (moreExpressions != null && moreExpressions.length > 0) {
            System.arraycopy(moreExpressions, 0, all, 1, moreExpressions.length);
        }
        return new FromStageQuery(env(), all);
    }

    /**
     * 开启 DTO 投影查询：orm.select(StatDto.class, count(s.getId()).as("total"), s.getTeacherId()).from(Student.class)...
     */
    public <DTO> DtoFromStageQuery<DTO> select(Class<DTO> dtoClass, Object expr1, Object... moreExpressions) {
        Object[] all = new Object[1 + (moreExpressions != null ? moreExpressions.length : 0)];
        all[0] = expr1;
        if (moreExpressions != null && moreExpressions.length > 0) {
            System.arraycopy(moreExpressions, 0, all, 1, moreExpressions.length);
        }
        return new DtoFromStageQuery<>(env(), dtoClass, all);
    }

    /**
     * 实体级插入（自增主键自动回填）
     */
    public <T> int insert(T entity) {
        try {
            return env().createInsert(entity).execute();
        } finally {
            com.javaorm.core.query.Query.resetAliasSeq();
        }
    }

    /**
     * 批量实体插入
     */
    public <T> int insert(java.util.Collection<T> entities) {
        if (entities == null || entities.isEmpty()) return 0;
        int sum = 0;
        for (T entity : entities) {
            sum += insert(entity);
        }
        return sum;
    }

    /**
     * 开启实体插入构造器
     */
    public <T> InsertBuilder<T> insertable(T entity) {
        return env().createInsert(entity);
    }

    /**
     * 实体级选择性更新（按主键更新非 null 属性）
     */
    public <T> int update(T entity) {
        try {
            return UpdateBuilder.updateEntity(entity, env(), true);
        } finally {
            com.javaorm.core.query.Query.resetAliasSeq();
        }
    }

    /**
     * 批量实体更新
     */
    public <T> int update(java.util.Collection<T> entities) {
        if (entities == null || entities.isEmpty()) return 0;
        int sum = 0;
        for (T entity : entities) {
            sum += update(entity);
        }
        return sum;
    }

    /**
     * 实体级全量更新（按主键更新包括 null 在内的所有属性）
     */
    public <T> int updateAllColumns(T entity) {
        try {
            return UpdateBuilder.updateEntity(entity, env(), false);
        } finally {
            com.javaorm.core.query.Query.resetAliasSeq();
        }
    }

    /**
     * 开启流式条件更新：orm.update(Student.class).set(s -> s.getAge(), 20).where()...
     */
    public <T> UpdateBuilder<T> update(Class<T> entityClass) {
        return env().createUpdate(entityClass);
    }

    /**
     * 别名方法：updatable 等价于 update
     */
    public <T> UpdateBuilder<T> updatable(Class<T> entityClass) {
        return update(entityClass);
    }

    @SuppressWarnings("unchecked")
    public <T> UpdateBuilder<T> updatable(T entity) {
        if (entity == null) {
            throw new IllegalArgumentException("Entity must not be null");
        }
        Class<T> clazz = (Class<T>) entity.getClass();
        com.javaorm.core.metadata.EntityMetadata meta = com.javaorm.core.metadata.EntityMetadataManager.get(clazz, config);
        UpdateBuilder<T> builder = update(clazz);
        builder.setColumns(entity);
        if (meta.getIdColumn() != null) {
            Object idVal = meta.getIdColumn().getValue(entity);
            if (idVal != null) {
                builder.whereById(idVal);
            }
        }
        return builder;
    }

    /**
     * 实体级删除（按实体主键删除，支持逻辑删除自动重写）
     */
    public <T> int delete(T entity) {
        try {
            return DeleteBuilder.deleteEntity(entity, env());
        } finally {
            com.javaorm.core.query.Query.resetAliasSeq();
        }
    }

    /**
     * 根据主键 ID 删除
     */
    public <T> int deleteById(Class<T> entityClass, Object id) {
        try {
            return DeleteBuilder.deleteById(entityClass, id, env());
        } finally {
            com.javaorm.core.query.Query.resetAliasSeq();
        }
    }

    /**
     * 开启流式条件删除：orm.delete(Student.class).where().and(s -> s.getAge()).lt(10)...
     */
    public <T> DeleteBuilder<T> delete(Class<T> entityClass) {
        return env().createDelete(entityClass);
    }

    /**
     * 别名方法：deletable 等价于 delete
     */
    public <T> DeleteBuilder<T> deletable(Class<T> entityClass) {
        return delete(entityClass);
    }

    @SuppressWarnings("unchecked")
    public <T> DeleteBuilder<T> deletable(T entity) {
        if (entity == null) {
            throw new IllegalArgumentException("Entity must not be null");
        }
        Class<T> clazz = (Class<T>) entity.getClass();
        com.javaorm.core.metadata.EntityMetadata meta = com.javaorm.core.metadata.EntityMetadataManager.get(clazz, config);
        DeleteBuilder<T> builder = delete(clazz);
        if (meta.getIdColumn() != null) {
            Object idVal = meta.getIdColumn().getValue(entity);
            if (idVal != null) {
                builder.whereById(idVal);
            }
        }
        return builder;
    }

    @SuppressWarnings("unchecked")
    public <T> DeleteBuilder<T> deletable(java.util.Collection<T> entities) {
        if (entities == null || entities.isEmpty()) {
            throw new IllegalArgumentException("Entities must not be empty");
        }
        T first = entities.iterator().next();
        Class<T> clazz = (Class<T>) first.getClass();
        com.javaorm.core.metadata.EntityMetadata meta = com.javaorm.core.metadata.EntityMetadataManager.get(clazz, config);
        DeleteBuilder<T> builder = delete(clazz);
        if (meta.getIdColumn() != null) {
            java.util.List<Object> ids = new java.util.ArrayList<>();
            for (T e : entities) {
                Object idVal = meta.getIdColumn().getValue(e);
                if (idVal != null) ids.add(idVal);
            }
            builder.whereByIds(ids);
        }
        return builder;
    }

    public <T> int delete(java.util.Collection<T> entities) {
        if (entities == null || entities.isEmpty()) return 0;
        return deletable(entities).execute();
    }

    public DataSource getDataSource() { return dataSource; }
    public Dialect getDialect() { return dialect; }
    public OrmConfig getConfig() { return config; }

    /**
     * 声明式 SQL 执行环境统一入口（SqlEnv DSL）：
     * <pre>
     * orm.sql(e -> {
     *     Department parent = e.table(Department.class, "d_parent");
     *     Department child  = e.table(Department.class, "d_child");
     *     e.select(parent.getName(), child.getAge())
     *      .from(parent)
     *      .leftJoin(child).on(child.getParentId(), parent.getId())
     *      .where().and(parent.getName()).eq("研发部")
     *      .orderBy(child.getId()).desc();
     * }).toList();
     * </pre>
     */
    public <T> SqlExecution<T> sql(Class<T> resultClass, Consumer<SqlEnv> envConsumer) {
        if (envConsumer == null) {
            throw new IllegalArgumentException("SqlEnv consumer must not be null");
        }
        SqlEnv env = env();
        try {
            envConsumer.accept(env);
            return env.toExecution(resultClass);
        } finally {
            com.javaorm.core.proxy.RecordingContext.clearRecording();
            com.javaorm.core.proxy.RecordingContext.clearAliases();
            com.javaorm.core.query.Query.resetAliasSeq();
        }
    }

    public <T> SqlExecution<T> sql(Consumer<SqlEnv> envConsumer) {
        if (envConsumer == null) {
            throw new IllegalArgumentException("SqlEnv consumer must not be null");
        }
        SqlEnv env = env();
        try {
            envConsumer.accept(env);
            return env.toExecution();
        } finally {
            com.javaorm.core.proxy.RecordingContext.clearRecording();
            com.javaorm.core.proxy.RecordingContext.clearAliases();
            com.javaorm.core.query.Query.resetAliasSeq();
        }
    }

    /**
     * 声明式 SQL 执行环境统一强类型查询入口（带返回值 lambda，直接 return e.select(...) / WhereBuilder 等 TQuery<T> 实例）：
     * <pre>
     * List<Student> list = orm.sql(e -> {
     *     Student s = e.table(Student.class);
     *     return e.select(s)
     *             .where().and(s.getAge()).ge(22)
     *             .orderBy(s.getId()).desc();
     * }).toList();
     * </pre>
     */
    public <T> SqlExecution<T> sql(Function<SqlEnv, ? extends TQuery<T>> envFunction) {
        if (envFunction == null) {
            throw new IllegalArgumentException("SqlEnv function must not be null");
        }
        SqlEnv env = env();
        try {
            TQuery<T> returned = envFunction.apply(env);
            return env.toExecutionFromReturn(returned);
        } finally {
            com.javaorm.core.proxy.RecordingContext.clearRecording();
            com.javaorm.core.proxy.RecordingContext.clearAliases();
            com.javaorm.core.query.Query.resetAliasSeq();
        }
    }

    private static Dialect detectDialect(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection()) {
            String prodName = conn.getMetaData().getDatabaseProductName();
            if (prodName != null) {
                String lower = prodName.toLowerCase();
                if (lower.contains("mysql") || lower.contains("mariadb")) {
                    return new MySQLDialect();
                }
                if (lower.contains("postgres") || lower.contains("pgsql")) {
                    return new PostgreSQLDialect();
                }
                if (lower.contains("sqlite")) {
                    return new SQLiteDialect();
                }
                if (lower.contains("h2")) {
                    return new H2Dialect();
                }
                if (lower.contains("oracle")) {
                    try {
                        int major = conn.getMetaData().getDatabaseMajorVersion();
                        if (major > 0 && major <= 11) {
                            return new Oracle11Dialect();
                        }
                    } catch (Exception ignored) {
                    }
                    return new OracleDialect();
                }
            }
        } catch (Exception ignored) {
        }
        return new MySQLDialect();
    }
}
