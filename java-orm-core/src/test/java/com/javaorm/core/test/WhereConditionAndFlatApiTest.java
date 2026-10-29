package com.javaorm.core.test;

import com.javaorm.core.JavaOrm;
import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.dialect.H2Dialect;
import com.javaorm.core.dialect.SqlAndParams;
import com.javaorm.core.operator.PropertyOperator;
import com.javaorm.core.proxy.RecordingContext;
import com.javaorm.core.query.WhereBuilder;
import com.javaorm.core.test.entity.Student;
import com.javaorm.core.test.entity.Teacher;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;

import static com.javaorm.core.SQL.min;
import static org.junit.Assert.*;

/**
 * 验证扁平语法、两阶段语法 condition 支持、ThreadLocal 零泄漏以及零堆分配（单例 PropertyOperator 复用）。
 */
public class WhereConditionAndFlatApiTest {

    private DataSource dataSource;
    private JavaOrm orm;

    @Before
    public void setup() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:where_test_db;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        this.dataSource = ds;
        this.orm = JavaOrm.create(dataSource, new H2Dialect(), new OrmConfig().setPrintSql(false));

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS \"t_student\"");
            stmt.execute("DROP TABLE IF EXISTS \"t_teacher\"");
            stmt.execute("CREATE TABLE \"t_teacher\" (\"id\" BIGINT PRIMARY KEY, \"teacher_name\" VARCHAR(100), \"age\" INT)");
            stmt.execute("CREATE TABLE \"t_student\" (\"id\" BIGINT PRIMARY KEY, \"student_name\" VARCHAR(100), \"age\" INT, \"teacher_id\" BIGINT, \"is_deleted\" INT DEFAULT 0, \"create_time\" TIMESTAMP)");
        }
    }

    @After
    public void cleanup() {
        RecordingContext.clear();
    }

    /**
     * 1. 测试扁平语法全形态支持：
     * - 代理类方法：w.eq(s.getAge(), 1)
     * - SQL 表达式：w.gt(min(s.getAge()), 10)
     * - 字符串列名：w.eq("age", 1)
     * - Lambda 方法引用：w.eq(Student::getStudentName, "Tom")
     */
    @Test
    public void testFlatQuerySyntaxFullForms() {
        Student s = orm.proxy(Student.class);

        SqlAndParams sp = orm.select(Student.class)
                .where()
                    .eq(s.getAge(), 20)
                    .gt(min(s.getAge()), 10)
                    .eq("age", 22)
                    .eq(Student::getStudentName, "Tom")
                .toSqlAndParams();

        String sql = sp.getSql();
        // 应该包含所有四个条件
        assertTrue("Must contain proxy condition t0.age = ?: " + sql, sql.contains("t0.\"age\" = ?"));
        assertTrue("Must contain expr condition MIN(t0.age) > ?: " + sql, sql.contains("MIN(t0.\"age\") > ?"));
        assertTrue("Must contain dynamic string condition: " + sql, sql.contains("\"age\" = ?"));
        assertTrue("Must contain lambda condition: " + sql, sql.contains("t0.\"student_name\" = ?"));
        assertEquals(5, sp.getParams().size()); // is_deleted(0) + 4 params
    }

    /**
     * 2. 测试扁平语法中的 boolean condition 能力及 ThreadLocal 消费防泄漏
     */
    @Test
    public void testFlatQueryConditionSkippingAndThreadLocalCleanup() {
        Student s = orm.proxy(Student.class);

        // condition = false 时，不应该生成任何 SQL，且 ThreadLocal 必须保持干净
        SqlAndParams sp = orm.select(Student.class)
                .where()
                    .eq(false, s.getAge(), 1)
                    .gt(false, min(s.getAge()), 10)
                    .eq(false, "age", 1)
                    .eq(false, Student::getStudentName, "Tom")
                    .eq(true, s.getAge(), 99)
                .toSqlAndParams();

        String sql = sp.getSql();
        assertFalse("Must NOT contain age = 1: " + sql, sql.contains("= 1"));
        assertFalse("Must NOT contain MIN: " + sql, sql.contains("MIN"));
        assertFalse("Must NOT contain Tom: " + sql, sql.contains("Tom"));
        assertTrue("Must contain age = 99: " + sql, sql.contains("t0.\"age\" = ?"));
        assertEquals(2, sp.getParams().size()); // is_deleted(0) + 99

        // 验证 ThreadLocal 中没有任何未消费的残留路径
        assertNull("RecordingContext must be completely clean", RecordingContext.finish());
    }

    /**
     * 3. 测试旧的两阶段 API 的 condition 扩展（and(cond, ...), eq(cond, ...), eqIf(cond, ...) 等）
     */
    @Test
    public void testOldTwoStageApiConditionSupport() {
        Student s = orm.proxy(Student.class);

        // 3.1 两阶段 API 在 eq(condition, ...) / gt(condition, ...) 上阻断
        SqlAndParams sp1 = orm.select(Student.class)
                .where()
                    .and(s.getAge()).eq(false, 18)
                    .and("studentName").eq(false, "Jack")
                    .and(Student::getAge).gt(false, 20)
                    .and(min(s.getAge())).gt(false, 10)
                    .and(s.getAge()).eq(true, 30)
                .toSqlAndParams();

        String sql1 = sp1.getSql();
        assertFalse("Must not contain age 18: " + sql1, sql1.contains("18"));
        assertFalse("Must not contain Jack: " + sql1, sql1.contains("Jack"));
        assertFalse("Must not contain gt 20: " + sql1, sql1.contains("> ?"));
        assertTrue("Must contain age = 30: " + sql1, sql1.contains("t0.\"age\" = ?"));
        assertEquals(2, sp1.getParams().size());
        assertNull("ThreadLocal must be clean after sp1", RecordingContext.finish());

        // 3.2 验证 condition 为 false 阻断，为 true 正常生效
        SqlAndParams sp2 = orm.select(Student.class)
                .where()
                    .and(s.getAge()).eq(false, 18)
                    .and(s.getAge()).eq(false, 19)
                    .and(s.getAge()).gt(false, 20)
                    .and(s.getAge()).gt(false, 21)
                    .and(s.getAge()).eq(true, 35)
                .toSqlAndParams();

        String sql2 = sp2.getSql();
        assertFalse("Must not contain 18: " + sql2, sql2.contains("18"));
        assertFalse("Must not contain 19: " + sql2, sql2.contains("19"));
        assertFalse("Must not contain 20: " + sql2, sql2.contains("20"));
        assertFalse("Must not contain 21: " + sql2, sql2.contains("21"));
        assertTrue("Must contain 35: " + sql2, sql2.contains("t0.\"age\" = ?"));
        assertEquals(2, sp2.getParams().size());
        assertNull("ThreadLocal must be clean after sp2", RecordingContext.finish());

        // 3.3 两阶段 API 字段与字段比较以及 condition 阻断
        SqlAndParams sp3 = orm.select(Student.class)
                .where()
                    .and(s.getAge()).eq(false, s.getId())
                    .and(s.getAge()).eq(true, s.getTeacherId())
                .toSqlAndParams();

        String sql3 = sp3.getSql();
        assertFalse("Must not compare with id: " + sql3, sql3.contains("t0.\"id\""));
        assertTrue("Must compare age with teacher_id: " + sql3, sql3.contains("t0.\"age\" = t0.\"teacher_id\""));
        assertNull("ThreadLocal must be clean after sp3", RecordingContext.finish());
    }

    /**
     * 4. 验证零堆分配（Zero Heap Allocation）：
     * 检验 and(s.getAge()) 返回的是 WhereBuilder 内部单例缓存的 reusableOperator，无需每次 new
     */
    @Test
    public void testZeroHeapAllocationReusableOperator() {
        Student s = orm.proxy(Student.class);
        WhereBuilder<Student> w = orm.select(Student.class).where();

        PropertyOperator<WhereBuilder<Student>, Integer> op1 = w.and(s.getAge());
        PropertyOperator<WhereBuilder<Student>, String> op2 = w.and(s.getStudentName());
        PropertyOperator<WhereBuilder<Student>, Long> op3 = w.or(s.getTeacherId());

        // 验证全部引用同一个 operator 单例实例，零对象分配
        assertSame("op1 and op2 must be the exact same instance", op1, op2);
        assertSame("op2 and op3 must be the exact same instance", op2, op3);
    }

    /**
     * 5. 测试级联导航关系字段的扁平语法（自动隐式 LEFT JOIN）：
     * w.eq(s.getTeacher().getTeacherName(), "张老师")
     */
    @Test
    public void testCascadeRelationNavigationFlatQuery() {
        Student s = orm.proxy(Student.class);

        SqlAndParams sp = orm.select(Student.class)
                .where()
                    .eq(s.getTeacher().getTeacherName(), "张老师")
                .toSqlAndParams();

        String sql = sp.getSql();
        assertTrue("Must include LEFT JOIN t_teacher: " + sql, sql.contains("LEFT JOIN \"t_teacher\""));
        assertTrue("Must compare teacher_name: " + sql, sql.contains("\"teacher_name\" = ?"));
        assertEquals("张老师", sp.getParams().get(1));
    }

    /**
     * 6. 测试所有扁平操作符及其 condition 表现：
     * eq, ne, gt, ge, lt, le, like, likeLeft, likeRight, likeAll, notLike, isNull, isNotNull, in, notIn, between
     */
    @Test
    public void testAllFlatOperators() {
        Student s = orm.proxy(Student.class);

        SqlAndParams sp = orm.select(Student.class)
                .where()
                    .ne(s.getAge(), 1)
                    .gt(s.getAge(), 2)
                    .ge(s.getAge(), 3)
                    .lt(s.getAge(), 4)
                    .le(s.getAge(), 5)
                    .like(s.getStudentName(), "A%")
                    .likeLeft(s.getStudentName(), "B")
                    .likeRight(s.getStudentName(), "C")
                    .likeAll(s.getStudentName(), "D")
                    .notLike(s.getStudentName(), "E%")
                    .isNull(s.getTeacherId())
                    .isNotNull(s.getCreateTime())
                    .in(s.getAge(), 10, 20, 30)
                    .notIn(s.getAge(), Arrays.asList(40, 50))
                    .between(s.getAge(), 15, 25)
                .toSqlAndParams();

        String sql = sp.getSql();
        assertTrue(sql.contains("t0.\"age\" <> ?"));
        assertTrue(sql.contains("t0.\"age\" > ?"));
        assertTrue(sql.contains("t0.\"age\" >= ?"));
        assertTrue(sql.contains("t0.\"age\" < ?"));
        assertTrue(sql.contains("t0.\"age\" <= ?"));
        assertTrue(sql.contains("t0.\"student_name\" LIKE ?"));
        assertTrue(sql.contains("t0.\"student_name\" NOT LIKE ?"));
        assertTrue(sql.contains("t0.\"teacher_id\" IS NULL"));
        assertTrue(sql.contains("t0.\"create_time\" IS NOT NULL"));
        assertTrue(sql.contains("t0.\"age\" IN (?, ?, ?)"));
        assertTrue(sql.contains("t0.\"age\" NOT IN (?, ?)"));
        assertTrue(sql.contains("t0.\"age\" BETWEEN ? AND ?"));
    }

    /**
     * 7. 测试扁平语法中 and() 与 or() 逻辑切换：
     * w.eq(s.getAge(), 18).or().gt(s.getAge(), 60)
     */
    @Test
    public void testAndOrLogicalSwitchingInFlatApi() {
        Student s = orm.proxy(Student.class);

        SqlAndParams sp = orm.select(Student.class)
                .where()
                    .eq(s.getAge(), 18)
                    .or().gt(s.getAge(), 60)
                .toSqlAndParams();

        String sql = sp.getSql();
        assertTrue("Must render OR between conditions: " + sql,
                sql.contains("t0.\"age\" = ? OR t0.\"age\" > ?"));
    }
}
