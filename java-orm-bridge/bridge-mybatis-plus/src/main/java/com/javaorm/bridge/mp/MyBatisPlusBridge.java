package com.javaorm.bridge.mp;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.javaorm.bridge.mybatis.MyBatisTypeHandlerConverter;
import com.javaorm.core.bridge.ColumnInfo;
import com.javaorm.core.bridge.MetadataBridge;
import com.javaorm.core.bridge.TableInfo;
import com.javaorm.core.converter.ValueConverter;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.UnknownTypeHandler;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static com.javaorm.core.bridge.MetadataBridge.getAnnotation;

/**
 * MyBatis-Plus 元数据桥接器
 * 支持 MyBatis-Plus 核心注解与全局生态：
 * - @TableName(value, schema) 与全局表名前缀
 * - @TableId(value, type) 主键定义与自增识别
 * - @TableField(value, exist, fill, typeHandler) 字段映射、自动填充与自定义/枚举 TypeHandler 自动绑定
 * - @Version 乐观锁
 * - @TableLogic 逻辑删除（支持实体注解与 MP 全局逻辑删除配置）
 * - 优先感知并复用 MyBatis-Plus 官方 {@link TableInfoHelper} 缓存元数据
 */
public class MyBatisPlusBridge implements MetadataBridge {

    private static final Configuration FALLBACK_CONFIGURATION = new Configuration();

    @Override
    public int getOrder() {
        return -100; // 优先级高于内置默认 JoMetadataBridge (0)
    }

    @Override
    public TableInfo resolveTable(Class<?> entityClass) {
        if (entityClass == null) return null;

        TableName tableName = entityClass.getAnnotation(TableName.class);
        if (tableName == null) {
            return null;
        }

        TableInfo info = new TableInfo();

        // 1. 若 MP TableInfoHelper 尚未初始化当前实体，主动触发一次轻量初始化
        try {
            if (TableInfoHelper.getTableInfo(entityClass) == null) {
                TableInfoHelper.initTableInfo(
                        new org.apache.ibatis.builder.MapperBuilderAssistant(FALLBACK_CONFIGURATION, ""),
                        entityClass);
            }
        } catch (Throwable ignored) {
        }

        // 2. 优先尝试从 MyBatis-Plus 预初始化的 TableInfoHelper 中获取
        try {
            com.baomidou.mybatisplus.core.metadata.TableInfo mpTable = TableInfoHelper.getTableInfo(entityClass);
            if (mpTable != null) {
                if (mpTable.getTableName() != null && !mpTable.getTableName().isEmpty()) {
                    info.setTableName(mpTable.getTableName());
                }
            }
        } catch (Throwable ignored) {
        }

        // 3. 解析 @TableName 注解作为补充或独立支持
        String name = tableName.value().trim();
        if (!name.isEmpty() && info.getTableName() == null) {
            info.setTableName(name);
        }

        String schema = tableName.schema().trim();
        if (!schema.isEmpty() && info.getSchema() == null) {
            info.setSchema(schema);
        }

        return info;
    }

