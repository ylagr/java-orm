package com.baomidou.dynamic.datasource.toolkit;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 模拟 Baomidou dynamic-datasource 框架核心上下文持有类
 */
public class DynamicDataSourceContextHolder {

    private static final ThreadLocal<Deque<String>> LOOKUP_KEY_HOLDER = ThreadLocal.withInitial(ArrayDeque::new);

    public static String peek() {
        return LOOKUP_KEY_HOLDER.get().peek();
    }

    public static String push(String ds) {
        LOOKUP_KEY_HOLDER.get().push(ds);
        return ds;
    }

    public static void poll() {
        Deque<String> deque = LOOKUP_KEY_HOLDER.get();
        deque.poll();
        if (deque.isEmpty()) {
            LOOKUP_KEY_HOLDER.remove();
        }
    }

    public static void clear() {
        LOOKUP_KEY_HOLDER.remove();
    }
}
