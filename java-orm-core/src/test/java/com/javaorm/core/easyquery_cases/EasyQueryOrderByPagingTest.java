package com.javaorm.core.easyquery_cases;

import com.javaorm.core.easyquery_cases.entity.Topic;
import com.javaorm.core.pagination.PageResult;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * 1:1 复刻 EasyQuery 官方排序与分页测试 (OrderByTest & PagingTest)
 */
public class EasyQueryOrderByPagingTest extends EasyQueryBaseTest {

    @Test
    public void testOrderByAscAndDesc() {
        Topic proxy = orm.proxy(Topic.class);

        // 1. 单列升序
        List<Topic> ascList = orm.queryable(Topic.class)
                .orderByAsc(proxy.getStars())
                .toList();
        assertEquals(6, ascList.size());
        assertEquals("1", ascList.get(0).getId());
        assertEquals("6", ascList.get(5).getId());

        // 2. 单列降序
        List<Topic> descList = orm.queryable(Topic.class)
                .orderByDesc(proxy.getStars())
                .toList();
        assertEquals(6, descList.size());
        assertEquals("6", descList.get(0).getId());
        assertEquals("1", descList.get(5).getId());
    }

    @Test
    public void testLimitAndOffset() {
        Topic proxy = orm.proxy(Topic.class);

        // limit(offset, rows)
        List<Topic> list = orm.queryable(Topic.class)
                .orderByAsc(proxy.getStars())
                .limit(2, 2)
                .toList();

        assertEquals(2, list.size());
        assertEquals("3", list.get(0).getId()); // offset 2: 1, 2 跳过，取 3
        assertEquals("4", list.get(1).getId());
    }

    @Test
    public void testToPageResultMetadata() {
        Topic proxy = orm.proxy(Topic.class);

        // 第 1 页，每页 2 条
        PageResult<Topic> page1 = orm.queryable(Topic.class)
                .orderByAsc(proxy.getStars())
                .toPageResult(1, 2);

        assertEquals(6L, page1.getTotal());
        assertEquals(2, page1.getData().size());
        assertEquals(1, page1.getPageNum());
        assertEquals(2, page1.getPageSize());
        assertEquals(3, page1.getTotalPages());
        assertTrue(page1.hasNext());
        assertFalse(page1.hasPrevious());
        assertEquals("1", page1.getData().get(0).getId());
        assertEquals("2", page1.getData().get(1).getId());

        // 第 2 页，每页 2 条
        PageResult<Topic> page2 = orm.queryable(Topic.class)
                .orderByAsc(proxy.getStars())
                .toPageResult(2, 2);

        assertEquals(6L, page2.getTotal());
        assertEquals(2, page2.getData().size());
        assertEquals(2, page2.getPageNum());
        assertTrue(page2.hasNext());
        assertTrue(page2.hasPrevious());
        assertEquals("3", page2.getData().get(0).getId());
        assertEquals("4", page2.getData().get(1).getId());

        // 第 3 页，每页 2 条（尾页）
        PageResult<Topic> page3 = orm.queryable(Topic.class)
                .orderByAsc(proxy.getStars())
                .toPageResult(3, 2);

        assertEquals(6L, page3.getTotal());
        assertEquals(2, page3.getData().size());
        assertFalse(page3.hasNext());
        assertTrue(page3.hasPrevious());
        assertEquals("5", page3.getData().get(0).getId());
        assertEquals("6", page3.getData().get(1).getId());
    }

    @Test
    public void testSqlModePaging() {
        Topic proxy = orm.proxy(Topic.class);

        PageResult<Topic> pageResult = orm.sql()
                .select(Topic.class)
                .where(w -> w.gt(proxy.getStars(), 10))
                .orderByAsc(proxy.getStars())
                .toPageResult(1, 3);

        assertEquals(5L, pageResult.getTotal());
        assertEquals(3, pageResult.getData().size());
        assertEquals("2", pageResult.getData().get(0).getId());
        assertEquals(2, pageResult.getTotalPages());
    }
}
