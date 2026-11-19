package com.javaorm.core.handler;

import com.javaorm.core.metadata.EntityMetadata;

/**
 * 实体属性自动填充处理器（类似 MyBatis-Plus 的 MetaObjectHandler 与 JPA 审计机制）。
 * 在执行 insert / update 时自动为标注了 @JoAutoFill 或桥接了 MP @TableField(fill = ...) 的字段赋值。
 */
public interface AutoFillHandler {

    /**
     * 插入记录时自动填充（如 create_time, create_by）
     *
     * @param entity   当前持久化实体对象
     * @param metadata 实体元数据
     */
    void insertFill(Object entity, EntityMetadata metadata);

    /**
     * 更新记录时自动填充（如 update_time, update_by）
     *
     * @param entity   当前更新实体对象
     * @param metadata 实体元数据
     */
    void updateFill(Object entity, EntityMetadata metadata);
}
