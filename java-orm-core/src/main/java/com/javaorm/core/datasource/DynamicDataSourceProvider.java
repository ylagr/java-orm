package com.javaorm.core.datasource;

/**
 * 动态多数据源扩展提供者接口（SPI）。
 * 允许外部桥接组件（如 Baomidou dynamic-datasource @DS）向 JavaORM 汇报当前上下文切换的数据源名称。
 */
public interface DynamicDataSourceProvider {

    /**
     * 探测当前线程上下文（如 @DS 注解、DynamicDataSourceContextHolder）所指定的数据源名称。
     * 若未在外部上下文中切换数据源，应返回 null。
     *
     * @return 目标数据源名称，若未指定则返回 null
     */
    String getCurrentDataSource();

    /**
     * 优先级顺序（数值越小优先级越高，默认 0）
     */
    default int getOrder() {
        return 0;
    }
}
