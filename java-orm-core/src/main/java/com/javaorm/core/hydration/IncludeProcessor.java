package com.javaorm.core.hydration;

import com.javaorm.core.annotation.RelationType;
import com.javaorm.core.ast.BinaryCondition;
import com.javaorm.core.ast.SelectStatement;
import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.dialect.Dialect;
import com.javaorm.core.metadata.ColumnMetadata;
import com.javaorm.core.metadata.EntityMetadata;
import com.javaorm.core.metadata.EntityMetadataManager;
import com.javaorm.core.metadata.RelationMetadata;
import com.javaorm.core.operator.SqlOperator;
import com.javaorm.core.proxy.PropertyPath;

import java.sql.Connection;
import java.util.*;

/**
 * 关联数据装配器（参考 Easy-Query 的 Split/Batch IN Query 机制）。
 * 杜绝笛卡尔积，精准高效批量回填关联子实体及子集合。
 */
public class IncludeProcessor {

    public static <T> void processIncludes(Connection conn,
                                           List<T> rootEntities,
                                           EntityMetadata rootMetadata,
                                           List<PropertyPath> includes,
                                           Dialect dialect,
                                           OrmConfig config) throws Exception {
        if (rootEntities == null || rootEntities.isEmpty() || includes == null || includes.isEmpty()) {
            return;
        }

        for (PropertyPath includePath : includes) {
            String prop = includePath.getTerminalProperty();
            if (prop == null || prop.isEmpty()) {
                if (!includePath.getRelationSteps().isEmpty()) {
                    prop = includePath.getRelationSteps().get(0);
                }
            }
            if (prop == null) continue;

            RelationMetadata relation = rootMetadata.getRelation(prop);
            if (relation == null) continue;

            Class<?> targetClass = relation.getTargetEntityClass();
            EntityMetadata targetMeta = EntityMetadataManager.get(targetClass, config);

            if (relation.getRelationType() == RelationType.MANY_TO_ONE || relation.getRelationType() == RelationType.ONE_TO_ONE) {
                processManyToOne(conn, rootEntities, rootMetadata, relation, targetMeta, dialect, config);
            } else if (relation.getRelationType() == RelationType.ONE_TO_MANY) {
                processOneToMany(conn, rootEntities, rootMetadata, relation, targetMeta, dialect, config);
            }
        }
    }

    private static <T> void processManyToOne(Connection conn,
                                             List<T> rootEntities,
                                             EntityMetadata rootMetadata,
                                             RelationMetadata relation,
                                             EntityMetadata targetMeta,
                                             Dialect dialect,
                                             OrmConfig config) throws Exception {
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

        // 执行分片查询：SELECT * FROM targetTable WHERE id IN (...)
        SelectStatement stmt = new SelectStatement(targetMeta.getEntityClass(), targetMeta.getTableName(), "t0");
        stmt.addWhereCondition(new BinaryCondition("t0", targetColName, SqlOperator.IN, fkValues));
        if (targetMeta.hasLogicDelete()) {
            ColumnMetadata ld = targetMeta.getLogicDeleteColumn();
            stmt.addWhereCondition(new BinaryCondition("t0", ld.getColumnName(), SqlOperator.EQ, ld.getLogicDeleteNormalValue()));
        }

        List<?> targets = EntityHydrator.queryList(conn, stmt, targetMeta, dialect, config);
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
                                             Dialect dialect,
                                             OrmConfig config) throws Exception {
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

        // 执行查询：SELECT * FROM childTable WHERE parent_id IN (...)
        SelectStatement stmt = new SelectStatement(targetMeta.getEntityClass(), targetMeta.getTableName(), "t0");
        stmt.addWhereCondition(new BinaryCondition("t0", childFkColName, SqlOperator.IN, parentIds));
        if (targetMeta.hasLogicDelete()) {
            ColumnMetadata ld = targetMeta.getLogicDeleteColumn();
            stmt.addWhereCondition(new BinaryCondition("t0", ld.getColumnName(), SqlOperator.EQ, ld.getLogicDeleteNormalValue()));
        }

        List<?> children = EntityHydrator.queryList(conn, stmt, targetMeta, dialect, config);
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
