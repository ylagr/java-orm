package com.javaorm.core.query;

import com.javaorm.core.ast.CteClause;
import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.dialect.Dialect;
import com.javaorm.core.sql.SqlEnv;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

/**
 * WITH 子句初始阶段构建器，用于在发起主查询前链式声明一个或多个 CTE。
 */
public class WithStage {

    private final SqlEnv env;
    private final List<CteClause> ctes = new ArrayList<>();

    public WithStage(SqlEnv env) {
        this.env = env != null ? env : new SqlEnv(null, null, null);
    }

    public WithStage(DataSource dataSource, Dialect dialect, OrmConfig config) {
        this(new SqlEnv(dataSource, dialect, config));
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

    public WithStage with(Cte<?> cte) {
        if (cte != null) {
            ctes.add(cte.toClause());
        }
        return this;
    }

    public WithStage with(String name, Object query) {
        ctes.add(new CteClause(name, query));
        return this;
    }

    public WithStage with(String name, Class<?> cteClass, Object query) {
        ctes.add(new CteClause(name, query, false, null, cteClass));
        return this;
    }

    public WithStage withRecursive(String name, Object query) {
        ctes.add(new CteClause(name, query, true));
        return this;
    }

    public WithStage withRecursive(String name, Class<?> cteClass, Object query) {
        ctes.add(new CteClause(name, query, true, null, cteClass));
        return this;
    }

    public <T> Query<T> select(Class<T> entityClass) {
        Query<T> query = new Query<>(env, entityClass);
        for (CteClause c : ctes) {
            query.getStatement().addCte(c);
        }
        return query;
    }

    public <T> Query<T> select(Cte<T> cte) {
        Query<T> query = new Query<>(env, cte);
        for (CteClause c : ctes) {
            boolean exists = false;
            for (CteClause existing : query.getStatement().getCtes()) {
                if (existing.getName().equalsIgnoreCase(c.getName())) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                query.getStatement().addCte(c);
            }
        }
        return query;
    }

    public FromStageQuery select(Object expr1, Object... moreExpressions) {
        Object[] all = new Object[1 + (moreExpressions != null ? moreExpressions.length : 0)];
        all[0] = expr1;
        if (moreExpressions != null && moreExpressions.length > 0) {
            System.arraycopy(moreExpressions, 0, all, 1, moreExpressions.length);
        }
        return new FromStageQuery(env, all) {
            @Override
            public <T> Query<T> from(Class<T> entityClass) {
                Query<T> q = super.from(entityClass);
                for (CteClause c : ctes) {
                    q.getStatement().addCte(c);
                }
                return q;
            }

            @Override
            public <T> Query<T> from(Cte<T> cte) {
                Query<T> q = super.from(cte);
                for (CteClause c : ctes) {
                    boolean exists = false;
                    for (CteClause existing : q.getStatement().getCtes()) {
                        if (existing.getName().equalsIgnoreCase(c.getName())) {
                            exists = true;
                            break;
                        }
                    }
                    if (!exists) {
                        q.getStatement().addCte(c);
                    }
                }
                return q;
            }
        };
    }

    public <DTO> DtoFromStageQuery<DTO> select(Class<DTO> dtoClass, Object expr1, Object... moreExpressions) {
        Object[] all = new Object[1 + (moreExpressions != null ? moreExpressions.length : 0)];
        all[0] = expr1;
        if (moreExpressions != null && moreExpressions.length > 0) {
            System.arraycopy(moreExpressions, 0, all, 1, moreExpressions.length);
        }
        return new DtoFromStageQuery<DTO>(env, dtoClass, all) {
            @Override
            public <T> DtoQuery<DTO, T> from(Class<T> entityClass) {
                DtoQuery<DTO, T> q = super.from(entityClass);
                for (CteClause c : ctes) {
                    q.getUnderlyingQuery().getStatement().addCte(c);
                }
                return q;
            }

            @Override
            public <T> DtoQuery<DTO, T> from(Cte<T> cte) {
                DtoQuery<DTO, T> q = super.from(cte);
                for (CteClause c : ctes) {
                    boolean exists = false;
                    for (CteClause existing : q.getUnderlyingQuery().getStatement().getCtes()) {
                        if (existing.getName().equalsIgnoreCase(c.getName())) {
                            exists = true;
                            break;
                        }
                    }
                    if (!exists) {
                        q.getUnderlyingQuery().getStatement().addCte(c);
                    }
                }
                return q;
            }
        };
    }
}
