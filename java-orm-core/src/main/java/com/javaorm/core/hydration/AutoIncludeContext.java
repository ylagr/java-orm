package com.javaorm.core.hydration;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * 自动关联装配线程上下文。
 * 用于在 DTO 未显式定义外键/主键属性时，跨嵌套层级传递原始行数据列映射，
 * 确保多层级递归 Split IN 查询能够精准获取关联键值。
 */
public class AutoIncludeContext {

    private static final ThreadLocal<Map<Object, Map<String, Object>>> CURRENT_ROW_CONTEXT = new ThreadLocal<>();

    public static Map<Object, Map<String, Object>> get() {
        return CURRENT_ROW_CONTEXT.get();
    }

    public static void set(Map<Object, Map<String, Object>> map) {
        CURRENT_ROW_CONTEXT.set(map);
    }

    public static void clear() {
        CURRENT_ROW_CONTEXT.remove();
    }

    public static void registerRow(Object dto, Map<String, Object> row) {
        Map<Object, Map<String, Object>> ctx = CURRENT_ROW_CONTEXT.get();
        if (ctx == null) {
            ctx = new IdentityHashMap<>();
            CURRENT_ROW_CONTEXT.set(ctx);
        }
        ctx.put(dto, row);
    }

    public static Map<String, Object> getRow(Object dto) {
        Map<Object, Map<String, Object>> ctx = CURRENT_ROW_CONTEXT.get();
        return ctx != null ? ctx.get(dto) : null;
    }
}
