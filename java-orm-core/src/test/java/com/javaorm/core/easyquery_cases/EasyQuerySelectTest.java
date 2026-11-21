package com.javaorm.core.easyquery_cases;

import com.javaorm.core.easyquery_cases.dto.TopicDTO;
import com.javaorm.core.easyquery_cases.entity.Topic;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * 1:1 复刻 EasyQuery 官方经典查询测试 (QueryTest)
 */
public class EasyQuerySelectTest extends EasyQueryBaseTest {

    @Test
    public void testSimpleSelect() {
        Topic proxy = orm.proxy(Topic.class);

        // 1. 全表查询（自动应用 is_deleted = 0 逻辑删除过滤）
        List<Topic> list = orm.queryable(Topic.class).toList();
        assertEquals(6, list.size()); // 7 条中 1 条被逻辑删除

        // 2. 校验 SQL 结构
        String sql = orm.queryable(Topic.class).toSQL();
        assertTrue("SQL 必须包含 t_topic: " + sql, sql.contains("\"t_topic\""));
        assertTrue("SQL 必须包含逻辑删除条件: " + sql, sql.contains("\"is_deleted\" = ?"));
    }

    @Test
    public void testWhereBasicComparisons() {
        Topic proxy = orm.proxy(Topic.class);

        // 1. eq
        Topic t1 = orm.queryable(Topic.class)
                .where(w -> w.eq(proxy.getId(), "1"))
                .firstOrNull();
        assertNotNull(t1);
        assertEquals("Java ORM Guide", t1.getTitle());

        // 2. ne
        List<Topic> listNe = orm.queryable(Topic.class)
                .where(w -> w.ne(proxy.getId(), "1"))
                .toList();
        assertEquals(5, listNe.size());

        // 3. gt / ge / lt / le
        List<Topic> listGt = orm.queryable(Topic.class)
                .where(w -> w.gt(proxy.getStars(), 30))
                .toList();
        assertEquals(3, listGt.size()); // 40, 50, 60

        List<Topic> listGe = orm.queryable(Topic.class)
                .where(w -> w.ge(proxy.getStars(), 30))
                .toList();
        assertEquals(4, listGe.size()); // 30, 40, 50, 60

        List<Topic> listLt = orm.queryable(Topic.class)
                .where(w -> w.lt(proxy.getStars(), 30))
                .toList();
        assertEquals(2, listLt.size()); // 10, 20

        List<Topic> listLe = orm.queryable(Topic.class)
                .where(w -> w.le(proxy.getStars(), 20))
                .toList();
        assertEquals(2, listLe.size()); // 10, 20
    }

    @Test
    public void testWhereLike() {
        Topic proxy = orm.proxy(Topic.class);

        // 1. like (全模糊)
        List<Topic> listLike = orm.queryable(Topic.class)
                .where(w -> w.like(proxy.getTitle(), "%ORM%"))
                .toList();
        assertEquals(1, listLike.size());
        assertEquals("1", listLike.get(0).getId());

        // 2. likeLeft (以...结尾)
        List<Topic> listLeft = orm.queryable(Topic.class)
                .where(w -> w.likeLeft(proxy.getTitle(), "Guide"))
                .toList();
        assertEquals(1, listLeft.size());
        assertEquals("Java ORM Guide", listLeft.get(0).getTitle());

        // 3. likeRight (以...开头)
        List<Topic> listRight = orm.queryable(Topic.class)
                .where(w -> w.likeRight(proxy.getTitle(), "Deep"))
                .toList();
        assertEquals(1, listRight.size());
        assertEquals("Deep Dive SQL", listRight.get(0).getTitle());
    }

    @Test
    public void testWhereInAndNotIn() {
        Topic proxy = orm.proxy(Topic.class);

        // 1. in
        List<Topic> listIn = orm.queryable(Topic.class)
                .where(w -> w.in(proxy.getId(), Arrays.asList("1", "2", "3")))
                .toList();
        assertEquals(3, listIn.size());

        // 2. notIn
        List<Topic> listNotIn = orm.queryable(Topic.class)
                .where(w -> w.notIn(proxy.getId(), Arrays.asList("1", "2")))
                .toList();
        assertEquals(4, listNotIn.size());
    }

