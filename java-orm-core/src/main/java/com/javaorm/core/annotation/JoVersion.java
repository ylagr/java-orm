package com.javaorm.core.annotation;

import java.lang.annotation.*;

/**
 * 乐观锁版本号注解。
 * 标注在实体类的版本控制字段上（支持 Integer, Long, int, long, Short, short, Timestamp 等类型）。
 * 在更新实体时，框架将自动添加版本条件 WHERE version = ?，并将版本号递增。
 * 若更新时受影响行数为 0，将抛出 OptimisticLockException。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface JoVersion {
}
