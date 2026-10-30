package com.javaorm.core.annotation;

import java.lang.annotation.*;

/**
 * 动态查询对象属性条件注解，用于 whereObject 自动生成 WHERE 条件。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface JoWhereCondition {

    enum Condition {
        EQ,
        NE,
        GT,
        GE,
        LT,
        LE,
        LIKE,
        LIKE_MATCH_LEFT,
        LIKE_MATCH_RIGHT,
        NOT_LIKE,
        IN,
        NOT_IN,
        RANGE_LEFT_OPEN,
        RANGE_LEFT_CLOSED,
        RANGE_RIGHT_OPEN,
        RANGE_RIGHT_CLOSED
    }

    /**
     * 映射到实体的属性名（默认与当前字段同名）
     */
    String propName() default "";

    /**
     * 条件匹配类型（默认 EQ）
     */
    Condition type() default Condition.EQ;
}
