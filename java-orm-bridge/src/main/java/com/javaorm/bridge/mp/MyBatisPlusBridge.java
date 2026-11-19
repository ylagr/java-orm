package com.javaorm.bridge.mp;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.javaorm.core.bridge.ColumnInfo;
import com.javaorm.core.bridge.MetadataBridge;
import com.javaorm.core.bridge.TableInfo;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * MyBatis-Plus 元数据桥接器
 * 支持 MyBatis-Plus 核心注解：
 * - @TableName(value, schema)
 * - @TableId(value, type)
 * - @TableField(value, exist)
 * - @Version 乐观锁
 * - @TableLogic 逻辑删除
 */
public class MyBatisPlusBridge implements MetadataBridge {

    @Override
    public int getOrder() {
        return -100; // 优先级高于内置默认 JoMetadataBridge (0)
    }

    @Override
    public TableInfo resolveTable(Class<?> entityClass) {
        TableName tableName = entityClass.getAnnotation(TableName.class);
        if (tableName == null) {
            return null;
        }

        TableInfo info = new TableInfo();
        String name = tableName.value().trim();
        if (!name.isEmpty()) {
            info.setTableName(name);
        }

        String schema = tableName.schema().trim();
        if (!schema.isEmpty()) {
            info.setSchema(schema);
        }

        try {
            com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                    new org.apache.ibatis.builder.MapperBuilderAssistant(new org.apache.ibatis.session.Configuration(), ""),
                    entityClass);
        } catch (Throwable ignored) {
        }

        return info;
    }

    @Override
    public ColumnInfo resolveColumn(Class<?> entityClass, Field field, Method getter) {
        TableField tableField = getAnnotation(field, getter, TableField.class);
        TableId tableId = getAnnotation(field, getter, TableId.class);
        Version version = getAnnotation(field, getter, Version.class);
        TableLogic tableLogic = getAnnotation(field, getter, TableLogic.class);

        MyBatisPlusEnumConverter enumConverter = null;
        if (field != null && field.getType().isEnum()) {
            enumConverter = MyBatisPlusEnumConverter.tryCreate(field.getType());
        }

        if (tableField == null && tableId == null && version == null && tableLogic == null && enumConverter == null) {
            return null;
        }

        ColumnInfo info = new ColumnInfo();
        if (enumConverter != null) {
            info.setConverter(enumConverter);
        }

        if (tableId != null) {
            info.setPrimaryKey(true);
            String colName = tableId.value().trim();
            if (!colName.isEmpty()) {
                info.setColumnName(colName);
            }
            if (tableId.type() == IdType.AUTO) {
                info.setGenerated(true);
            }
        }

        if (tableField != null) {
            if (!tableField.exist()) {
                info.setIgnored(true);
            }
            String colName = tableField.value().trim();
            if (!colName.isEmpty()) {
                info.setColumnName(colName);
            }
            if (tableField.fill() != null && tableField.fill() != com.baomidou.mybatisplus.annotation.FieldFill.DEFAULT) {
                info.setAutoFill(true);
                if (tableField.fill() == com.baomidou.mybatisplus.annotation.FieldFill.INSERT) {
                    info.setAutoFillType(com.javaorm.core.annotation.AutoFillType.INSERT);
                } else if (tableField.fill() == com.baomidou.mybatisplus.annotation.FieldFill.UPDATE) {
                    info.setAutoFillType(com.javaorm.core.annotation.AutoFillType.UPDATE);
                } else if (tableField.fill() == com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE) {
                    info.setAutoFillType(com.javaorm.core.annotation.AutoFillType.INSERT_UPDATE);
                }
            }
        }

        if (version != null) {
            info.setVersion(true);
        }

        if (tableLogic != null) {
            info.setLogicDelete(true);
            String normalVal = tableLogic.value().trim();
            if (!normalVal.isEmpty()) {
                info.setLogicDeleteNormalValue(normalVal);
            }
            String delVal = tableLogic.delval().trim();
            if (!delVal.isEmpty()) {
                info.setLogicDeleteDeletedValue(delVal);
            }
        }

        return info;
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
