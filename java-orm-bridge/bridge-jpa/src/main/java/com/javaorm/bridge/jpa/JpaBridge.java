package com.javaorm.bridge.jpa;

import com.javaorm.core.bridge.ColumnInfo;
import com.javaorm.core.bridge.MetadataBridge;
import com.javaorm.core.bridge.TableInfo;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.Version;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static com.javaorm.core.bridge.MetadataBridge.getAnnotation;

/**
 * JPA (javax.persistence) 元数据桥接器
 * 支持 JPA 核心注解：
 * - @Table(name, schema)
 * - @Id 主键
 * - @GeneratedValue 自增键
 * - @Column(name)
 * - @Version 乐观锁
 * - @Transient 忽略字段
 */
public class JpaBridge implements MetadataBridge {

    @Override
    public int getOrder() {
        return -80; // 优先级高于内置默认 JoMetadataBridge (0)
    }

    @Override
    public TableInfo resolveTable(Class<?> entityClass) {
        Table table = entityClass.getAnnotation(Table.class);
        if (table == null) {
            return null;
        }

        TableInfo info = new TableInfo();
        String name = table.name().trim();
        if (!name.isEmpty()) {
            info.setTableName(name);
        }

        String schema = table.schema().trim();
        if (!schema.isEmpty()) {
            info.setSchema(schema);
        }

        // 解析 JPA @Index 索引配置
        try {
            javax.persistence.Index[] jpaIndexes = table.indexes();
            if (jpaIndexes != null) {
                for (javax.persistence.Index idx : jpaIndexes) {
                    String idxName = idx.name().trim();
                    boolean unique = idx.unique();
                    String colList = idx.columnList().trim();
                    java.util.List<String> cols = new java.util.ArrayList<>();
                    for (String part : colList.split(",")) {
                        String col = part.trim().split("\\s+")[0].trim();
                        if (!col.isEmpty()) {
                            cols.add(col);
                        }
                    }
                    info.addIndex(new com.javaorm.core.metadata.IndexMetadata(idxName, cols, unique));
                }
            }
        } catch (Throwable ignored) {
        }

        return info;
    }

    @Override
    public ColumnInfo resolveColumn(Class<?> entityClass, Field field, Method getter) {
        Id id = getAnnotation(field, getter, Id.class);
        GeneratedValue generatedValue = getAnnotation(field, getter, GeneratedValue.class);
        Column column = getAnnotation(field, getter, Column.class);
        Version version = getAnnotation(field, getter, Version.class);
        Transient transientAnnotation = getAnnotation(field, getter, Transient.class);

        if (id == null && generatedValue == null && column == null && version == null && transientAnnotation == null) {
            return null;
        }

        ColumnInfo info = new ColumnInfo();

        if (transientAnnotation != null) {
            info.setIgnored(true);
        }

        if (id != null) {
            info.setPrimaryKey(true);
        }

        if (generatedValue != null) {
            info.setGenerated(true);
        }

        if (column != null) {
            String colName = column.name().trim();
            if (!colName.isEmpty()) {
                info.setColumnName(colName);
            }
        }

        if (version != null) {
            info.setVersion(true);
        }

        return info;
    }
}
