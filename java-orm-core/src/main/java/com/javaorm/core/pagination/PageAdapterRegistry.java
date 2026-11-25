package com.javaorm.core.pagination;

import java.lang.reflect.Method;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 分页适配器注册中心
 */
public class PageAdapterRegistry {

    private static final List<PageAdapter<?>> ADAPTERS = new CopyOnWriteArrayList<>();

    static {
        // 自动发现通过 SPI 注册的分页适配器（如 java-orm-bridge 提供的 MyBatis-Plus 适配器）
        ServiceLoader<PageAdapter> loader = ServiceLoader.load(PageAdapter.class);
        for (PageAdapter adapter : loader) {
            ADAPTERS.add(adapter);
        }
        // 注册基于 FastBean 机制的高性能零反射探测适配器（零反射调用，按类型全局缓存）
        ADAPTERS.add(new FastBeanPageAdapter());
        // 默认反射兜底适配器
        ADAPTERS.add(new ReflectivePageAdapter());
    }

    public static void register(PageAdapter<?> adapter) {
        if (adapter != null) {
            ADAPTERS.add(0, adapter); // 高优先级置前
        }
    }

    @SuppressWarnings("unchecked")
    public static <P> PageAdapter<P> getAdapter(P page) {
        if (page == null) return null;
        for (PageAdapter<?> adapter : ADAPTERS) {
            if (adapter.supports(page)) {
                return (PageAdapter<P>) adapter;
            }
        }
        return null;
    }

    /**
     * 通用反射兜底适配器：自动识别包含 getPageNum/getCurrent/getPageNo、getPageSize/getSize、setTotal、setRecords/setData 等常见命名规范的类
     */
    private static class ReflectivePageAdapter implements PageAdapter<Object> {

        @Override
        public boolean supports(Object page) {
            if (page == null) return false;
            Class<?> clazz = page.getClass();
            return (hasMethod(clazz, "getPageNum") || hasMethod(clazz, "getCurrent") || hasMethod(clazz, "getPageNo"))
                    && (hasMethod(clazz, "getPageSize") || hasMethod(clazz, "getSize"))
                    && (hasMethod(clazz, "setTotal", long.class) || hasMethod(clazz, "setTotal", Long.class) || hasMethod(clazz, "setTotal", int.class) || hasMethod(clazz, "setTotal", Integer.class))
                    && (hasMethod(clazz, "setRecords", List.class) || hasMethod(clazz, "setData", List.class) || hasMethod(clazz, "setList", List.class));
        }

        @Override
        public long getPageNum(Object page) {
            Object val = invokeAny(page, "getPageNum", "getCurrent", "getPageNo");
            if (val instanceof Number) return ((Number) val).longValue();
            return 1;
        }

        @Override
        public long getPageSize(Object page) {
            Object val = invokeAny(page, "getPageSize", "getSize");
            if (val instanceof Number) return ((Number) val).longValue();
            return 20;
        }

        @Override
        public void setTotal(Object page, long total) {
            invokeSetter(page, total, "setTotal");
        }

        @Override
        public <T> void setRecords(Object page, List<T> records) {
            invokeSetter(page, records, "setRecords", "setData", "setList");
        }

        private boolean hasMethod(Class<?> clazz, String name, Class<?>... paramTypes) {
            try {
                clazz.getMethod(name, paramTypes);
                return true;
            } catch (NoSuchMethodException e) {
                for (Method m : clazz.getMethods()) {
                    if (m.getName().equals(name)) return true;
                }
                return false;
            }
        }

        private Object invokeAny(Object target, String... methodNames) {
            for (String name : methodNames) {
                try {
                    Method m = target.getClass().getMethod(name);
                    return m.invoke(target);
                } catch (Exception ignored) {
                }
            }
            return null;
        }

        private void invokeSetter(Object target, Object value, String... methodNames) {
            for (String name : methodNames) {
                for (Method m : target.getClass().getMethods()) {
                    if (!m.getName().equals(name) || m.getParameterCount() != 1) {
                        continue;
                    }
                    try {
                        m.invoke(target, adaptSetterArgument(value, m.getParameterTypes()[0]));
                        return;
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        private Object adaptSetterArgument(Object value, Class<?> paramType) {
            if (!(value instanceof Number)) {
                return value;
            }
            Number num = (Number) value;
            if (paramType == long.class || paramType == Long.class) {
                return num.longValue();
            }
            if (paramType == int.class || paramType == Integer.class) {
                return num.intValue();
            }
            return value;
        }
    }
}
