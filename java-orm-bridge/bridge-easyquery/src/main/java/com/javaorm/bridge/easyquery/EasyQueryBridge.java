package com.javaorm.bridge.easyquery;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.ColumnIgnore;
import com.easy.query.core.annotation.Encryption;
import com.easy.query.core.annotation.LogicDelete;
import com.easy.query.core.annotation.ShardingDataSourceKey;
import com.easy.query.core.annotation.ShardingTableKey;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.annotation.ValueObject;
import com.easy.query.core.annotation.Version;
import com.javaorm.core.bridge.ColumnInfo;
import com.javaorm.core.bridge.MetadataBridge;
import com.javaorm.core.bridge.TableInfo;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * EasyQuery 元数据桥接器
 * 支持 EasyQuery 核心注解：
 * - @Table(value, schema)
 * - @Column(value, primaryKey, generatedKey, autoSelect)
 * - @ColumnIgnore
 * - @Version 乐观锁
 * - @LogicDelete 逻辑删除
 * - @ShardingTableKey / @ShardingDataSourceKey 分片键
 * - @ValueObject 值对象/VO嵌入
 * - @Encryption 加密
 */
public class EasyQueryBridge implements MetadataBridge {

    @Override
    public int getOrder() {
        return -90; // 优先级高于内置默认 JoMetadataBridge (0)
    }

    @Override
    public TableInfo resolveTable(Class<?> entityClass) {
        Table table = entityClass.getAnnotation(Table.class);
        if (table == null) {
            return null;
        }

        TableInfo info = new TableInfo();
        String name = table.value().trim();
        if (!name.isEmpty()) {
            info.setTableName(name);
        }

        String schema = table.schema().trim();
        if (!schema.isEmpty()) {
            info.setSchema(schema);
        }

        return info;
    }

    @Override
    public ColumnInfo resolveColumn(Class<?> entityClass, Field field, Method getter) {
        Column column = getAnnotation(field, getter, Column.class);
        ColumnIgnore columnIgnore = getAnnotation(field, getter, ColumnIgnore.class);
        Version version = getAnnotation(field, getter, Version.class);
        LogicDelete logicDelete = getAnnotation(field, getter, LogicDelete.class);
        ShardingTableKey shardingTableKey = getAnnotation(field, getter, ShardingTableKey.class);
        ShardingDataSourceKey shardingDataSourceKey = getAnnotation(field, getter, ShardingDataSourceKey.class);

        if (column == null && columnIgnore == null && version == null && logicDelete == null
                && shardingTableKey == null && shardingDataSourceKey == null) {
            return null;
        }

        ColumnInfo info = new ColumnInfo();

        if (columnIgnore != null) {
            info.setIgnored(true);
        }

        if (column != null) {
            String colName = column.value().trim();
            if (!colName.isEmpty()) {
                info.setColumnName(colName);
            }
            if (column.primaryKey()) {
                info.setPrimaryKey(true);
            }
            if (column.generatedKey()) {
                info.setGenerated(true);
            }
        }

        if (version != null) {
            info.setVersion(true);
        }

        if (logicDelete != null) {
            info.setLogicDelete(true);
        }

        if (shardingTableKey != null || shardingDataSourceKey != null) {
            info.setShardingKey(true);
        }

        return info;
    }

    @Override
    public Boolean resolveValueObject(Class<?> entityClass, Field field, Method getter) {
        ValueObject valueObject = getAnnotation(field, getter, ValueObject.class);
        if (valueObject != null) {
            return true;
        }
        return null;
    }

    private <A extends Annotation> A getAnnotation(Field field, Method getter, Class<A> annotationClass) {
        if (field != null) {
            A ann = field.getAnnotation(annotationClass);
            if (ann != null) return ann;
        }
        if (getter != null) {
            return getter.getAnnotation(annotationClass);
        }
        return null;
    }
}
