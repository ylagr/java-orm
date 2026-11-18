package com.javaorm.core.datasource;

import java.util.function.Supplier;

/**
 * 线程级数据源上下文切换器。
 * 支持通过 ThreadLocal 动态切换当前线程使用的数据源。
 */
public class DataSourceContext {

    private static final ThreadLocal<String> CURRENT_DS = new ThreadLocal<>();

    public static String getDataSource() {
        return CURRENT_DS.get();
    }

    public static void setDataSource(String dataSourceName) {
        CURRENT_DS.set(dataSourceName);
    }

    public static void clear() {
        CURRENT_DS.remove();
    }

    public static <T> T use(String dataSourceName, Supplier<T> action) {
        String previous = getDataSource();
        try {
            setDataSource(dataSourceName);
            return action.get();
        } finally {
            if (previous != null) {
                setDataSource(previous);
            } else {
                clear();
            }
        }
    }

    public static void use(String dataSourceName, Runnable action) {
        use(dataSourceName, () -> {
            action.run();
            return null;
        });
    }
}
