package com.javaorm.core.easyquery_cases;

import com.javaorm.core.easyquery_cases.entity.BlogEntity;
import com.javaorm.core.easyquery_cases.entity.Topic;
import org.junit.Test;

import java.util.List;

import static com.javaorm.core.SQL.avg;
import static org.junit.Assert.*;

/**
 * 1:1 复刻 EasyQuery 官方子查询与集合操作测试 (SubQueryTest & UnionTest)
 */
public class EasyQuerySubQueryTest extends EasyQueryBaseTest {

    @Test
    public void testWhereInSubQuery() {
        BlogEntity b = orm.proxy(BlogEntity.class);
        Topic t = orm.proxy(Topic.class);

        // 查询发表过 star >= 25 博客的 Topic
        List<Topic> list = orm.queryable(Topic.class)
                .where(w -> w.in(Topic::getId,
                        orm.select(b.getTopicId())
                                .from(BlogEntity.class)
                                .where().ge(b.getStar(), 25)))
                .orderByAsc(t.getId())
                .toList();

        // b3(star 25, topicId 2), b4(star 35, topicId 3)
        assertEquals(2, list.size());
        assertEquals("2", list.get(0).getId());
        assertEquals("3", list.get(1).getId());
    }

    @Test
    public void testWhereNotInSubQuery() {
        BlogEntity b = orm.proxy(BlogEntity.class);
        Topic t = orm.proxy(Topic.class);

        // 查询没有关联任何博客的 Topic (总共 6 条有效 Topic，b1-b4 关联了 1, 2, 3，剩下 4, 5, 6 没有博客)
        List<Topic> list = orm.queryable(Topic.class)
                .where(w -> w.notIn(Topic::getId,
                        orm.select(b.getTopicId()).from(BlogEntity.class)))
                .orderByAsc(t.getId())
                .toList();

        assertEquals(3, list.size());
        assertEquals("4", list.get(0).getId());
        assertEquals("5", list.get(1).getId());
        assertEquals("6", list.get(2).getId());
    }

    @Test
    public void testWhereScalarComparisonSubQuery() {
        Topic subT = orm.proxy(Topic.class);
        com.javaorm.core.query.SelectBuilder<Topic> avgSubQuery = orm.select(avg(subT.getStars())).from(Topic.class);

        Topic t = orm.proxy(Topic.class);

        // 查询 stars 大于所有主题平均 stars 的主题
        // 10, 20, 30, 40, 50, 60 -> avg = 35
        List<Topic> list = orm.queryable(Topic.class)
                .where(w -> w.gt(t.getStars(), com.javaorm.core.SQL.subQuery(avgSubQuery)))
                .orderByAsc(t.getStars())
                .toList();

        assertEquals(3, list.size()); // 40, 50, 60
        assertEquals("4", list.get(0).getId());
        assertEquals("5", list.get(1).getId());
        assertEquals("6", list.get(2).getId());
    }

    @Test
    public void testUnionAndUnionAll() {
        Topic t = orm.proxy(Topic.class);

        // query1: stars <= 20 (id: 1, 2)
        // query2: stars >= 20 and stars <= 30 (id: 2, 3)
        com.javaorm.core.query.SelectBuilder<Topic> q1 = orm.queryable(Topic.class).where(w -> w.le(t.getStars(), 20));
        com.javaorm.core.query.SelectBuilder<Topic> q2 = orm.queryable(Topic.class).where(w -> w.between(t.getStars(), 20, 30));

        // 1. UNION (自动去重，预期 1, 2, 3 共 3 条)
        List<Topic> unionList = q1.union(q2).toList();
        assertEquals(3, unionList.size());

        // 2. UNION ALL (不去重，id 2 重复，预期共 4 条)
        com.javaorm.core.query.SelectBuilder<Topic> q3 = orm.queryable(Topic.class).where(w -> w.le(t.getStars(), 20));
        com.javaorm.core.query.SelectBuilder<Topic> q4 = orm.queryable(Topic.class).where(w -> w.between(t.getStars(), 20, 30));
        List<Topic> unionAllList = q3.unionAll(q4).toList();
        assertEquals(4, unionAllList.size());
    }

    @Test
    public void testSqlModeSubQuery() {
        BlogEntity b = orm.proxy(BlogEntity.class);

        List<Topic> list = orm.sql()
                .select(Topic.class)
                .where(w -> w.in(Topic::getId,
                        orm.select(b.getTopicId())
                                .from(BlogEntity.class)
                                .where().eq(b.getStar(), 5)))
                .toList();

        assertEquals(1, list.size());
        assertEquals("1", list.get(0).getId());
    }
}
