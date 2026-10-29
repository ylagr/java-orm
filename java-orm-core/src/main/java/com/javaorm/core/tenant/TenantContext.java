package com.javaorm.core.tenant;

/**
 * 多租户上下文（基于 ThreadLocal）。
 * 用于在当前线程上下文中保存和获取当前租户 ID。
 */
public class TenantContext {

    private static final ThreadLocal<Object> CURRENT_TENANT = new ThreadLocal<>();

    public static void setTenantId(Object tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static Object getTenantId() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
