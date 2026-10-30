package com.javaorm.core.annotation;

import java.lang.annotation.*;

/**
 * 声明嵌入式值对象（Value Object / Embedded Object）。
 * 标注在实体类的字段上，将该对象的属性平铺（Flatten）到宿主实体的单张物理数据表中，无需任何 JOIN。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface JoValueObject {
}
