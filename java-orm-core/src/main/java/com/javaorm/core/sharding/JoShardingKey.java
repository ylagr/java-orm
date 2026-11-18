package com.javaorm.core.sharding;

import java.lang.annotation.*;

/**
 * 标记实体的分片键字段（如 order_id, user_id, create_time 等）。
 * 分片处理器在编译期解析 WHERE 条件和插入实体时，将优先提取该字段的值计算物理路由目标。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface JoShardingKey {
}
