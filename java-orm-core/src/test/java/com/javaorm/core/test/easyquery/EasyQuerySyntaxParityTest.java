package com.javaorm.core.test.easyquery;

import com.javaorm.core.JavaOrm;
import com.javaorm.core.pagination.PageResult;
import com.javaorm.core.query.SelectBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sqlite.SQLiteDataSource;

import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class EasyQuerySyntaxParityTest {

    private JavaOrm orm;
    private SQLiteDataSource dataSource;

    @Before
    public void setup() throws Exception {
        dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:target/easyquery_syntax_test.db");
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
                    "('1', 10, 'Java ORM Guide', 0, '2026-01-01T10:00:00'), " +
                    "('2', 20, 'EasyQuery Parity', 0, '2026-01-02T10:00:00'), " +
                    "('3', 30, 'Deep Dive SQL', 0, '2026-01-03T10:00:00'), " +
                    "('4', 40, '', 0, '2026-01-04T10:00:00'), " +
                    "('5', 50, '   ', 0, '2026-01-05T10:00:00'), " +
                    "('6', 60, NULL, 0, '2026-01-06T10:00:00'), " +
                    "('7', 70, 'Deleted Topic', 1, '2026-01-07T10:00:00')");

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
    public void testBasicQueryableAndExecution() {
        Topic proxy = orm.proxy(Topic.class);

        // 1. toList
        List<Topic> list = orm.select(Topic.class).toList();
        assertEquals(6, list.size()); // 7 records total, 1 logic deleted

        // 2. firstOrNull
        Topic first = orm.select(Topic.class)
                .where(w -> w.eq(proxy.getId(), "1"))
                .firstOrNull();
        assertNotNull(first);
        assertEquals("Java ORM Guide", first.getTitle());

        Topic notFound = orm.select(Topic.class)
                .where(w -> w.eq(proxy.getId(), "non-existent"))
                .firstOrNull();
        assertNull(notFound);

        // 3. singleOrNull
        Topic single = orm.select(Topic.class)
                .where(w -> w.eq(proxy.getId(), "2"))
                .singleOrNull();
        assertNotNull(single);
        assertEquals(Integer.valueOf(20), single.getStars());

        // 4. whereById
        Topic byId = orm.select(Topic.class).whereById("3").firstOrNull();
        assertNotNull(byId);
        assertEquals("Deep Dive SQL", byId.getTitle());

        // whereById conditional
        Topic byIdCondFalse = orm.select(Topic.class)
                .whereById(false, "3")
                .firstOrNull();
        assertNotNull(byIdCondFalse);
        assertEquals("1", byIdCondFalse.getId()); // Condition false, returns first record without filter

        // 5. whereByIds
        List<Topic> byIds = orm.select(Topic.class).whereByIds("1", "2").toList();
        assertEquals(2, byIds.size());

        List<Topic> byIdsColl = orm.select(Topic.class).whereByIds(Arrays.asList("2", "3")).toList();
        assertEquals(2, byIdsColl.size());

        List<Topic> byIdsFalse = orm.select(Topic.class).whereByIds(false, Arrays.asList("1", "2")).toList();
        assertEquals(6, byIdsFalse.size());
    }

    @Test
    public void testPaginationAndPageResult() {
        Topic proxy = orm.proxy(Topic.class);

        PageResult<Topic> pageResult = orm.select(Topic.class)
                .orderByAsc(proxy.getStars())
                .toPageResult(1, 2);

        assertEquals(6L, pageResult.getTotal());
        assertEquals(2, pageResult.getData().size());
        assertEquals(1, pageResult.getPageNum());
        assertEquals(2, pageResult.getPageSize());
        assertEquals(3, pageResult.getTotalPages());
        assertTrue(pageResult.hasNext());
        assertFalse(pageResult.hasPrevious());
        assertEquals("1", pageResult.getData().get(0).getId());
        assertEquals("2", pageResult.getData().get(1).getId());

        PageResult<Topic> page2 = orm.select(Topic.class)
                .orderByAsc(proxy.getStars())
                .toPageResult(2, 2);
        assertEquals(2, page2.getData().size());
        assertEquals(2, page2.getPageNum());
        assertTrue(page2.hasNext());
        assertTrue(page2.hasPrevious());
        assertEquals("3", page2.getData().get(0).getId());
    }

    @Test
    public void testComparisonAndConditionalPredicates() {
        Topic proxy = orm.proxy(Topic.class);

        // eq, ne, gt, ge, lt, le
        List<Topic> geList = orm.select(Topic.class)
                .where(w -> w.ge(proxy.getStars(), 30))
                .toList();
        assertEquals(4, geList.size()); // 30, 40, 50, 60

        List<Topic> ltList = orm.select(Topic.class)
                .where(w -> w.lt(proxy.getStars(), 30))
                .toList();
        assertEquals(2, ltList.size()); // 10, 20

        // Conditional predicate false: should be omitted
        List<Topic> condList = orm.select(Topic.class)
                .where(w -> {
                    w.ge(false, proxy.getStars(), 100);
                    w.le(true, proxy.getStars(), 30);
                })
                .toList();
        assertEquals(3, condList.size()); // 10, 20, 30
    }

    @Test
    public void testLikeAndPatternMatching() {
        Topic proxy = orm.proxy(Topic.class);

        // like
        List<Topic> likeList = orm.select(Topic.class)
                .where(w -> w.like(proxy.getTitle(), "%ORM%"))
                .toList();
        assertEquals(1, likeList.size());
        assertEquals("Java ORM Guide", likeList.get(0).getTitle());

        // likeMatchLeft: 'Java%'
        List<Topic> matchLeftList = orm.select(Topic.class)
                .where(w -> w.likeMatchLeft(proxy.getTitle(), "Java"))
                .toList();
        assertEquals(1, matchLeftList.size());
        assertEquals("Java ORM Guide", matchLeftList.get(0).getTitle());

        // likeMatchRight: '%Guide'
        List<Topic> matchRightList = orm.select(Topic.class)
                .where(w -> w.likeMatchRight(proxy.getTitle(), "Guide"))
                .toList();
        assertEquals(1, matchRightList.size());

        // notLikeMatchLeft: NOT LIKE 'Java%'
        List<Topic> notMatchLeft = orm.select(Topic.class)
                .where(w -> {
                    w.isNotNull(proxy.getTitle());
                    w.notLikeMatchLeft(proxy.getTitle(), "Java");
                })
                .toList();
        assertTrue(notMatchLeft.stream().noneMatch(t -> "Java ORM Guide".equals(t.getTitle())));

        // notLikeMatchRight: NOT LIKE '%Guide'
        List<Topic> notMatchRight = orm.select(Topic.class)
                .where(w -> {
                    w.isNotNull(proxy.getTitle());
                    w.notLikeMatchRight(proxy.getTitle(), "Guide");
                })
                .toList();
        assertTrue(notMatchRight.stream().noneMatch(t -> "Java ORM Guide".equals(t.getTitle())));
    }

    @Test
    public void testRangePredicates() {
        Topic proxy = orm.proxy(Topic.class);

        // 1. rangeClosed: [20, 40] -> stars >= 20 AND stars <= 40
        List<Topic> closed = orm.select(Topic.class)
                .where(w -> w.rangeClosed(proxy.getStars(), 20, 40))
                .toList();
        assertEquals(3, closed.size()); // 20, 30, 40

        // 2. rangeOpen: (20, 40) -> stars > 20 AND stars < 40
        List<Topic> open = orm.select(Topic.class)
                .where(w -> w.rangeOpen(proxy.getStars(), 20, 40))
                .toList();
        assertEquals(1, open.size()); // 30

        // 3. rangeClosedOpen: [20, 40) -> stars >= 20 AND stars < 40
        List<Topic> closedOpen = orm.select(Topic.class)
                .where(w -> w.rangeClosedOpen(proxy.getStars(), 20, 40))
                .toList();
        assertEquals(2, closedOpen.size()); // 20, 30

        // 4. rangeOpenClosed: (20, 40] -> stars > 20 AND stars <= 40
        List<Topic> openClosed = orm.select(Topic.class)
                .where(w -> w.rangeOpenClosed(proxy.getStars(), 20, 40))
                .toList();
        assertEquals(2, openClosed.size()); // 30, 40

        // 5. Partial range: left only (right is null)
        List<Topic> leftOnly = orm.select(Topic.class)
                .where(w -> w.rangeClosed(proxy.getStars(), 50, null))
                .toList();
        assertEquals(2, leftOnly.size()); // 50, 60

        // 6. Partial range: right only (left is null)
        List<Topic> rightOnly = orm.select(Topic.class)
                .where(w -> w.rangeClosed(proxy.getStars(), null, 20))
                .toList();
        assertEquals(2, rightOnly.size()); // 10, 20
    }

    @Test
    public void testEmptyAndBlankAssertions() {
        Topic proxy = orm.proxy(Topic.class);

        // 1. isEmpty: (title IS NULL OR title = '')
        List<Topic> emptyList = orm.select(Topic.class)
                .where(w -> w.isEmpty(proxy.getTitle()))
                .toList();
        // ID 4 is empty string '', ID 6 is NULL
        assertEquals(2, emptyList.size());
        assertTrue(emptyList.stream().anyMatch(t -> "4".equals(t.getId())));
        assertTrue(emptyList.stream().anyMatch(t -> "6".equals(t.getId())));

        // 2. isNotEmpty: (title IS NOT NULL AND title <> '')
        List<Topic> notEmptyList = orm.select(Topic.class)
                .where(w -> w.isNotEmpty(proxy.getTitle()))
                .toList();
        assertEquals(4, notEmptyList.size()); // ID 1, 2, 3, 5

        // 3. isBlank: (title IS NULL OR title = '' OR LTRIM(title) = '')
        List<Topic> blankList = orm.select(Topic.class)
                .where(w -> w.isBlank(proxy.getTitle()))
                .toList();
        // ID 4 (''), ID 5 ('   '), ID 6 (NULL)
        assertEquals(3, blankList.size());
        assertTrue(blankList.stream().anyMatch(t -> "5".equals(t.getId())));

        // 4. isNotBlank: (title IS NOT NULL AND title <> '' AND LTRIM(title) <> '')
        List<Topic> notBlankList = orm.select(Topic.class)
                .where(w -> w.isNotBlank(proxy.getTitle()))
                .toList();
        // ID 1, 2, 3
        assertEquals(3, notBlankList.size());
    }

    @Test
    public void testEmptyCollectionInAndNotIn() {
        Topic proxy = orm.proxy(Topic.class);

        // Empty collection in IN -> 1 = 2 (returns 0 rows)
        List<Topic> emptyIn = orm.select(Topic.class)
                .where(w -> w.in(proxy.getStars(), Collections.emptyList()))
                .toList();
        assertEquals(0, emptyIn.size());

        // Empty collection in NOT IN -> 1 = 1 (returns all active rows)
        List<Topic> emptyNotIn = orm.select(Topic.class)
                .where(w -> w.notIn(proxy.getStars(), Collections.emptyList()))
                .toList();
        assertEquals(6, emptyNotIn.size());
    }

    @Test
    public void testWhereObjectDynamicQuery() {
        TopicSearchRequest req = new TopicSearchRequest();
        req.setTitlePrefix("Java");
        req.setCreateTimeBegin(LocalDateTime.of(2026, 1, 1, 0, 0, 0));

        List<Topic> list = orm.select(Topic.class)
                .whereObject(req)
                .toList();
        assertEquals(1, list.size());
        assertEquals("Java ORM Guide", list.get(0).getTitle());

        // Empty/null search request should return all
        TopicSearchRequest emptyReq = new TopicSearchRequest();
        List<Topic> allList = orm.select(Topic.class)
                .whereObject(emptyReq)
                .toList();
        assertEquals(6, allList.size());
    }

    @Test
    public void testLogicDeleteAndDisableLogicDelete() {
        // Normal query ignores is_deleted = 1
        List<Topic> normal = orm.select(Topic.class).toList();
        assertEquals(6, normal.size());
        assertTrue(normal.stream().noneMatch(t -> "7".equals(t.getId())));

        // disableLogicDelete includes is_deleted = 1
        List<Topic> all = orm.select(Topic.class)
                .disableLogicDelete()
                .toList();
        assertEquals(7, all.size());
        assertTrue(all.stream().anyMatch(t -> "7".equals(t.getId())));
    }
}
