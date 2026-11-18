package com.javaorm.core.sharding;

import java.util.Collections;
import java.util.List;

/**
 * 默认空分表路由器（标记类，表示未配置分表路由器）。
 */
public final class DefaultTableRouter implements TableRouter<Object> {

    @Override
    public String route(String logicTableName, Object shardingValue) {
        return logicTableName;
    }

    @Override
    public List<String> allTables(String logicTableName) {
        return Collections.singletonList(logicTableName);
    }
}
