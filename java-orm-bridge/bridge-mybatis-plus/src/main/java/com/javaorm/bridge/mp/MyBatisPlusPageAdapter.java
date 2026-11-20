package com.javaorm.bridge.mp;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.javaorm.core.pagination.PageAdapter;

import java.util.List;

/**
 * MyBatis-Plus IPage 分页适配器。
 * 让 Java-ORM 的 toPage(mpPage) 能够零侵入无缝回填并返回 MyBatis-Plus 的 IPage/Page 对象。
 */
public class MyBatisPlusPageAdapter implements PageAdapter<IPage<?>> {

    @Override
    public boolean supports(Object page) {
        return page instanceof IPage;
    }

    @Override
    public long getPageNum(IPage<?> page) {
        return page != null ? page.getCurrent() : 1;
    }

    @Override
    public long getPageSize(IPage<?> page) {
        return page != null ? page.getSize() : 20;
    }

    @Override
    public void setTotal(IPage<?> page, long total) {
        if (page != null) {
            page.setTotal(total);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> void setRecords(IPage<?> page, List<T> records) {
        if (page != null) {
            ((IPage<T>) page).setRecords(records);
        }
    }
}
