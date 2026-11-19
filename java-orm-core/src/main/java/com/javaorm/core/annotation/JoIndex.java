package com.javaorm.core.annotation;

import java.lang.annotation.*;

/**
 * 数据库索引定义注解（支持标注在类上定义单列/复合索引，或直接标注在字段/getter上定义单列索引）。
 * 支持 JPA @Index、EasyQuery @Index 以及 MyBatis-Plus 生态的索引声明。
 */
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(JoIndexes.class)
public @interface JoIndex {

    /**
     * 索引名称（若未指定，系统自动按照规范生成，如 idx_表名_列名 或 uniq_表名_列名）
     */
    String name() default "";

    /**
     * 索引包含的物理列名列表（按复合索引列顺序排列）。
     * 当标注在实体类上时，需指定一个或多个物理列名；
     * 当标注在字段或方法上时，可省略，默认使用当前字段映射的物理列名。
     */
    String[] columns() default {};

    /**
     * 是否为唯一索引（UNIQUE INDEX），默认为 false 普通索引
     */
    boolean unique() default false;
}