    @Override
    public ColumnInfo resolveColumn(Class<?> entityClass, Field field, Method getter) {
        TableField tableField = getAnnotation(field, getter, TableField.class);
        TableId tableId = getAnnotation(field, getter, TableId.class);
        Version version = getAnnotation(field, getter, Version.class);
        TableLogic tableLogic = getAnnotation(field, getter, TableLogic.class);

        // 检查是否有 MP 预缓存元数据
        com.baomidou.mybatisplus.core.metadata.TableInfo mpTable = null;
        TableFieldInfo mpFieldInfo = null;
        try {
            mpTable = TableInfoHelper.getTableInfo(entityClass);
            if (mpTable != null && field != null) {
                for (TableFieldInfo tfi : mpTable.getFieldList()) {
                    if (field.getName().equals(tfi.getProperty())) {
                        mpFieldInfo = tfi;
                        break;
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        ValueConverter<?, Object> converter = null;

        // 1. 优先解析 @TableField(typeHandler = ...) 或从 MP TableFieldInfo 中获取配置的 TypeHandler Class
        Class<?> thClass = null;
        if (tableField != null && tableField.typeHandler() != null && !UnknownTypeHandler.class.equals(tableField.typeHandler())) {
            thClass = tableField.typeHandler();
        } else if (mpFieldInfo != null && mpFieldInfo.getTypeHandler() != null && !UnknownTypeHandler.class.equals(mpFieldInfo.getTypeHandler())) {
            thClass = mpFieldInfo.getTypeHandler();
        }

        if (thClass != null) {
            TypeHandler<?> th = instantiateTypeHandler(field != null ? field.getType() : null, thClass);
            if (th != null) {
                converter = new MyBatisTypeHandlerConverter<>(th, field != null ? field.getType() : null);
            }
        }

        // 2. 如果未指定 TypeHandler 且字段是枚举类型，尝试创建 MyBatis-Plus 枚举转换器
        if (converter == null && field != null && field.getType().isEnum()) {
            converter = (ValueConverter<?, Object>) MyBatisPlusEnumConverter.tryCreate(field.getType());
        }

        if (tableField == null && tableId == null && version == null && tableLogic == null && converter == null) {
            return null;
        }

        ColumnInfo info = new ColumnInfo();
        if (converter != null) {
            info.setConverter(converter);
        }

        // 处理主键
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

        // 处理普通字段
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

        // 处理乐观锁
        if (version != null) {
            info.setVersion(true);
        }

        // 处理逻辑删除
        if (tableLogic != null) {
            info.setLogicDelete(true);
            String normalVal = tableLogic.value().trim();
            String delVal = tableLogic.delval().trim();

            if (normalVal.isEmpty() && mpFieldInfo != null && mpFieldInfo.getLogicNotDeleteValue() != null) {
                normalVal = mpFieldInfo.getLogicNotDeleteValue();
            }
            if (delVal.isEmpty() && mpFieldInfo != null && mpFieldInfo.getLogicDeleteValue() != null) {
                delVal = mpFieldInfo.getLogicDeleteValue();
            }

            // 若仍为空，尝试获取 MP 全局配置中的默认值
            if (normalVal.isEmpty() || delVal.isEmpty()) {
                try {
                    com.baomidou.mybatisplus.core.config.GlobalConfig.DbConfig dbConfig =
                            GlobalConfigUtils.getGlobalConfig(FALLBACK_CONFIGURATION).getDbConfig();
                    if (normalVal.isEmpty() && dbConfig.getLogicNotDeleteValue() != null) {
                        normalVal = dbConfig.getLogicNotDeleteValue();
                    }
                    if (delVal.isEmpty() && dbConfig.getLogicDeleteValue() != null) {
                        delVal = dbConfig.getLogicDeleteValue();
                    }
                } catch (Throwable ignored) {
                }
            }

            info.setLogicDeleteNormalValue(!normalVal.isEmpty() ? normalVal : "0");
            info.setLogicDeleteDeletedValue(!delVal.isEmpty() ? delVal : "1");
        }

        return info;
    }

    private TypeHandler<?> instantiateTypeHandler(Class<?> propertyType, Class<?> handlerClass) {
        if (handlerClass == null || UnknownTypeHandler.class.equals(handlerClass)) {
            return null;
        }
        try {
            // 1. 优先尝试 (Class.class) 构造函数（针对 MybatisEnumTypeHandler(Class) 或有参数的 TypeHandler）
            if (propertyType != null) {
                try {
                    Constructor<?> ctor = handlerClass.getConstructor(Class.class);
                    ctor.setAccessible(true);
                    return (TypeHandler<?>) ctor.newInstance(propertyType);
                } catch (NoSuchMethodException ignored) {
                }
            }
            // 2. 尝试无参构造函数
            Constructor<?> ctor = handlerClass.getDeclaredConstructor();
            ctor.setAccessible(true);
            return (TypeHandler<?>) ctor.newInstance();
        } catch (Throwable t) {
            throw new RuntimeException("Failed to instantiate TypeHandler: " + handlerClass.getName(), t);
        }
    }
}
