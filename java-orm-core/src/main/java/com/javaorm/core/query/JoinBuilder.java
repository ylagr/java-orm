package com.javaorm.core.query;

import com.javaorm.core.ast.TableJoin;
import com.javaorm.core.proxy.ProxyFactory;
import com.javaorm.core.proxy.RecordingContext;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 关联 JOIN 条件构造器。
 * 支持针对实体表或 CTE 结果集以强类型 Lambda 表达式方式配置 ON 条件。
 *
 * @param <TRoot> 主表实体类型
 * @param <TJoin> 连接表实体或 CTE 契约类型
 */
public class JoinBuilder<TRoot, TJoin> {

    private final SelectBuilder<TRoot> rootQuery;
    private final TableJoin.JoinType joinType;
    private final String tableName;
    private final Class<TJoin> joinClass;
    private final String joinAlias;
    private final Cte<TJoin> cte;
    private final Runnable onComplete;

    public JoinBuilder(SelectBuilder<TRoot> rootQuery, TableJoin.JoinType joinType, String tableName,
                       Class<TJoin> joinClass, String joinAlias, Cte<TJoin> cte) {
        this(rootQuery, joinType, tableName, joinClass, joinAlias, cte, null);
    }

    public JoinBuilder(SelectBuilder<TRoot> rootQuery, TableJoin.JoinType joinType, String tableName,
                       Class<TJoin> joinClass, String joinAlias, Cte<TJoin> cte, Runnable onComplete) {
        this.rootQuery = rootQuery;
        this.joinType = joinType;
        this.tableName = tableName;
        this.joinClass = joinClass;
        this.joinAlias = joinAlias;
        this.cte = cte;
        this.onComplete = onComplete;
    }

    @SuppressWarnings("unchecked")
    public SelectBuilder<TRoot> on(TriConsumer<WhereBuilder<TJoin>, TRoot, TJoin> onConsumer) {
        TableJoin join = new TableJoin(joinType, tableName, joinAlias);
        rootQuery.getStatement().addJoin(join);

        if (cte == null && rootQuery.getConfig() != null && joinClass != null) {
            com.javaorm.core.metadata.EntityMetadata meta = com.javaorm.core.metadata.EntityMetadataManager.get(joinClass, rootQuery.getConfig());
            if (meta != null) {
                if (meta.hasLogicDelete()) {
                    com.javaorm.core.metadata.ColumnMetadata ld = meta.getLogicDeleteColumn();
                    join.addOnCondition(new com.javaorm.core.ast.BinaryCondition(joinAlias, ld.getColumnName(), com.javaorm.core.operator.SqlOperator.EQ, ld.getLogicDeleteNormalValue()));
                }
                if (meta.hasTenant()) {
                    com.javaorm.core.metadata.ColumnMetadata tc = meta.getTenantColumn();
                    Object tenantId = rootQuery.getConfig().getCurrentTenantId();
                    if (tenantId == null) {
                        tenantId = com.javaorm.core.tenant.TenantContext.getTenantId();
                    }
                    if (tenantId != null) {
                        join.addOnCondition(new com.javaorm.core.ast.BinaryCondition(joinAlias, tc.getColumnName(), com.javaorm.core.operator.SqlOperator.EQ, tenantId));
                    }
                }
            }
        }
        WhereBuilder<TJoin> onBuilder = new WhereBuilder<>(joinClass, rootQuery, joinAlias,
                join::addOnCondition, null, null, null, null);

        TRoot rootProxy = rootQuery.getRootEntityClass() != null
                ? ProxyFactory.createProxy(rootQuery.getRootEntityClass(), rootQuery.getStatement().getRootTableAlias())
                : (rootQuery.getRootCte() != null ? (TRoot) rootQuery.getRootCte().as(rootQuery.getStatement().getRootTableAlias()) : null);

        TJoin joinProxy;
        if (joinClass != null) {
            joinProxy = ProxyFactory.createProxy(joinClass, joinAlias);
        } else if (cte != null) {
            joinProxy = (TJoin) cte.as(joinAlias);
        } else {
            joinProxy = null;
        }

        try {
            if (joinClass != null) {
                RecordingContext.registerAlias(joinClass, joinAlias);
            }
            if (rootQuery.getRootEntityClass() != null) {
                RecordingContext.registerAlias(rootQuery.getRootEntityClass(), rootQuery.getStatement().getRootTableAlias());
            }
            if (onConsumer != null) {
                onConsumer.accept(onBuilder, rootProxy, joinProxy);
            }
        } finally {
            RecordingContext.clearRecording();
        }

        if (cte != null) {
            rootQuery.ensureCteAdded(cte);
        }
        if (onComplete != null) {
            onComplete.run();
        }
        return rootQuery;
    }

    public SelectBuilder<TRoot> on(BiConsumer<WhereBuilder<TJoin>, TJoin> onConsumer) {
        return on((on, root, join) -> {
            if (onConsumer != null) {
                onConsumer.accept(on, join);
            }
        });
    }

    public SelectBuilder<TRoot> on(Consumer<WhereBuilder<TJoin>> onConsumer) {
        return on((on, root, join) -> {
            if (onConsumer != null) {
                onConsumer.accept(on);
            }
        });
    }

    public String getJoinAlias() {
        return joinAlias;
    }
}
