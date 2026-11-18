package com.javaorm.core.pagination;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 分页查询结果载体，提供与 easy-query EasyPageResult 对应的强类型分页数据封装。
 *
 * @param <T> 数据实体或 DTO 类型
 */
public class PageResult<T> implements Page<T> {

    private static final long serialVersionUID = 1L;

    private final long total;
    private final List<T> data;
    private final int pageNum;
    private final int pageSize;

    public PageResult(long total, List<T> data, int pageNum, int pageSize) {
        this.total = total;
        this.data = data != null ? data : Collections.<T>emptyList();
        this.pageNum = pageNum;
        this.pageSize = pageSize;
    }

    public PageResult(long total, List<T> data, long pageNum, long pageSize) {
        this(total, data, (int) pageNum, (int) pageSize);
    }

    @Override
    public long getTotal() {
        return total;
    }

    public List<T> getData() {
        return data;
    }

    @Override
    public List<T> getRecords() {
        return data;
    }

    @Override
    public long getPageNum() {
        return pageNum;
    }

    @Override
    public long getPageSize() {
        return pageSize;
    }

    public long getTotalPages() {
        if (pageSize <= 0) {
            return 0;
        }
        return (total + pageSize - 1) / pageSize;
    }

    public boolean hasNext() {
        return pageNum < getTotalPages();
    }

    public boolean hasPrevious() {
        return pageNum > 1;
    }

    @Override
    public String toString() {
        return "PageResult{" +
                "total=" + total +
                ", pageNum=" + pageNum +
                ", pageSize=" + pageSize +
                ", totalPages=" + getTotalPages() +
                ", dataSize=" + data.size() +
                '}';
    }
}
