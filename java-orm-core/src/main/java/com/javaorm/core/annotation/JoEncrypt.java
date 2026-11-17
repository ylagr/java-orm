package com.javaorm.core.annotation;

import com.javaorm.core.converter.EncryptionStrategy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 敏感数据透明加密注解。
 * 作用于实体属性上，入库时透明加密，出库时透明解密，在 WHERE 等值查询条件中自动加密参数。
 * 底层统一复用 ValueConverter 管线。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface JoEncrypt {

    /**
     * 指定加解密策略实现类。
     */
    Class<? extends EncryptionStrategy> value();
}
