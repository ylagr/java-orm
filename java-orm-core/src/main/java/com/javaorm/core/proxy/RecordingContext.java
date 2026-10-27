package com.javaorm.core.proxy;

/**
 * 线程级属性调用录制上下文（ThreadLocal）。
 */
public class RecordingContext {

    private static final ThreadLocal<PropertyPath> CURRENT_PATH = new ThreadLocal<>();

    public static void start(Class<?> rootEntityClass) {
        CURRENT_PATH.set(new PropertyPath(rootEntityClass));
    }

    public static PropertyPath get() {
        return CURRENT_PATH.get();
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
            path.setTerminal(terminalProperty, terminalType);
        }
    }

    public static PropertyPath finish() {
        PropertyPath path = CURRENT_PATH.get();
        CURRENT_PATH.remove();
        return path;
    }

    public static void clear() {
        CURRENT_PATH.remove();
    }
}
