package com.javaorm.core.hydration;

import com.javaorm.core.annotation.RelationType;
import com.javaorm.core.ast.BinaryCondition;
import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.dialect.Dialect;
import com.javaorm.core.metadata.ColumnMetadata;
import com.javaorm.core.metadata.EntityMetadata;
import com.javaorm.core.metadata.EntityMetadataManager;
import com.javaorm.core.metadata.RelationMetadata;
import com.javaorm.core.operator.SqlOperator;
import com.javaorm.core.proxy.PropertyPath;
import com.javaorm.core.query.IncludeSpec;
import com.javaorm.core.query.Query;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.*;

/**
 * 关联数据装配器（参考 Easy-Query 的 Split/Batch IN Query 机制）。
 * 杜绝笛卡尔积，精准高效批量回填关联子实体及子集合，全面支持多层级嵌套与各级独立筛选。
 */
public class IncludeProcessor {

    public static <T> void processIncludes(Connection conn,
                                           List<T> rootEntities,
                                           EntityMetadata rootMetadata,
                                           List<IncludeSpec> includes,
                                           Dialect dialect,
                                           OrmConfig config,
                                           DataSource dataSource) throws Exception {
        if (rootEntities == null || rootEntities.isEmpty() || includes == null || includes.isEmpty()) {
            return;
        }

        for (IncludeSpec spec : includes) {
            PropertyPath includePath = spec.getPath();
            if (includePath == null) continue;

            String prop = includePath.getTerminalProperty();
            List<String> steps = includePath.getRelationSteps();
            if (prop == null || prop.isEmpty()) {
                if (!steps.isEmpty()) {
                    prop = steps.get(0);
                }
            }
            if (prop == null) continue;

            RelationMetadata relation = rootMetadata.getRelation(prop);
            if (relation == null) continue;

            Class<?> targetClass = relation.getTargetEntityClass();
            EntityMetadata targetMeta = EntityMetadataManager.get(targetClass, config);

            // 如果路径含有多层（例如 steps.size() > 1），构造下级 includeSpec
            IncludeSpec effectiveSpec = spec;
            if (steps.size() > 1) {
                List<String> remainingSteps = steps.subList(1, steps.size());
                PropertyPath subPath = new PropertyPath(targetClass);
                for (String step : remainingSteps) {
                    subPath.addRelationStep(step);
                }
                if (includePath.getTerminalProperty() != null && !remainingSteps.contains(includePath.getTerminalProperty())) {
                    subPath.setTerminal(includePath.getTerminalProperty(), includePath.getTerminalType());
                }
                effectiveSpec = new IncludeSpec(new PropertyPath(rootMetadata.getEntityClass()), targetQ -> {
                    spec.applyConfig(targetQ);
                    targetQ.addIncludeSpec(new IncludeSpec(subPath, spec.getQueryConfig()));
                });
            }

            if (relation.getRelationType() == RelationType.MANY_TO_ONE || relation.getRelationType() == RelationType.ONE_TO_ONE) {
                processManyToOne(conn, rootEntities, rootMetadata, relation, targetMeta, effectiveSpec, dialect, config, dataSource);
            } else if (relation.getRelationType() == RelationType.ONE_TO_MANY) {
                processOneToMany(conn, rootEntities, rootMetadata, relation, targetMeta, effectiveSpec, dialect, config, dataSource);
            }
        }
    }

    private static <T> void processManyToOne(Connection conn,
                                             List<T> rootEntities,
                                             EntityMetadata rootMetadata,
                                             RelationMetadata relation,
                                             EntityMetadata targetMeta,
                                             IncludeSpec includeSpec,
                                             Dialect dialect,
                                             OrmConfig config,
                                             DataSource dataSource) throws Exception {
        ColumnMetadata selfFkCol = rootMetadata.getColumnByProperty(relation.getSelfProperty());
        if (selfFkCol == null) return;

        // 收集所有外键 ID
        Set<Object> fkValues = new LinkedHashSet<>();
        for (T entity : rootEntities) {
            Object fk = selfFkCol.getValue(entity);
            if (fk != null) {
                fkValues.add(fk);
            }
        }
        if (fkValues.isEmpty()) return;

        ColumnMetadata targetPkCol = targetMeta.getColumnByProperty(relation.getTargetProperty());
        String targetColName = targetPkCol != null ? targetPkCol.getColumnName() : config.getNameConversion().convert(relation.getTargetProperty());

        // 使用 Query 构建目标查询，支持子查询、筛选、排序与递归包含
        @SuppressWarnings({"rawtypes", "unchecked"})
        Query<?> targetQuery = new Query(relation.getTargetEntityClass(), dataSource, dialect, config);
        if (includeSpec != null) {
            includeSpec.applyConfig(targetQuery);
        }

        targetQuery.getStatement().addWhereCondition(
                new BinaryCondition(targetQuery.getStatement().getRootTableAlias(), targetColName, SqlOperator.IN, fkValues));

        List<?> targets = targetQuery.list(conn);
        Map<Object, Object> targetMap = new HashMap<>();
        for (Object target : targets) {
            Object pkVal = targetPkCol != null ? targetPkCol.getValue(target) : null;
            if (pkVal != null) {
                targetMap.put(pkVal, target);
            }
        }

        // 装配回根实体
        for (T entity : rootEntities) {
            Object fk = selfFkCol.getValue(entity);
            if (fk != null) {
                Object matched = targetMap.get(fk);
                relation.setValue(entity, matched);
            }
        }
    }

    private static <T> void processOneToMany(Connection conn,
                                             List<T> rootEntities,
                                             EntityMetadata rootMetadata,
                                             RelationMetadata relation,
                                             EntityMetadata targetMeta,
                                             IncludeSpec includeSpec,
                                             Dialect dialect,
                                             OrmConfig config,
                                             DataSource dataSource) throws Exception {
        ColumnMetadata selfPkCol = rootMetadata.getIdColumn();
        if (selfPkCol == null) return;

        // 收集根实体主键 ID
        Set<Object> parentIds = new LinkedHashSet<>();
        for (T entity : rootEntities) {
            Object id = selfPkCol.getValue(entity);
            if (id != null) {
                parentIds.add(id);
            }
        }
        if (parentIds.isEmpty()) return;

        ColumnMetadata childFkCol = targetMeta.getColumnByProperty(relation.getTargetProperty());
        String childFkColName = childFkCol != null ? childFkCol.getColumnName() : config.getNameConversion().convert(relation.getTargetProperty());

        // 使用 Query 构建目标查询，支持下级筛选、排序与下级递归 include
        @SuppressWarnings({"rawtypes", "unchecked"})
        Query<?> targetQuery = new Query(relation.getTargetEntityClass(), dataSource, dialect, config);
        if (includeSpec != null) {
            includeSpec.applyConfig(targetQuery);
        }

        targetQuery.getStatement().addWhereCondition(
                new BinaryCondition(targetQuery.getStatement().getRootTableAlias(), childFkColName, SqlOperator.IN, parentIds));

        List<?> children = targetQuery.list(conn);
        Map<Object, List<Object>> groupedChildren = new HashMap<>();
        for (Object child : children) {
            Object fkVal = childFkCol != null ? childFkCol.getValue(child) : null;
            if (fkVal != null) {
                groupedChildren.computeIfAbsent(fkVal, k -> new ArrayList<>()).add(child);
            }
        }

        // 装配 List<Child> 回根实体
        for (T entity : rootEntities) {
            Object id = selfPkCol.getValue(entity);
            List<Object> list = groupedChildren.getOrDefault(id, new ArrayList<>());
            relation.setValue(entity, list);
        }
    }
}
