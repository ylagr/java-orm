package com.javaorm.core.datasource;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多数据源管理器。
 * 负责集中注册、获取与管理多个物理数据源实例。
 */
public class DataSourceManager {

    private final Map<String, DataSource> dataSourceMap = new ConcurrentHashMap<>();
    private DataSource defaultDataSource;

    public DataSourceManager() {
    }

    public DataSourceManager(DataSource defaultDataSource) {
        this.defaultDataSource = defaultDataSource;
        if (defaultDataSource != null) {
            register("default", defaultDataSource);
            register("master", defaultDataSource);
        }
    }

    public DataSourceManager register(String name, DataSource dataSource) {
        if (name == null || dataSource == null) {
            throw new IllegalArgumentException("DataSource name and instance must not be null");
        }
        dataSourceMap.put(name, dataSource);
        if (defaultDataSource == null) {
            defaultDataSource = dataSource;
        }
        return this;
    }

    public DataSource getDataSource(String name) {
        if (name == null || name.isEmpty() || "default".equalsIgnoreCase(name)) {
            return defaultDataSource;
        }
        DataSource ds = dataSourceMap.get(name);
        if (ds == null) {
            throw new IllegalStateException("DataSource not found with name: '" + name + "'");
        }
        return ds;
    }

    public DataSource getDefaultDataSource() {
        return defaultDataSource;
    }

    public DataSourceManager setDefaultDataSource(DataSource defaultDataSource) {
        this.defaultDataSource = defaultDataSource;
        return this;
    }

    public boolean hasDataSource(String name) {
        return dataSourceMap.containsKey(name);
    }

    public Map<String, DataSource> getAllDataSources() {
        return Collections.unmodifiableMap(dataSourceMap);
    }
}