    @Test
    public void testWhereBetween() {
        Topic proxy = orm.proxy(Topic.class);

        List<Topic> list = orm.queryable(Topic.class)
                .where(w -> w.between(proxy.getStars(), 20, 50))
                .toList();
        assertEquals(4, list.size()); // 20, 30, 40, 50
    }

    @Test
    public void testWhereNullAndNotNull() {
        Topic proxy = orm.proxy(Topic.class);

        // 1. isNull
        List<Topic> listNull = orm.queryable(Topic.class)
                .where(w -> w.isNull(proxy.getTitle()))
                .toList();
        assertEquals(1, listNull.size());
        assertEquals("6", listNull.get(0).getId());

        // 2. isNotNull
        List<Topic> listNotNull = orm.queryable(Topic.class)
                .where(w -> w.isNotNull(proxy.getTitle()))
                .toList();
        assertEquals(5, listNotNull.size());
    }

    @Test
    public void testWhereByIdAndIds() {
        // 1. whereById
        Topic t = orm.queryable(Topic.class).whereById("2").firstOrNull();
        assertNotNull(t);
        assertEquals("EasyQuery Parity", t.getTitle());

        // 2. whereByIds
        List<Topic> list = orm.queryable(Topic.class).whereByIds("1", "3").toList();
        assertEquals(2, list.size());
    }

    @Test
    public void testFirstAndSingleOrNull() {
        Topic proxy = orm.proxy(Topic.class);

        // 1. firstOrNull 存在
        Topic first = orm.queryable(Topic.class)
                .where(w -> w.eq(proxy.getId(), "1"))
                .firstOrNull();
        assertNotNull(first);

        // 2. firstOrNull 不存在
        Topic notFound = orm.queryable(Topic.class)
                .where(w -> w.eq(proxy.getId(), "non_existing_id"))
                .firstOrNull();
        assertNull(notFound);

        // 3. singleOrNull
        Topic single = orm.queryable(Topic.class)
                .where(w -> w.eq(proxy.getId(), "3"))
                .singleOrNull();
        assertNotNull(single);
        assertEquals(Integer.valueOf(30), single.getStars());
    }

    @Test
    public void testCountAndAny() {
        Topic proxy = orm.proxy(Topic.class);

        // 1. count
        long count = orm.queryable(Topic.class).count();
        assertEquals(6, count);

        long countFilter = orm.queryable(Topic.class)
                .where(w -> w.gt(proxy.getStars(), 30))
                .count();
        assertEquals(3, countFilter);

        // 2. any (exists)
        boolean exists = orm.queryable(Topic.class)
                .where(w -> w.eq(proxy.getId(), "1"))
                .any();
        assertTrue(exists);

        boolean notExists = orm.queryable(Topic.class)
                .where(w -> w.eq(proxy.getId(), "9999"))
                .any();
        assertFalse(notExists);
    }

    @Test
    public void testSelectDto() {
        Topic proxy = orm.proxy(Topic.class);

        // 自动按 DTO 列投影并映射
        List<TopicDTO> dtos = orm.queryable(Topic.class)
                .where(w -> w.le(proxy.getStars(), 20))
                .select(TopicDTO.class)
                .toList();

        assertEquals(2, dtos.size());
        assertEquals("1", dtos.get(0).getId());
        assertEquals("Java ORM Guide", dtos.get(0).getTitle());
        assertEquals(Integer.valueOf(10), dtos.get(0).getStars());
    }

    @Test
    public void testSelectColumn() {
        Topic proxy = orm.proxy(Topic.class);

        List<String> titles = orm.queryable(Topic.class)
                .where(w -> w.isNotNull(proxy.getTitle()))
                .orderByAsc(proxy.getStars())
                .selectColumn(proxy.getTitle());

        assertNotNull(titles);
        assertEquals(5, titles.size());
        assertEquals("Java ORM Guide", titles.get(0));
    }

    @Test
    public void testSqlModeSelect() {
        Topic proxy = orm.proxy(Topic.class);

        // orm.sql() 执行器模式
        List<Topic> list = orm.sql()
                .select(Topic.class)
                .where(w -> w.gt(proxy.getStars(), 20))
                .orderByAsc(proxy.getStars())
                .toList();

        assertEquals(4, list.size());
        assertEquals("3", list.get(0).getId());
        assertEquals(Integer.valueOf(30), list.get(0).getStars());
    }
}
