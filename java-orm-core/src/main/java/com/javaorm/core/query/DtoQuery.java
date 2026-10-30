package com.javaorm.core.query;

import com.javaorm.core.ast.SelectStatement;
import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.dialect.Dialect;
import com.javaorm.core.hydration.EntityHydrator;
import com.javaorm.core.proxy.ProxyFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * DTO 结果投影查询器。
 * 针对 DTO 结果类绑定实体源表查询，例如：
 * orm.select(StatDto.class).from(Student.class).select(count(s.getId()).as("total"), s.getTeacherId()).list();
 *
 * @param <DTO> 结果 DTO 类型
 * @param <T>   数据源实体类型
 */
public class DtoQuery<DTO, T> {

    private final Class<DTO> dtoClass;
    private final Class<T> entityClass;
    private final DataSource dataSource;
    private final Dialect dialect;
    private final OrmConfig config;
    private final Query<T> underlyingQuery;

    public DtoQuery(Class<DTO> dtoClass, Class<T> entityClass, DataSource dataSource, Dialect dialect, OrmConfig config) {
        this(dtoClass, entityClass, dataSource, dialect, config, null);
    }

    public DtoQuery(Class<DTO> dtoClass, Class<T> entityClass, DataSource dataSource, Dialect dialect, OrmConfig config, String rootAlias) {
        this.dtoClass = dtoClass;
        this.entityClass = entityClass;
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.config = config;
        this.underlyingQuery = new Query<>(entityClass, dataSource, dialect, config, rootAlias);
    }

    public DtoQuery(Class<DTO> dtoClass, Cte<T> cte, DataSource dataSource, Dialect dialect, OrmConfig config) {
        if (cte == null) {
            throw new IllegalArgumentException("CTE must not be null");
        }
        this.dtoClass = dtoClass;
        this.entityClass = cte.getEntityClass();
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.config = config;
        this.underlyingQuery = new Query<>(cte, dataSource, dialect, config);
    }

    public T proxy() {
        return entityClass != null ? ProxyFactory.getProxy(entityClass) : null;
    }

    public DtoQuery<DTO, T> select(Object... expressions) {
        underlyingQuery.select(expressions);
        return this;
    }

    public WhereBuilder<T> where() {
        return new WhereBuilder<>(underlyingQuery, this::list);
    }

    public DtoQuery<DTO, T> where(Consumer<WhereBuilder<T>> consumer) {
        consumer.accept(where());
        return this;
    }

    public DtoQuery<DTO, T> where(WhereBuilder.BiWhereConsumer<T> consumer) {
        WhereBuilder<T> w = where();
        consumer.accept(w, w.proxy());
        return this;
    }

    public DtoQuery<DTO, T> groupBy(Object... expressions) {
        underlyingQuery.groupBy(expressions);
        return this;
    }

    public HavingBuilder<T> having() {
        return new HavingBuilder<>(underlyingQuery, this::list);
    }

    public DtoQuery<DTO, T> having(Consumer<HavingBuilder<T>> consumer) {
        consumer.accept(having());
        return this;
    }

    public <R> OrderByBuilder<DtoQuery<DTO, T>> orderBy(Function<T, R> getter) {
        com.javaorm.core.proxy.PropertyPath path = underlyingQuery.recordPath(getter);
        String alias = underlyingQuery.resolveTableAlias(path);
        String colName = underlyingQuery.resolveColumnName(path);
        return new OrderByBuilder<>(this, getStatement(), alias, colName);
    }

    public <R> OrderByBuilder<DtoQuery<DTO, T>> orderBy(R val) {
        if (val instanceof com.javaorm.core.ast.SqlExpression) {
            return orderBy((com.javaorm.core.ast.SqlExpression) val);
        }
        com.javaorm.core.proxy.PropertyPath path = com.javaorm.core.proxy.RecordingContext.finish();
        if (path == null) {
            if (val instanceof String) {
                return orderBy((String) val);
            }
            throw new IllegalStateException("No proxy property accessed before calling orderBy(...)");
        }
        String alias = underlyingQuery.resolveTableAlias(path);
        String colName = underlyingQuery.resolveColumnName(path);
        return new OrderByBuilder<>(this, getStatement(), alias, colName);
    }

    public OrderByBuilder<DtoQuery<DTO, T>> orderBy(String propertyOrColumn) {
        com.javaorm.core.proxy.PropertyPath path = com.javaorm.core.proxy.RecordingContext.finish();
        if (path != null) {
            String alias = underlyingQuery.resolveTableAlias(path);
            String colName = underlyingQuery.resolveColumnName(path);
            return new OrderByBuilder<>(this, getStatement(), alias, colName);
        }
        if (propertyOrColumn == null) {
            throw new IllegalArgumentException("Order by column cannot be null");
        }
        com.javaorm.core.ast.ColumnExpression ce = underlyingQuery.stringToColumnExpression(propertyOrColumn);
        return new OrderByBuilder<>(this, getStatement(), ce.getTableAlias(), ce.getColumnName());
    }

    public OrderByBuilder<DtoQuery<DTO, T>> orderBy(com.javaorm.core.ast.SqlExpression<?> expr) {
        underlyingQuery.resolveExpression(expr);
        return new OrderByBuilder<>(this, getStatement(), expr);
    }

    public <R> DtoQuery<DTO, T> orderByAsc(R val) {
        orderBy(val).asc();
        return this;
    }

    public <R> DtoQuery<DTO, T> orderByDesc(R val) {
        orderBy(val).desc();
        return this;
    }

    public <R> DtoQuery<DTO, T> orderBy(R val, boolean isAsc) {
        orderBy(val).sort(isAsc);
        return this;
    }

