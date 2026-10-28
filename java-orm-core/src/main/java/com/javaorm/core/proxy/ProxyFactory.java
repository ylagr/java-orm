package com.javaorm.core.proxy;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ByteBuddy 动态代理工厂。
 * 负责在运行时按需为实体生成无状态的子类代理，并全局单例复用，极速录制且零 GC 压力。
 */
public class ProxyFactory {

    private static final Map<Class<?>, Object> PROXY_INSTANCE_CACHE = new ConcurrentHashMap<>();
    private static final ByteBuddy BYTE_BUDDY = new ByteBuddy();

    @SuppressWarnings("unchecked")
    public static <T> T getProxy(Class<T> entityClass) {
        return (T) PROXY_INSTANCE_CACHE.computeIfAbsent(entityClass, ProxyFactory::createProxyInstance);
    }

    @SuppressWarnings("unchecked")
    private static <T> T createProxyInstance(Class<T> entityClass) {
        try {
            DynamicType.Unloaded<T> make = BYTE_BUDDY
                    .subclass(entityClass)
                    .name(entityClass.getName() + "$JavaOrmRecorder")
                    .method(ElementMatchers.isGetter()
                            .or(ElementMatchers.nameStartsWith("is"))
                            .or(ElementMatchers.named("toString"))
                            .or(ElementMatchers.named("hashCode"))
                            .or(ElementMatchers.named("equals")))
                    .intercept(MethodDelegation.to(RecorderInterceptor.class))
                    .make();

            Class<? extends T> proxyClass;
            try {
                // 优先尝试注入方式
                proxyClass = make.load(entityClass.getClassLoader(), ClassLoadingStrategy.Default.INJECTION)
                        .getLoaded();
            } catch (Throwable t) {
                // JDK 9+ 模块限制时回退为 WRAPPER
                proxyClass = make.load(entityClass.getClassLoader(), ClassLoadingStrategy.Default.WRAPPER)
                        .getLoaded();
            }

            return proxyClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create recorder proxy for " + entityClass.getName(), e);
        }
    }
}
