package com.javaorm.core.track;

import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.metadata.EntityMetadata;
import com.javaorm.core.metadata.EntityMetadataManager;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实体追踪与快照管理器。
 * 1. 采用 Identity 弱引用机制（IdentityWeakKey），基于对象物理内存地址（== 与 System.identityHashCode）进行哈希与比对，
 *    即使实体重写了 equals/hashCode 也绝不会产生状态冲突。
 * 2. 结合 ReferenceQueue 自动在 GC 后清理失效 Entry，杜绝任何内存泄漏。
 * 3. 线程安全，支持并发 attach / detach / isDirty 检索。
 */
public class TrackManager {

    private final Map<IdentityWeakKey, EntitySnapshot<?>> snapshotMap = new ConcurrentHashMap<>();
    private final ReferenceQueue<Object> refQueue = new ReferenceQueue<>();

    /**
     * 将实体附加到追踪管理器（记录当前状态作为基准快照）
     */
    @SuppressWarnings("unchecked")
    public <T> EntitySnapshot<T> attach(T entity, OrmConfig config) {
        if (entity == null) return null;
        cleanStaleEntries();
        Class<T> clazz = (Class<T>) entity.getClass();
        EntityMetadata metadata = EntityMetadataManager.get(clazz, config);
        return attach(entity, metadata);
    }

    /**
     * 将实体附加到追踪管理器（使用已解析的 EntityMetadata）
     */
    @SuppressWarnings("unchecked")
    public <T> EntitySnapshot<T> attach(T entity, EntityMetadata metadata) {
        if (entity == null || metadata == null) return null;
        cleanStaleEntries();
        IdentityWeakKey key = new IdentityWeakKey(entity, refQueue);
        EntitySnapshot<T> snapshot = new EntitySnapshot<>(entity, metadata);
        snapshotMap.put(key, snapshot);
        return snapshot;
    }

    /**
     * 批量附加实体
     */
    public <T> void attachAll(Collection<T> entities, OrmConfig config) {
        if (entities == null || entities.isEmpty()) return;
        cleanStaleEntries();
        for (T entity : entities) {
            if (entity != null) {
                attach(entity, config);
            }
        }
    }

    /**
     * 取消对指定实体的追踪
     */
    public void detach(Object entity) {
        if (entity == null) return;
        snapshotMap.remove(new LookupKey(entity));
    }

    /**
     * 批量取消实体追踪
     */
    public void detachAll(Collection<?> entities) {
        if (entities == null || entities.isEmpty()) return;
        for (Object entity : entities) {
            detach(entity);
        }
    }

    /**
     * 清空所有实体追踪快照
     */
    public void clear() {
        snapshotMap.clear();
    }

    /**
     * 获取指定实体的快照（若未开启追踪则返回 null）
     */
    @SuppressWarnings("unchecked")
    public <T> EntitySnapshot<T> getSnapshot(T entity) {
        if (entity == null) return null;
        return (EntitySnapshot<T>) snapshotMap.get(new LookupKey(entity));
    }

    /**
     * 判断指定实体当前是否处于追踪中
     */
    public boolean isTracking(Object entity) {
        if (entity == null) return false;
        return snapshotMap.containsKey(new LookupKey(entity));
    }

    /**
     * 判断指定实体是否已变脏（属性值与初始快照相比发生变更）
     */
    public boolean isDirty(Object entity) {
        EntitySnapshot<?> snapshot = getSnapshot(entity);
        return snapshot != null && snapshot.isDirty();
    }

    /**
     * 获取指定实体所有变脏的列与其最新值（以数据库列名为键）
     */
    public Map<String, Object> getDirtyColumns(Object entity) {
        EntitySnapshot<?> snapshot = getSnapshot(entity);
        return snapshot != null ? snapshot.getDirtyColumns() : Collections.emptyMap();
    }

    /**
     * 获取指定实体所有变脏的数据库列名集合
     */
    public Set<String> getDirtyColumnNames(Object entity) {
        EntitySnapshot<?> snapshot = getSnapshot(entity);
        return snapshot != null ? snapshot.getDirtyColumnNames() : Collections.emptySet();
    }

    /**
     * 获取指定实体所有变脏的 Java 属性名列表
     */
    public List<String> getDirtyProperties(Object entity) {
        EntitySnapshot<?> snapshot = getSnapshot(entity);
        return snapshot != null ? snapshot.getDirtyProperties() : Collections.emptyList();
    }

    /**
     * 当更新成功后，将快照刷新为当前最新状态
     */
    public void updateSnapshot(Object entity) {
        EntitySnapshot<?> snapshot = getSnapshot(entity);
        if (snapshot != null) {
            snapshot.updateSnapshot();
        }
    }

    /**
     * 当前被追踪的实体数量（包含待清理的软/弱引用）
     */
    public int size() {
        cleanStaleEntries();
        return snapshotMap.size();
    }

    /**
     * 从引用队列清理已经被 GC 回收的弱引用条目
     */
    private void cleanStaleEntries() {
        IdentityWeakKey staleKey;
        while ((staleKey = (IdentityWeakKey) refQueue.poll()) != null) {
            snapshotMap.remove(staleKey);
        }
    }

    // ==========================================
    // 弱引用键实现（基于对象真实内存地址）
    // ==========================================

    private static class IdentityWeakKey extends WeakReference<Object> {
        private final int hashCode;

        IdentityWeakKey(Object referent, ReferenceQueue<Object> q) {
            super(referent, q);
            this.hashCode = System.identityHashCode(referent);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj instanceof IdentityWeakKey) {
                Object myRef = this.get();
                Object otherRef = ((IdentityWeakKey) obj).get();
                return myRef != null && otherRef != null && myRef == otherRef;
            }
            if (obj instanceof LookupKey) {
                Object myRef = this.get();
                Object target = ((LookupKey) obj).target;
                return myRef != null && myRef == target;
            }
            return false;
        }
    }

    private static class LookupKey {
        private final Object target;
        private final int hashCode;

        LookupKey(Object target) {
            this.target = target;
            this.hashCode = System.identityHashCode(target);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj instanceof IdentityWeakKey) {
                Object otherRef = ((IdentityWeakKey) obj).get();
                return otherRef != null && target == otherRef;
            }
            if (obj instanceof LookupKey) {
                return target == ((LookupKey) obj).target;
            }
            return false;
        }
    }
}