    public <R> DtoQuery<DTO, T> orderBy(R val, com.javaorm.core.ast.OrderDirection direction) {
        orderBy(val).sort(direction);
        return this;
    }

    public <R> DtoQuery<DTO, T> orderBy(R val, String direction) {
        orderBy(val).sort(direction);
        return this;
    }

    public <R> DtoQuery<DTO, T> orderBy(boolean condition, R val, boolean isAsc) {
        if (condition) {
            orderBy(val).sort(isAsc);
        }
        return this;
    }

    public <R> DtoQuery<DTO, T> orderBy(Function<T, R> getter, boolean isAsc) {
        orderBy(getter).sort(isAsc);
        return this;
    }

    public <R> DtoQuery<DTO, T> orderBy(boolean condition, Function<T, R> getter, boolean isAsc) {
        if (condition) {
            orderBy(getter).sort(isAsc);
        }
        return this;
    }

    public DtoQuery<DTO, T> printSql() {
        underlyingQuery.printSql();
        return this;
    }

    public DtoQuery<DTO, T> printSql(boolean print) {
        underlyingQuery.printSql(print);
        return this;
    }

    public com.javaorm.core.dialect.SqlAndParams toSqlAndParams() {
        return underlyingQuery.toSqlAndParams();
    }

    public String toSql() {
        return underlyingQuery.toSql();
    }

    @Override
    public String toString() {
        return toSql();
    }

    public DtoQuery<DTO, T> page(int pageNum, int pageSize) {
        underlyingQuery.page(pageNum, pageSize);
        return this;
    }

    public DtoQuery<DTO, T> limit(int limit) {
        underlyingQuery.limit(limit);
        return this;
    }

    public DtoQuery<DTO, T> offset(int offset) {
        underlyingQuery.offset(offset);
        return this;
    }

    public DtoQuery<DTO, T> distinct() {
        underlyingQuery.distinct();
        return this;
    }

    public List<DTO> list() {
        try (Connection conn = dataSource.getConnection()) {
            return EntityHydrator.queryDtoList(conn, underlyingQuery.getStatement(), dtoClass, dialect, underlyingQuery.getEffectiveConfig());
        } catch (Exception e) {
            throw new RuntimeException("Failed to query DTO list for " + dtoClass.getName(), e);
        }
    }

    public List<DTO> toList() {
        return list();
    }

    public Cte<DTO> asCte(String name) {
        return new Cte<>(name, dtoClass, this, false);
    }

    public Cte<?> asDynamicCte(String name) {
        return new Cte<>(name, null, this, false);
    }

    public Cte<DTO> asCteRecursive(String name) {
        return new Cte<>(name, dtoClass, this, true);
    }

    public Cte<?> asDynamicCteRecursive(String name) {
        return new Cte<>(name, null, this, true);
    }

    public DtoQuery<DTO, T> with(Cte<?> cte) {
        underlyingQuery.with(cte);
        return this;
    }

    public DtoQuery<DTO, T> with(String name, Object query) {
        underlyingQuery.with(name, query);
        return this;
    }

    public DtoQuery<DTO, T> with(String name, Class<?> cteClass, Object query) {
        underlyingQuery.with(name, cteClass, query);
        return this;
    }

    public DtoQuery<DTO, T> withRecursive(String name, Object query) {
        underlyingQuery.withRecursive(name, query);
        return this;
    }

    public DtoQuery<DTO, T> withRecursive(String name, Class<?> cteClass, Object query) {
        underlyingQuery.withRecursive(name, cteClass, query);
        return this;
    }

    public <TJoin> JoinBuilder<T, TJoin> leftJoin(Cte<TJoin> cte) {
        return underlyingQuery.leftJoin(cte);
    }

    public <TJoin> DtoQuery<DTO, T> leftJoin(Cte<TJoin> cte, TriConsumer<WhereBuilder<TJoin>, T, TJoin> onConsumer) {
        underlyingQuery.leftJoin(cte, onConsumer);
        return this;
    }

    public <TJoin> JoinBuilder<T, TJoin> innerJoin(Cte<TJoin> cte) {
        return underlyingQuery.innerJoin(cte);
    }

    public <TJoin> DtoQuery<DTO, T> innerJoin(Cte<TJoin> cte, TriConsumer<WhereBuilder<TJoin>, T, TJoin> onConsumer) {
        underlyingQuery.innerJoin(cte, onConsumer);
        return this;
    }

    public <TJoin> JoinBuilder<T, TJoin> rightJoin(Cte<TJoin> cte) {
        return underlyingQuery.rightJoin(cte);
    }

    public <TJoin> DtoQuery<DTO, T> rightJoin(Cte<TJoin> cte, TriConsumer<WhereBuilder<TJoin>, T, TJoin> onConsumer) {
        underlyingQuery.rightJoin(cte, onConsumer);
        return this;
    }

    public DTO single() {
        underlyingQuery.limit(1);
        List<DTO> list = list();
        return list.isEmpty() ? null : list.get(0);
    }

    public DTO firstOrNull() {
        return single();
    }

    public List<Map<String, Object>> listMaps() {
        return underlyingQuery.listMaps();
    }

    public List<Map<String, Object>> maps() {
        return underlyingQuery.listMaps();
    }

    public <V> V fetchOne(Class<V> targetType) {
        return underlyingQuery.fetchOne(targetType);
    }

    public <V> List<V> fetchList(Class<V> targetType) {
        return underlyingQuery.fetchList(targetType);
    }

    public SelectStatement getStatement() {
        return underlyingQuery.getStatement();
    }

    public Query<T> getUnderlyingQuery() {
        return underlyingQuery;
    }
}
