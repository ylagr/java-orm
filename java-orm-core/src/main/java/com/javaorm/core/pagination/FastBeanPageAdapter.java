package com.javaorm.core.pagination;

import com.javaorm.core.fastbean.FastPropertyGetter;
import com.javaorm.core.fastbean.FastPropertySetter;
import com.javaorm.core.fastbean.LambdaFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 FastBean (LambdaMetafactory & MethodHandle) 机制的高性能通用分页适配器。
 * <p>
 * 彻底消除传统反射调用（Class.getMethod / Method.invoke）带来的开销，
 * 对同一分页类型（Class）自动进行元数据探测与调用点预热，并在内存中进行全局线程安全缓存（ConcurrentHashMap），
 * 实现 O(1) 复杂度的 JVM 原生字节码级快速读写。
 * </p>
 */
public class FastBeanPageAdapter implements PageAdapter<Object> {

    /**
     * 针对同一分页类型的快速访问描述符缓存（避免重复解析与预热）
     */
    private static final Map<Class<?>, FastPageDescriptor> CACHE = new ConcurrentHashMap<>();

    private static final String[] PAGE_NUM_CANDIDATES = {"getPageNum", "getCurrent", "getPageNo", "getPageIndex", "pageNum", "current", "pageNo", "pageIndex"};
    private static final String[] PAGE_SIZE_CANDIDATES = {"getPageSize", "getSize", "getLimit", "pageSize", "size", "limit"};
    private static final String[] TOTAL_CANDIDATES = {"setTotal", "setTotalCount", "setCount", "total", "totalCount", "count"};
    private static final String[] RECORDS_CANDIDATES = {"setRecords", "setData", "setList", "setRows", "records", "data", "list", "rows"};

    @Override
    public boolean supports(Object page) {
        if (page == null) return false;
        FastPageDescriptor desc = getOrResolve(page.getClass());
        return desc.isSupported();
    }

    @Override
    public long getPageNum(Object page) {
        if (page == null) return 1;
        FastPageDescriptor desc = getOrResolve(page.getClass());
        return desc.getPageNum(page);
    }

    @Override
    public long getPageSize(Object page) {
        if (page == null) return 20;
        FastPageDescriptor desc = getOrResolve(page.getClass());
        return desc.getPageSize(page);
    }

    @Override
    public void setTotal(Object page, long total) {
        if (page == null) return;
        FastPageDescriptor desc = getOrResolve(page.getClass());
        desc.setTotal(page, total);
    }

    @Override
    public <T> void setRecords(Object page, List<T> records) {
        if (page == null) return;
        FastPageDescriptor desc = getOrResolve(page.getClass());
        desc.setRecords(page, records);
    }

    /**
     * 获取或解析并缓存指定分页类型的快速访问描述符
     */
    public static FastPageDescriptor getOrResolve(Class<?> clazz) {
        return CACHE.computeIfAbsent(clazz, FastBeanPageAdapter::resolveDescriptor);
    }

    /**
     * 清空缓存（主要供测试或热重载时使用）
     */
    public static void clearCache() {
        CACHE.clear();
    }

    /**
     * 获取当前已缓存的分页类型数量
     */
    public static int getCachedCount() {
        return CACHE.size();
    }

