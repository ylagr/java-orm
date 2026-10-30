package com.javaorm.core.test;

import com.javaorm.core.JavaOrm;
import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.dialect.H2Dialect;
import com.javaorm.core.dialect.SqlAndParams;
import com.javaorm.core.proxy.RecordingContext;
import com.javaorm.core.query.SelectBuilder;
import com.javaorm.core.test.entity.Student;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Arrays;

import static com.javaorm.core.SQL.*;
import static org.junit.Assert.assertTrue;

public class WhereBuilderGenericConstraintsTest {

    private DataSource dataSource;
    private JavaOrm orm;

    @Before
    public void setup() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:test_where_generic;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        this.dataSource = ds;

        this.orm = JavaOrm.create(dataSource, new H2Dialect(), new OrmConfig().setPrintSql(false));

        try (Connection conn = dataSource.getConnection();
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
            stmt.execute("INSERT INTO \"t_student\" (\"student_name\", \"age\", \"teacher_id\", \"is_deleted\") VALUES ('Charlie', 30, 2, 0)");
        }
    }

    @After
    public void tearDown() throws Exception {
        RecordingContext.clear();
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS \"t_student\"");
        }
    }

    @Test
    public void testTypedEqualityAndComparisonConstraints() {
        Student s = orm.proxy(Student.class);

        // 1. 测试 eq/ne 的泛型约束与 cross-number 支持（例如 Long 属性与 int 字面量）
        SqlAndParams sp = orm.select(Student.class)
                .where()
                .eq(s.getId(), 1L)              // R=Long, val=Long
                .eq(s.getId(), 1)               // Long 与 int 交叉数字匹配
                .ne(s.getAge(), 18)             // R=Integer, val=Integer
                .gt(s.getAge(), 19)             // Comparable<? super Integer>
                .ge(s.getAge(), 20)
                .lt(s.getId(), 100)             // Long 与 int 交叉数字比较
                .le(s.getId(), 100L)
                .toSqlAndParams();

        String sql = sp.getSql();
        assertTrue(sql.contains("t0.\"id\" = ?"));
        assertTrue(sql.contains("t0.\"age\" <> ?"));
        assertTrue(sql.contains("t0.\"age\" > ?"));
        assertTrue(sql.contains("t0.\"age\" >= ?"));
        assertTrue(sql.contains("t0.\"id\" < ?"));
        assertTrue(sql.contains("t0.\"id\" <= ?"));
    }

    @Test
    public void testTypedRangeAndStringConstraints() {
        Student s = orm.proxy(Student.class);

        // 2. 测试 between 与 like 系列的强类型限制
        SqlAndParams sp = orm.select(Student.class)
                .where()
                .between(s.getAge(), 18, 30)                // Comparable R
                .between(s.getId(), 1, 100)                 // Number cross-type
                .like(s.getStudentName(), "A%")             // CharSequence
                .likeLeft(s.getStudentName(), "lice")       // CharSequence
                .likeRight(s.getStudentName(), "Ali")       // CharSequence
                .likeAll(s.getStudentName(), "lic")         // CharSequence
                .notLike(s.getStudentName(), "Z%")          // CharSequence
                .toSqlAndParams();

        String sql = sp.getSql();
        assertTrue(sql.contains("t0.\"age\" BETWEEN ? AND ?"));
        assertTrue(sql.contains("t0.\"id\" BETWEEN ? AND ?"));
        assertTrue(sql.contains("t0.\"student_name\" LIKE ?"));
        assertTrue(sql.contains("t0.\"student_name\" NOT LIKE ?"));
    }

    @Test
    public void testTypedInAndSubQueryConstraints() {
        Student s = orm.proxy(Student.class);

        SelectBuilder<Student> sub = orm.select(Student.class).where().eq(s.getAge(), 20).toQuery();

        SqlAndParams sp = orm.select(Student.class)
                .where()
                .in(s.getAge(), 18, 20, 22)                         // varargs R...
                .in(s.getAge(), Arrays.asList(25, 30))              // Collection<? extends R>
                .notIn(s.getStudentName(), "Tom", "Jerry")          // String varargs
                .in(s.getId(), sub)                                 // subQuery
                .andExists(sub)                                     // andExists with Query
                .toSqlAndParams();

        String sql = sp.getSql();
        assertTrue(sql.contains("t0.\"age\" IN (?, ?, ?)"));
        assertTrue(sql.contains("t0.\"age\" IN (?, ?)"));
        assertTrue(sql.contains("t0.\"student_name\" NOT IN (?, ?)"));
        assertTrue(sql.contains("EXISTS (SELECT"));
    }

    @Test
    public void testSqlExpressionTypedConstraints() {
        Student s = orm.proxy(Student.class);

        SqlAndParams sp = orm.select(Student.class)
                .where()
                .eq(upper(s.getStudentName()), "ALICE")
                .gt(count("*"), 0L)
                .between(abs(s.getAge()), 10, 50)
                .toSqlAndParams();

        String sql = sp.getSql();
        assertTrue("Must contain UPPER: " + sql, sql.contains("UPPER(t0.\"student_name\") = ?"));
        assertTrue("Must contain COUNT(*): " + sql, sql.contains("COUNT(*) > ?"));
        assertTrue("Must contain ABS: " + sql, sql.contains("ABS(t0.\"age\") BETWEEN ? AND ?"));
    }
}
