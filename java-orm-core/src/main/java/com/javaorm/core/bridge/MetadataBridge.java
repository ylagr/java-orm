package com.javaorm.core.bridge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 实体元数据桥接器接口（SPI）。
 * 允许外部桥接模块（如 MyBatis-Plus, EasyQuery, JPA 等）无侵入地接入元数据解析体系。
 * 框架在解析类和字段时，从已启用的桥接器列表中按优先级提取信息并自动去重合并。
 */
public interface MetadataBridge {

    /**
     * 桥接器优先级，数值越小优先级越高。
     * 内置默认的 JoMetadataBridge 优先级为 0。
     */
    default int getOrder() {
        return 100;
    }

    /**
     * 解析实体类的表名与 Schema。若此桥接器不匹配则返回 null。
     */
    default TableInfo resolveTable(Class<?> entityClass) {
        return null;
    }

    /**
     * 解析字段或 Getter 上的列信息。若此桥接器不匹配则返回 null。
     */
    default ColumnInfo resolveColumn(Class<?> entityClass, Field field, Method getter) {
        return null;
    }

    /**
     * 解析字段是否为值对象（Embedded / ValueObject）
     */
    default Boolean resolveValueObject(Class<?> entityClass, Field field, Method getter) {
        return null;
    }
}
