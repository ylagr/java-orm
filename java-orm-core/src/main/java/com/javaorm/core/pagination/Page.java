package com.javaorm.core.pagination;

import java.io.Serializable;
import java.util.List;

/**
 * 通用分页契约接口（只读标准抽象，解耦任意具体分页实现）。
 *
 * @param <T> 数据记录类型
 */
public interface Page<T> extends Serializable {

    /**
     * 获取总记录数
     */
    long getTotal();

    /**
     * 获取当前页记录列表
     */
    List<T> getRecords();

    /**
     * 获取当前页码（从 1 开始）
     */
    long getPageNum();

    /**
     * 获取每页大小
     */
    long getPageSize();

    /**
     * 获取总页数
     */
    default long getTotalPages() {
        long size = getPageSize();
        if (size <= 0) return 0;
        return (getTotal() + size - 1) / size;
    }

    /**
     * 是否有下一页
     */
    default boolean hasNext() {
        return getPageNum() < getTotalPages();
    }

    /**
     * 是否有上一页
     */
    default boolean hasPrevious() {
        return getPageNum() > 1;
    }
}
