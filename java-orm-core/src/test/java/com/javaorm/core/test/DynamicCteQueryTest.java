package com.javaorm.core.test;

import com.javaorm.core.JavaOrm;
import com.javaorm.core.SQL;
import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.dialect.H2Dialect;
import com.javaorm.core.proxy.RecordingContext;
import com.javaorm.core.query.Cte;
import com.javaorm.core.test.entity.Student;
import com.javaorm.core.test.entity.Teacher;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static com.javaorm.core.SQL.count;
import static org.junit.Assert.*;

/**
 * 纯动态 CTE（作为临时表使用，零 Entity/DTO 绑定）全功能测试套件。
 */
public class DynamicCteQueryTest {

    private DataSource dataSource;
    private JavaOrm orm;

    public static class AgeCountDto {
        private Integer age;
        private Long cnt;

        public Integer getAge() { return age; }
        public void setAge(Integer age) { this.age = age; }
        public Long getCnt() { return cnt; }
        public void setCnt(Long cnt) { this.cnt = cnt; }
    }

    @Before
    public void setUp() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:dynamic_cte_test;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        this.dataSource = ds;
        this.orm = JavaOrm.create(dataSource, new H2Dialect(), new OrmConfig().setPrintSql(false));

        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS \"t_student\"");
            stmt.execute("DROP TABLE IF EXISTS \"t_teacher\"");

            stmt.execute("CREATE TABLE \"t_teacher\" (" +
                    "\"id\" BIGINT PRIMARY KEY, " +
                    "\"teacher_name\" VARCHAR(50), " +
                    "\"age\" INT)");

            stmt.execute("CREATE TABLE \"t_student\" (" +
                    "\"id\" BIGINT PRIMARY KEY, " +
                    "\"student_name\" VARCHAR(50), " +
                    "\"age\" INT, " +
                    "\"teacher_id\" BIGINT, " +
                    "\"is_deleted\" INT DEFAULT 0, " +
                    "\"create_time\" TIMESTAMP)");

            stmt.execute("INSERT INTO \"t_teacher\" (\"id\", \"teacher_name\", \"age\") VALUES (1, 'Prof. Smith', 45)");
            stmt.execute("INSERT INTO \"t_teacher\" (\"id\", \"teacher_name\", \"age\") VALUES (2, 'Dr. Johnson', 38)");

            stmt.execute("INSERT INTO \"t_student\" (\"id\", \"student_name\", \"age\", \"teacher_id\", \"is_deleted\", \"create_time\") VALUES (1, 'Alice', 20, 1, 0, NOW())");
            stmt.execute("INSERT INTO \"t_student\" (\"id\", \"student_name\", \"age\", \"teacher_id\", \"is_deleted\", \"create_time\") VALUES (2, 'Bob', 22, 1, 0, NOW())");
            stmt.execute("INSERT INTO \"t_student\" (\"id\", \"student_name\", \"age\", \"teacher_id\", \"is_deleted\", \"create_time\") VALUES (3, 'Charlie', 20, 2, 0, NOW())");
            stmt.execute("INSERT INTO \"t_student\" (\"id\", \"student_name\", \"age\", \"teacher_id\", \"is_deleted\", \"create_time\") VALUES (4, 'David', 25, 2, 0, NOW())");
            stmt.execute("INSERT INTO \"t_student\" (\"id\", \"student_name\", \"age\", \"teacher_id\", \"is_deleted\", \"create_time\") VALUES (5, 'Eve', 22, 1, 0, NOW())");
        }
    }

    @After
    public void tearDown() throws Exception {
        RecordingContext.clear();
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS \"t_student\"");
            stmt.execute("DROP TABLE IF EXISTS \"t_teacher\"");
        }
    }

    /**
     * 1. 测试从分组聚合查询中创建纯动态 CTE，并作为主表查询 listMaps()
     */
    @Test
    public void testDynamicCteFromQueryWithoutEntity() {
        Student s = SQL.$(Student.class);
        Cte<?> ageSummary = orm.select(s.getAge(), count(s.getId()).as("cnt"))
                .from(Student.class)
                .groupBy(s.getAge())
                .asDynamicCte("age_summary");

        assertTrue("Should be dynamic CTE", ageSummary.isDynamic());

        List<Map<String, Object>> results = orm.select(ageSummary).listMaps();
        assertNotNull(results);
        assertEquals(3, results.size()); // age 20, 22, 25

        for (Map<String, Object> row : results) {
            assertTrue(row.containsKey("AGE") || row.containsKey("age"));
            assertTrue(row.containsKey("CNT") || row.containsKey("cnt"));
        }
    }

    /**
     * 2. 测试在动态 CTE 临时表上使用 .col("...") 进行过滤
     */
    @Test
    public void testDynamicCteFilteringWithCol() {
        Student s = SQL.$(Student.class);
        Cte<?> ageSummary = orm.select(s.getAge(), count(s.getId()).as("cnt"))
                .from(Student.class)
                .groupBy(s.getAge())
                .asDynamicCte("age_summary");

        // 仅筛选人数等于 2 的年龄段 (age 20 -> 2人, age 22 -> 2人)
        List<Map<String, Object>> results = orm.select(ageSummary)
                .where().and(ageSummary.col("cnt")).eq(2)
                .listMaps();

        assertEquals(2, results.size());
    }

    /**
     * 3. 测试动态 CTE 的特定列投影与排序
     */
    @Test
    public void testDynamicCteOrderByAndProject() {
        Student s = SQL.$(Student.class);
        Cte<?> ageSummary = orm.select(s.getAge(), count(s.getId()).as("cnt"))
                .from(Student.class)
                .groupBy(s.getAge())
                .asDynamicCte("age_summary");

        List<Map<String, Object>> results = orm.select(ageSummary.col("age"), ageSummary.col("cnt"))
                .from(ageSummary)
                .orderBy(ageSummary.col("age")).desc()
                .listMaps();

        assertEquals(3, results.size());
        Number firstAge = (Number) (results.get(0).get("age") != null ? results.get(0).get("age") : results.get(0).get("AGE"));
        assertEquals(25, firstAge.intValue());
    }

    /**
     * 4. 测试实体表与动态 CTE 临时表进行 INNER JOIN 连接与条件关联
     */
    @Test
    public void testDynamicCteJoinWithEntityTable() {
        Student s = SQL.$(Student.class);
        Cte<?> ageSummary = orm.select(s.getAge(), count(s.getId()).as("cnt"))
                .from(Student.class)
                .groupBy(s.getAge())
                .asDynamicCte("age_summary");

        // 查询处于人数大于 1 的年龄段（age 20 和 22，共 4 位学生）
        List<Student> students = orm.select(Student.class)
                .innerJoin(ageSummary, (on, student, sum) -> on.eq(ageSummary.col("age"), student.getAge()))
                .where().and(ageSummary.col("cnt")).gt(1)
                .toList();

        assertEquals(4, students.size());
        for (Student st : students) {
            assertTrue(st.getAge() == 20 || st.getAge() == 22);
        }
    }

    /**
     * 5. 测试主表为动态 CTE，LEFT JOIN 实体表
     */
    @Test
    public void testDynamicCteAsRootJoinEntity() {
        Student s = SQL.$(Student.class);
        Cte<?> ageSummary = orm.select(s.getAge(), count(s.getId()).as("cnt"))
                .from(Student.class)
                .groupBy(s.getAge())
                .asDynamicCte("age_summary");

        List<Map<String, Object>> list = orm.select(ageSummary)
                .leftJoin(Teacher.class, (on, sum, teacher) -> on.eq(teacher.getId(), ageSummary.col("age")))
                .listMaps();

        assertEquals(3, list.size());
    }

    /**
     * 6. 测试从原生 SQL 字符串创建动态 CTE 临时表
     */
    @Test
    public void testDynamicCteFromRawSql() {
        Cte<?> rawCte = orm.cte("senior_students", "SELECT \"id\", \"student_name\", \"age\" FROM \"t_student\" WHERE \"age\" >= 22");

        List<Map<String, Object>> list = orm.select(rawCte).listMaps();
        assertEquals(3, list.size()); // Bob(22), David(25), Eve(22)

        // 进一步在外层追加过滤
        List<Map<String, Object>> filtered = orm.select(rawCte)
                .where().and(rawCte.col("student_name")).eq("David")
                .listMaps();

        assertEquals(1, filtered.size());
        Object name = filtered.get(0).get("student_name") != null ? filtered.get(0).get("student_name") : filtered.get(0).get("STUDENT_NAME");
        assertEquals("David", name);
    }

    /**
     * 7. 测试将动态 CTE 临时表直接映射为 DTO
     */
    @Test
    public void testDynamicCteMappedToDto() {
        Student s = SQL.$(Student.class);
        Cte<?> ageSummary = orm.select(s.getAge(), count(s.getId()).as("cnt"))
                .from(Student.class)
                .groupBy(s.getAge())
                .asDynamicCte("age_summary");

        List<AgeCountDto> dtos = orm.select(AgeCountDto.class).from(ageSummary).toList();
        assertNotNull(dtos);
        assertEquals(3, dtos.size());

        for (AgeCountDto dto : dtos) {
            assertNotNull(dto.getAge());
            assertNotNull(dto.getCnt());
        }
    }

    /**
     * 8. 测试动态 CTE 的 count() 计数统计
     */
    @Test
    public void testDynamicCteCount() {
        Student s = SQL.$(Student.class);
        Cte<?> ageSummary = orm.select(s.getAge(), count(s.getId()).as("cnt"))
                .from(Student.class)
                .groupBy(s.getAge())
                .asDynamicCte("age_summary");

        long count = orm.select(ageSummary).count();
        assertEquals(3L, count);

        long filteredCount = orm.select(ageSummary)
                .where().and(ageSummary.col("cnt")).gt(1)
                .count();
        assertEquals(2L, filteredCount);
    }

    /**
     * 9. 测试纯动态递归 CTE
     */
    @Test
    public void testDynamicRecursiveCte() {
        Cte<?> seqCte = orm.cteRecursive("seq", cte ->
                SQL.raw("SELECT 1 AS \"num\" UNION ALL SELECT \"num\" + 1 FROM \"seq\" WHERE \"num\" < 5")
        ).columns("num");

        List<Map<String, Object>> rows = orm.select(seqCte).listMaps();
        assertEquals(5, rows.size());
    }

    /**
     * 10. 测试两个动态 CTE 临时表在外层查询中进行连接
     */
    @Test
    public void testMultipleDynamicCtesJoined() {
        Cte<?> namesCte = orm.cte("t_names", "SELECT \"id\", \"student_name\" AS \"name\" FROM \"t_student\"");
        Cte<?> agesCte = orm.cte("t_ages", "SELECT \"id\", \"age\" * 10 AS \"score\" FROM \"t_student\"");

        List<Map<String, Object>> results = orm.select(namesCte.col("name"), agesCte.col("score"))
                .from(namesCte)
                .leftJoin(agesCte, (on, c1, c2) -> on.eq(agesCte.col("id"), namesCte.col("id")))
                .orderBy(agesCte.col("score")).asc()
                .listMaps();

        assertEquals(5, results.size());
        for (Map<String, Object> row : results) {
            assertTrue(row.containsKey("name") || row.containsKey("NAME"));
            assertTrue(row.containsKey("score") || row.containsKey("SCORE"));
        }
    }
}
