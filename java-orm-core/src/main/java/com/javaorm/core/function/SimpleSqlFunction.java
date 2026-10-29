package com.javaorm.core.function;

import java.util.Objects;

/**
 * 通用自定义 SQL 函数逻辑标识符实现。
 */
public class SimpleSqlFunction implements SqlFunction {

    private final String name;

    public SimpleSqlFunction(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Function name cannot be null or empty");
        }
        this.name = name.trim().toUpperCase();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SqlFunction)) return false;
        SqlFunction that = (SqlFunction) o;
        return Objects.equals(name, that.getName());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name);
    }

    @Override
    public String toString() {
        return name;
    }
}
