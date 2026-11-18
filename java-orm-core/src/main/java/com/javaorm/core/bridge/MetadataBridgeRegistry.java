package com.javaorm.core.bridge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 实体元数据桥接器注册中心与解析调度引擎。
 * 1. 默认包含核心内置的 JoMetadataBridge。
 * 2. 自动通过 ServiceLoader 加载 classpath 上可用的桥接模块（如 MyBatis-Plus、EasyQuery、JPA 桥接）。
 * 3. 严格按照优先级合并属性，保证同一字段去重并生成唯一确定性的元数据。
 */
public class MetadataBridgeRegistry {

    private static final List<MetadataBridge> BRIDGES = new CopyOnWriteArrayList<>();

    static {
        reset();
    }

    public static synchronized void reset() {
        BRIDGES.clear();
        // 1. 核心内置默认桥接器（Order 0）
        BRIDGES.add(new JoMetadataBridge());

        // 2. 通过 ServiceLoader 自动探测外部桥接模块
        try {
            ServiceLoader<MetadataBridge> loader = ServiceLoader.load(MetadataBridge.class);
            for (MetadataBridge bridge : loader) {
                if (!(bridge instanceof JoMetadataBridge)) {
                    BRIDGES.add(bridge);
                }
            }
        } catch (Throwable ignored) {
        }
        sortBridges();
    }

    public static synchronized void register(MetadataBridge bridge) {
        if (bridge != null && !BRIDGES.contains(bridge)) {
            BRIDGES.add(bridge);
            sortBridges();
        }
    }

    private static void sortBridges() {
        BRIDGES.sort(Comparator.comparingInt(MetadataBridge::getOrder));
    }

    public static List<MetadataBridge> getBridges() {
        return Collections.unmodifiableList(BRIDGES);
    }

    /**
     * 从支持列表中提取表名与 Schema，遇到首个有效匹配即返回
     */
    public static TableInfo resolveTable(Class<?> entityClass) {
        for (MetadataBridge bridge : BRIDGES) {
            TableInfo info = bridge.resolveTable(entityClass);
            if (info != null && info.getTableName() != null && !info.getTableName().isEmpty()) {
                return info;
            }
        }
        return null;
    }

    /**
     * 从支持列表中按优先级提取列信息，合并非空属性并保证去重
     */
    public static ColumnInfo resolveColumn(Class<?> entityClass, Field field, Method getter) {
        ColumnInfo merged = null;
        for (MetadataBridge bridge : BRIDGES) {
            ColumnInfo info = bridge.resolveColumn(entityClass, field, getter);
            if (info != null) {
                if (merged == null) {
                    merged = new ColumnInfo();
                }
                merged.merge(info);
            }
        }
        return merged;
    }

    /**
     * 判断是否为值对象（Embedded / ValueObject）
     */
    public static boolean resolveValueObject(Class<?> entityClass, Field field, Method getter) {
        for (MetadataBridge bridge : BRIDGES) {
            Boolean isVo = bridge.resolveValueObject(entityClass, field, getter);
            if (Boolean.TRUE.equals(isVo)) {
                return true;
            }
        }
        return false;
    }
}
