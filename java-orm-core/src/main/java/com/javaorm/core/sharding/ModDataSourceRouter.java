package com.javaorm.core.sharding;

import java.util.ArrayList;
import java.util.List;

/**
 * 常用取模分库路由器（支持数值直接取模或对象 HashCode 取模）。
 * 例如 count = 2, format = "ds_%d" -> 生成 ds_0, ds_1
 */
public class ModDataSourceRouter implements DataSourceRouter<Object> {

    private final int count;
    private final String format;

    public ModDataSourceRouter() {
        this(2, "ds_%d");
    }

    public ModDataSourceRouter(int count) {
        this(count, "ds_%d");
    }

    public ModDataSourceRouter(int count, String format) {
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }
        this.count = count;
        this.format = format != null ? format : "ds_%d";
    }

    @Override
    public String route(Object shardingValue) {
        if (shardingValue == null) {
            throw new IllegalArgumentException("Sharding value must not be null for ModDataSourceRouter");
        }
        long hash;
        if (shardingValue instanceof Number) {
            hash = Math.abs(((Number) shardingValue).longValue());
        } else {
            hash = Math.abs((long) shardingValue.hashCode());
        }
        int idx = (int) (hash % count);
        return String.format(format, idx);
    }

    @Override
    public List<String> allDataSources() {
        List<String> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(String.format(format, i));
        }
        return list;
    }
}
