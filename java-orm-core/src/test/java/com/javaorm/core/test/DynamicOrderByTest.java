package com.javaorm.core.test;

import com.javaorm.core.JavaOrm;
import com.javaorm.core.ast.OrderDirection;
import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.dialect.H2Dialect;
import com.javaorm.core.query.Query;
import com.javaorm.core.test.entity.Student;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

import static com.javaorm.core.SQL.$;
import static com.javaorm.core.SQL.count;
import static com.javaorm.core.SQL.rowNumber;
import static org.junit.Assert.*;

/**
 * 动态 ORDER BY（动态 ASC / DESC、方向切换、条件排序）单元测试。
 */
public class DynamicOrderByTest {

    private JavaOrm orm;

    @Before
    public void setUp() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:dynamic_order_test;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");

        OrmConfig config = new OrmConfig();
        config.setPrintSql(false);
        orm = JavaOrm.create(ds, new H2Dialect(), config);

        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS \"t_student\"");
            stmt.execute("CREATE TABLE \"t_student\" (" +
                    "\"id\" BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "\"student_name\" VARCHAR(50), " +
                    "\"age\" INT, " +
                    "\"teacher_id\" BIGINT, " +
                    "\"is_deleted\" INT DEFAULT 0, " +
                    "\"create_time\" TIMESTAMP)");

            stmt.execute("INSERT INTO \"t_student\" (\"student_name\", \"age\", \"teacher_id\", \"is_deleted\") VALUES ('Alice', 20, 1, 0)");
            stmt.execute("INSERT INTO \"t_student\" (\"student_name\", \"age\", \"teacher_id\", \"is_deleted\") VALUES ('Bob', 25, 1, 0)");
            stmt.execute("INSERT INTO \"t_student\" (\"student_name\", \"age\", \"teacher_id\", \"is_deleted\") VALUES ('Charlie', 18, 2, 0)");
        }
    }

    @Test
    public void testOrderByBuilderAscBoolean() {
        Student s = $(Student.class);

        // asc(true) -> ASC
        String sqlAsc = orm.select(Student.class).orderBy(s.getAge()).asc(true).toSql();
        assertTrue("asc(true) must generate ASC: " + sqlAsc, sqlAsc.contains("ORDER BY t0.\"age\" ASC"));

        // asc(false) -> DESC
        String sqlDesc = orm.select(Student.class).orderBy(s.getAge()).asc(false).toSql();
        assertTrue("asc(false) must generate DESC: " + sqlDesc, sqlDesc.contains("ORDER BY t0.\"age\" DESC"));

        // asc((Boolean) null) -> 默认 ASC
        String sqlDefault = orm.select(Student.class).orderBy(s.getAge()).asc((Boolean) null).toSql();
        assertTrue("asc(null) must default to ASC: " + sqlDefault, sqlDefault.contains("ORDER BY t0.\"age\" ASC"));
    }

    @Test
    public void testOrderByBuilderDescBoolean() {
        Student s = $(Student.class);

        // desc(true) -> DESC
        String sqlDesc = orm.select(Student.class).orderBy(s.getAge()).desc(true).toSql();
        assertTrue("desc(true) must generate DESC: " + sqlDesc, sqlDesc.contains("ORDER BY t0.\"age\" DESC"));

        // desc(false) -> ASC
        String sqlAsc = orm.select(Student.class).orderBy(s.getAge()).desc(false).toSql();
        assertTrue("desc(false) must generate ASC: " + sqlAsc, sqlAsc.contains("ORDER BY t0.\"age\" ASC"));

        // desc((Boolean) null) -> 默认 DESC
        String sqlDefault = orm.select(Student.class).orderBy(s.getAge()).desc((Boolean) null).toSql();
        assertTrue("desc(null) must default to DESC: " + sqlDefault, sqlDefault.contains("ORDER BY t0.\"age\" DESC"));
    }

    @Test
    public void testOrderByBuilderSortAndDirection() {
        Student s = $(Student.class);

        // sort(boolean)
        String s1 = orm.select(Student.class).orderBy(s.getAge()).sort(true).toSql();
        assertTrue(s1.contains("ORDER BY t0.\"age\" ASC"));
        String s2 = orm.select(Student.class).orderBy(s.getAge()).sort(false).toSql();
        assertTrue(s2.contains("ORDER BY t0.\"age\" DESC"));

        // sort(OrderDirection)
        String s3 = orm.select(Student.class).orderBy(s.getAge()).sort(OrderDirection.ASC).toSql();
        assertTrue(s3.contains("ORDER BY t0.\"age\" ASC"));
        String s4 = orm.select(Student.class).orderBy(s.getAge()).sort(OrderDirection.DESC).toSql();
        assertTrue(s4.contains("ORDER BY t0.\"age\" DESC"));

        // sort(String)
        String s5 = orm.select(Student.class).orderBy(s.getAge()).sort("asc").toSql();
        assertTrue(s5.contains("ORDER BY t0.\"age\" ASC"));
        String s6 = orm.select(Student.class).orderBy(s.getAge()).sort("DESC").toSql();
        assertTrue(s6.contains("ORDER BY t0.\"age\" DESC"));

        // direction alias
        String s7 = orm.select(Student.class).orderBy(s.getAge()).direction("desc").toSql();
        assertTrue(s7.contains("ORDER BY t0.\"age\" DESC"));
        String s8 = orm.select(Student.class).orderBy(s.getAge()).ascOrDesc(false).toSql();
        assertTrue(s8.contains("ORDER BY t0.\"age\" DESC"));
    }

    @Test
    public void testConditionalSorting() {
        Student s = $(Student.class);

        // ascIf
        String s1 = orm.select(Student.class).orderBy(s.getAge()).ascIf(true).toSql();
        assertTrue(s1.contains("ORDER BY t0.\"age\" ASC"));
        String s2 = orm.select(Student.class).orderBy(s.getAge()).ascIf(false).toSql();
        assertFalse("ascIf(false) must not have ORDER BY: " + s2, s2.contains("ORDER BY"));

        // descIf
        String s3 = orm.select(Student.class).orderBy(s.getAge()).descIf(true).toSql();
        assertTrue(s3.contains("ORDER BY t0.\"age\" DESC"));
        String s4 = orm.select(Student.class).orderBy(s.getAge()).descIf(false).toSql();
        assertFalse("descIf(false) must not have ORDER BY: " + s4, s4.contains("ORDER BY"));

        // sort(condition, isAsc)
        String s5 = orm.select(Student.class).orderBy(s.getAge()).sort(true, false).toSql();
        assertTrue(s5.contains("ORDER BY t0.\"age\" DESC"));
        String s6 = orm.select(Student.class).orderBy(s.getAge()).sort(false, false).toSql();
        assertFalse(s6.contains("ORDER BY"));

        // sort(condition, direction)
        String s7 = orm.select(Student.class).orderBy(s.getAge()).sort(true, "desc").toSql();
        assertTrue(s7.contains("ORDER BY t0.\"age\" DESC"));
        String s8 = orm.select(Student.class).orderBy(s.getAge()).sort(false, "desc").toSql();
        assertFalse(s8.contains("ORDER BY"));
    }

    @Test
    public void testQueryDirectDynamicOrderBy() {
        Student s = $(Student.class);

        // query.orderBy(val, isAsc)
        String q1 = orm.select(Student.class).orderBy(s.getAge(), false).toSql();
        assertTrue(q1.contains("ORDER BY t0.\"age\" DESC"));

        String q2 = orm.select(Student.class).orderBy(s.getAge(), true).toSql();
        assertTrue(q2.contains("ORDER BY t0.\"age\" ASC"));

        // query.orderBy(val, "desc")
        String q3 = orm.select(Student.class).orderBy(s.getAge(), "desc").toSql();
        assertTrue(q3.contains("ORDER BY t0.\"age\" DESC"));

        // query.orderBy(val, OrderDirection.DESC)
        String q4 = orm.select(Student.class).orderBy(s.getAge(), OrderDirection.DESC).toSql();
        assertTrue(q4.contains("ORDER BY t0.\"age\" DESC"));

        // query.orderBy(condition, val, isAsc)
        String q5 = orm.select(Student.class).orderBy(false, s.getAge(), true).toSql();
        assertFalse(q5.contains("ORDER BY"));
        String q6 = orm.select(Student.class).orderBy(true, s.getAge(), false).toSql();
        assertTrue(q6.contains("ORDER BY t0.\"age\" DESC"));

        // query.orderByAsc(condition, val)
        String q7 = orm.select(Student.class).orderByAsc(true, s.getAge()).toSql();
        assertTrue(q7.contains("ORDER BY t0.\"age\" ASC"));
        String q8 = orm.select(Student.class).orderByAsc(false, s.getAge()).toSql();
        assertFalse(q8.contains("ORDER BY"));

        // Lambda overload
        String q9 = orm.select(Student.class).orderBy(Student::getAge, false).toSql();
        assertTrue(q9.contains("ORDER BY t0.\"age\" DESC"));
        String q10 = orm.select(Student.class).orderBy(true, Student::getAge, "desc").toSql();
        assertTrue(q10.contains("ORDER BY t0.\"age\" DESC"));
    }

    @Test
    public void testWhereAndHavingAndWindowDynamicOrderBy() {
        Student s = $(Student.class);

        // WhereBuilder 链式动态排序
        String wSql = orm.select(Student.class)
                .where()
                .and(s.getAge()).gt(10)
                .orderBy(s.getAge(), false)
                .toSql();
        assertTrue(wSql.contains("WHERE t0.\"is_deleted\" = ? AND (t0.\"age\" > ?) ORDER BY t0.\"age\" DESC"));

        // HavingBuilder 链式动态排序
        String hSql = orm.select(Student.class)
                .groupBy(s.getTeacherId())
                .having()
                .and(count(s.getId())).gt(0L)
                .orderBy(s.getTeacherId(), false)
                .toSql();
        assertTrue(hSql.contains("HAVING COUNT(t0.\"id\") > ? ORDER BY t0.\"teacher_id\" DESC"));

        // 开窗函数动态排序
        Query<Student> winQuery = orm.select(
                s.getId(),
                rowNumber().over().partitionBy(s.getTeacherId()).orderBy(s.getAge()).asc(false).as("rn")
        ).from(Student.class);
        String winSql = winQuery.toSql();
        assertTrue(winSql.contains("ROW_NUMBER() OVER (PARTITION BY t0.\"teacher_id\" ORDER BY t0.\"age\" DESC) AS \"rn\""));
    }

    @Test
    public void testDatabaseExecutionResults() {
        Student s = $(Student.class);

        // 升序查询：应为 Charlie (18), Alice (20), Bob (25)
        List<Student> ascList = orm.select(Student.class)
                .orderBy(s.getAge()).asc(true)
                .list();
        assertEquals(3, ascList.size());
        assertEquals("Charlie", ascList.get(0).getStudentName());
        assertEquals(Integer.valueOf(18), ascList.get(0).getAge());
        assertEquals("Bob", ascList.get(2).getStudentName());
        assertEquals(Integer.valueOf(25), ascList.get(2).getAge());

        // 降序查询：应为 Bob (25), Alice (20), Charlie (18)
        List<Student> descList = orm.select(Student.class)
                .orderBy(s.getAge()).asc(false)
                .list();
        assertEquals(3, descList.size());
        assertEquals("Bob", descList.get(0).getStudentName());
        assertEquals(Integer.valueOf(25), descList.get(0).getAge());
        assertEquals("Charlie", descList.get(2).getStudentName());
        assertEquals(Integer.valueOf(18), descList.get(2).getAge());

        // 动态字符串参数测试
        List<Student> strDescList = orm.select(Student.class)
                .orderBy(s.getAge(), "desc")
                .list();
        assertEquals("Bob", strDescList.get(0).getStudentName());
    }
}
