package com.javaorm.core.sharding;

import java.util.ArrayList;
import java.util.List;

/**
 * 常用取模分表路由（支持数值直接取模或对象 HashCode 取模）。
 * 例如 logicTableName = "t_order", tableCount = 4 -> 生成 t_order_0, t_order_1, t_order_2, t_order_3
 */
public class ModTableRouter implements TableRouter<Object> {

    private final int tableCount;
    private final String format;

    public ModTableRouter() {
        this(4, "%s_%d");
    }

    public ModTableRouter(int tableCount) {
        this(tableCount, "%s_%d");
    }

    public ModTableRouter(int tableCount, String format) {
        if (tableCount <= 0) {
            throw new IllegalArgumentException("tableCount must be positive");
        }
        this.tableCount = tableCount;
        this.format = format != null ? format : "%s_%d";
    }

    @Override
    public String route(String logicTableName, Object shardingValue) {
        if (shardingValue == null) {
            throw new IllegalArgumentException("Sharding value must not be null for ModTableRouter");
        }
        long hash;
        if (shardingValue instanceof Number) {
            hash = Math.abs(((Number) shardingValue).longValue());
        } else {
            hash = Math.abs((long) shardingValue.hashCode());
        }
        int idx = (int) (hash % tableCount);
        return String.format(format, logicTableName, idx);
    }

    @Override
    public List<String> allTables(String logicTableName) {
        List<String> list = new ArrayList<>(tableCount);
        for (int i = 0; i < tableCount; i++) {
            list.add(String.format(format, logicTableName, i));
        }
        return list;
    }
}
