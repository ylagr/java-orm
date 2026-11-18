package com.javaorm.core.pagination;

import java.util.List;

/**
 * 针对具体分页实例对象的适配器。
 * 负责从已有分页对象中读取 pageNum / pageSize，并将执行结果 total 和 records 回写进去。
 *
 * @param <P> 分页对象类型（如 MyBatis-Plus 的 IPage、Spring Data 的 Page 等）
 */
public interface PageAdapter<P> {

    /**
     * 判断当前适配器是否支持处理该分页对象实例
     */
    boolean supports(Object page);

    /**
     * 读取当前页码（从 1 开始）
     */
    long getPageNum(P page);

    /**
     * 读取每页条数
     */
    long getPageSize(P page);

    /**
     * 回写总记录数
     */
    void setTotal(P page, long total);

    /**
     * 回写当前页数据列表
     */
    <T> void setRecords(P page, List<T> records);
}
