package com.javaorm.core.aot;

import com.javaorm.core.proxy.ProxyFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * AOT 预热注册中心。
 * 在 Native Image 启动时调用 initialize()，可一次性将所有预编译的实体代理直接加载到单例缓存中，
 * 避免运行期首次调用的类查找开销。
 */
public class JavaOrmAotRegistry {

    private static volatile boolean initialized = false;

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = JavaOrmAotRegistry.class.getClassLoader();
        }
        try (InputStream is = cl.getResourceAsStream("META-INF/javaorm/aot-entities.txt")) {
            if (is != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (!line.isEmpty()) {
                            try {
                                Class<?> entityClass = Class.forName(line, false, cl);
                                // 触发 ProxyFactory 预加载入缓存（0 反射）
                                ProxyFactory.getProxy(entityClass);
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        initialized = true;
    }
}
