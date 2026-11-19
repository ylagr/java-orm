package com.javaorm.core.ast;

/**
 * 数据库查询行级悲观锁模式。
 */
public enum LockMode {
    /**
     * 不加行级锁（默认）
     */
    NONE,

    /**
     * 阻塞加排他行级锁：FOR UPDATE
     */
    FOR_UPDATE,

    /**
     * 非阻塞加排他行级锁（遇锁立即抛异常）：FOR UPDATE NOWAIT
     */
    FOR_UPDATE_NOWAIT,

    /**
     * 跳过已被锁定的行（常用于高并发分布式任务队列出队抢占）：FOR UPDATE SKIP LOCKED
     */
    FOR_UPDATE_SKIP_LOCKED
}
