package com.javaorm.core.datasource;

import javax.sql.DataSource;

/**
 * 读写分离从数据源元数据描述
 */
public class SlaveDataSource {

    private final String name;
    private final DataSource dataSource;
    private final int weight;

    public SlaveDataSource(String name, DataSource dataSource, int weight) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Slave DataSource name must not be empty");
        }
        if (dataSource == null) {
            throw new IllegalArgumentException("Slave DataSource instance must not be null");
        }
        this.name = name;
        this.dataSource = dataSource;
        this.weight = Math.max(1, weight);
    }

    public String getName() {
        return name;
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public int getWeight() {
        return weight;
    }

    @Override
    public String toString() {
        return "SlaveDataSource{" +
                "name='" + name + '\'' +
                ", weight=" + weight +
                '}';
    }
}
