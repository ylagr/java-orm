package com.javaorm.core.annotation;

import com.javaorm.core.converter.JsonProvider;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记该字段以 JSON 字符串形式持久化到数据库单列中。
 * 出入库由 JsonProvider SPI 自动序列化/反序列化，自动保留泛型类型信息（支持如 List&lt;Item&gt;）。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface JoJson {

    /**
     * 可选：指定特定的 JsonProvider 实现类。若不指定则使用全局默认 Provider。
     */
    Class<? extends JsonProvider> provider() default JsonProvider.class;
}
