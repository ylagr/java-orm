package com.javaorm.core.sharding;

import java.util.Collections;
import java.util.List;

/**
 * 默认空数据源路由器（标记类，表示未配置分库路由器）。
 */
public final class DefaultDataSourceRouter implements DataSourceRouter<Object> {

    @Override
    public String route(Object shardingValue) {
        return "";
    }

    @Override
    public List<String> allDataSources() {
        return Collections.emptyList();
    }
}
