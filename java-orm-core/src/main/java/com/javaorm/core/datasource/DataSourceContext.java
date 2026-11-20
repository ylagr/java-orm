package com.javaorm.core.datasource;

import java.util.function.Supplier;

/**
 * 线程级数据源上下文切换器。
 * 支持通过 ThreadLocal 动态切换当前线程使用的数据源。
 */
public class DataSourceContext {

    private static final ThreadLocal<String> CURRENT_DS = new ThreadLocal<>();
    private static final java.util.List<DynamicDataSourceProvider> PROVIDERS = new java.util.concurrent.CopyOnWriteArrayList<>();

    static {
        try {
            java.util.ServiceLoader<DynamicDataSourceProvider> loader = java.util.ServiceLoader.load(DynamicDataSourceProvider.class);
            for (DynamicDataSourceProvider p : loader) {
                PROVIDERS.add(p);
            }
            PROVIDERS.sort(java.util.Comparator.comparingInt(DynamicDataSourceProvider::getOrder));
        } catch (Throwable ignored) {
        }
    }

    public static void registerProvider(DynamicDataSourceProvider provider) {
        if (provider != null && !PROVIDERS.contains(provider)) {
            PROVIDERS.add(provider);
            PROVIDERS.sort(java.util.Comparator.comparingInt(DynamicDataSourceProvider::getOrder));
        }
    }

    public static String getDataSource() {
        String ds = CURRENT_DS.get();
        if (ds != null && !ds.trim().isEmpty()) {
            return ds;
        }
        for (DynamicDataSourceProvider provider : PROVIDERS) {
            try {
                String externalDs = provider.getCurrentDataSource();
                if (externalDs != null && !externalDs.trim().isEmpty()) {
                    return externalDs.trim();
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
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
