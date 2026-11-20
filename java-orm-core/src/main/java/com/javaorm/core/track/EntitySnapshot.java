package com.javaorm.core.track;

import com.javaorm.core.metadata.ColumnMetadata;
import com.javaorm.core.metadata.EntityMetadata;

import java.lang.ref.WeakReference;
import java.math.BigDecimal;
import java.util.*;

/**
 * 实体状态快照。
 * 记录实体从数据库查询加载或显式 attach 时的初始状态，用于在 update 时进行变脏检测（Dirty Checking）。
 *
 * @param <T> 实体类型
 */
public class EntitySnapshot<T> {

    private final WeakReference<T> entityRef;
    private final EntityMetadata metadata;
    private final Map<String, Object> originalColumnValues = new HashMap<>();

    public EntitySnapshot(T entity, EntityMetadata metadata) {
        if (entity == null) {
            throw new IllegalArgumentException("Entity cannot be null");
        }
        if (metadata == null) {
            throw new IllegalArgumentException("EntityMetadata cannot be null");
        }
        this.entityRef = new WeakReference<>(entity);
        this.metadata = metadata;
        takeSnapshot(entity);
    }

    /**
     * 捕获当前实体的状态保存为快照
     */
    public synchronized void takeSnapshot(T entity) {
        if (entity == null) return;
        originalColumnValues.clear();
        for (ColumnMetadata col : metadata.getColumns()) {
            if (col.isIgnored()) {
                continue;
            }
            Object rawVal = col.getValue(entity);
            Object snapVal = createSnapshotValue(col, rawVal);
            originalColumnValues.put(col.getColumnName(), snapVal);
        }
    }

    /**
     * 刷新快照（使用当前引用的实体最新状态）
     */
    public synchronized void updateSnapshot() {
        T entity = entityRef.get();
        if (entity != null) {
            takeSnapshot(entity);
        }
    }

    /**
     * 获取实体弱引用
     */
    public T getEntity() {
        return entityRef.get();
    }

    /**
     * 获取实体元数据
     */
    public EntityMetadata getMetadata() {
        return metadata;
    }

    /**
     * 检查当前实体是否变脏（即是否有任何可持久化属性被修改）
     */
    public synchronized boolean isDirty() {
        T entity = entityRef.get();
        if (entity == null) return false;

        for (ColumnMetadata col : metadata.getColumns()) {
            if (col.isPrimaryKey() || col.isIgnored() || col.isVersion()) {
                continue;
            }
            if (isColumnDirty(col, entity)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取所有变脏的列与其最新值（以数据库列名为键）
     */
    public synchronized Map<String, Object> getDirtyColumns() {
        T entity = entityRef.get();
        if (entity == null) return Collections.emptyMap();

        Map<String, Object> dirty = new LinkedHashMap<>();
        for (ColumnMetadata col : metadata.getColumns()) {
            if (col.isPrimaryKey() || col.isIgnored() || col.isVersion()) {
                continue;
            }
            if (isColumnDirty(col, entity)) {
                dirty.put(col.getColumnName(), col.getValue(entity));
            }
        }
        return dirty;
    }

    /**
     * 获取所有变脏的数据库列名集合
     */
    public synchronized Set<String> getDirtyColumnNames() {
        return getDirtyColumns().keySet();
    }

    /**
     * 获取所有变脏的 Java 属性名列表
     */
    public synchronized List<String> getDirtyProperties() {
        T entity = entityRef.get();
        if (entity == null) return Collections.emptyList();

        List<String> dirtyProps = new ArrayList<>();
        for (ColumnMetadata col : metadata.getColumns()) {
            if (col.isPrimaryKey() || col.isIgnored() || col.isVersion()) {
                continue;
            }
            if (isColumnDirty(col, entity)) {
                dirtyProps.add(col.getPropertyName());
            }
        }
        return dirtyProps;
    }

    /**
     * 获取指定列的快照原始值
     */
    public synchronized Object getOriginalValue(String columnName) {
        return originalColumnValues.get(columnName);
    }

    private boolean isColumnDirty(ColumnMetadata col, T entity) {
        Object originalVal = originalColumnValues.get(col.getColumnName());
        Object currentVal = col.getValue(entity);
        return isValueChanged(col, originalVal, currentVal);
    }

    private Object createSnapshotValue(ColumnMetadata col, Object rawVal) {
        if (rawVal == null) return null;

        // 如果配置了转换器（如 JSON 转换器、枚举转换器等），保存序列化后的表现形式以支持复杂对象精准比对
        if (col.getConverter() != null) {
            return col.serializeParam(rawVal);
        }

        // 数组克隆
        if (rawVal instanceof byte[]) {
            return ((byte[]) rawVal).clone();
        }
        if (rawVal instanceof char[]) {
            return ((char[]) rawVal).clone();
        }
        if (rawVal instanceof int[]) {
            return ((int[]) rawVal).clone();
        }
        if (rawVal instanceof long[]) {
            return ((long[]) rawVal).clone();
        }
        if (rawVal instanceof Object[]) {
            return ((Object[]) rawVal).clone();
        }

        return rawVal;
    }

    private boolean isValueChanged(ColumnMetadata col, Object originalVal, Object currentVal) {
        if (originalVal == null && currentVal == null) {
            return false;
        }
        if (originalVal == null || currentVal == null) {
            return true;
        }

        // 转换器处理后的序列化比较
        if (col.getConverter() != null) {
            Object serializedCurrent = col.serializeParam(currentVal);
            return !Objects.equals(originalVal, serializedCurrent);
        }

        // BigDecimal 特殊处理（10.0 与 10.00 数值相等）
        if (originalVal instanceof BigDecimal && currentVal instanceof BigDecimal) {
            return ((BigDecimal) originalVal).compareTo((BigDecimal) currentVal) != 0;
        }

        // 数组深度比较
        if (originalVal.getClass().isArray() && currentVal.getClass().isArray()) {
            return !Objects.deepEquals(originalVal, currentVal);
        }

        return !Objects.equals(originalVal, currentVal);
    }
}
