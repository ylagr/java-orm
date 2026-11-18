package com.javaorm.core.sharding;

import java.lang.annotation.*;

/**
 * 标记实体启用分库分表路由处理。
 * 整合“静态切库”、“动态分库”与“动态分表”于一体，统一心智模型。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface JoSharding {

    /**
     * 1. 静态指定数据源名称（例如固定读写分离从库 "slave", "ds_readonly" 等）。
     * 若配置了该属性且未配置 dataSourceRouter，则所有对该实体的物理操作定向到该数据源。
     */
    String dataSource() default "";

    /**
     * 2. 动态分库路由器实现类（如按 key 取模路由到 ds_0, ds_1 等）。
     */
    Class<? extends DataSourceRouter<?>> dataSourceRouter() default DefaultDataSourceRouter.class;

    /**
     * 3. 动态分表路由器实现类（如按 key 取模路由到 t_order_0 .. 3，或按月分表）。
     */
    Class<? extends TableRouter<?>> tableRouter() default DefaultTableRouter.class;

    /**
     * 兼容旧版属性 router()，等价于 tableRouter()。
     */
    Class<? extends TableRouter<?>> router() default DefaultTableRouter.class;

    /**
     * 当 WHERE 条件中不包含分片键时，是否允许广播扫描全部物理库/分表。
     * 默认 false（安全第一，防止意外的全表全分片扫描导致慢 SQL 或 OOM）。
     */
    boolean allowFullScan() default false;
}
