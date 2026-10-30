package com.javaorm.core.query;

import com.javaorm.core.ast.*;
import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.dialect.Dialect;
import com.javaorm.core.metadata.ColumnMetadata;
import com.javaorm.core.metadata.EntityMetadata;
import com.javaorm.core.metadata.EntityMetadataManager;
import com.javaorm.core.metadata.RelationMetadata;
import com.javaorm.core.operator.SqlOperator;
import com.javaorm.core.proxy.PropertyPath;
import com.javaorm.core.proxy.ProxyFactory;
import com.javaorm.core.proxy.RecordingContext;

import com.javaorm.core.sql.SqlEnv;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 核心 Fluent 查询构建器。
 */
public class SelectBuilder<T> implements Query<T> {

    private final SqlEnv env;
    private final Class<T> rootEntityClass;
    private final EntityMetadata rootMetadata;
    private Cte<T> rootCte;
    private Dialect dialectOverride = null;

    private final SelectStatement statement;
    private final Map<String, String> relationJoinAliases = new LinkedHashMap<>();
    private final List<IncludeSpec> includes = new ArrayList<>();
    private int aliasSeq = 0;

    public List<IncludeSpec> getIncludeSpecs() {
        return Collections.unmodifiableList(includes);
    }

    public SelectBuilder<T> addIncludeSpec(IncludeSpec spec) {
        if (spec != null) {
            includes.add(spec);
        }
        return this;
    }

    public String nextAlias() {
        String alias = "t" + (aliasSeq++);
        if (statement != null && alias.equalsIgnoreCase(statement.getRootTableAlias())) {
            alias = "t" + (aliasSeq++);
        }
        return alias;
    }

    public static void resetAliasSeq() {
        RecordingContext.clearAliases();
    }

    public SqlEnv getEnv() {
        return env;
    }

    @Override
    public Class<T> getResultClass() {
        return rootEntityClass;
    }

    public DataSource getDataSource() {
        return env.getDataSource();
    }

    public Dialect getDialect() {
        return dialectOverride != null ? dialectOverride : env.getDialect();
    }

    public OrmConfig getConfig() {
        return env.getConfig();
    }

    public SelectBuilder(SqlEnv env, Class<T> rootEntityClass) {
        this(env, rootEntityClass, true);
    }

    public SelectBuilder(SqlEnv env, Class<T> rootEntityClass, boolean allocateAlias) {
        this.env = env != null ? env : new SqlEnv(null, null, null);
        this.rootEntityClass = rootEntityClass;
        this.rootMetadata = this.env.getConfig() != null ? this.env.getConfig().getMetadata(rootEntityClass) : EntityMetadataManager.get(rootEntityClass, this.env.getConfig());

        if (!allocateAlias) {
            this.statement = new SelectStatement(rootEntityClass, rootMetadata != null ? rootMetadata.getTableName() : null, null);
            return;
        }

        String rootAlias = nextAlias();
        this.statement = new SelectStatement(rootEntityClass, rootMetadata != null ? rootMetadata.getTableName() : null, rootAlias);
        RecordingContext.registerAlias(rootEntityClass, rootAlias);

        injectSystemConditions(rootAlias);
    }

