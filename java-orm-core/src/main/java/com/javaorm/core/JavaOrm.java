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
        if (this.config.getDataSourceManager() == null) {
            this.config.setDataSourceManager(new com.javaorm.core.datasource.DataSourceManager(dataSource));
        } else if (this.config.getDataSourceManager().getDefaultDataSource() == null) {
            this.config.getDataSourceManager().setDefaultDataSource(dataSource);
        }
        this.dialect = dialect != null ? dialect : detectDialect(dataSource);
        if (this.config.getCodeFirstMode() != null && this.config.getCodeFirstMode() != com.javaorm.core.codefirst.CodeFirstMode.NONE) {
            codeFirst().syncAll();
        }
    }

    /**
     * 获取 Code-First 数据库架构同步引擎
     */
    public com.javaorm.core.codefirst.CodeFirst codeFirst() {
        return new com.javaorm.core.codefirst.CodeFirst(dataSource, dialect, config);
    }

    /**
     * 获取多数据源管理器
     */
    public com.javaorm.core.datasource.DataSourceManager getDataSourceManager() {
        return this.config.getDataSourceManager();
    }

    /**
     * 注册命名物理数据源（支持链式配置）
     */
    public JavaOrm registerDataSource(String name, DataSource ds) {
        if (this.config.getDataSourceManager() != null) {
            this.config.getDataSourceManager().register(name, ds);
        }
        return this;
    }

    /**
     * 在指定物理数据源上下文中执行代码块（无返回值）
     */
    public void useDataSource(String dataSourceName, Runnable action) {
        com.javaorm.core.datasource.DataSourceContext.use(dataSourceName, action);
    }

    /**
     * 在指定物理数据源上下文中执行代码块（带返回值）
     */
    public <R> R useDataSource(String dataSourceName, java.util.function.Supplier<R> action) {
        return com.javaorm.core.datasource.DataSourceContext.use(dataSourceName, action);
    }

    public static JavaOrm create(DataSource dataSource) {
        return new JavaOrm(dataSource, null, new OrmConfig());
    }

    public static JavaOrm create(DataSource dataSource, Dialect dialect) {
        return new JavaOrm(dataSource, dialect, new OrmConfig());
    }

    public static JavaOrm create(DataSource dataSource, OrmConfig config) {
        return new JavaOrm(dataSource, null, config);
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
                } catch (Exception ignored) {
                }
                if (t instanceof RuntimeException) {
                    throw (RuntimeException) t;
                }
                throw new RuntimeException("Transaction rolled back due to error", t);
            } finally {
                com.javaorm.core.connection.TransactionContext.clear();
                try {
                    conn.setAutoCommit(autoCommit);
                } catch (Exception ignored) {
                }
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
    public <T> SelectBuilder<T> select(Class<T> entityClass) {
        return env().createQuery(entityClass);
    }

    /**
     * 开启流式查询别名（等价于 select(entityClass)）
     */
    public <T> SelectBuilder<T> queryable(Class<T> entityClass) {
        return select(entityClass);
    }

    /**
     * 开启流式查询别名（等价于 select(entityClass)）
     */
    public <T> SelectBuilder<T> query(Class<T> entityClass) {
        return select(entityClass);
    }

    /**
     * 根据主键查询单条实体
     */
    public <T> T findById(Class<T> entityClass, Object id) {
        if (id == null) return null;
        com.javaorm.core.metadata.EntityMetadata meta = com.javaorm.core.metadata.EntityMetadataManager.get(entityClass, config);
        if (meta.getIdColumn() == null) {
            throw new IllegalArgumentException("Entity " + entityClass.getName() + " does not have an ID column");
        }
        return select(entityClass).whereById(id).first();
    }

    /**
     * 开启 CTE 强类型流式查询：orm.select(cte).where()...
     */
    public <T> SelectBuilder<T> select(Cte<T> cte) {
        return env().createQuery(cte);
    }

    /**
     * 定义强类型或动态 CTE
     */
    public <T> Cte<T> cte(String name, SelectBuilder<T> query) {
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

    public <T> Cte<T> cteRecursive(String name, SelectBuilder<T> query) {
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
            SelectBuilder.resetAliasSeq();
        }
    }

    /**
     * 批量实体插入（默认分批执行，每批 500 条）
     */
    public <T> int insert(java.util.Collection<T> entities) {
        if (entities == null || entities.isEmpty()) return 0;
        return insertBatch(entities).execute();
    }

    /**
     * 开启批处理插入构建器（参考 EasyQuery，支持自定义 batchSize、多值/JDBC Batch 模式）
     */
    public <T> com.javaorm.core.batch.BatchInsertBuilder<T> insertBatch(java.util.Collection<T> entities) {
        return new com.javaorm.core.batch.BatchInsertBuilder<>(env(), entities);
    }

    /**
     * 开启实体插入构造器
     */
    public <T> InsertBuilder<T> insertable(T entity) {
        return env().createInsert(entity);
    }

    /**
     * 实体级 UPSERT（存在即更新，不存在即插入，跨方言自适应）
     */
    public <T> int insertOrUpdate(T entity) {
        try {
            return env().createUpsert(entity).execute();
        } finally {
            SelectBuilder.resetAliasSeq();
        }
    }

    /**
     * 实体级 UPSERT 别名（等价于 insertOrUpdate(entity)）
     */
    public <T> int upsert(T entity) {
        return insertOrUpdate(entity);
    }

    /**
     * 批量实体 UPSERT（默认分批执行，每批 500 条）
     */
    public <T> int insertOrUpdate(java.util.Collection<T> entities) {
        if (entities == null || entities.isEmpty()) return 0;
        return insertOrUpdateBatch(entities).execute();
    }

    /**
     * 批量实体 UPSERT 别名（等价于 insertOrUpdate(entities)）
     */
    public <T> int upsert(java.util.Collection<T> entities) {
        return insertOrUpdate(entities);
    }

    /**
     * 开启实体级 UPSERT 构建器（支持指定 onConflict 列、updateColumns、ignoreColumns、doNothing 等）
     */
    public <T> com.javaorm.core.mutation.UpsertBuilder<T> insertOrUpdateable(T entity) {
        return env().createUpsert(entity);
    }

    /**
     * 开启实体级 UPSERT 构建器别名
     */
    public <T> com.javaorm.core.mutation.UpsertBuilder<T> upsertable(T entity) {
        return insertOrUpdateable(entity);
    }

    /**
     * 开启批量 UPSERT 构建器（支持自定义 batchSize、onConflict、updateColumns、ignoreColumns 等）
     */
    public <T> com.javaorm.core.batch.BatchUpsertBuilder<T> insertOrUpdateBatch(java.util.Collection<T> entities) {
        return new com.javaorm.core.batch.BatchUpsertBuilder<>(env(), entities);
    }

    /**
     * 开启批量 UPSERT 构建器别名
     */
    public <T> com.javaorm.core.batch.BatchUpsertBuilder<T> upsertBatch(java.util.Collection<T> entities) {
        return insertOrUpdateBatch(entities);
    }

    /**
     * 实体级选择性更新（按主键更新非 null 属性）
     */
    public <T> int update(T entity) {
        try {
            return UpdateBuilder.updateEntity(entity, env(), true);
        } finally {
            SelectBuilder.resetAliasSeq();
        }
    }

    /**
     * 批量实体更新（默认分批执行，支持乐观锁与分库分表）
     */
    public <T> int update(java.util.Collection<T> entities) {
        if (entities == null || entities.isEmpty()) return 0;
        return updateBatch(entities).execute();
    }

    /**
     * 开启批处理更新构建器（支持自定义 batchSize、selective 配置）
     */
    public <T> com.javaorm.core.batch.BatchUpdateBuilder<T> updateBatch(java.util.Collection<T> entities) {
        return new com.javaorm.core.batch.BatchUpdateBuilder<>(env(), entities);
    }

    /**
     * 实体级全量更新（按主键更新包括 null 在内的所有属性）
     */
    public <T> int updateAllColumns(T entity) {
        try {
            return UpdateBuilder.updateEntity(entity, env(), false);
        } finally {
            SelectBuilder.resetAliasSeq();
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
    /**
     * 实体级删除（按实体主键删除，支持逻辑删除自动重写）
     */
    public <T> int delete(T entity) {
        try {
            return DeleteBuilder.deleteEntity(entity, env());
        } finally {
            SelectBuilder.resetAliasSeq();
        }
    }

    /**
     * 批量实体删除
     */
    public <T> int delete(java.util.Collection<T> entities) {
        if (entities == null || entities.isEmpty()) return 0;
        return deleteBatch(entities).execute();
    }

    /**
     * 开启批处理实体删除构建器
     */
    public <T> com.javaorm.core.batch.BatchDeleteBuilder<T> deleteBatch(java.util.Collection<T> entities) {
        return new com.javaorm.core.batch.BatchDeleteBuilder<>(env(), entities);
    }

    /**
     * 根据主键 ID 删除
     */
    public <T> int deleteById(Class<T> entityClass, Object id) {
        try {
            return DeleteBuilder.deleteById(entityClass, id, env());
        } finally {
            SelectBuilder.resetAliasSeq();
        }
    }

    /**
     * 批量根据主键 ID 删除
     */
    public <T> int deleteByIds(Class<T> entityClass, java.util.Collection<?> ids) {
        if (ids == null || ids.isEmpty()) return 0;
        return deleteByIdsBuilder(entityClass, ids).execute();
    }

    /**
     * 开启批处理 ID 删除构建器
     */
    public <T> com.javaorm.core.batch.BatchDeleteBuilder<T> deleteByIdsBuilder(Class<T> entityClass, java.util.Collection<?> ids) {
        return new com.javaorm.core.batch.BatchDeleteBuilder<>(env(), entityClass, ids);
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


    public DataSource getDataSource() {
        return dataSource;
    }

    public Dialect getDialect() {
        return dialect;
    }

    public OrmConfig getConfig() {
        return config;
    }

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
    /**
     * 创建基于当前 ORM 实例的声明式 SQL 执行门面入口，支持直接链式调用：
     * <pre>
     * List<MatrixOrderDto> orders = orm.sql().selectAutoInclude(MatrixOrderDto.class).list();
     * </pre>
     */
    public SqlExecution<Object> sql() {
        SqlEnv env = env();
        return new SqlExecution<>(env, null);
    }

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
            SelectBuilder.resetAliasSeq();
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
            SelectBuilder.resetAliasSeq();
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
    public <T> SqlExecution<T> sql(Function<SqlEnv, ? extends Query<T>> envFunction) {
        if (envFunction == null) {
            throw new IllegalArgumentException("SqlEnv function must not be null");
        }
        SqlEnv env = env();
        try {
            Query<T> returned = envFunction.apply(env);
            return env.toExecutionFromReturn(returned);
        } finally {
            com.javaorm.core.proxy.RecordingContext.clearRecording();
            com.javaorm.core.proxy.RecordingContext.clearAliases();
            SelectBuilder.resetAliasSeq();
        }
    }

    /**
     * 获取全局变脏追踪管理器
     */
    public com.javaorm.core.track.TrackManager getTrackManager() {
        return this.config.getTrackManager();
    }

    /**
     * 将实体纳入快照追踪（拍摄当前状态快照）
     */
    public <T> T attach(T entity) {
        if (entity != null && this.config.getTrackManager() != null) {
            this.config.getTrackManager().attach(entity, this.config);
        }
        return entity;
    }

    /**
     * 批量将实体纳入快照追踪
     */
    public <T> void attachAll(Iterable<T> entities) {
        if (entities != null && this.config.getTrackManager() != null) {
            for (T entity : entities) {
                if (entity != null) {
                    this.config.getTrackManager().attach(entity, this.config);
                }
            }
        }
    }

    /**
     * 从追踪管理器中移除实体
     */
    public void detach(Object entity) {
        if (entity != null && this.config.getTrackManager() != null) {
            this.config.getTrackManager().detach(entity);
        }
    }

    /**
     * 清空所有被追踪的实体快照
     */
    public void clearTrack() {
        if (this.config.getTrackManager() != null) {
            this.config.getTrackManager().clear();
        }
    }

    /**
     * 获取指定实体的快照对象
     */
    public com.javaorm.core.track.EntitySnapshot getSnapshot(Object entity) {
        if (entity != null && this.config.getTrackManager() != null) {
            return this.config.getTrackManager().getSnapshot(entity);
        }
        return null;
    }

    /**
     * 判断实体是否被追踪
     */
    public boolean isTracking(Object entity) {
        if (entity != null && this.config.getTrackManager() != null) {
            return this.config.getTrackManager().isTracking(entity);
        }
        return false;
    }

    /**
     * 判断实体自快照以来是否发生了属性变更（变脏）
     */
    public boolean isDirty(Object entity) {
        if (entity != null && this.config.getTrackManager() != null) {
            return this.config.getTrackManager().isDirty(entity);
        }
        return false;
    }

    /**
     * 获取实体自快照以来发生变脏的属性名列表
     */
    public java.util.List<String> getDirtyProperties(Object entity) {
        if (entity != null && this.config.getTrackManager() != null) {
            return this.config.getTrackManager().getDirtyProperties(entity);
        }
        return java.util.Collections.emptyList();
    }

    /**
     * 获取实体自快照以来发生变脏的数据库列与最新值映射（以列名为键）
     */
    public java.util.Map<String, Object> getDirtyColumns(Object entity) {
        if (entity != null && this.config.getTrackManager() != null) {
            return this.config.getTrackManager().getDirtyColumns(entity);
        }
        return java.util.Collections.emptyMap();
    }

    /**
     * 获取实体自快照以来发生变脏的数据库列名集合
     */
    public java.util.Set<String> getDirtyColumnNames(Object entity) {
        if (entity != null && this.config.getTrackManager() != null) {
            return this.config.getTrackManager().getDirtyColumnNames(entity);
        }
        return java.util.Collections.emptySet();
    }

    /**
     * 获取读写分离管理器
     */
    public com.javaorm.core.datasource.MasterSlaveManager getMasterSlaveManager() {
        return this.config.getMasterSlaveManager();
    }

    /**
     * 配置读写分离主从路由器（链式调用）
     */
    public JavaOrm masterSlave(Consumer<com.javaorm.core.datasource.MasterSlaveManager> consumer) {
        if (this.config.getMasterSlaveManager() == null) {
            this.config.setMasterSlaveManager(new com.javaorm.core.datasource.MasterSlaveManager(this.dataSource));
        }
        if (consumer != null) {
            consumer.accept(this.config.getMasterSlaveManager());
        }
        return this;
    }

    /**
     * 读写分离配置别名（readWrite）
     */
    public JavaOrm readWrite(Consumer<com.javaorm.core.datasource.MasterSlaveManager> consumer) {
        return masterSlave(consumer);
    }

    /**
     * 在强制使用 Master 主库的上下文中执行操作（带返回值）
     */
    public <R> R useMaster(java.util.function.Supplier<R> action) {
        return com.javaorm.core.datasource.ReadWriteContext.useMaster(action);
    }

    /**
     * 在强制使用 Master 主库的上下文中执行操作（无返回值）
     */
    public void useMaster(Runnable action) {
        com.javaorm.core.datasource.ReadWriteContext.useMaster(action);
    }

    /**
     * 在强制使用 Slave 从库的上下文中执行操作（带返回值）
     */
    public <R> R useSlave(java.util.function.Supplier<R> action) {
        return com.javaorm.core.datasource.ReadWriteContext.useSlave(action);
    }

    /**
     * 在强制使用 Slave 从库的上下文中执行操作（无返回值）
     */
    public void useSlave(Runnable action) {
        com.javaorm.core.datasource.ReadWriteContext.useSlave(action);
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
