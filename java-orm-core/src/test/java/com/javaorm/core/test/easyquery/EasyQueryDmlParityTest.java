package com.javaorm.core.test.easyquery;

import com.javaorm.core.JavaOrm;
import com.javaorm.core.dialect.MySQLDialect;
import com.javaorm.core.dialect.PostgreSQLDialect;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sqlite.SQLiteDataSource;

import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class EasyQueryDmlParityTest {

    private JavaOrm orm;
    private SQLiteDataSource dataSource;

    @Before
    public void setup() throws Exception {
        dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:target/easyquery_dml_test.db");
        orm = JavaOrm.create(dataSource);

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS t_topic");
            stmt.execute("CREATE TABLE t_topic (" +
                    "id VARCHAR(50) PRIMARY KEY, " +
                    "stars INTEGER, " +
                    "title VARCHAR(200), " +
                    "is_deleted INTEGER DEFAULT 0, " +
                    "create_time TIMESTAMP)");

            stmt.execute("INSERT INTO t_topic (id, stars, title, is_deleted, create_time) VALUES " +
                    "('1', 10, 'Initial Topic', 0, '2026-01-01 10:00:00'), " +
                    "('2', 20, 'Topic 2', 0, '2026-01-02 10:00:00')");
        }
    }

    @After
    public void tearDown() {
        com.javaorm.core.query.Query.resetAliasSeq();
    }

    @Test
    public void testInsertableAndExecution() {
        Topic newTopic = new Topic("100", 99, "Inserted Topic", LocalDateTime.now());

        // 1. toSQL check
        String sql = orm.insertable(newTopic).toSQL();
        assertTrue("Insert SQL must contain INSERT INTO: " + sql, sql.startsWith("INSERT INTO"));
        assertTrue("Must quote table name: " + sql, sql.contains("\"t_topic\""));

        // 2. executeRows
        int rows = orm.insertable(newTopic).executeRows();
        assertEquals(1, rows);

        Topic query = orm.queryable(Topic.class).whereById("100").firstOrNull();
        assertNotNull(query);
        assertEquals("Inserted Topic", query.getTitle());
        assertEquals(Integer.valueOf(99), query.getStars());

        // 3. Batch insert
        Topic t1 = new Topic("101", 10, "Batch 1", LocalDateTime.now());
        Topic t2 = new Topic("102", 20, "Batch 2", LocalDateTime.now());
        int batchRows = orm.insert(Arrays.asList(t1, t2));
        assertEquals(2, batchRows);

        List<Topic> batchQuery = orm.queryable(Topic.class).whereByIds("101", "102").toList();
        assertEquals(2, batchQuery.size());
    }

    @Test
    public void testUpdatableAndExecution() {
        Topic proxy = orm.proxy(Topic.class);

        // 1. Fluent update with whereById
        String sql = orm.updatable(Topic.class)
                .set(proxy.getTitle(), "Updated Title 1")
                .set(proxy.getStars(), 88)
                .whereById("1")
                .toSQL();
        assertTrue(sql.contains("UPDATE \"t_topic\" SET"));
        assertTrue(sql.contains("\"title\" = ?"));
        assertTrue(sql.contains("\"stars\" = ?"));

        int rows = orm.updatable(Topic.class)
                .set(proxy.getTitle(), "Updated Title 1")
                .set(proxy.getStars(), 88)
                .whereById("1")
                .executeRows();
        assertEquals(1, rows);

        Topic query = orm.queryable(Topic.class).whereById("1").firstOrNull();
        assertNotNull(query);
        assertEquals("Updated Title 1", query.getTitle());
        assertEquals(Integer.valueOf(88), query.getStars());

        // 2. Entity-level updatable
        query.setTitle("Entity Updated");
        int entityRows = orm.updatable(query).executeRows();
        assertEquals(1, entityRows);

        Topic updated = orm.queryable(Topic.class).whereById("1").firstOrNull();
        assertNotNull(updated);
        assertEquals("Entity Updated", updated.getTitle());

        // 3. Multi-dialect SQL output
        String mysqlSql = orm.updatable(Topic.class)
                .set(proxy.getTitle(), "Dialect Title")
                .whereById("1")
                .toSQL(new MySQLDialect());
        assertTrue("MySQL must use backticks: " + mysqlSql, mysqlSql.contains("`t_topic`"));

        String pgSql = orm.updatable(Topic.class)
                .set(proxy.getTitle(), "Dialect Title")
                .whereById("1")
                .toSQL(new PostgreSQLDialect());
        assertTrue("PostgreSQL must use double quotes: " + pgSql, pgSql.contains("\"t_topic\""));
    }

    @Test
    public void testDeletableLogicDeleteAndPhysicalDelete() {
        Topic proxy = orm.proxy(Topic.class);

        // 1. Logic delete: should rewrite to UPDATE t_topic SET is_deleted = 1
        String logicDeleteSql = orm.deletable(Topic.class)
                .whereById("1")
                .toSQL();
        assertTrue("Logic delete must rewrite to UPDATE: " + logicDeleteSql, logicDeleteSql.startsWith("UPDATE"));
        assertTrue("Must set is_deleted: " + logicDeleteSql, logicDeleteSql.contains("\"is_deleted\" = ?"));

        int rows = orm.deletable(Topic.class).whereById("1").executeRows();
        assertEquals(1, rows);

        // Verify normal query cannot find it anymore
        Topic normalQuery = orm.queryable(Topic.class).whereById("1").firstOrNull();
        assertNull(normalQuery);

        // Verify record still physically exists in database
        Topic rawRecord = orm.queryable(Topic.class).disableLogicDelete().whereById("1").firstOrNull();
        assertNotNull(rawRecord);
        assertEquals(Integer.valueOf(1), rawRecord.getIsDeleted());

        // 2. Physical delete with disableLogicDelete(): DELETE FROM t_topic
        String physicalSql = orm.deletable(Topic.class)
                .disableLogicDelete()
                .whereById("1")
                .toSQL();
        assertTrue("Physical delete must use DELETE FROM: " + physicalSql, physicalSql.startsWith("DELETE FROM"));

        int physicalRows = orm.deletable(Topic.class)
                .disableLogicDelete()
                .whereById("1")
                .executeRows();
        assertEquals(1, physicalRows);

        // Record completely removed
        Topic physicallyDeleted = orm.queryable(Topic.class).disableLogicDelete().whereById("1").firstOrNull();
        assertNull(physicallyDeleted);

        // 3. Entity-level deletable and batch deletable
        Topic t2 = orm.queryable(Topic.class).whereById("2").firstOrNull();
        assertNotNull(t2);
        int delRows = orm.deletable(t2).executeRows();
        assertEquals(1, delRows);

        assertNull(orm.queryable(Topic.class).whereById("2").firstOrNull());
    }
}
