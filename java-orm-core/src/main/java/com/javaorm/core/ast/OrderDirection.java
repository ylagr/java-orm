package com.javaorm.core.ast;

/**
 * SQL 排序方向枚举（ASC 升序，DESC 降序）。
 */
public enum OrderDirection {
    ASC(true),
    DESC(false);

    private final boolean ascending;

    OrderDirection(boolean ascending) {
        this.ascending = ascending;
    }

    public boolean isAscending() {
        return ascending;
    }

    public static OrderDirection of(boolean isAsc) {
        return isAsc ? ASC : DESC;
    }

    public static OrderDirection from(String direction) {
        if (direction == null || direction.trim().isEmpty()) {
            return ASC;
        }
        String d = direction.trim();
        if ("desc".equalsIgnoreCase(d)) {
            return DESC;
        }
        return ASC;
    }
}
