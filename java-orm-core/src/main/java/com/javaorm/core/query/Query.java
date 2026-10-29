package com.javaorm.core.query;

import com.javaorm.core.ast.*;
import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.dialect.Dialect;
import com.javaorm.core.hydration.EntityHydrator;
import com.javaorm.core.hydration.IncludeProcessor;
import com.javaorm.core.metadata.ColumnMetadata;
import com.javaorm.core.metadata.EntityMetadata;
import com.javaorm.core.metadata.EntityMetadataManager;
import com.javaorm.core.metadata.RelationMetadata;
import com.javaorm.core.operator.SqlOperator;
import com.javaorm.core.proxy.PropertyPath;
import com.javaorm.core.proxy.ProxyFactory;
import com.javaorm.core.proxy.RecordingContext;

import com.javaorm.core.connection.ConnectionUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 核心 Fluent 查询构建器。
 */
public class Query<T> {

    private final Class<T> rootEntityClass;
    private final EntityMetadata rootMetadata;
    private final DataSource dataSource;
    private Dialect dialect;
    private final OrmConfig config;
    private Boolean printSqlOverride = null;

    private final SelectStatement statement;
    private final Map<String, String> relationJoinAliases = new LinkedHashMap<>();
    private final List<IncludeSpec> includes = new ArrayList<>();
    private int aliasSeq = 0;

    public List<IncludeSpec> getIncludeSpecs() {
        return Collections.unmodifiableList(includes);
    }

