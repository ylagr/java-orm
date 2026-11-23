package com.javaorm.core.easyquery_cases;

import com.javaorm.core.easyquery_cases.entity.BlogEntity;
import com.javaorm.core.easyquery_cases.entity.Company;
import com.javaorm.core.easyquery_cases.entity.SysUser;
import com.javaorm.core.easyquery_cases.entity.Topic;
import com.javaorm.core.easyquery_cases.entity.UserExtra;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * 1:1 复刻 EasyQuery 官方多表连接查询测试 (JoinTest)
 */
public class EasyQueryJoinTest extends EasyQueryBaseTest {

    @Test
    public void testLeftJoin2Tables() {
        BlogEntity b = orm.proxy(BlogEntity.class);
        Topic t = orm.proxy(Topic.class);

        // BlogEntity left join Topic on b.topicId = t.id
        List<BlogEntity> list = orm.queryable(BlogEntity.class)
                .leftJoin(Topic.class, (w, blog, topic) -> w.eq(blog.getTopicId(), topic.getId()))
                .where(w -> w.ge(b.getStar(), 10))
                .toList();

        assertEquals(4, list.size()); // b2(15), b3(25), b4(35), b5(45)

        String sql = orm.queryable(BlogEntity.class)
                .leftJoin(Topic.class, (w, blog, topic) -> w.eq(blog.getTopicId(), topic.getId()))
                .where(w -> w.ge(b.getStar(), 10))
                .toSQL();

        assertTrue("SQL 必须包含 LEFT JOIN: " + sql, sql.contains("LEFT JOIN"));
        assertTrue("SQL 必须包含 t_topic: " + sql, sql.contains("\"t_topic\""));
    }

    @Test
    public void testInnerJoin2Tables() {
        // BlogEntity inner join Topic on b.topicId = t.id
        // 注意：b5 的 topicId 是 '999'，在 t_topic 中不存在；t_topic 的 id='7' 已逻辑删除
        // 故 inner join 结果不包含 b5
        List<BlogEntity> list = orm.queryable(BlogEntity.class)
                .innerJoin(Topic.class, (w, blog, topic) -> w.eq(blog.getTopicId(), topic.getId()))
                .toList();

        assertEquals(4, list.size()); // b1, b2, b3, b4

        String sql = orm.queryable(BlogEntity.class)
                .innerJoin(Topic.class, (w, blog, topic) -> w.eq(blog.getTopicId(), topic.getId()))
                .toSQL();

        assertTrue("SQL 必须包含 INNER JOIN: " + sql, sql.contains("INNER JOIN"));
    }

    @Test
    public void testRightJoin2Tables() {
        String sql = orm.queryable(BlogEntity.class)
                .rightJoin(Topic.class, (w, blog, topic) -> w.eq(blog.getTopicId(), topic.getId()))
                .toSQL();

        assertTrue("SQL 必须包含 RIGHT JOIN: " + sql, sql.contains("RIGHT JOIN"));
    }

    @Test
    public void testThreeTableJoin() {
        SysUser u = orm.proxy(SysUser.class);

        // SysUser left join Company left join UserExtra
        List<SysUser> users = orm.queryable(SysUser.class)
                .leftJoin(Company.class, (w, usr, comp) -> w.eq(usr.getCompanyId(), comp.getId()))
                .leftJoin(UserExtra.class, (w, usr, ext) -> w.eq(usr.getId(), ext.getUserId()))
                .where(w -> w.gt(u.getAge(), 26))
                .orderByAsc(u.getAge())
                .toList();

        assertEquals(3, users.size()); // Charlie(28), Bob(30), David(35)
        assertEquals("Charlie", users.get(0).getName());
        assertEquals("Bob", users.get(1).getName());
        assertEquals("David", users.get(2).getName());
    }

    @Test
    public void testJoinWithWhereFilters() {
        BlogEntity b = orm.proxy(BlogEntity.class);
        Topic t = orm.proxy(Topic.class);

        // 连表附带两表各自的过滤条件
        List<BlogEntity> list = orm.queryable(BlogEntity.class)
                .leftJoin(Topic.class, (w, blog, topic) -> w.eq(blog.getTopicId(), topic.getId()))
                .where(w -> w.gt(b.getStar(), 10).and(w2 -> w2.eq(t.getId(), "1")))
                .toList();

        assertEquals(1, list.size());
        assertEquals("b2", list.get(0).getId());
    }

    @Test
    public void testSqlModeJoin() {
        // 使用 orm.sql() 模式进行两表连接
        List<BlogEntity> list = orm.sql(e -> {
            BlogEntity b = e.table(BlogEntity.class, "b");
            Topic t = e.table(Topic.class, "t");
            e.select(b)
                    .from(b)
                    .leftJoin(t).on(b.getTopicId(), t.getId())
                    .where(w -> w.eq(t.getId(), "2"));
        }).toList();

        assertEquals(1, list.size());
        assertEquals("b3", list.get(0).getId());
    }

    @Test
    public void testSqlModeLeftJoinMultipleOnConditions() {
        // 使用 leftjoin().on(on -> on.eq(col1, col2).eq(col3, col4)) 进行链式多条件 ON 关联
        List<BlogEntity> list = orm.sql(e -> {
            BlogEntity b = e.table(BlogEntity.class, "b");
            Topic t = e.table(Topic.class, "t");
            e.select(b)
                    .from(b)
                    .leftjoin(t).on(on -> on.eq(b.getTopicId(), t.getId()).eq(t.getId(), "2"))
                    .where(w -> w.isNotNull(t.getId()));
        }).toList();

        assertEquals(1, list.size());
        assertEquals("b3", list.get(0).getId());
    }

    @Test
    public void testQueryableLeftJoinMultipleOnConditions() {
        // 使用 queryable 的 leftjoin().on(on -> on.eq(col1, col2).eq(col3, col4))
        BlogEntity b = com.javaorm.core.proxy.ProxyFactory.createProxy(BlogEntity.class, "t0");
        Topic t = com.javaorm.core.proxy.ProxyFactory.createProxy(Topic.class, "t1");

        List<BlogEntity> list = orm.queryable(BlogEntity.class)
                .leftjoin(Topic.class)
                .on(on -> on.eq(b.getTopicId(), t.getId()).eq(t.getId(), "2"))
                .where(w -> w.isNotNull(t.getId()))
                .toList();

        assertEquals(1, list.size());
        assertEquals("b3", list.get(0).getId());
    }
}
