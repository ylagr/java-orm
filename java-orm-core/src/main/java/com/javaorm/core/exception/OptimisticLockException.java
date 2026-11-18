package com.javaorm.core.exception;

/**
 * 乐观并发控制锁异常。
 * 当实体更新时，若版本号不匹配（记录已被其他事务修改）导致更新行数为 0，抛出此异常。
 */
public class OptimisticLockException extends RuntimeException {

    private final Class<?> entityClass;
    private final Object id;
    private final Object version;

    public OptimisticLockException(String message) {
        super(message);
        this.entityClass = null;
        this.id = null;
        this.version = null;
    }

    public OptimisticLockException(Class<?> entityClass, Object id, Object version) {
        super(String.format("Optimistic lock error: Entity %s with id [%s] and version [%s] was updated or deleted by another transaction",
                entityClass != null ? entityClass.getName() : "Unknown", id, version));
        this.entityClass = entityClass;
        this.id = id;
        this.version = version;
    }

    public Class<?> getEntityClass() {
        return entityClass;
    }

    public Object getId() {
        return id;
    }

    public Object getVersion() {
        return version;
    }
}
