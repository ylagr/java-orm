package com.javaorm.core.sharding;

import java.lang.annotation.*;

/**
 * 标记实体启用分库分表路由处理。
 * 只有标注了该注解或在查询/操作时显式指定路由器的实体，才会激活分片处理器。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface JoSharding {

    /**
     * 分表/分库路由器实现类
     */
    Class<? extends TableRouter<?>> router();

    /**
     * 当 WHERE 条件中不包含分片键时，是否允许广播扫描全部物理分表。
     * 默认 false（安全第一，防止意外的全表全分片扫描导致慢 SQL 或 OOM）。
     */
    boolean allowFullScan() default false;
}
