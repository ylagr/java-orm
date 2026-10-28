package com.javaorm.core.proxy;

import java.util.*;

/**
 * 线程级属性调用录制上下文（ThreadLocal）。
 * 支持单属性及多属性（参数列表）FIFO 队列录制与多表别名追踪。
 */
public class RecordingContext {

    private static final ThreadLocal<Queue<PropertyPath>> RECORDED_QUEUE = ThreadLocal.withInitial(LinkedList::new);
    private static final ThreadLocal<PropertyPath> CURRENT_PATH = new ThreadLocal<>();
    private static final ThreadLocal<Map<Class<?>, String>> ACTIVE_ALIASES = ThreadLocal.withInitial(HashMap::new);

    public static void start(Class<?> rootEntityClass) {
        RECORDED_QUEUE.get().clear();
        CURRENT_PATH.set(new PropertyPath(rootEntityClass));
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
        PropertyPath path = CURRENT_PATH.get();
        CURRENT_PATH.remove();
        return path;
    }

    public static PropertyPath finish() {
        Queue<PropertyPath> queue = RECORDED_QUEUE.get();
        if (!queue.isEmpty()) {
            PropertyPath polled = queue.poll();
            if (queue.isEmpty() && CURRENT_PATH.get() == null) {
                RECORDED_QUEUE.remove();
            }
            return polled;
        }
        PropertyPath path = CURRENT_PATH.get();
        CURRENT_PATH.remove();
        RECORDED_QUEUE.remove();
        return path;
    }

    public static List<PropertyPath> finishAll() {
        List<PropertyPath> result = new ArrayList<>();
        Queue<PropertyPath> queue = RECORDED_QUEUE.get();
        while (!queue.isEmpty()) {
            result.add(queue.poll());
        }
        PropertyPath path = CURRENT_PATH.get();
        if (path != null) {
            result.add(path);
            CURRENT_PATH.remove();
        }
        RECORDED_QUEUE.remove();
        return result;
    }

    public static void clear() {
        RECORDED_QUEUE.remove();
        CURRENT_PATH.remove();
        ACTIVE_ALIASES.remove();
    }
}