    public Query<T> addIncludeSpec(IncludeSpec spec) {
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

    public Query(Class<T> rootEntityClass, DataSource dataSource, Dialect dialect, OrmConfig config) {
        this(rootEntityClass, dataSource, dialect, config, true);
    }

    public Query(Class<T> rootEntityClass, DataSource dataSource, Dialect dialect, OrmConfig config, boolean allocateAlias) {
        this.rootEntityClass = rootEntityClass;
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.config = config;
        this.rootMetadata = EntityMetadataManager.get(rootEntityClass, config);

        if (!allocateAlias) {
            this.statement = new SelectStatement(rootEntityClass, rootMetadata.getTableName(), null);
            return;
        }

        String rootAlias = nextAlias();
        this.statement = new SelectStatement(rootEntityClass, rootMetadata.getTableName(), rootAlias);
        RecordingContext.registerAlias(rootEntityClass, rootAlias);

        // 默认自动注入逻辑删除系统过滤条件
        if (rootMetadata.hasLogicDelete()) {
            ColumnMetadata ld = rootMetadata.getLogicDeleteColumn();
            statement.addSystemCondition(new BinaryCondition(rootAlias, ld.getColumnName(), SqlOperator.EQ, ld.getLogicDeleteNormalValue()));
        }

        // 默认自动注入多租户隔离系统过滤条件
        if (rootMetadata.hasTenant()) {
            ColumnMetadata tc = rootMetadata.getTenantColumn();
            Object tenantId = config != null ? config.getCurrentTenantId() : com.javaorm.core.tenant.TenantContext.getTenantId();
            if (tenantId != null) {
                statement.addSystemCondition(new BinaryCondition(rootAlias, tc.getColumnName(), SqlOperator.EQ, tenantId));
            } else if (config != null && config.isTenantStrict()) {
                throw new IllegalStateException("Tenant context is required for multi-tenant entity: " + rootEntityClass.getName());
            }
        }
    }

    public Query(Class<T> rootEntityClass, DataSource dataSource, Dialect dialect, OrmConfig config, String rootAlias) {
        this.rootEntityClass = rootEntityClass;
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.config = config;
        this.rootMetadata = EntityMetadataManager.get(rootEntityClass, config);

        if (rootAlias == null) {
            rootAlias = nextAlias();
        } else {
            if (rootAlias.startsWith("t")) {
                try {
                    int num = Integer.parseInt(rootAlias.substring(1));
                    if (aliasSeq <= num) {
                        aliasSeq = num + 1;
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        this.statement = new SelectStatement(rootEntityClass, rootMetadata.getTableName(), rootAlias);
        RecordingContext.registerAlias(rootEntityClass, rootAlias);

        // 默认自动注入逻辑删除系统过滤条件
        if (rootMetadata.hasLogicDelete()) {
            ColumnMetadata ld = rootMetadata.getLogicDeleteColumn();
            statement.addSystemCondition(new BinaryCondition(rootAlias, ld.getColumnName(), SqlOperator.EQ, ld.getLogicDeleteNormalValue()));
        }

        // 默认自动注入多租户隔离系统过滤条件
        if (rootMetadata.hasTenant()) {
            ColumnMetadata tc = rootMetadata.getTenantColumn();
            Object tenantId = config != null ? config.getCurrentTenantId() : com.javaorm.core.tenant.TenantContext.getTenantId();
            if (tenantId != null) {
                statement.addSystemCondition(new BinaryCondition(rootAlias, tc.getColumnName(), SqlOperator.EQ, tenantId));
            } else if (config != null && config.isTenantStrict()) {
                throw new IllegalStateException("Tenant context is required for multi-tenant entity: " + rootEntityClass.getName());
            }
        }
    }

    public Query(Cte<T> cte, DataSource dataSource, Dialect dialect, OrmConfig config) {
        if (cte == null) {
            throw new IllegalArgumentException("CTE must not be null");
        }
        this.rootEntityClass = cte.getEntityClass();
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.config = config;
        this.rootMetadata = EntityMetadataManager.get(rootEntityClass, config);
        String rootAlias = nextAlias();
        this.statement = new SelectStatement(rootEntityClass, cte.getName(), rootAlias);
        this.statement.addCte(cte.toClause());
        RecordingContext.registerAlias(rootEntityClass, rootAlias);
    }

    public Class<T> getRootEntityClass() {
        return rootEntityClass;
    }

    /**
     * 链式配置：本次执行按照当前全局配置决定是否打印 SQL
     */
    public Query<T> printSql() {
        this.printSqlOverride = config.isPrintSql();
        return this;
    }

    /**
     * 链式配置：本次执行强制指定是否打印 SQL（忽略全局配置）
     */
    public Query<T> printSql(boolean print) {
        this.printSqlOverride = print;
        return this;
    }

    public OrmConfig getEffectiveConfig() {
        if (printSqlOverride == null) {
            return config;
        }
        OrmConfig effective = new OrmConfig();
        effective.setNameConversion(config.getNameConversion());
        effective.setDefaultLogicDeleteProperty(config.getDefaultLogicDeleteProperty());
        effective.setDefaultLogicDeleteNormalValue(config.getDefaultLogicDeleteNormalValue());
        effective.setDefaultLogicDeleteDeletedValue(config.getDefaultLogicDeleteDeletedValue());
        effective.setPrintSql(printSqlOverride);
        return effective;
    }

    public Query<T> setDialect(Dialect dialect) {
        this.dialect = dialect;
        return this;
    }

    /**
     * 随时获取当前生成的 SQL 及参数对象
     */
    public com.javaorm.core.dialect.SqlAndParams toSqlAndParams() {
        return dialect.buildSelect(statement);
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
                    || statement.getJoins().isEmpty();
            if (isRoot) {
                tableAlias = statement.getRootTableAlias();
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
                } else if (config != null && config.getNameConversion() != null) {
                    colName = config.getNameConversion().convert(colName);
                }
            }
        }
        ColumnExpression ce = new ColumnExpression(tableAlias, colName);
        if (alias != null && !alias.isEmpty()) {
            ce.as(alias);
        }
        return ce;
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
    public Query<T> select(Object... expressions) {
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
        return new DtoQuery<>(this.rootEntityClass, fromEntityClass, dataSource, dialect, config);
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
    public Query<T> where(Consumer<WhereBuilder<T>> consumer) {
        consumer.accept(where());
        return this;
    }

    /**
     * 双参数作用域 WHERE 块：orm.select(Student.class).where((w, s) -> { w.and(s.getAge()).gt(18); })
     */
    public Query<T> where(WhereBuilder.BiWhereConsumer<T> consumer) {
        WhereBuilder<T> w = where();
        consumer.accept(w, w.proxy());
        return this;
    }

    /**
     * 直接单条件快速断言：orm.select(Student.class).where(s.getId(), SqlOperator.EQ, 1L)
     */
    public Query<T> where(SqlExpression expr, SqlOperator op, Object... values) {
        where().and(expr).op(op, values);
        return this;
    }

    public Query<T> where(Object prop, SqlOperator op, Object... values) {
        if (prop instanceof SqlExpression) {
            return where((SqlExpression) prop, op, values);
        }
        where().and(prop).op(op, values);
        return this;
    }

    /**
     * 分组查询 GROUP BY
     */
    public Query<T> groupBy(Object... expressions) {
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

    public Query<T> having(Consumer<HavingBuilder<T>> consumer) {
        consumer.accept(having());
        return this;
    }

    /**
     * 级联子表作用域过滤
     */
    public <TSub> Query<T> whereRelation(Function<T, TSub> relationGetter, Consumer<WhereBuilder<TSub>> subBuilderConsumer) {
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
    public <TJoin> Query<T> leftJoin(Function<T, TJoin> relationGetter) {
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

    public <C> Cte<C> asCte(String name, Class<C> cteClass) {
        return new Cte<>(name, cteClass, this, false);
    }

    /**
     * 将当前查询构建器封装为强类型递归 CTE
     */
    public Cte<T> asCteRecursive(String name) {
        return new Cte<>(name, rootEntityClass, this, true);
    }

    public <C> Cte<C> asCteRecursive(String name, Class<C> cteClass) {
        return new Cte<>(name, cteClass, this, true);
    }

    /**
     * 附加通用表表达式 CTE
     */
    public Query<T> with(Cte<?> cte) {
        if (cte != null) {
            statement.addCte(cte.toClause());
        }
        return this;
    }

    public Query<T> with(String name, Object query) {
        statement.addCte(new CteClause(name, query));
        return this;
    }

    public Query<T> with(String name, Class<?> cteClass, Object query) {
        statement.addCte(new CteClause(name, query, false, null, cteClass));
        return this;
    }

    public Query<T> withRecursive(String name, Object query) {
        statement.addCte(new CteClause(name, query, true));
        return this;
    }

    public Query<T> withRecursive(String name, Class<?> cteClass, Object query) {
        statement.addCte(new CteClause(name, query, true, null, cteClass));
        return this;
    }

    public Query<T> ensureCteAdded(Cte<?> cte) {
        if (cte == null) return this;
        for (CteClause existing : statement.getCtes()) {
            if (existing.getName().equalsIgnoreCase(cte.getName())) {
                return this;
            }
        }
        statement.addCte(cte.toClause());
        return this;
    }

    // --- CTE 连接支持 ---
    public <TJoin> JoinBuilder<T, TJoin> leftJoin(Cte<TJoin> cte) {
        String joinAlias = nextAlias();
        return new JoinBuilder<>(this, TableJoin.JoinType.LEFT_JOIN, cte.getName(), cte.getEntityClass(), joinAlias, cte);
    }

    public <TJoin> Query<T> leftJoin(Cte<TJoin> cte, TriConsumer<WhereBuilder<TJoin>, T, TJoin> onConsumer) {
        return leftJoin(cte).on(onConsumer);
    }

    public <TJoin> JoinBuilder<T, TJoin> innerJoin(Cte<TJoin> cte) {
        String joinAlias = nextAlias();
        return new JoinBuilder<>(this, TableJoin.JoinType.INNER_JOIN, cte.getName(), cte.getEntityClass(), joinAlias, cte);
    }

    public <TJoin> Query<T> innerJoin(Cte<TJoin> cte, TriConsumer<WhereBuilder<TJoin>, T, TJoin> onConsumer) {
        return innerJoin(cte).on(onConsumer);
    }

    public <TJoin> JoinBuilder<T, TJoin> rightJoin(Cte<TJoin> cte) {
        String joinAlias = nextAlias();
        return new JoinBuilder<>(this, TableJoin.JoinType.RIGHT_JOIN, cte.getName(), cte.getEntityClass(), joinAlias, cte);
    }

    public <TJoin> Query<T> rightJoin(Cte<TJoin> cte, TriConsumer<WhereBuilder<TJoin>, T, TJoin> onConsumer) {
        return rightJoin(cte).on(onConsumer);
    }

    // --- 实体表连接支持 ---
    public <TJoin> JoinBuilder<T, TJoin> leftJoin(Class<TJoin> entityClass) {
        EntityMetadata meta = EntityMetadataManager.get(entityClass, config);
        String joinAlias = nextAlias();
        return new JoinBuilder<>(this, TableJoin.JoinType.LEFT_JOIN, meta.getTableName(), entityClass, joinAlias, null);
    }

    public <TJoin> Query<T> leftJoin(Class<TJoin> entityClass, TriConsumer<WhereBuilder<TJoin>, T, TJoin> onConsumer) {
        return leftJoin(entityClass).on(onConsumer);
    }

    public <TJoin> JoinBuilder<T, TJoin> innerJoin(Class<TJoin> entityClass) {
        EntityMetadata meta = EntityMetadataManager.get(entityClass, config);
        String joinAlias = nextAlias();
        return new JoinBuilder<>(this, TableJoin.JoinType.INNER_JOIN, meta.getTableName(), entityClass, joinAlias, null);
    }

    public <TJoin> Query<T> innerJoin(Class<TJoin> entityClass, TriConsumer<WhereBuilder<TJoin>, T, TJoin> onConsumer) {
        return innerJoin(entityClass).on(onConsumer);
    }

    public <TJoin> JoinBuilder<T, TJoin> rightJoin(Class<TJoin> entityClass) {
        EntityMetadata meta = EntityMetadataManager.get(entityClass, config);
        String joinAlias = nextAlias();
        return new JoinBuilder<>(this, TableJoin.JoinType.RIGHT_JOIN, meta.getTableName(), entityClass, joinAlias, null);
    }

    public <TJoin> Query<T> rightJoin(Class<TJoin> entityClass, TriConsumer<WhereBuilder<TJoin>, T, TJoin> onConsumer) {
        return rightJoin(entityClass).on(onConsumer);
    }

    /**
     * 声明显式装配关联对象（避免笛卡尔积，采用 Split IN 查询，支持各级独立筛选与多层级嵌套）
     */
    public Query<T> include(Function<T, ?> relationGetter) {
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
    public <R> Query<T> include(Function<T, ?> relationGetter, Consumer<Query<R>> config) {
        RecordingContext.start(rootEntityClass);
        try {
            T proxy = ProxyFactory.getProxy(rootEntityClass);
            relationGetter.apply(proxy);
            PropertyPath path = RecordingContext.finish();
            if (path != null) {
                includes.add(new IncludeSpec(path, (Consumer<Query<?>>) (Consumer<?>) config));
            }
        } finally {
            RecordingContext.clearRecording();
        }
        return this;
    }

    public <R> Query<T> include(Function<T, ?> relationGetter, BiConsumer<Query<R>, R> biConfig) {
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

    public <R> Query<T> include(R relationProxy) {
        PropertyPath path = RecordingContext.finish();
        if (path != null) {
            includes.add(new IncludeSpec(path));
        }
        return this;
    }

    @SuppressWarnings("unchecked")
    public <R> Query<T> include(R relationProxy, Consumer<Query<R>> config) {
        PropertyPath path = RecordingContext.finish();
        if (path != null) {
            includes.add(new IncludeSpec(path, (Consumer<Query<?>>) (Consumer<?>) config));
        }
        return this;
    }

    public <R> Query<T> include(R relationProxy, BiConsumer<Query<R>, R> biConfig) {
        PropertyPath path = RecordingContext.finish();
        if (path != null) {
            includes.add(IncludeSpec.ofBi(path, biConfig));
        }
        return this;
    }

    public <E> Query<T> include(Collection<E> relationProxy) {
        PropertyPath path = RecordingContext.finish();
        if (path != null) {
            includes.add(new IncludeSpec(path));
        }
        return this;
    }

    @SuppressWarnings("unchecked")
    public <E> Query<T> include(Collection<E> relationProxy, Consumer<Query<E>> config) {
        PropertyPath path = RecordingContext.finish();
        if (path != null) {
            includes.add(new IncludeSpec(path, (Consumer<Query<?>>) (Consumer<?>) config));
        }
        return this;
    }

    public <E> Query<T> include(Collection<E> relationProxy, BiConsumer<Query<E>, E> biConfig) {
        PropertyPath path = RecordingContext.finish();
        if (path != null) {
            includes.add(IncludeSpec.ofBi(path, biConfig));
        }
        return this;
    }

    /**
     * 显式重命名当前查询的根表别名（用于子查询或自连接消除歧义）
     */
    public Query<T> as(String alias) {
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

    public <R> OrderByBuilder<Query<T>> orderBy(Function<T, R> propertyGetter) {
        PropertyPath path = recordPath(propertyGetter);
        String alias = resolveTableAlias(path);
        String colName = resolveColumnName(path);
        return new OrderByBuilder<>(this, statement, alias, colName);
    }

    public <R> OrderByBuilder<Query<T>> orderBy(R val) {
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

    public OrderByBuilder<Query<T>> orderBy(String propertyOrColumn) {
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

    public OrderByBuilder<Query<T>> orderBy(SqlExpression<?> expr) {
        resolveExpression(expr);
        return new OrderByBuilder<>(this, statement, expr);
    }

    public <R> Query<T> orderByAsc(R val) {
        orderBy(val).asc();
        return this;
    }

    public <R> Query<T> orderByDesc(R val) {
        orderBy(val).desc();
        return this;
    }

    public <R> Query<T> orderBy(R val, boolean isAsc) {
        orderBy(val).sort(isAsc);
        return this;
    }

    public <R> Query<T> orderBy(R val, OrderDirection direction) {
        orderBy(val).sort(direction);
        return this;
    }

    public <R> Query<T> orderBy(R val, String direction) {
        orderBy(val).sort(direction);
        return this;
    }

    public <R> Query<T> orderBy(boolean condition, R val, boolean isAsc) {
        if (condition) {
            orderBy(val).sort(isAsc);
        }
        return this;
    }

    public <R> Query<T> orderBy(boolean condition, R val, OrderDirection direction) {
        if (condition) {
            orderBy(val).sort(direction);
        }
        return this;
    }

    public <R> Query<T> orderBy(boolean condition, R val, String direction) {
        if (condition) {
            orderBy(val).sort(direction);
        }
        return this;
    }

    public <R> Query<T> orderByAsc(boolean condition, R val) {
        if (condition) {
            orderByAsc(val);
        }
        return this;
    }

    public <R> Query<T> orderByDesc(boolean condition, R val) {
        if (condition) {
            orderByDesc(val);
        }
        return this;
    }

    public <R> Query<T> orderBy(Function<T, R> getter, boolean isAsc) {
        orderBy(getter).sort(isAsc);
        return this;
    }

    public <R> Query<T> orderBy(Function<T, R> getter, OrderDirection direction) {
        orderBy(getter).sort(direction);
        return this;
    }

    public <R> Query<T> orderBy(Function<T, R> getter, String direction) {
        orderBy(getter).sort(direction);
        return this;
    }

    public <R> Query<T> orderBy(boolean condition, Function<T, R> getter, boolean isAsc) {
        if (condition) {
            orderBy(getter).sort(isAsc);
        }
        return this;
    }

    public <R> Query<T> orderBy(boolean condition, Function<T, R> getter, OrderDirection direction) {
        if (condition) {
            orderBy(getter).sort(direction);
        }
        return this;
    }

    public <R> Query<T> orderBy(boolean condition, Function<T, R> getter, String direction) {
        if (condition) {
            orderBy(getter).sort(direction);
        }
        return this;
    }

    public <R> Query<T> orderByAsc(boolean condition, Function<T, R> getter) {
        if (condition) {
            orderBy(getter).asc();
        }
        return this;
    }

    public <R> Query<T> orderByDesc(boolean condition, Function<T, R> getter) {
        if (condition) {
            orderBy(getter).desc();
        }
        return this;
    }

    public Query<T> union(Object other) {
        statement.addUnion(new UnionClause(other, false));
        return this;
    }

    public Query<T> unionAll(Object other) {
        statement.addUnion(new UnionClause(other, true));
        return this;
    }

    public Query<T> page(int pageNum, int pageSize) {
        int offset = (Math.max(pageNum, 1) - 1) * pageSize;
        statement.setLimit(pageSize);
        statement.setOffset(offset);
        return this;
    }

    public Query<T> limit(int limit) {
        statement.setLimit(limit);
        return this;
    }

    public Query<T> limit(int limit, int offset) {
        statement.setLimit(limit);
        statement.setOffset(offset);
        return this;
    }

    public Query<T> offset(int offset) {
        statement.setOffset(offset);
        return this;
    }

    public Query<T> distinct() {
        statement.setDistinct(true);
        return this;
    }

    public List<T> list() {
        Connection conn = null;
        boolean isTx = false;
        try {
            conn = ConnectionUtils.getConnection(dataSource, config);
            isTx = ConnectionUtils.isTransactionConnection(conn);
            return list(conn);
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute query list", e);
        } finally {
            if (!isTx) ConnectionUtils.closeConnection(conn);
        }
    }

    public List<T> toList() {
        return list();
    }

    public List<T> toList(Connection conn) {
        return list(conn);
    }

    public List<T> list(Connection conn) {
        try {
            List<T> result = EntityHydrator.queryList(conn, statement, rootMetadata, dialect, getEffectiveConfig());
            if (!includes.isEmpty() && !result.isEmpty()) {
                IncludeProcessor.processIncludes(conn, result, rootMetadata, includes, dialect, getEffectiveConfig(), dataSource);
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute query list", e);
        }
    }

    public List<Map<String, Object>> listMaps() {
        Connection conn = null;
        boolean isTx = false;
        try {
            conn = ConnectionUtils.getConnection(dataSource, config);
            isTx = ConnectionUtils.isTransactionConnection(conn);
            return EntityHydrator.queryMaps(conn, statement, dialect, getEffectiveConfig());
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute listMaps query", e);
        } finally {
            if (!isTx) ConnectionUtils.closeConnection(conn);
        }
    }

    public List<Map<String, Object>> maps() {
        return listMaps();
    }

    public List<Map<String, Object>> queryMaps() {
        return listMaps();
    }

    public <V> V fetchOne(Class<V> targetType) {
        statement.setLimit(1);
        Connection conn = null;
        boolean isTx = false;
        try {
            conn = ConnectionUtils.getConnection(dataSource, config);
            isTx = ConnectionUtils.isTransactionConnection(conn);
            return EntityHydrator.queryScalar(conn, statement, targetType, dialect, getEffectiveConfig());
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch one scalar", e);
        } finally {
            if (!isTx) ConnectionUtils.closeConnection(conn);
        }
    }

    public <V> List<V> fetchList(Class<V> targetType) {
        Connection conn = null;
        boolean isTx = false;
        try {
            conn = ConnectionUtils.getConnection(dataSource, config);
            isTx = ConnectionUtils.isTransactionConnection(conn);
            return EntityHydrator.queryScalarList(conn, statement, targetType, dialect, getEffectiveConfig());
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch scalar list", e);
        } finally {
            if (!isTx) ConnectionUtils.closeConnection(conn);
        }
    }

    public T single() {
        statement.setLimit(1);
        List<T> list = list();
        return list.isEmpty() ? null : list.get(0);
    }

    public T firstOrNull() {
        return single();
    }

    public long count() {
        boolean isComplex = statement.isDistinct()
                || !statement.getGroupByExpressions().isEmpty()
                || !statement.getHavingConditions().isEmpty()
                || !statement.getUnions().isEmpty();

        Connection conn = null;
        boolean isTx = false;
        try {
            conn = ConnectionUtils.getConnection(dataSource, config);
            isTx = ConnectionUtils.isTransactionConnection(conn);

            if (!isComplex) {
                SelectStatement countStmt = new SelectStatement(rootEntityClass, statement.getRootTableName(), statement.getRootTableAlias());
                for (CteClause cte : statement.getCtes()) {
                    countStmt.addCte(cte);
                }
                countStmt.addSelectColumn("COUNT(1)");
                for (TableJoin join : statement.getJoins()) {
                    countStmt.addJoin(join);
                }
                for (Condition cond : statement.getSystemConditions()) {
                    countStmt.addSystemCondition(cond);
                }
                for (Condition cond : statement.getWhereConditions()) {
                    countStmt.addWhereCondition(cond);
                }
                return EntityHydrator.queryCount(conn, countStmt, dialect, getEffectiveConfig());
            } else {
                // 复杂查询保留 DISTINCT / GROUP BY / HAVING / UNION 语义，忽略分页并以外层子查询包装计数
                SelectStatement innerStmt = new SelectStatement(rootEntityClass, statement.getRootTableName(), statement.getRootTableAlias());
                for (CteClause cte : statement.getCtes()) {
                    innerStmt.addCte(cte);
                }
                innerStmt.setDistinct(statement.isDistinct());
                for (SqlExpression expr : statement.getProjectExpressions()) {
                    innerStmt.addProjectExpression(expr);
                }
                for (String col : statement.getSelectColumns()) {
                    innerStmt.addSelectColumn(col);
                }
                if (!statement.getGroupByExpressions().isEmpty() && innerStmt.getProjectExpressions().isEmpty() && innerStmt.getSelectColumns().isEmpty()) {
                    for (SqlExpression expr : statement.getGroupByExpressions()) {
                        innerStmt.addProjectExpression(expr);
                    }
                }
                for (TableJoin join : statement.getJoins()) {
                    innerStmt.addJoin(join);
                }
                for (Condition cond : statement.getSystemConditions()) {
                    innerStmt.addSystemCondition(cond);
                }
                for (Condition cond : statement.getWhereConditions()) {
                    innerStmt.addWhereCondition(cond);
                }
                for (SqlExpression expr : statement.getGroupByExpressions()) {
                    innerStmt.addGroupByExpression(expr);
                }
                for (Condition cond : statement.getHavingConditions()) {
                    innerStmt.addHavingCondition(cond);
                }
                for (UnionClause u : statement.getUnions()) {
                    innerStmt.addUnion(u);
                }
                // 分页 limit / offset 不参与 count 统计

                com.javaorm.core.dialect.SqlAndParams innerSql = dialect.buildSelect(innerStmt);
                String countSql = "SELECT COUNT(1) FROM (" + innerSql.getSql() + ") count_sub";
                com.javaorm.core.dialect.SqlAndParams countSqlAndParams = new com.javaorm.core.dialect.SqlAndParams(countSql, innerSql.getParams());
                return EntityHydrator.queryCount(conn, countSqlAndParams, getEffectiveConfig());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute count query", e);
        } finally {
            if (!isTx) ConnectionUtils.closeConnection(conn);
        }
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
                        } else if (config != null && config.getNameConversion() != null) {
                            ce.setColumnName(config.getNameConversion().convert(ce.getColumnName()));
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
                    } else if (config != null && config.getNameConversion() != null) {
                        bc.setColumnName(config.getNameConversion().convert(bc.getColumnName()));
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
        Class<?> targetClass = resolveTargetClass(path);
        if (path.getRootEntityClass() != null && path.getRootEntityClass() != rootEntityClass && !path.hasRelations()) {
            targetClass = path.getRootEntityClass();
        }
        EntityMetadata targetMeta = EntityMetadataManager.get(targetClass, config);
        ColumnMetadata col = targetMeta.getColumnByProperty(path.getTerminalProperty());
        if (col != null) {
            return col.getColumnName();
        }
        return config.getNameConversion().convert(path.getTerminalProperty());
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
                currentMeta = EntityMetadataManager.get(currentClass, config);
                continue;
            }

            RelationMetadata rel = currentMeta.getRelation(step);
            if (rel == null) {
                throw new IllegalStateException("Relation property '" + step + "' not found on " + currentClass.getName());
            }

            String nextAlias = nextAlias();
            RecordingContext.registerAlias(rel.getTargetEntityClass(), nextAlias);
            Class<?> targetClass = rel.getTargetEntityClass();
            EntityMetadata targetMeta = EntityMetadataManager.get(targetClass, config);

            TableJoin join = new TableJoin(TableJoin.JoinType.LEFT_JOIN, targetMeta.getTableName(), nextAlias);

            // 构建 ON 条件
            ColumnMetadata selfCol = currentMeta.getColumnByProperty(rel.getSelfProperty());
            ColumnMetadata targetCol = targetMeta.getColumnByProperty(rel.getTargetProperty());

            String selfColName = selfCol != null ? selfCol.getColumnName() : config.getNameConversion().convert(rel.getSelfProperty());
            String targetColName = targetCol != null ? targetCol.getColumnName() : config.getNameConversion().convert(rel.getTargetProperty());

            join.addOnCondition(new ColumnEqualityCondition(currentAlias, selfColName, nextAlias, targetColName));

            // 子表逻辑删除自动过滤
            if (targetMeta.hasLogicDelete()) {
                ColumnMetadata ld = targetMeta.getLogicDeleteColumn();
                join.addOnCondition(new BinaryCondition(nextAlias, ld.getColumnName(), SqlOperator.EQ, ld.getLogicDeleteNormalValue()));
            }

            // 子表多租户自动过滤
            if (targetMeta.hasTenant()) {
                ColumnMetadata tc = targetMeta.getTenantColumn();
                Object tenantId = config != null ? config.getCurrentTenantId() : com.javaorm.core.tenant.TenantContext.getTenantId();
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
        Class<?> current = path.getRootEntityClass() != null ? path.getRootEntityClass() : rootEntityClass;
        EntityMetadata currentMeta = EntityMetadataManager.get(current, config);
        for (String step : path.getRelationSteps()) {
            RelationMetadata rel = currentMeta.getRelation(step);
            if (rel == null) break;
            current = rel.getTargetEntityClass();
            currentMeta = EntityMetadataManager.get(current, config);
        }
        return current;
    }

    public SelectStatement getStatement() { return statement; }
    public OrmConfig getConfig() { return config; }
    public Dialect getDialect() { return dialect; }
}
