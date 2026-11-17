package com.javaorm.core.converter;

import java.util.Iterator;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JsonProvider 管理器。
 * 负责通过 SPI 自动探测或手动注册 JSON 序列化实现，避免 core 强依赖特定第三方库。
 */
public class JsonProviderManager {

    private static volatile JsonProvider DEFAULT_PROVIDER;
    private static final Map<Class<? extends JsonProvider>, JsonProvider> PROVIDER_INSTANCES = new ConcurrentHashMap<>();

    static {
        // 尝试通过 ServiceLoader SPI 加载
        try {
            ServiceLoader<JsonProvider> loader = ServiceLoader.load(JsonProvider.class);
            Iterator<JsonProvider> iterator = loader.iterator();
            if (iterator.hasNext()) {
                DEFAULT_PROVIDER = iterator.next();
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 设置全局默认的 JsonProvider
     */
    public static void setDefaultProvider(JsonProvider provider) {
        DEFAULT_PROVIDER = provider;
    }

    /**
     * 获取全局默认的 JsonProvider
     */
    public static JsonProvider getDefaultProvider() {
        return DEFAULT_PROVIDER;
    }

    /**
     * 获取指定类型的 JsonProvider 实例
     */
    public static JsonProvider getProvider(Class<? extends JsonProvider> providerClass) {
        if (providerClass == null || providerClass == JsonProvider.class) {
            JsonProvider def = DEFAULT_PROVIDER;
            if (def == null) {
                throw new IllegalStateException("No default JsonProvider found. Please register one via JsonProviderManager.setDefaultProvider(...) or add an SPI implementation module (e.g., Jackson/Fastjson2).");
            }
            return def;
        }
        return PROVIDER_INSTANCES.computeIfAbsent(providerClass, k -> {
            try {
                return k.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException("Failed to instantiate JsonProvider: " + k.getName(), e);
            }
        });
    }
}