    public SelectBuilder(SqlEnv env, Class<T> rootEntityClass, String rootAlias) {
        this.env = env != null ? env : new SqlEnv(null, null, null);
        this.rootEntityClass = rootEntityClass;
        this.rootMetadata = this.env.getConfig() != null ? this.env.getConfig().getMetadata(rootEntityClass) : EntityMetadataManager.get(rootEntityClass, this.env.getConfig());

        if (rootAlias == null) {
            rootAlias = nextAlias();
        } else {
            if (rootAlias.startsWith("t")) {
                try {
                    int num = Integer.parseInt(rootAlias.substring(1));
                    if (aliasSeq <= num) {
                        aliasSeq = num + 1;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        this.statement = new SelectStatement(rootEntityClass, rootMetadata != null ? rootMetadata.getTableName() : null, rootAlias);
        RecordingContext.registerAlias(rootEntityClass, rootAlias);

        injectSystemConditions(rootAlias);
    }

    public SelectBuilder(SqlEnv env, Cte<T> cte) {
        if (cte == null) {
            throw new IllegalArgumentException("CTE must not be null");
        }
        this.env = env != null ? env : new SqlEnv(null, null, null);
        this.rootCte = cte;
        this.rootEntityClass = cte.getEntityClass();
        this.rootMetadata = rootEntityClass != null ? EntityMetadataManager.get(rootEntityClass, this.env.getConfig()) : null;
        String rootAlias = cte.getAlias() != null ? cte.getAlias() : nextAlias();
        this.statement = new SelectStatement(rootEntityClass, cte.getName(), rootAlias);
        this.statement.addCte(cte.toClause());
        if (rootEntityClass != null) {
            RecordingContext.registerAlias(rootEntityClass, rootAlias);
        }
    }

    public SelectBuilder(Class<T> rootEntityClass, DataSource dataSource, Dialect dialect, OrmConfig config) {
        this(new SqlEnv(dataSource, dialect, config), rootEntityClass, true);
    }

    public SelectBuilder(Class<T> rootEntityClass, DataSource dataSource, Dialect dialect, OrmConfig config, boolean allocateAlias) {
        this(new SqlEnv(dataSource, dialect, config), rootEntityClass, allocateAlias);
    }

    public SelectBuilder(Class<T> rootEntityClass, DataSource dataSource, Dialect dialect, OrmConfig config, String rootAlias) {
        this(new SqlEnv(dataSource, dialect, config), rootEntityClass, rootAlias);
    }

    public SelectBuilder(Cte<T> cte, DataSource dataSource, Dialect dialect, OrmConfig config) {
        this(new SqlEnv(dataSource, dialect, config), cte);
    }

    private void injectSystemConditions(String rootAlias) {
        // 默认自动注入逻辑删除系统过滤条件
        if (rootMetadata != null && rootMetadata.hasLogicDelete()) {
            ColumnMetadata ld = rootMetadata.getLogicDeleteColumn();
            statement.addSystemCondition(new BinaryCondition(rootAlias, ld.getColumnName(), SqlOperator.EQ, ld.getLogicDeleteNormalValue()));
        }

        // 默认自动注入多租户隔离系统过滤条件
        if (rootMetadata != null && rootMetadata.hasTenant()) {
            ColumnMetadata tc = rootMetadata.getTenantColumn();
            Object tenantId = env.getConfig() != null ? env.getConfig().getCurrentTenantId() : com.javaorm.core.tenant.TenantContext.getTenantId();
            if (tenantId != null) {
                statement.addSystemCondition(new BinaryCondition(rootAlias, tc.getColumnName(), SqlOperator.EQ, tenantId));
            } else if (env.getConfig() != null && env.getConfig().isTenantStrict()) {
                throw new IllegalStateException("Tenant context is required for multi-tenant entity: " + rootEntityClass.getName());
            }
        }
    }

    public Cte<T> getRootCte() {
        return rootCte;
    }

    public Class<T> getRootEntityClass() {
        return rootEntityClass;
    }

    /**
     * 链式配置：本次执行按照当前全局配置决定是否打印 SQL
     */
    public SelectBuilder<T> printSql() {
        env.setPrintSql(env.getConfig() != null && env.getConfig().isPrintSql());
        return this;
    }

    /**
     * 链式配置：本次执行强制指定是否打印 SQL（忽略全局配置）
     */
    public SelectBuilder<T> printSql(boolean print) {
        env.setPrintSql(print);
        return this;
    }

    public OrmConfig getEffectiveConfig() {
        return env.getEffectiveConfig();
    }

    public SelectBuilder<T> setDialect(Dialect dialect) {
        this.dialectOverride = dialect;
        return this;
    }

    /**
     * 随时获取当前生成的 SQL 及参数对象
     */
    public com.javaorm.core.dialect.SqlAndParams toSqlAndParams() {
        return getDialect().buildSelect(statement);
    }

    public com.javaorm.core.dialect.SqlAndParams toSqlAndParams(Dialect customDialect) {
        return customDialect.buildSelect(statement);
    }

    /**
     * 随时获取当前生成的 SQL 字符串
     */
    public String toSql() {
        return toSqlAndParams().getSql();
    }

    public String toSql(Dialect customDialect) {
        return toSqlAndParams(customDialect).getSql();
    }

    public String toSQL() {
        return toSql();
    }

    public String toSQL(Dialect customDialect) {
        return toSql(customDialect);
    }

    @Override
    public String toString() {
        return toSql();
    }

    public ColumnExpression stringToColumnExpression(String str) {
        if (str == null) return null;
        str = str.trim();
        String alias = null;
        String colPart = str;
        String[] parts = str.split(" (?i)as ");
        if (parts.length == 2) {
            colPart = parts[0].trim();
            alias = parts[1].trim();
        } else {
            int spaceIdx = str.lastIndexOf(' ');
            if (spaceIdx > 0 && !str.contains("(")) {
                colPart = str.substring(0, spaceIdx).trim();
                alias = str.substring(spaceIdx + 1).trim();
            }
        }

        String tableAlias;
        String colName;
        if (colPart.contains(".")) {
            int dot = colPart.lastIndexOf('.');
            tableAlias = colPart.substring(0, dot);
            colName = colPart.substring(dot + 1);
            boolean isRoot = tableAlias.equalsIgnoreCase(statement.getRootTableAlias())
                    || (rootEntityClass != null && tableAlias.equalsIgnoreCase(rootEntityClass.getSimpleName()))
                    || (rootMetadata != null && tableAlias.equalsIgnoreCase(rootMetadata.getTableName()))
                    || (statement.getRootTableName() != null && tableAlias.equalsIgnoreCase(statement.getRootTableName()))
                    || statement.getJoins().isEmpty();
            if (isRoot) {
                tableAlias = statement.getRootTableAlias();
            } else {
                for (TableJoin join : statement.getJoins()) {
                    if (tableAlias.equalsIgnoreCase(join.getTableName())) {
                        tableAlias = join.getTableAlias();
                        break;
                    }
                }
            }
        } else {
            tableAlias = statement.getRootTableAlias();
            colName = colPart;
        }
        if (rootMetadata != null) {
            ColumnMetadata cm = rootMetadata.getColumnByProperty(colName);
            if (cm != null) {
                colName = cm.getColumnName();
            } else {
                ColumnMetadata dbCol = rootMetadata.getColumnByDbName(colName);
                if (dbCol != null) {
                    colName = dbCol.getColumnName();
                } else if (getConfig() != null && getConfig().getNameConversion() != null) {
                    colName = getConfig().getNameConversion().convert(colName);
                }
            }
        }
        ColumnExpression ce = new ColumnExpression(tableAlias, colName);
        if (alias != null && !alias.isEmpty()) {
            ce.as(alias);
        }
        return ce;
    }

    public EntityMetadata getRootMetadata() {
        return rootMetadata;
    }

    /**
     * 获取当前实体类型的录制代理对象
     */
    public T proxy() {
        return ProxyFactory.getProxy(rootEntityClass);
    }

    /**
     * 自定义字段投影查询
     */
    public SelectBuilder<T> select(Object... expressions) {
        statement.getProjectExpressions().clear();
        if (expressions != null && expressions.length > 0) {
            List<PropertyPath> paths = RecordingContext.finishAll(rootEntityClass);
            int pathIdx = 0;
            for (Object item : expressions) {
                SqlExpression expr;
                if (item instanceof WindowOrderByBuilder) {
                    expr = ((WindowOrderByBuilder) item).getWindowExpr();
                } else if (item instanceof SqlExpression) {
                    expr = (SqlExpression) item;
                } else if (pathIdx < paths.size()) {
                    expr = pathToColumnExpression(paths.get(pathIdx++));
                } else {
                    PropertyPath p = RecordingContext.finish(rootEntityClass);
                    if (p != null) {
                        expr = pathToColumnExpression(p);
                    } else if (item instanceof String) {
                        expr = stringToColumnExpression((String) item);
                    } else if (item != null) {
                        expr = new RawSqlExpression("?", item);
                    } else {
                        continue;
                    }
                }
                resolveExpression(expr);
                statement.addProjectExpression(expr);
            }
        }
        return this;
    }

    /**
     * 绑定源表，转为 DTO 投影查询（例如 orm.select(StatDto.class).from(Student.class)...）
     */
    public <R> DtoQuery<T, R> from(Class<R> fromEntityClass) {
        return new DtoQuery<>(this.env, this.rootEntityClass, fromEntityClass);
    }

    public <R> DtoQuery<T, R> from(Cte<R> cte) {
        return new DtoQuery<>(this.env, this.rootEntityClass, cte);
    }

    /**
     * 开启 WHERE 条件构建块
     */
    public WhereBuilder<T> where() {
        return new WhereBuilder<>(this);
    }

    /**
     * 作用域 WHERE 块：orm.select(Student.class).where(w -> { w.and(...); })
     */
    public SelectBuilder<T> where(Consumer<WhereBuilder<T>> consumer) {
        consumer.accept(where());
        return this;
    }

    public SelectBuilder<T> where(boolean condition, Consumer<WhereBuilder<T>> consumer) {
        if (condition && consumer != null) {
            consumer.accept(where());
        }
        return this;
    }

    /**
     * 双参数作用域 WHERE 块：orm.select(Student.class).where((w, s) -> { w.and(s.getAge()).gt(18); })
     */
    public SelectBuilder<T> where(WhereBuilder.BiWhereConsumer<T> consumer) {
        WhereBuilder<T> w = where();
        consumer.accept(w, w.proxy());
        return this;
    }

    public SelectBuilder<T> where(boolean condition, WhereBuilder.BiWhereConsumer<T> consumer) {
        if (condition && consumer != null) {
            WhereBuilder<T> w = where();
            consumer.accept(w, w.proxy());
        }
        return this;
    }

    public SelectBuilder<T> whereById(Object id) {
        return whereById(true, id);
    }

    public SelectBuilder<T> whereById(boolean condition, Object id) {
        if (!condition) return this;
        ColumnMetadata idCol = rootMetadata != null ? rootMetadata.getIdColumn() : null;
        String idProp = idCol != null ? idCol.getPropertyName() : "id";
        where().eq(idProp, id);
        return this;
    }

    public SelectBuilder<T> whereByIds(Collection<?> ids) {
        return whereByIds(true, ids);
    }

    public SelectBuilder<T> whereByIds(boolean condition, Collection<?> ids) {
        if (!condition) return this;
        ColumnMetadata idCol = rootMetadata != null ? rootMetadata.getIdColumn() : null;
        String idProp = idCol != null ? idCol.getPropertyName() : "id";
        where().in(idProp, (Collection) ids);
        return this;
    }

    @SafeVarargs
    public final SelectBuilder<T> whereByIds(Object... ids) {
        if (ids != null && ids.length == 1 && ids[0] instanceof Collection) {
            return whereByIds((Collection<?>) ids[0]);
        }
        return whereByIds(ids != null ? Arrays.asList(ids) : Collections.emptyList());
    }

    @SafeVarargs
    public final SelectBuilder<T> whereByIds(boolean condition, Object... ids) {
        if (!condition) return this;
        return whereByIds(ids);
    }

    public SelectBuilder<T> whereObject(Object request) {
        return whereObject(true, request);
    }

    public SelectBuilder<T> whereObject(boolean condition, Object request) {
        if (!condition || request == null) return this;
        where(w -> w.whereObject(request));
        return this;
    }

    public SelectBuilder<T> disableLogicDelete() {
        statement.getSystemConditions().removeIf(c -> {
            if (c instanceof BinaryCondition) {
                BinaryCondition bc = (BinaryCondition) c;
                ColumnMetadata ld = rootMetadata != null ? rootMetadata.getLogicDeleteColumn() : null;
                return ld != null && ld.getColumnName().equalsIgnoreCase(bc.getColumnName());
            }
            return false;
        });
        return this;
    }

    /**
     * 直接单条件快速断言：orm.select(Student.class).where(s.getId(), SqlOperator.EQ, 1L)
     */
    public SelectBuilder<T> where(SqlExpression expr, SqlOperator op, Object... values) {
        where().and(expr).op(op, values);
        return this;
    }

    public SelectBuilder<T> where(Object prop, SqlOperator op, Object... values) {
        if (prop instanceof SqlExpression) {
            return where((SqlExpression) prop, op, values);
        }
        where().and(prop).op(op, values);
        return this;
    }

    /**
     * 分组查询 GROUP BY
     */
    public SelectBuilder<T> groupBy(Object... expressions) {
        if (expressions != null && expressions.length > 0) {
            List<PropertyPath> paths = RecordingContext.finishAll(rootEntityClass);
            int pathIdx = 0;
            for (Object item : expressions) {
                SqlExpression expr;
                if (item instanceof SqlExpression) {
                    expr = (SqlExpression) item;
                } else if (pathIdx < paths.size()) {
                    expr = pathToColumnExpression(paths.get(pathIdx++));
                } else {
                    PropertyPath p = RecordingContext.finish(rootEntityClass);
                    if (p != null) {
                        expr = pathToColumnExpression(p);
                    } else if (item instanceof String) {
                        expr = stringToColumnExpression((String) item);
                    } else if (item != null) {
                        expr = new RawSqlExpression("?", item);
                    } else {
                        continue;
                    }
                }
                resolveExpression(expr);
                statement.addGroupByExpression(expr);
            }
        }
        return this;
    }

    /**
     * HAVING 过滤条件块
     */
    public HavingBuilder<T> having() {
        return new HavingBuilder<>(this);
    }

    public SelectBuilder<T> having(Consumer<HavingBuilder<T>> consumer) {
        consumer.accept(having());
        return this;
    }

    /**
     * 级联子表作用域过滤
     */
    public <TSub> SelectBuilder<T> whereRelation(Function<T, TSub> relationGetter, Consumer<WhereBuilder<TSub>> subBuilderConsumer) {
        RecordingContext.start(rootEntityClass);
        try {
            T proxy = ProxyFactory.getProxy(rootEntityClass);
            relationGetter.apply(proxy);
            PropertyPath path = RecordingContext.finish();
            if (path != null) {
                String joinAlias = resolveJoinAlias(path);
                Class<?> subEntityClass = resolveTargetClass(path);
                if (subEntityClass != null) {
                    @SuppressWarnings("unchecked")
                    Class<TSub> typedSubClass = (Class<TSub>) subEntityClass;
                    WhereBuilder<TSub> subBuilder = new WhereBuilder<>(typedSubClass, this, joinAlias);
                    subBuilderConsumer.accept(subBuilder);
                }
            }
        } finally {
            RecordingContext.clearRecording();
        }
        return this;
    }

    /**
     * 显式关联 JOIN（基于导航关系自动推导 ON 条件）
     */
    public <TJoin> SelectBuilder<T> leftJoin(Function<T, TJoin> relationGetter) {
        RecordingContext.start(rootEntityClass);
        try {
            T proxy = ProxyFactory.getProxy(rootEntityClass);
            relationGetter.apply(proxy);
            PropertyPath path = RecordingContext.finish();
            if (path != null) {
                resolveJoinAlias(path);
            }
        } finally {
            RecordingContext.clearRecording();
        }
        return this;
    }

    /**
     * 将当前查询构建器封装为强类型 CTE
     */
    public Cte<T> asCte(String name) {
        return new Cte<>(name, rootEntityClass, this, false);
    }

    public Cte<?> asDynamicCte(String name) {
        return new Cte<>(name, null, this, false);
    }

    public <C> Cte<C> asCte(String name, Class<C> cteClass) {
        return new Cte<>(name, cteClass, this, false);
    }

    /**
     * 将当前查询构建器封装为强类型递归 CTE
     */
    public Cte<T> asCteRecursive(String name) {
        return new Cte<>(name, rootEntityClass, this, true);
    }

    public Cte<?> asDynamicCteRecursive(String name) {
        return new Cte<>(name, null, this, true);
    }

    public <C> Cte<C> asCteRecursive(String name, Class<C> cteClass) {
        return new Cte<>(name, cteClass, this, true);
    }

    /**
     * 附加通用表表达式 CTE
     */
    public SelectBuilder<T> with(Cte<?> cte) {
        if (cte != null) {
            statement.addCte(cte.toClause());
        }
        return this;
    }

    public SelectBuilder<T> with(String name, Object query) {
        statement.addCte(new CteClause(name, query));
        return this;
    }

    public SelectBuilder<T> with(String name, Class<?> cteClass, Object query) {
        statement.addCte(new CteClause(name, query, false, null, cteClass));
        return this;
    }

    public SelectBuilder<T> withRecursive(String name, Object query) {
        statement.addCte(new CteClause(name, query, true));
        return this;
    }

    public SelectBuilder<T> withRecursive(String name, Class<?> cteClass, Object query) {
        statement.addCte(new CteClause(name, query, true, null, cteClass));
        return this;
    }

    public SelectBuilder<T> ensureCteAdded(Cte<?> cte) {
        if (cte == null) return this;
        for (CteClause existing : statement.getCtes()) {
            if (existing.getName().equalsIgnoreCase(cte.getName())) {
                return this;
            }
        }
        statement.addCte(cte.toClause());
        return this;
    }

    private boolean isAliasUsed(String alias) {
        if (alias == null) return false;
        if (statement != null && alias.equalsIgnoreCase(statement.getRootTableAlias())) return true;
        if (statement != null) {
            for (TableJoin join : statement.getJoins()) {
                if (alias.equalsIgnoreCase(join.getTableAlias())) return true;
            }
        }
        return false;
    }

    private String obtainJoinAlias(Class<?> entityClass) {
        String joinAlias = (entityClass != null) ? RecordingContext.getAlias(entityClass) : null;
        if (joinAlias == null || isAliasUsed(joinAlias)) {
            joinAlias = nextAlias();
            if (entityClass != null) {
                RecordingContext.registerAlias(entityClass, joinAlias);
            }
        }
        return joinAlias;
    }

    // --- CTE 连接支持 ---
    public <TJoin> JoinBuilder<T, TJoin> leftJoin(Cte<TJoin> cte) {
        String joinAlias = obtainJoinAlias(cte != null ? cte.getEntityClass() : null);
        return new JoinBuilder<>(this, TableJoin.JoinType.LEFT_JOIN, cte.getName(), cte.getEntityClass(), joinAlias, cte);
    }

    public <TJoin> SelectBuilder<T> leftJoin(Cte<TJoin> cte, TriConsumer<WhereBuilder<TJoin>, T, TJoin> onConsumer) {
        return leftJoin(cte).on(onConsumer);
    }

    public <TJoin> JoinBuilder<T, TJoin> innerJoin(Cte<TJoin> cte) {
        String joinAlias = obtainJoinAlias(cte != null ? cte.getEntityClass() : null);
        return new JoinBuilder<>(this, TableJoin.JoinType.INNER_JOIN, cte.getName(), cte.getEntityClass(), joinAlias, cte);
    }

    public <TJoin> SelectBuilder<T> innerJoin(Cte<TJoin> cte, TriConsumer<WhereBuilder<TJoin>, T, TJoin> onConsumer) {
        return innerJoin(cte).on(onConsumer);
    }

    public <TJoin> JoinBuilder<T, TJoin> rightJoin(Cte<TJoin> cte) {
        String joinAlias = obtainJoinAlias(cte != null ? cte.getEntityClass() : null);
        return new JoinBuilder<>(this, TableJoin.JoinType.RIGHT_JOIN, cte.getName(), cte.getEntityClass(), joinAlias, cte);
    }

    public <TJoin> SelectBuilder<T> rightJoin(Cte<TJoin> cte, TriConsumer<WhereBuilder<TJoin>, T, TJoin> onConsumer) {
        return rightJoin(cte).on(onConsumer);
    }

    // --- 实体表连接支持 ---
    public <TJoin> JoinBuilder<T, TJoin> leftJoin(Class<TJoin> entityClass) {
        EntityMetadata meta = EntityMetadataManager.get(entityClass, getConfig());
        String joinAlias = obtainJoinAlias(entityClass);
        return new JoinBuilder<>(this, TableJoin.JoinType.LEFT_JOIN, meta.getTableName(), entityClass, joinAlias, null);
    }

    public <TJoin> SelectBuilder<T> leftJoin(Class<TJoin> entityClass, TriConsumer<WhereBuilder<TJoin>, T, TJoin> onConsumer) {
        return leftJoin(entityClass).on(onConsumer);
    }

    public <TJoin> JoinBuilder<T, TJoin> innerJoin(Class<TJoin> entityClass) {
        EntityMetadata meta = EntityMetadataManager.get(entityClass, getConfig());
        String joinAlias = obtainJoinAlias(entityClass);
        return new JoinBuilder<>(this, TableJoin.JoinType.INNER_JOIN, meta.getTableName(), entityClass, joinAlias, null);
    }

    public <TJoin> SelectBuilder<T> innerJoin(Class<TJoin> entityClass, TriConsumer<WhereBuilder<TJoin>, T, TJoin> onConsumer) {
        return innerJoin(entityClass).on(onConsumer);
    }

    public <TJoin> JoinBuilder<T, TJoin> rightJoin(Class<TJoin> entityClass) {
        EntityMetadata meta = EntityMetadataManager.get(entityClass, getConfig());
        String joinAlias = obtainJoinAlias(entityClass);
        return new JoinBuilder<>(this, TableJoin.JoinType.RIGHT_JOIN, meta.getTableName(), entityClass, joinAlias, null);
    }

    public <TJoin> SelectBuilder<T> rightJoin(Class<TJoin> entityClass, TriConsumer<WhereBuilder<TJoin>, T, TJoin> onConsumer) {
        return rightJoin(entityClass).on(onConsumer);
    }

    /**
     * 声明显式装配关联对象（避免笛卡尔积，采用 Split IN 查询，支持各级独立筛选与多层级嵌套）
     */
    public SelectBuilder<T> include(Function<T, ?> relationGetter) {
        RecordingContext.start(rootEntityClass);
        try {
            T proxy = ProxyFactory.getProxy(rootEntityClass);
            relationGetter.apply(proxy);
            PropertyPath path = RecordingContext.finish();
            if (path != null) {
                includes.add(new IncludeSpec(path));
            }
        } finally {
            RecordingContext.clearRecording();
        }
        return this;
    }

    @SuppressWarnings("unchecked")
    public <R> SelectBuilder<T> include(Function<T, ?> relationGetter, Consumer<SelectBuilder<R>> config) {
        RecordingContext.start(rootEntityClass);
        try {
            T proxy = ProxyFactory.getProxy(rootEntityClass);
            relationGetter.apply(proxy);
            PropertyPath path = RecordingContext.finish();
            if (path != null) {
                includes.add(new IncludeSpec(path, (Consumer<SelectBuilder<?>>) (Consumer<?>) config));
            }
        } finally {
            RecordingContext.clearRecording();
        }
        return this;
    }

    public <R> SelectBuilder<T> include(Function<T, ?> relationGetter, BiConsumer<SelectBuilder<R>, R> biConfig) {
        RecordingContext.start(rootEntityClass);
        try {
            T proxy = ProxyFactory.getProxy(rootEntityClass);
            relationGetter.apply(proxy);
            PropertyPath path = RecordingContext.finish();
            if (path != null) {
                includes.add(IncludeSpec.ofBi(path, biConfig));
            }
        } finally {
            RecordingContext.clearRecording();
        }
        return this;
    }

    public <R> SelectBuilder<T> include(R relationProxy) {
        PropertyPath path = RecordingContext.finish();
        if (path != null) {
            includes.add(new IncludeSpec(path));
        }
        return this;
    }

    @SuppressWarnings("unchecked")
    public <R> SelectBuilder<T> include(R relationProxy, Consumer<SelectBuilder<R>> config) {
        PropertyPath path = RecordingContext.finish();
        if (path != null) {
            includes.add(new IncludeSpec(path, (Consumer<SelectBuilder<?>>) (Consumer<?>) config));
        }
        return this;
    }

    public <R> SelectBuilder<T> include(R relationProxy, BiConsumer<SelectBuilder<R>, R> biConfig) {
        PropertyPath path = RecordingContext.finish();
        if (path != null) {
            includes.add(IncludeSpec.ofBi(path, biConfig));
        }
        return this;
    }

    public <E> SelectBuilder<T> include(Collection<E> relationProxy) {
        PropertyPath path = RecordingContext.finish();
        if (path != null) {
            includes.add(new IncludeSpec(path));
        }
        return this;
    }

    @SuppressWarnings("unchecked")
    public <E> SelectBuilder<T> include(Collection<E> relationProxy, Consumer<SelectBuilder<E>> config) {
        PropertyPath path = RecordingContext.finish();
        if (path != null) {
            includes.add(new IncludeSpec(path, (Consumer<SelectBuilder<?>>) (Consumer<?>) config));
        }
        return this;
    }

    public <E> SelectBuilder<T> include(Collection<E> relationProxy, BiConsumer<SelectBuilder<E>, E> biConfig) {
        PropertyPath path = RecordingContext.finish();
        if (path != null) {
            includes.add(IncludeSpec.ofBi(path, biConfig));
        }
        return this;
    }

    /**
     * 显式重命名当前查询的根表别名（用于子查询或自连接消除歧义）
     */
    public SelectBuilder<T> as(String alias) {
        if (alias != null && !alias.isEmpty()) {
            String oldAlias = statement.getRootTableAlias();
            statement.setRootTableAlias(alias);
            RecordingContext.registerAlias(rootEntityClass, alias);
            shiftRootTableAliasTo(oldAlias, alias);
        }
        return this;
    }

    private void shiftRootTableAliasTo(String oldAlias, String newAlias) {
        for (SqlExpression expr : statement.getProjectExpressions()) {
            updateExpressionTableAlias(expr, oldAlias, newAlias);
        }
        updateConditionsTableAlias(statement.getSystemConditions(), oldAlias, newAlias);
        updateConditionsTableAlias(statement.getWhereConditions(), oldAlias, newAlias);
        for (TableJoin join : statement.getJoins()) {
            updateConditionsTableAlias(join.getOnConditions(), oldAlias, newAlias);
        }
        for (SqlExpression expr : statement.getGroupByExpressions()) {
            updateExpressionTableAlias(expr, oldAlias, newAlias);
        }
        updateConditionsTableAlias(statement.getHavingConditions(), oldAlias, newAlias);
        for (OrderBy ob : statement.getOrderBys()) {
            if (ob.getExpression() != null) {
                updateExpressionTableAlias(ob.getExpression(), oldAlias, newAlias);
            }
        }
    }

    public <R> OrderByBuilder<SelectBuilder<T>> orderBy(Function<T, R> propertyGetter) {
        PropertyPath path = recordPath(propertyGetter);
        String alias = resolveTableAlias(path);
        String colName = resolveColumnName(path);
        return new OrderByBuilder<>(this, statement, alias, colName);
    }

    public <R> OrderByBuilder<SelectBuilder<T>> orderBy(R val) {
        if (val instanceof SqlExpression) {
            return orderBy((SqlExpression) val);
        }
        PropertyPath path = RecordingContext.finish();
        if (path == null) {
            if (val instanceof String) {
                return orderBy((String) val);
            }
            throw new IllegalStateException("No proxy property accessed before calling orderBy(...)");
        }
        String alias = resolveTableAlias(path);
        String colName = resolveColumnName(path);
        return new OrderByBuilder<>(this, statement, alias, colName);
    }

    public OrderByBuilder<SelectBuilder<T>> orderBy(String propertyOrColumn) {
        PropertyPath path = RecordingContext.finish();
        if (path != null) {
            String alias = resolveTableAlias(path);
            String colName = resolveColumnName(path);
            return new OrderByBuilder<>(this, statement, alias, colName);
        }
        if (propertyOrColumn == null) {
            throw new IllegalArgumentException("Order by column cannot be null");
        }
        ColumnExpression ce = stringToColumnExpression(propertyOrColumn);
        return new OrderByBuilder<>(this, statement, ce.getTableAlias(), ce.getColumnName());
    }

    public OrderByBuilder<SelectBuilder<T>> orderBy(SqlExpression<?> expr) {
        resolveExpression(expr);
        return new OrderByBuilder<>(this, statement, expr);
    }

    public <R> SelectBuilder<T> orderByAsc(R val) {
        orderBy(val).asc();
        return this;
    }

    public <R> SelectBuilder<T> orderByDesc(R val) {
        orderBy(val).desc();
        return this;
    }

    public <R> SelectBuilder<T> orderBy(R val, boolean isAsc) {
        orderBy(val).sort(isAsc);
        return this;
    }

    public <R> SelectBuilder<T> orderBy(R val, OrderDirection direction) {
        orderBy(val).sort(direction);
        return this;
    }

    public <R> SelectBuilder<T> orderBy(R val, String direction) {
        orderBy(val).sort(direction);
        return this;
    }

    public <R> SelectBuilder<T> orderBy(boolean condition, R val, boolean isAsc) {
        if (condition) {
            orderBy(val).sort(isAsc);
        }
        return this;
    }

    public <R> SelectBuilder<T> orderBy(boolean condition, R val, OrderDirection direction) {
        if (condition) {
            orderBy(val).sort(direction);
        }
        return this;
    }

    public <R> SelectBuilder<T> orderBy(boolean condition, R val, String direction) {
        if (condition) {
            orderBy(val).sort(direction);
        }
        return this;
    }

    public <R> SelectBuilder<T> orderByAsc(boolean condition, R val) {
        if (condition) {
            orderByAsc(val);
        }
        return this;
    }

    public <R> SelectBuilder<T> orderByDesc(boolean condition, R val) {
        if (condition) {
            orderByDesc(val);
        }
        return this;
    }

    public <R> SelectBuilder<T> orderBy(Function<T, R> getter, boolean isAsc) {
        orderBy(getter).sort(isAsc);
        return this;
    }

    public <R> SelectBuilder<T> orderBy(Function<T, R> getter, OrderDirection direction) {
        orderBy(getter).sort(direction);
        return this;
    }

    public <R> SelectBuilder<T> orderBy(Function<T, R> getter, String direction) {
        orderBy(getter).sort(direction);
        return this;
    }

    public <R> SelectBuilder<T> orderBy(boolean condition, Function<T, R> getter, boolean isAsc) {
        if (condition) {
            orderBy(getter).sort(isAsc);
        }
        return this;
    }

    public <R> SelectBuilder<T> orderBy(boolean condition, Function<T, R> getter, OrderDirection direction) {
        if (condition) {
            orderBy(getter).sort(direction);
        }
        return this;
    }

    public <R> SelectBuilder<T> orderBy(boolean condition, Function<T, R> getter, String direction) {
        if (condition) {
            orderBy(getter).sort(direction);
        }
        return this;
    }

    public <R> SelectBuilder<T> orderByAsc(boolean condition, Function<T, R> getter) {
        if (condition) {
            orderBy(getter).asc();
        }
        return this;
    }

    public <R> SelectBuilder<T> orderByDesc(boolean condition, Function<T, R> getter) {
        if (condition) {
            orderBy(getter).desc();
        }
        return this;
    }

    public SelectBuilder<T> union(Object other) {
        statement.addUnion(new UnionClause(other, false));
        return this;
    }

    public SelectBuilder<T> unionAll(Object other) {
        statement.addUnion(new UnionClause(other, true));
        return this;
    }

    public SelectBuilder<T> page(int pageNum, int pageSize) {
        int offset = (Math.max(pageNum, 1) - 1) * pageSize;
        statement.setLimit(pageSize);
        statement.setOffset(offset);
        return this;
    }

    public com.javaorm.core.pagination.PageResult<T> toPageResult(int pageNum, int pageSize) {
        long total = count();
        page(pageNum, pageSize);
        List<T> data = (total > 0) ? list() : Collections.<T>emptyList();
        return new com.javaorm.core.pagination.PageResult<>(total, data, pageNum, pageSize);
    }

    public com.javaorm.core.pagination.PageResult<T> toPageResult() {
        int limit = statement.getLimit();
        int offset = statement.getOffset();
        int pageSize = limit > 0 ? limit : 20;
        int pageNum = offset > 0 ? (offset / pageSize + 1) : 1;
        long total = count();
        List<T> data = (total > 0) ? list() : Collections.<T>emptyList();
        return new com.javaorm.core.pagination.PageResult<>(total, data, pageNum, pageSize);
    }

    public SelectBuilder<T> limit(int limit) {
        statement.setLimit(limit);
        return this;
    }

    public SelectBuilder<T> limit(int limit, int offset) {
        statement.setLimit(limit);
        statement.setOffset(offset);
        return this;
    }

    public SelectBuilder<T> offset(int offset) {
        statement.setOffset(offset);
        return this;
    }

    public SelectBuilder<T> distinct() {
        statement.setDistinct(true);
        return this;
    }

    public List<T> list() {
        return list(null);
    }

    public List<T> toList() {
        return list();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R> List<R> toList(Class<R> targetClass) {
        if (targetClass == null) {
            throw new IllegalArgumentException("Target class must not be null");
        }
        if (targetClass == rootEntityClass) {
            return (List<R>) toList();
        }
        if (ColumnMetadata.isScalarType(targetClass)) {
            return env.executeSelectScalarList(null, statement, targetClass);
        }
        if (Map.class.isAssignableFrom(targetClass)) {
            return (List<R>) env.executeSelectMaps(null, statement);
        }
        return env.executeSelectDtoList(null, statement, targetClass);
    }

    public List<T> toList(Connection conn) {
        return list(conn);
    }

    public List<T> list(Connection conn) {
        return env.executeSelectList(conn, statement, rootMetadata, includes);
    }

    public List<Map<String, Object>> listMaps() {
        return env.executeSelectMaps(null, statement);
    }

    public List<Map<String, Object>> maps() {
        return listMaps();
    }

    public List<Map<String, Object>> queryMaps() {
        return listMaps();
    }

    public <V> V fetchOne(Class<V> targetType) {
        statement.setLimit(1);
        return env.executeSelectScalar(null, statement, targetType);
    }

    public <V> List<V> fetchList(Class<V> targetType) {
        return env.executeSelectScalarList(null, statement, targetType);
    }

    public T single() {
        statement.setLimit(1);
        List<T> list = list();
        return list.isEmpty() ? null : list.get(0);
    }

    public T firstOrNull() {
        return single();
    }

    @Override
    public <R> R firstOrNull(Class<R> targetClass) {
        List<R> list = toList(targetClass);
        return (list != null && !list.isEmpty()) ? list.get(0) : null;
    }

    public T singleOrNull() {
        return single();
    }

    public long count() {
        return env.executeSelectCount(null, statement, getDialect(), rootEntityClass);
    }

    // 内部路径解析器
    public <R> PropertyPath recordPath(Function<T, R> getter) {
        RecordingContext.start(rootEntityClass);
        try {
            T proxy = ProxyFactory.getProxy(rootEntityClass);
            getter.apply(proxy);
            return RecordingContext.finish();
        } finally {
            RecordingContext.clearRecording();
        }
    }

    public void resolveExpression(SqlExpression<?> expr) {
        if (expr == null) return;
        if (expr instanceof WindowOrderByBuilder) {
            expr = ((WindowOrderByBuilder) expr).getWindowExpr();
        }
        if (expr instanceof ColumnExpression) {
            ColumnExpression ce = (ColumnExpression) expr;
            if (ce.getTableAlias() == null && ce.getPath() != null) {
                ce.setTableAlias(resolveTableAlias(ce.getPath()));
                ce.setColumnName(resolveColumnName(ce.getPath()));
            } else if (ce.getTableAlias() == null && ce.getColumnName() != null) {
                ce.setTableAlias(statement.getRootTableAlias());
                if (rootMetadata != null) {
                    ColumnMetadata cm = rootMetadata.getColumnByProperty(ce.getColumnName());
                    if (cm != null) {
                        ce.setColumnName(cm.getColumnName());
                    } else {
                        ColumnMetadata dbCol = rootMetadata.getColumnByDbName(ce.getColumnName());
                        if (dbCol != null) {
                            ce.setColumnName(dbCol.getColumnName());
                        } else if (getConfig() != null && getConfig().getNameConversion() != null) {
                            ce.setColumnName(getConfig().getNameConversion().convert(ce.getColumnName()));
                        }
                    }
                }
            } else if (ce.getTableAlias() == null && ce.getColumnName() == null) {
                PropertyPath path = RecordingContext.finish();
                if (path != null) {
                    ce.setPath(path);
                    ce.setTableAlias(resolveTableAlias(path));
                    ce.setColumnName(resolveColumnName(path));
                }
            } else if (ce.getTableAlias() != null) {
                String ta = ce.getTableAlias();
                if (statement.getRootTableName() != null && ta.equalsIgnoreCase(statement.getRootTableName())) {
                    ce.setTableAlias(statement.getRootTableAlias());
                } else if (rootMetadata != null && ta.equalsIgnoreCase(rootMetadata.getTableName())) {
                    ce.setTableAlias(statement.getRootTableAlias());
                } else {
                    for (TableJoin join : statement.getJoins()) {
                        if (ta.equalsIgnoreCase(join.getTableName())) {
                            ce.setTableAlias(join.getTableAlias());
                            break;
                        }
                    }
                }
            }
        } else if (expr instanceof FunctionExpression) {
            FunctionExpression fe = (FunctionExpression) expr;
            for (Object arg : fe.getArguments()) {
                if (arg instanceof SqlExpression) {
                    resolveExpression((SqlExpression) arg);
                }
            }
        } else if (expr instanceof WindowFunctionExpression) {
            WindowFunctionExpression<?> wfe = (WindowFunctionExpression<?>) expr;
            resolveExpression(wfe.getFunctionExpr());
            for (SqlExpression<?> part : wfe.getPartitionBys()) {
                resolveExpression(part);
            }
            for (OrderBy ob : wfe.getOrderBys()) {
                if (ob.getExpression() != null) {
                    resolveExpression(ob.getExpression());
                }
            }
        } else if (expr instanceof CaseWhenBuilder) {
            CaseWhenBuilder<?> cwb = (CaseWhenBuilder<?>) expr;
            for (CaseWhenBuilder.WhenBranch branch : cwb.getBranches()) {
                resolveCondition(branch.getCondition());
                if (branch.getThenVal() instanceof SqlExpression) {
                    resolveExpression((SqlExpression<?>) branch.getThenVal());
                }
            }
            if (cwb.getElseVal() instanceof SqlExpression) {
                resolveExpression((SqlExpression<?>) cwb.getElseVal());
            }
        }
    }

    public void resolveCondition(Condition cond) {
        if (cond == null) return;
        if (cond instanceof BinaryCondition) {
            BinaryCondition bc = (BinaryCondition) cond;
            if (bc.getPath() != null) {
                bc.setTableAlias(resolveTableAlias(bc.getPath()));
                bc.setColumnName(resolveColumnName(bc.getPath()));
            } else if (bc.getTableAlias() == null) {
                bc.setTableAlias(statement.getRootTableAlias());
                if (rootMetadata != null) {
                    ColumnMetadata cm = rootMetadata.getColumnByProperty(bc.getColumnName());
                    if (cm != null) {
                        bc.setColumnName(cm.getColumnName());
                    } else if (getConfig() != null && getConfig().getNameConversion() != null) {
                        bc.setColumnName(getConfig().getNameConversion().convert(bc.getColumnName()));
                    }
                }
            }
            if (bc.getValues() != null) {
                for (int i = 0; i < bc.getValues().length; i++) {
                    if (bc.getValues()[i] instanceof SqlExpression) {
                        resolveExpression((SqlExpression) bc.getValues()[i]);
                    }
                }
            }
        } else if (cond instanceof ExpressionCondition) {
            ExpressionCondition ec = (ExpressionCondition) cond;
            resolveExpression(ec.getExpression());
            if (ec.getValues() != null) {
                for (int i = 0; i < ec.getValues().length; i++) {
                    if (ec.getValues()[i] instanceof SqlExpression) {
                        resolveExpression((SqlExpression) ec.getValues()[i]);
                    }
                }
            }
        } else if (cond instanceof GroupCondition) {
            for (Condition child : ((GroupCondition) cond).getChildren()) {
                resolveCondition(child);
            }
        }
    }

    public ColumnExpression pathToColumnExpression(PropertyPath path) {
        if (path == null) {
            path = RecordingContext.finish();
        }
        if (path == null) {
            throw new IllegalStateException("No property path recorded!");
        }
        String tableAlias = resolveTableAlias(path);
        String columnName = resolveColumnName(path);
        ColumnExpression col = new ColumnExpression(tableAlias, columnName);
        col.setPath(path);
        return col;
    }

    public String resolveTableAlias(PropertyPath path) {
        if (path.getExplicitAlias() != null) {
            return path.getExplicitAlias();
        }
        if (!path.hasRelations()) {
            if (path.getRootEntityClass() != null && path.getRootEntityClass() != rootEntityClass) {
                String reg = RecordingContext.getAlias(path.getRootEntityClass());
                if (reg != null) {
                    if (reg.equalsIgnoreCase(statement.getRootTableAlias())) {
                        shiftRootTableAlias();
                    }
                    return reg;
                } else {
                    String extAlias = nextAlias();
                    while (RecordingContext.hasAlias(extAlias) || extAlias.equalsIgnoreCase(statement.getRootTableAlias())) {
                        extAlias = nextAlias();
                    }
                    RecordingContext.registerAlias(path.getRootEntityClass(), extAlias);
                    return extAlias;
                }
            }
            return statement.getRootTableAlias();
        }
        return resolveJoinAlias(path);
    }

    public String shiftRootTableAlias() {
        String oldAlias = statement.getRootTableAlias();
        String newAlias = nextAlias();
        while (RecordingContext.hasAlias(newAlias)) {
            newAlias = nextAlias();
        }
        statement.setRootTableAlias(newAlias);
        RecordingContext.registerAlias(rootEntityClass, newAlias);

        for (SqlExpression expr : statement.getProjectExpressions()) {
            updateExpressionTableAlias(expr, oldAlias, newAlias);
        }
        updateConditionsTableAlias(statement.getSystemConditions(), oldAlias, newAlias);
        updateConditionsTableAlias(statement.getWhereConditions(), oldAlias, newAlias);
        for (TableJoin join : statement.getJoins()) {
            updateConditionsTableAlias(join.getOnConditions(), oldAlias, newAlias);
        }
        for (SqlExpression expr : statement.getGroupByExpressions()) {
            updateExpressionTableAlias(expr, oldAlias, newAlias);
        }
        updateConditionsTableAlias(statement.getHavingConditions(), oldAlias, newAlias);
        for (OrderBy ob : statement.getOrderBys()) {
            if (ob.getExpression() != null) {
                updateExpressionTableAlias(ob.getExpression(), oldAlias, newAlias);
            }
        }
        return newAlias;
    }

    private void updateConditionsTableAlias(List<Condition> conditions, String oldAlias, String newAlias) {
        if (conditions == null) return;
        for (Condition cond : conditions) {
            if (cond instanceof BinaryCondition) {
                BinaryCondition bc = (BinaryCondition) cond;
                if (oldAlias.equalsIgnoreCase(bc.getTableAlias())) {
                    bc.setTableAlias(newAlias);
                }
                if (bc.getValues() != null) {
                    for (Object v : bc.getValues()) {
                        if (v instanceof SqlExpression) {
                            updateExpressionTableAlias((SqlExpression) v, oldAlias, newAlias);
                        }
                    }
                }
            } else if (cond instanceof ColumnEqualityCondition) {
                ColumnEqualityCondition cec = (ColumnEqualityCondition) cond;
                if (oldAlias.equalsIgnoreCase(cec.getLeftTableAlias())) {
                    cec.setLeftTableAlias(newAlias);
                }
                if (oldAlias.equalsIgnoreCase(cec.getRightTableAlias())) {
                    cec.setRightTableAlias(newAlias);
                }
            } else if (cond instanceof ExpressionCondition) {
                ExpressionCondition ec = (ExpressionCondition) cond;
                updateExpressionTableAlias(ec.getExpression(), oldAlias, newAlias);
                if (ec.getValues() != null) {
                    for (Object v : ec.getValues()) {
                        if (v instanceof SqlExpression) {
                            updateExpressionTableAlias((SqlExpression) v, oldAlias, newAlias);
                        }
                    }
                }
            } else if (cond instanceof GroupCondition) {
                updateConditionsTableAlias(((GroupCondition) cond).getChildren(), oldAlias, newAlias);
            }
        }
    }

    private void updateExpressionTableAlias(SqlExpression<?> expr, String oldAlias, String newAlias) {
        if (expr == null) return;
        if (expr instanceof ColumnExpression) {
            ColumnExpression ce = (ColumnExpression) expr;
            if (oldAlias.equalsIgnoreCase(ce.getTableAlias())) {
                ce.setTableAlias(newAlias);
            }
        } else if (expr instanceof FunctionExpression) {
            FunctionExpression fe = (FunctionExpression) expr;
            for (Object arg : fe.getArguments()) {
                if (arg instanceof SqlExpression) {
                    updateExpressionTableAlias((SqlExpression) arg, oldAlias, newAlias);
                }
            }
        } else if (expr instanceof WindowFunctionExpression) {
            WindowFunctionExpression<?> wfe = (WindowFunctionExpression<?>) expr;
            updateExpressionTableAlias(wfe.getFunctionExpr(), oldAlias, newAlias);
            for (SqlExpression<?> part : wfe.getPartitionBys()) {
                updateExpressionTableAlias(part, oldAlias, newAlias);
            }
            for (OrderBy ob : wfe.getOrderBys()) {
                if (ob.getExpression() != null) {
                    updateExpressionTableAlias(ob.getExpression(), oldAlias, newAlias);
                }
            }
        }
    }

    public String resolveColumnName(PropertyPath path) {
        if (!path.hasRelations()) {
            Class<?> targetClass = (path.getRootEntityClass() != null && path.getRootEntityClass() != rootEntityClass)
                    ? path.getRootEntityClass() : rootEntityClass;
            EntityMetadata targetMeta = (targetClass == rootEntityClass && rootMetadata != null)
                    ? rootMetadata : (getConfig() != null ? getConfig().getMetadata(targetClass) : EntityMetadataManager.get(targetClass, getConfig()));
            ColumnMetadata col = targetMeta.getColumnByProperty(path.getTerminalProperty());
            if (col != null) {
                return col.getColumnName();
            }
            return getConfig() != null && getConfig().getNameConversion() != null
                    ? getConfig().getNameConversion().convert(path.getTerminalProperty())
                    : path.getTerminalProperty();
        }
        Class<?> targetClass = resolveTargetClass(path);
        if (path.getRootEntityClass() != null && path.getRootEntityClass() != rootEntityClass && !path.hasRelations()) {
            targetClass = path.getRootEntityClass();
        }
        EntityMetadata targetMeta = (targetClass == rootEntityClass && rootMetadata != null)
                ? rootMetadata : (getConfig() != null ? getConfig().getMetadata(targetClass) : EntityMetadataManager.get(targetClass, getConfig()));
        ColumnMetadata col = targetMeta.getColumnByProperty(path.getTerminalProperty());
        if (col != null) {
            return col.getColumnName();
        }
        return getConfig() != null && getConfig().getNameConversion() != null
                ? getConfig().getNameConversion().convert(path.getTerminalProperty())
                : path.getTerminalProperty();
    }

    public String resolveJoinAlias(PropertyPath path) {
        String currentAlias = statement.getRootTableAlias();
        Class<?> currentClass = rootEntityClass;
        EntityMetadata currentMeta = rootMetadata;

        StringBuilder pathKey = new StringBuilder();
        for (String step : path.getRelationSteps()) {
            if (pathKey.length() > 0) pathKey.append(".");
            pathKey.append(step);

            String existingAlias = relationJoinAliases.get(pathKey.toString());
            if (existingAlias != null) {
                currentAlias = existingAlias;
                RelationMetadata rel = currentMeta.getRelation(step);
                currentClass = rel.getTargetEntityClass();
                currentMeta = getConfig() != null ? getConfig().getMetadata(currentClass) : EntityMetadataManager.get(currentClass, getConfig());
                continue;
            }

            RelationMetadata rel = currentMeta.getRelation(step);
            if (rel == null) {
                throw new IllegalStateException("Relation property '" + step + "' not found on " + currentClass.getName());
            }

            String nextAlias = nextAlias();
            RecordingContext.registerAlias(rel.getTargetEntityClass(), nextAlias);
            Class<?> targetClass = rel.getTargetEntityClass();
            EntityMetadata targetMeta = getConfig() != null ? getConfig().getMetadata(targetClass) : EntityMetadataManager.get(targetClass, getConfig());

            TableJoin join = new TableJoin(TableJoin.JoinType.LEFT_JOIN, targetMeta.getTableName(), nextAlias);

            // 构建 ON 条件
            ColumnMetadata selfCol = currentMeta.getColumnByProperty(rel.getSelfProperty());
            ColumnMetadata targetCol = targetMeta.getColumnByProperty(rel.getTargetProperty());

            String selfColName = selfCol != null ? selfCol.getColumnName() : (getConfig() != null && getConfig().getNameConversion() != null ? getConfig().getNameConversion().convert(rel.getSelfProperty()) : rel.getSelfProperty());
            String targetColName = targetCol != null ? targetCol.getColumnName() : (getConfig() != null && getConfig().getNameConversion() != null ? getConfig().getNameConversion().convert(rel.getTargetProperty()) : rel.getTargetProperty());

            join.addOnCondition(new ColumnEqualityCondition(currentAlias, selfColName, nextAlias, targetColName));

            // 子表逻辑删除自动过滤
            if (targetMeta.hasLogicDelete()) {
                ColumnMetadata ld = targetMeta.getLogicDeleteColumn();
                join.addOnCondition(new BinaryCondition(nextAlias, ld.getColumnName(), SqlOperator.EQ, ld.getLogicDeleteNormalValue()));
            }

            // 子表多租户自动过滤
            if (targetMeta.hasTenant()) {
                ColumnMetadata tc = targetMeta.getTenantColumn();
                Object tenantId = getConfig() != null ? getConfig().getCurrentTenantId() : com.javaorm.core.tenant.TenantContext.getTenantId();
                if (tenantId != null) {
                    join.addOnCondition(new BinaryCondition(nextAlias, tc.getColumnName(), SqlOperator.EQ, tenantId));
                }
            }

            statement.addJoin(join);
            relationJoinAliases.put(pathKey.toString(), nextAlias);

            currentAlias = nextAlias;
            currentClass = targetClass;
            currentMeta = targetMeta;
        }

        return currentAlias;
    }

    private Class<?> resolveTargetClass(PropertyPath path) {
        if (!path.hasRelations()) {
            return path.getRootEntityClass() != null ? path.getRootEntityClass() : rootEntityClass;
        }
        Class<?> current = path.getRootEntityClass() != null ? path.getRootEntityClass() : rootEntityClass;
        EntityMetadata currentMeta = (current == rootEntityClass && rootMetadata != null)
                ? rootMetadata : (getConfig() != null ? getConfig().getMetadata(current) : EntityMetadataManager.get(current, getConfig()));
        for (String step : path.getRelationSteps()) {
            RelationMetadata rel = currentMeta.getRelation(step);
            if (rel == null) break;
            current = rel.getTargetEntityClass();
            currentMeta = getConfig() != null ? getConfig().getMetadata(current) : EntityMetadataManager.get(current, getConfig());
        }
        return current;
    }

    public SelectStatement getStatement() {
        return statement;
    }
}