    /**
     * 解析类的分页属性并生成零反射调用器
     */
    private static FastPageDescriptor resolveDescriptor(Class<?> clazz) {
        Map<String, Method> methodMap = new HashMap<>();
        for (Method m : clazz.getMethods()) {
            if (Modifier.isStatic(m.getModifiers())) continue;
            methodMap.putIfAbsent(m.getName(), m);
        }

        Map<String, Field> fieldMap = new HashMap<>();
        Class<?> curr = clazz;
        while (curr != null && curr != Object.class) {
            for (Field f : curr.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                fieldMap.putIfAbsent(f.getName(), f);
            }
            curr = curr.getSuperclass();
        }

        // 1. 解析 PageNum Getter
        FastPropertyGetter pageNumGetter = null;
        for (String name : PAGE_NUM_CANDIDATES) {
            Method m = methodMap.get(name);
            if (m != null && m.getParameterCount() == 0 && (Number.class.isAssignableFrom(m.getReturnType()) || m.getReturnType() == long.class || m.getReturnType() == int.class)) {
                pageNumGetter = LambdaFactory.createGetter(clazz, m, null);
                break;
            }
        }
        if (pageNumGetter == null) {
            for (String name : new String[]{"pageNum", "current", "pageNo", "pageIndex"}) {
                Field f = fieldMap.get(name);
                if (f != null) {
                    pageNumGetter = LambdaFactory.createGetter(clazz, null, f);
                    break;
                }
            }
        }

        // 2. 解析 PageSize Getter
        FastPropertyGetter pageSizeGetter = null;
        for (String name : PAGE_SIZE_CANDIDATES) {
            Method m = methodMap.get(name);
            if (m != null && m.getParameterCount() == 0 && (Number.class.isAssignableFrom(m.getReturnType()) || m.getReturnType() == long.class || m.getReturnType() == int.class)) {
                pageSizeGetter = LambdaFactory.createGetter(clazz, m, null);
                break;
            }
        }
        if (pageSizeGetter == null) {
            for (String name : new String[]{"pageSize", "size", "limit"}) {
                Field f = fieldMap.get(name);
                if (f != null) {
                    pageSizeGetter = LambdaFactory.createGetter(clazz, null, f);
                    break;
                }
            }
        }

        // 3. 解析 Total Setter
        FastPropertySetter totalSetter = null;
        Class<?> totalParamType = null;
        for (String name : TOTAL_CANDIDATES) {
            for (Method m : clazz.getMethods()) {
                if (Modifier.isStatic(m.getModifiers())) continue;
                if (m.getName().equals(name) && m.getParameterCount() == 1) {
                    Class<?> pType = m.getParameterTypes()[0];
                    if (Number.class.isAssignableFrom(pType) || pType == long.class || pType == int.class) {
                        totalSetter = LambdaFactory.createSetter(clazz, m, null);
                        totalParamType = pType;
                        break;
                    }
                }
            }
            if (totalSetter != null) break;
        }
        if (totalSetter == null) {
            for (String name : new String[]{"total", "totalCount", "count"}) {
                Field f = fieldMap.get(name);
                if (f != null) {
                    totalSetter = LambdaFactory.createSetter(clazz, null, f);
                    totalParamType = f.getType();
                    break;
                }
            }
        }

        // 4. 解析 Records Setter
        FastPropertySetter recordsSetter = null;
        for (String name : RECORDS_CANDIDATES) {
            for (Method m : clazz.getMethods()) {
                if (Modifier.isStatic(m.getModifiers())) continue;
                if (m.getName().equals(name) && m.getParameterCount() == 1) {
                    Class<?> pType = m.getParameterTypes()[0];
                    if (Collection.class.isAssignableFrom(pType) || Iterable.class.isAssignableFrom(pType)) {
                        recordsSetter = LambdaFactory.createSetter(clazz, m, null);
                        break;
                    }
                }
            }
            if (recordsSetter != null) break;
        }
        if (recordsSetter == null) {
            for (String name : new String[]{"records", "data", "list", "rows"}) {
                Field f = fieldMap.get(name);
                if (f != null) {
                    recordsSetter = LambdaFactory.createSetter(clazz, null, f);
                    break;
                }
            }
        }

        boolean supported = (pageNumGetter != null && pageSizeGetter != null && totalSetter != null && recordsSetter != null);
        return new FastPageDescriptor(supported, pageNumGetter, pageSizeGetter, totalSetter, totalParamType, recordsSetter);
    }

    /**
     * 单个分页类的快速访问元数据描述符
     */
    public static class FastPageDescriptor {
        private final boolean supported;
        private final FastPropertyGetter pageNumGetter;
        private final FastPropertyGetter pageSizeGetter;
        private final FastPropertySetter totalSetter;
        private final Class<?> totalParamType;
        private final FastPropertySetter recordsSetter;

        public FastPageDescriptor(boolean supported,
                                  FastPropertyGetter pageNumGetter,
                                  FastPropertyGetter pageSizeGetter,
                                  FastPropertySetter totalSetter,
                                  Class<?> totalParamType,
                                  FastPropertySetter recordsSetter) {
            this.supported = supported;
            this.pageNumGetter = pageNumGetter;
            this.pageSizeGetter = pageSizeGetter;
            this.totalSetter = totalSetter;
            this.totalParamType = totalParamType;
            this.recordsSetter = recordsSetter;
        }

        public boolean isSupported() {
            return supported;
        }

        public long getPageNum(Object page) {
            if (pageNumGetter != null && page != null) {
                try {
                    Object val = pageNumGetter.get(page);
                    if (val instanceof Number) {
                        return ((Number) val).longValue();
                    }
                } catch (Exception ignored) {
                }
            }
            return 1;
        }

        public long getPageSize(Object page) {
            if (pageSizeGetter != null && page != null) {
                try {
                    Object val = pageSizeGetter.get(page);
                    if (val instanceof Number) {
                        return ((Number) val).longValue();
                    }
                } catch (Exception ignored) {
                }
            }
            return 20;
        }

        public void setTotal(Object page, long total) {
            if (totalSetter != null && page != null) {
                try {
                    Object valueToPass;
                    if (totalParamType == int.class || totalParamType == Integer.class) {
                        valueToPass = (int) total;
                    } else {
                        valueToPass = total;
                    }
                    totalSetter.set(page, valueToPass);
                } catch (Exception ignored) {
                }
            }
        }

        public void setRecords(Object page, List<?> records) {
            if (recordsSetter != null && page != null) {
                try {
                    recordsSetter.set(page, records);
                } catch (Exception ignored) {
                }
            }
        }
    }
}
