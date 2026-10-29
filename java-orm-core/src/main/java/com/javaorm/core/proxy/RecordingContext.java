package com.javaorm.core.proxy;

import java.util.*;

/**
 * 线程级属性调用录制上下文（ThreadLocal）。
 * 支持单属性及多属性（参数列表）FIFO 队列录制与多表别名追踪。
 */
public class RecordingContext {

    private static final ThreadLocal<LinkedList<PropertyPath>> RECORDED_QUEUE = ThreadLocal.withInitial(LinkedList::new);
    private static final ThreadLocal<PropertyPath> CURRENT_PATH = new ThreadLocal<>();
    private static final ThreadLocal<Map<Class<?>, String>> ACTIVE_ALIASES = ThreadLocal.withInitial(HashMap::new);

    public static void start(Class<?> rootEntityClass) {
        clearRecording(rootEntityClass);
        CURRENT_PATH.set(new PropertyPath(rootEntityClass));
    }

    public static void ensureStarted(Class<?> rootEntityClass) {
        PropertyPath cur = CURRENT_PATH.get();
        if (cur == null) {
            CURRENT_PATH.set(new PropertyPath(rootEntityClass));
        } else if (cur.getTerminalProperty() != null) {
            // 前一个属性已完成录制，推入队列，开启新实体的录制
            RECORDED_QUEUE.get().offer(cur);
            CURRENT_PATH.set(new PropertyPath(rootEntityClass));
        }
    }

    public static PropertyPath get() {
        return CURRENT_PATH.get();
    }

    public static void registerAlias(Class<?> entityClass, String alias) {
        if (entityClass != null && alias != null) {
            ACTIVE_ALIASES.get().put(entityClass, alias);
        }
    }

    public static String getAlias(Class<?> entityClass) {
        return entityClass != null ? ACTIVE_ALIASES.get().get(entityClass) : null;
    }

    public static boolean hasAlias(String alias) {
        if (alias == null) return false;
        return ACTIVE_ALIASES.get().containsValue(alias);
    }

    public static int getActiveAliasCount() {
        return ACTIVE_ALIASES.get().size();
    }

    public static void clearAliases() {
        ACTIVE_ALIASES.remove();
    }

    public static void clearRecording() {
        RECORDED_QUEUE.remove();
        CURRENT_PATH.remove();
    }

    public static void clearRecording(Class<?> entityClass) {
        if (entityClass == null) {
            clearRecording();
            return;
        }
        LinkedList<PropertyPath> queue = RECORDED_QUEUE.get();
        queue.removeIf(p -> p.getRootEntityClass() == entityClass);
        PropertyPath cur = CURRENT_PATH.get();
        if (cur != null && cur.getRootEntityClass() == entityClass) {
            CURRENT_PATH.remove();
        }
    }

    public static void recordRelation(String relationProperty) {
        PropertyPath path = CURRENT_PATH.get();
        if (path != null) {
            path.addRelationStep(relationProperty);
        }
    }

    public static void recordTerminal(String terminalProperty, Class<?> terminalType) {
        PropertyPath path = CURRENT_PATH.get();
        if (path != null) {
            if (path.getTerminalProperty() != null) {
                // 前一个属性已完成录制，推入队列，开启新属性
                RECORDED_QUEUE.get().offer(path);
                path = new PropertyPath(path.getRootEntityClass());
                CURRENT_PATH.set(path);
            }
            path.setTerminal(terminalProperty, terminalType);
        }
    }

    public static PropertyPath finishCurrent() {
        return finishCurrent(null);
    }

    public static PropertyPath finishCurrent(Class<?> entityClass) {
        PropertyPath path = CURRENT_PATH.get();
        if (path != null && (entityClass == null || path.getRootEntityClass() == entityClass)) {
            CURRENT_PATH.remove();
            return path;
        }
        return null;
    }

    public static PropertyPath finish() {
        return finish((Class<?>) null);
    }

    public static PropertyPath finish(Class<?> entityClass) {
        PropertyPath cur = CURRENT_PATH.get();
        if (cur != null && (entityClass == null || cur.getRootEntityClass() == entityClass)) {
            CURRENT_PATH.remove();
            return cur;
        }

        LinkedList<PropertyPath> queue = RECORDED_QUEUE.get();
        if (entityClass == null) {
            if (!queue.isEmpty()) {
                PropertyPath polled = queue.poll();
                if (queue.isEmpty() && CURRENT_PATH.get() == null) {
                    RECORDED_QUEUE.remove();
                }
                return polled;
            }
            return null;
        }

        // 针对指定实体查找
        for (Iterator<PropertyPath> it = queue.iterator(); it.hasNext(); ) {
            PropertyPath p = it.next();
            if (p.getRootEntityClass() == entityClass) {
                it.remove();
                if (queue.isEmpty() && CURRENT_PATH.get() == null) {
                    RECORDED_QUEUE.remove();
                }
                return p;
            }
        }
        return null;
    }

    /**
     * 获取最近录制的指定数量的属性（按调用先后正序返回），常用于多参数函数如 concat(a, b)、partitionBy(a, b)
     */
    public static List<PropertyPath> finishLast(int count) {
        return finishLast(null, count);
    }

    public static List<PropertyPath> finishLast(Class<?> entityClass, int count) {
        List<PropertyPath> list = new ArrayList<>();
        if (count <= 0) return list;

        PropertyPath cur = CURRENT_PATH.get();
        if (cur == null || (entityClass != null && cur.getRootEntityClass() != entityClass)) {
            // 没有当前属性调用，说明不是通过 proxy getter 调用的，绝不偷取外层的 RECORDED_QUEUE
            return list;
        }

        CURRENT_PATH.remove();
        list.add(cur);
        count--;

        if (count > 0) {
            LinkedList<PropertyPath> queue = RECORDED_QUEUE.get();
            List<PropertyPath> fromQueue = new ArrayList<>();
            for (int i = queue.size() - 1; i >= 0 && count > 0; i--) {
                PropertyPath p = queue.get(i);
                if (entityClass == null || p.getRootEntityClass() == entityClass) {
                    fromQueue.add(p);
                    queue.remove(i);
                    count--;
                }
            }
            if (queue.isEmpty() && CURRENT_PATH.get() == null) {
                RECORDED_QUEUE.remove();
            }
            Collections.reverse(fromQueue);
            fromQueue.addAll(list);
            return fromQueue;
        }

        return list;
    }

    public static List<PropertyPath> finishAll() {
        return finishAll(null);
    }

    public static List<PropertyPath> finishAll(Class<?> entityClass) {
        List<PropertyPath> result = new ArrayList<>();
        LinkedList<PropertyPath> queue = RECORDED_QUEUE.get();
        for (Iterator<PropertyPath> it = queue.iterator(); it.hasNext(); ) {
            PropertyPath p = it.next();
            if (entityClass == null || p.getRootEntityClass() == entityClass) {
                result.add(p);
                it.remove();
            }
        }
        PropertyPath path = CURRENT_PATH.get();
        if (path != null && (entityClass == null || path.getRootEntityClass() == entityClass)) {
            result.add(path);
            CURRENT_PATH.remove();
        }
        if (queue.isEmpty() && CURRENT_PATH.get() == null) {
            RECORDED_QUEUE.remove();
        }
        return result;
    }

    public static void clear() {
        RECORDED_QUEUE.remove();
        CURRENT_PATH.remove();
        ACTIVE_ALIASES.remove();
    }
}
