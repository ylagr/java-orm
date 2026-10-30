package com.javaorm.core.proxy;

import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 代理工厂（AOT / JIT 统一调度中心）。
 * 1. 优先探测 Classpath 是否有预编译好的 AOT 固化代理类；
 * 2. 若无则无缝降级为动态 JIT 生成，核心复用 ProxyBytecodeBuilder.buildUnloaded；
 * 3. 实例化采用原生 MethodHandle 与 ProxyInstanceFactory 接口分发，全生命周期 0 反射。
 */
public class ProxyFactory {

    private static final Map<Class<?>, Object> PROXY_INSTANCE_CACHE = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public static <T> T getProxy(Class<T> entityClass) {
        if (entityClass == null) {
            return null;
        }
        return (T) PROXY_INSTANCE_CACHE.computeIfAbsent(entityClass, ProxyFactory::createOrLoadProxy);
    }

    /**
     * 注册预编译或自定义代理实例（主要由 AOT Registry 启动时调用，纯原生注册）。
     */
    public static <T> void registerProxy(Class<T> entityClass, T proxyInstance) {
        if (entityClass != null && proxyInstance != null) {
            PROXY_INSTANCE_CACHE.put(entityClass, proxyInstance);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T createProxy(Class<T> entityClass, String alias) {
        if (entityClass == null) {
            return null;
        }
        T singleton = getProxy(entityClass);
        if (alias == null) {
            return singleton;
        }

        T newInstance;
        if (singleton instanceof ProxyInstanceFactory) {
            // 纯接口多态虚方法分发调用 createNew()，0 反射！
            newInstance = ((ProxyInstanceFactory<T>) singleton).createNew();
        } else {
            try {
                newInstance = (T) singleton.getClass().getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                newInstance = singleton;
            }
        }

        RecordingContext.registerProxyAlias(newInstance, alias);
        return newInstance;
    }

    @SuppressWarnings("unchecked")
    private static <T> T createOrLoadProxy(Class<T> entityClass) {
        String proxyClassName = ProxyBytecodeBuilder.getProxyClassName(entityClass);
        ClassLoader classLoader = entityClass.getClassLoader();
        Class<? extends T> proxyClass = null;

        // 1. AOT 探测：优先检查该类是否在构建期已经被预编译在 Classpath 中
        try {
            proxyClass = (Class<? extends T>) Class.forName(proxyClassName, false, classLoader);
        } catch (ClassNotFoundException ignored) {
            // 2. JIT 降级：未探测到预编译类，复用 ProxyBytecodeBuilder 构建并在内存中加载
            DynamicType.Unloaded<T> unloaded = ProxyBytecodeBuilder.buildUnloaded(entityClass);
            proxyClass = loadUnloaded(unloaded, entityClass);
        }

        // 3. 优先通过 JVM 原生 MethodHandle 零反射调用静态 createInstance()
        try {
            MethodHandle handle = MethodHandles.publicLookup()
                    .findStatic(proxyClass, "createInstance", MethodType.methodType(entityClass));
            return (T) handle.invoke();
        } catch (Throwable t) {
            // 4. 容错降级
            try {
                return proxyClass.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException("Failed to instantiate proxy for " + entityClass.getName(), e);
            }
        }
    }

    private static <T> Class<? extends T> loadUnloaded(DynamicType.Unloaded<T> unloaded, Class<T> entityClass) {
        try {
            return unloaded.load(entityClass.getClassLoader(), ClassLoadingStrategy.Default.INJECTION)
                    .getLoaded();
        } catch (Throwable t) {
            return unloaded.load(entityClass.getClassLoader(), ClassLoadingStrategy.Default.WRAPPER)
                    .getLoaded();
        }
    }
}
