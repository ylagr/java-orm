package com.javaorm.core.pagination;

import java.util.List;

/**
 * 自定义分页对象构建工厂函数。
 * 允许用户传入任意第三方或自定义的 Page 构造函数（如 MyPage::new），
 * 彻底实现零侵入、零依赖的分页结果返回。
 *
 * @param <T> 数据记录类型
 * @param <P> 用户自定义的目标分页类
 */
@FunctionalInterface
public interface PageFactory<T, P> {

    /**
     * 根据查询到的分页元数据与数据列表，构建并返回用户自定义的分页对象
     *
     * @param total    总记录数
     * @param records  当前页数据列表
     * @param pageNum  当前页码（从 1 开始）
     * @param pageSize 每页条数
     * @return 用户自定义的分页对象
     */
    P create(long total, List<T> records, long pageNum, long pageSize);
}
