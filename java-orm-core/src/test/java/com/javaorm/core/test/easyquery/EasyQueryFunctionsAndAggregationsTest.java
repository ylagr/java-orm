package com.javaorm.core.test.easyquery;

import com.javaorm.core.JavaOrm;
import com.javaorm.core.query.Cte;
import com.javaorm.core.query.SelectBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sqlite.SQLiteDataSource;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static com.javaorm.core.SQL.*;
import static org.junit.Assert.assertEquals;

public class EasyQueryFunctionsAndAggregationsTest {

    private JavaOrm orm;
    private SQLiteDataSource dataSource;

    @Before
    public void setup() throws Exception {
        dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:target/easyquery_funcs_test.db");
        orm = JavaOrm.create(dataSource);

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS t_topic");
            stmt.execute("DROP TABLE IF EXISTS t_blog");
            stmt.execute("CREATE TABLE t_topic (" +
                    "id VARCHAR(50) PRIMARY KEY, " +
                    "stars INTEGER, " +
                    "title VARCHAR(200), " +
                    "is_deleted INTEGER DEFAULT 0, " +
                    "create_time TIMESTAMP)");

            stmt.execute("CREATE TABLE t_blog (" +
                    "id VARCHAR(50) PRIMARY KEY, " +
                    "title VARCHAR(200), " +
                    "content TEXT, " +
                    "star INTEGER, " +
                    "topic_id VARCHAR(50), " +
                    "create_time TIMESTAMP)");

            stmt.execute("INSERT INTO t_topic (id, stars, title, is_deleted, create_time) VALUES " +
                    "('1', 10, 'Java ORM', 0, '2026-01-01T10:00:00'), " +
                    "('2', 20, 'EasyQuery Parity', 0, '2026-01-02T10:00:00'), " +
                    "('3', 30, 'SQL Engine', 0, '2026-01-03T10:00:00'), " +
                    "('4', 40, NULL, 0, '2026-01-04T10:00:00')");

            stmt.execute("INSERT INTO t_blog (id, title, content, star, topic_id, create_time) VALUES " +
                    "('b1', 'Blog 1', 'Content 1', 5, '1', '2026-01-01T11:00:00'), " +
                    "('b2', 'Blog 2', 'Content 2', 15, '1', '2026-01-01T12:00:00'), " +
                    "('b3', 'Blog 3', 'Content 3', 25, '2', '2026-01-02T11:00:00')");
        }
    }

    @After
    public void tearDown() {
        SelectBuilder.resetAliasSeq();
    }

    @Test
    public void testStringFormatAndNullOrDefault() {
        Topic proxy = orm.proxy(Topic.class);

        // 1. stringFormat: CONCAT('ID:', id, ', Title:', title)
        List<Map<String, Object>> list = orm.select(
                proxy.getId(),
                stringFormat("ID:{0}, Title:{1}", proxy.getId(), proxy.getTitle()).as("info")
        ).from(Topic.class).maps();

        assertEquals(4, list.size());
        assertEquals("ID:1, Title:Java ORM", list.get(0).get("info"));

        // 2. nullOrDefault: COALESCE(title, 'Default Title')
        List<Map<String, Object>> nullList = orm.select(
                        proxy.getId(),
                        nullOrDefault(proxy.getTitle(), "Default Title").as("safe_title")
                ).from(Topic.class)
                .where().and(proxy.getId()).eq("4")
                .maps();

        assertEquals(1, nullList.size());
        assertEquals("Default Title", nullList.get(0).get("safe_title"));
    }

    @Test
    public void testAggregationsAndGroupByHaving() {
        Blog blog = orm.proxy(Blog.class);

        // 1. Aggregate functions
        List<Map<String, Object>> stats = orm.select(
                        blog.getTopicId(),
                        count().as("total_blogs"),
                        sum(blog.getStar()).as("total_stars"),
                        avg(blog.getStar()).as("avg_stars"),
                        min(blog.getStar()).as("min_star"),
                        max(blog.getStar()).as("max_star")
                ).from(Blog.class)
                .groupBy(blog.getTopicId())
                .having(w -> w.and(count()).ge(2))
                .maps();

        assertEquals(1, stats.size());
        Map<String, Object> stat1 = stats.get(0);
        assertEquals("1", stat1.get("topic_id"));
        assertEquals(2L, ((Number) stat1.get("total_blogs")).longValue());
        assertEquals(20L, ((Number) stat1.get("total_stars")).longValue());
    }

    @Test
    public void testWindowFunctions() {
        Topic proxy = orm.proxy(Topic.class);

        List<Map<String, Object>> windowList = orm.select(
                        proxy.getId(),
                        proxy.getStars(),
                        rowNumber().over().orderBy(proxy.getStars()).desc().as("rn"),
                        rank().over().orderBy(proxy.getStars()).desc().as("rk"),
                        denseRank().over().orderBy(proxy.getStars()).desc().as("drk"),
                        ntile(2).over().orderBy(proxy.getStars()).desc().as("nt")
                ).from(Topic.class)
                .orderByDesc(proxy.getStars())
                .maps();

        assertEquals(4, windowList.size());
        // Rank 1: stars = 40
        assertEquals(1L, ((Number) windowList.get(0).get("rn")).longValue());
        assertEquals(40, ((Number) windowList.get(0).get("stars")).intValue());
    }

    @Test
    public void testJoinsAndSubqueries() {
        Topic topic = orm.proxy(Topic.class);
        Blog blog = orm.proxy(Blog.class);

        // 1. Left Join
        List<Map<String, Object>> leftJoinList = orm.select(
                        topic.getId(),
                        topic.getTitle(),
                        col(blog.getTitle()).as("blog_title")
                ).from(Topic.class)
                .leftJoin(Blog.class, (w, t, b) -> w.and(b.getTopicId()).eq(t.getId()))
                .where().and(topic.getId()).eq("1")
                .maps();

        assertEquals(2, leftJoinList.size());

        // 2. EXISTS Subquery in WHERE
        SelectBuilder<Blog> existsSubQuery = orm.queryable(Blog.class)
                .where(w -> w.and(blog.getTopicId()).eq(topic.getId()));

        List<Topic> existsList = orm.queryable(Topic.class)
                .where(w -> w.andExists(existsSubQuery))
                .toList();
        assertEquals(2, existsList.size()); // topics 1 and 2 have blogs

        // 3. IN Subquery in WHERE
        SelectBuilder<Blog> topicIdSubQuery = orm.queryable(Blog.class)
                .select(blog.getTopicId());

        List<Topic> inSubQueryList = orm.queryable(Topic.class)
                .where(w -> w.and(topic.getId()).in(topicIdSubQuery))
                .toList();
        assertEquals(2, inSubQueryList.size());
    }

    @Test
    public void testCteAndDtoQuery() {
        Topic topic = orm.proxy(Topic.class);

        // 1. DTO Projection
        List<TopicDto> dtoList = orm.select(TopicDto.class,
                        topic.getId(),
                        topic.getTitle(),
                        topic.getStars(),
                        topic.getCreateTime()
                ).from(Topic.class)
                .where(w -> w.and(topic.getStars()).gt(15))
                .orderByAsc(topic.getStars())
                .toList();

        assertEquals(3, dtoList.size());
        assertEquals("2", dtoList.get(0).getId());
        assertEquals("EasyQuery Parity", dtoList.get(0).getTitle());

        // 2. CTE query
        Cte<Topic> highStarsCte = orm.queryable(Topic.class)
                .where(w -> w.and(topic.getStars()).ge(30))
                .asCte("cte_high_stars");

        Topic highProxy = highStarsCte.proxy();
        List<Topic> fromCte = orm.select(highStarsCte)
                .where(w -> w.and(highProxy.getStars()).le(35))
                .toList();

        assertEquals(1, fromCte.size());
        assertEquals("3", fromCte.get(0).getId());
    }
}
