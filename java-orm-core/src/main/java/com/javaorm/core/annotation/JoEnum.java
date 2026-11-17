package com.javaorm.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 实体枚举字段专用配置注解。
 * 用于支持第三方不可修改源码的枚举、指定特定的取值属性、切换 Name/Ordinal 模式以及设置未知值兜底策略。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface JoEnum {

    /**
     * 枚举存储模式，默认 AUTO（有 @JoEnumValue 走字段，无则走 name()）。
     */
    EnumMappingType mode() default EnumMappingType.AUTO;

    /**
     * 当 mode 为 PROPERTY 或需要提取特定属性时指定该属性名称（如 "code"、"id"）。
     */
    String property() default "";

    /**
     * 当从数据库读取到未知/脏数据值时，兜底降级使用的枚举名称（如 "UNKNOWN"），防止直接抛出异常或反序列化为 null。
     * 为空字符串时表示不使用兜底，未匹配到返回 null。
     */
    String fallback() default "";
}
