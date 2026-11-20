package com.javaorm.bridge.mp;

import com.javaorm.core.datasource.DynamicDataSourceProvider;

import java.lang.reflect.Method;

/**
 * 针对 Baomidou dynamic-datasource 框架（@DS 注解）的动态多数据源桥接提供者。
 * 运行时自动探测并提取 DynamicDataSourceContextHolder.peek() 切换的数据源名称，
 * 使得在外部 Spring 项目中使用 @DS("slave_1") 或手动 push/peek 时，JavaORM 能无感感知并自动路由到对应物理数据源。
 */
public class BaomidouDynamicDataSourceProvider implements DynamicDataSourceProvider {

    private static Method peekMethod = null;
    private static boolean checked = false;

    private static synchronized void init() {
        if (checked) return;
        try {
            Class<?> clazz = Class.forName("com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder");
            peekMethod = clazz.getMethod("peek");
        } catch (Throwable ignored) {
        } finally {
            checked = true;
        }
    }

    @Override
    public String getCurrentDataSource() {
        if (!checked) {
            init();
        }
        if (peekMethod != null) {
            try {
                Object val = peekMethod.invoke(null);
                if (val instanceof String) {
                    String ds = (String) val;
                    if (!ds.trim().isEmpty()) {
                        return ds.trim();
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    @Override
    public int getOrder() {
        return 100;
    }
}
