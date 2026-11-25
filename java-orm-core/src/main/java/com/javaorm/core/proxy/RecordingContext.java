package com.javaorm.core.proxy;

import java.util.*;

/**
 * 线程级属性调用录制上下文（ThreadLocal）。
 * 支持单属性及多属性（参数列表）FIFO 队列录制与多表别名追踪。
 */
public class RecordingContext {

    private static final ThreadLocal<LinkedList<PropertyPath>> RECORDED_QUEUE = new ThreadLocal<>();
    private static final ThreadLocal<PropertyPath> CURRENT_PATH = new ThreadLocal<>();
    private static final ThreadLocal<Map<Class<?>, String>> ACTIVE_ALIASES = new ThreadLocal<>();
    private static final ThreadLocal<Map<Object, String>> PROXY_ALIASES = new ThreadLocal<>();
    private static final Map<Object, String> GLOBAL_PROXY_ALIASES = new java.util.concurrent.ConcurrentHashMap<>();

    private static LinkedList<PropertyPath> getOrCreateQueue() {
        LinkedList<PropertyPath> queue = RECORDED_QUEUE.get();
        if (queue == null) {
            queue = new LinkedList<>();
            RECORDED_QUEUE.set(queue);
        }
        return queue;
    }

    public static void start(Class<?> rootEntityClass) {
        clearRecording(rootEntityClass);
        CURRENT_PATH.set(new PropertyPath(rootEntityClass));
    }

    public static void ensureStarted(Class<?> rootEntityClass) {
        PropertyPath cur = CURRENT_PATH.get();
        if (cur == null) {
            CURRENT_PATH.set(new PropertyPath(rootEntityClass));
        } else if (cur.isValueObject() && cur.getTerminalType() == rootEntityClass) {
            // 正在当前 ValueObject 代理对象上调用方法（如 addressProxy.getCity()），保持当前路径
            return;
        } else if (cur.getTerminalProperty() != null) {
            // 前一个属性已完成录制，推入队列，开启新实体的录制
            getOrCreateQueue().offer(cur);
            CURRENT_PATH.set(new PropertyPath(rootEntityClass));
        }
    }

    public static PropertyPath get() {
        return CURRENT_PATH.get();
    }

    public static void registerAlias(Class<?> entityClass, String alias) {
        if (entityClass != null && alias != null) {
            Map<Class<?>, String> map = ACTIVE_ALIASES.get();
            if (map == null) {
                map = new HashMap<>();
                ACTIVE_ALIASES.set(map);
            }
            map.put(entityClass, alias);
        }
    }

    public static String getAlias(Class<?> entityClass) {
        if (entityClass == null) return null;
        Map<Class<?>, String> map = ACTIVE_ALIASES.get();
        return map != null ? map.get(entityClass) : null;
    }

    public static boolean hasAlias(String alias) {
        if (alias == null) return false;
        Map<Class<?>, String> map = ACTIVE_ALIASES.get();
        return map != null && map.containsValue(alias);
    }

    public static int getActiveAliasCount() {
        Map<Class<?>, String> map = ACTIVE_ALIASES.get();
        return map != null ? map.size() : 0;
    }

    public static void registerProxyAlias(Object proxy, String alias) {
        if (proxy != null && alias != null) {
            Map<Object, String> map = PROXY_ALIASES.get();
            if (map == null) {
                map = new IdentityHashMap<>();
                PROXY_ALIASES.set(map);
            }
            map.put(proxy, alias);
        }
    }

    public static void registerGlobalProxyAlias(Object proxy, String alias) {
        if (proxy != null && alias != null) {
            GLOBAL_PROXY_ALIASES.put(proxy, alias);
        }
    }

    public static String getProxyAlias(Object proxy) {
        if (proxy == null) return null;
        String global = GLOBAL_PROXY_ALIASES.get(proxy);
        if (global != null) {
            return global;
        }
        Map<Object, String> local = PROXY_ALIASES.get();
        return local != null ? local.get(proxy) : null;
    }

    public static void clearAliases() {
        ACTIVE_ALIASES.remove();
        PROXY_ALIASES.remove();
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
        if (queue != null) {
            queue.removeIf(p -> p.getRootEntityClass() == entityClass);
            if (queue.isEmpty()) {
                RECORDED_QUEUE.remove();
            }
        }
        PropertyPath cur = CURRENT_PATH.get();
        if (cur != null && cur.getRootEntityClass() == entityClass) {
            CURRENT_PATH.remove();
        }
    }

    public static boolean isQueueEmpty() {
        LinkedList<PropertyPath> queue = RECORDED_QUEUE.get();
        return queue == null || queue.isEmpty();
    }

    public static void recordRelation(String relationProperty) {
        PropertyPath path = CURRENT_PATH.get();
        if (path != null) {
            path.addRelationStep(relationProperty);
        }
    }

