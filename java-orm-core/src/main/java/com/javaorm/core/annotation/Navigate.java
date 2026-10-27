package com.javaorm.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 实体导航属性注解（支持一对一、多对一、一对多等关联关系）。
 * 也可无缝使用标准 JPA @ManyToOne / @OneToMany / @JoinColumn 注解。
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Navigate {
    /**
     * 关联类型
     */
    RelationType value() default RelationType.MANY_TO_ONE;

    /**
     * 当前实体中用于关联的属性名称（例如 "teacherId"）
     */
    String selfProperty() default "";

    /**
     * 目标实体中被关联的属性名称（例如 "id"）
     */
    String targetProperty() default "";
}