    public static void recordValueObject(String voProperty, Class<?> voType) {
        PropertyPath path = CURRENT_PATH.get();
        if (path != null) {
            if (path.isValueObject()) {
                // 上一个也是 VO，将上一个转为 VO 步骤（支持嵌套 VO）
                path.addValueObjectStep(path.getTerminalProperty());
            } else if (path.getTerminalProperty() != null) {
                // 上一个普通属性已完成，推入队列
                getOrCreateQueue().offer(path);
                path = new PropertyPath(path.getRootEntityClass());
                CURRENT_PATH.set(path);
            }
            path.setTerminal(voProperty, voType);
            path.setValueObject(true);
        }
    }

    public static void recordTerminal(String terminalProperty, Class<?> terminalType) {
        PropertyPath path = CURRENT_PATH.get();
        if (path != null) {
            if (path.isValueObject()) {
                // 前一个调用是 VO（如 address.city），将 address 转为 VO 步骤，当前 terminal 为 city
                path.addValueObjectStep(path.getTerminalProperty());
                path.setValueObject(false);
                path.setTerminal(terminalProperty, terminalType);
                return;
            }
            if (path.getTerminalProperty() != null) {
                // 前一个属性已完成录制，推入队列，开启新属性
                getOrCreateQueue().offer(path);
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

    /**
     * 按先进先出（FIFO）顺序提取并移除一个录制的属性路径。
     * 若队列中有较早录制的属性，则优先出队；否则提取当前最新的属性。
     */
    public static PropertyPath poll() {
        LinkedList<PropertyPath> queue = RECORDED_QUEUE.get();
        if (queue != null && !queue.isEmpty()) {
            PropertyPath polled = queue.poll();
            if (queue.isEmpty() && CURRENT_PATH.get() == null) {
                RECORDED_QUEUE.remove();
            }
            return polled;
        }
        PropertyPath cur = CURRENT_PATH.get();
        if (cur != null) {
            CURRENT_PATH.remove();
            return cur;
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
            if (queue != null && !queue.isEmpty()) {
                PropertyPath polled = queue.poll();
                if (queue.isEmpty() && CURRENT_PATH.get() == null) {
                    RECORDED_QUEUE.remove();
                }
                return polled;
            }
            return null;
        }

        if (queue == null || queue.isEmpty()) {
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
        if (cur != null && (entityClass == null || cur.getRootEntityClass() == entityClass)) {
            CURRENT_PATH.remove();
            list.add(cur);
            count--;
        }

        if (count > 0) {
            LinkedList<PropertyPath> queue = RECORDED_QUEUE.get();
            if (queue != null && !queue.isEmpty()) {
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
        }

        return list;
    }

    public static List<PropertyPath> finishAll() {
        return finishAll(null);
    }

    public static List<PropertyPath> finishAll(Class<?> entityClass) {
        List<PropertyPath> result = new ArrayList<>();
        LinkedList<PropertyPath> queue = RECORDED_QUEUE.get();
        if (queue != null) {
            for (Iterator<PropertyPath> it = queue.iterator(); it.hasNext(); ) {
                PropertyPath p = it.next();
                if (entityClass == null || p.getRootEntityClass() == entityClass) {
                    result.add(p);
                    it.remove();
                }
            }
            if (queue.isEmpty() && CURRENT_PATH.get() == null) {
                RECORDED_QUEUE.remove();
            }
        }
        PropertyPath path = CURRENT_PATH.get();
        if (path != null && (entityClass == null || path.getRootEntityClass() == entityClass)) {
            result.add(path);
            CURRENT_PATH.remove();
        }
        if ((queue == null || queue.isEmpty()) && CURRENT_PATH.get() == null) {
            RECORDED_QUEUE.remove();
        }
        return result;
    }

    public static <T, R> PropertyPath recordPath(Class<T> entityClass, java.util.function.Function<T, R> getter) {
        if (entityClass == null || getter == null) {
            return null;
        }
        start(entityClass);
        try {
            T proxy = ProxyFactory.getProxy(entityClass);
            getter.apply(proxy);
            return finish();
        } finally {
            clearRecording();
        }
    }

    public static <T> List<PropertyPath> recordPaths(Class<T> entityClass, java.util.function.Consumer<T> proxyAction) {
        if (entityClass == null || proxyAction == null) {
            return Collections.emptyList();
        }
        start(entityClass);
        try {
            T proxy = ProxyFactory.getProxy(entityClass);
            proxyAction.accept(proxy);
            return finishAll();
        } finally {
            clearRecording();
        }
    }

    public static void clear() {
        RECORDED_QUEUE.remove();
        CURRENT_PATH.remove();
        ACTIVE_ALIASES.remove();
    }
}
