package com.javaorm.core.test;

import com.javaorm.core.JavaOrm;
import com.javaorm.core.SQL;
import com.javaorm.core.annotation.JoId;
import com.javaorm.core.annotation.JoTable;
import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.dialect.*;
import com.javaorm.core.proxy.RecordingContext;
import com.javaorm.core.query.Cte;
import com.javaorm.core.query.Query;
import com.javaorm.core.test.entity.Student;
import com.javaorm.core.test.entity.Teacher;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.Assert.*;

/**
 * 强类型 CTE（Common Table Expression, 通用表表达式）特性全面测试套件。
 */
public class CteStrongTypedQueryTest {

    private DataSource dataSource;
    private JavaOrm orm;

    @JoTable(name = "t_department")
    public static class Department {
        @JoId
        private Long id;
        private Long parentId;
        private String name;

        public Department() {}
        public Department(Long id, Long parentId, String name) {
            this.id = id;
            this.parentId = parentId;
            this.name = name;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getParentId() { return parentId; }
        public void setParentId(Long parentId) { this.parentId = parentId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    public static class StudentDto {
        private Long id;
        private String studentName;
        private Integer age;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getStudentName() { return studentName; }
        public void setStudentName(String studentName) { this.studentName = studentName; }
        public Integer getAge() { return age; }
        public void setAge(Integer age) { this.age = age; }
    }

    @Before
    public void setup() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:cte_test_db;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        this.dataSource = ds;
        this.orm = JavaOrm.create(dataSource, new H2Dialect(), new OrmConfig().setPrintSql(false));

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS \"t_student\"");
            stmt.execute("DROP TABLE IF EXISTS \"t_teacher\"");
            stmt.execute("DROP TABLE IF EXISTS \"t_department\"");
            stmt.execute("CREATE TABLE \"t_teacher\" (\"id\" BIGINT PRIMARY KEY, \"teacher_name\" VARCHAR(100), \"age\" INT)");
            stmt.execute("CREATE TABLE \"t_student\" (\"id\" BIGINT PRIMARY KEY, \"student_name\" VARCHAR(100), \"age\" INT, \"teacher_id\" BIGINT, \"is_deleted\" INT DEFAULT 0, \"create_time\" TIMESTAMP)");
            stmt.execute("CREATE TABLE \"t_department\" (\"id\" BIGINT PRIMARY KEY, \"parent_id\" BIGINT, \"name\" VARCHAR(100))");

            // 初始化老师数据
            stmt.execute("INSERT INTO \"t_teacher\" (\"id\", \"teacher_name\", \"age\") VALUES (1, 'Prof. Smith', 45)");
            stmt.execute("INSERT INTO \"t_teacher\" (\"id\", \"teacher_name\", \"age\") VALUES (2, 'Dr. Johnson', 38)");

            // 初始化学生数据
            stmt.execute("INSERT INTO \"t_student\" (\"id\", \"student_name\", \"age\", \"teacher_id\", \"is_deleted\") VALUES (1, 'Alice', 20, 1, 0)");
            stmt.execute("INSERT INTO \"t_student\" (\"id\", \"student_name\", \"age\", \"teacher_id\", \"is_deleted\") VALUES (2, 'Bob', 22, 1, 0)");
            stmt.execute("INSERT INTO \"t_student\" (\"id\", \"student_name\", \"age\", \"teacher_id\", \"is_deleted\") VALUES (3, 'Charlie', 22, 1, 0)");
            stmt.execute("INSERT INTO \"t_student\" (\"id\", \"student_name\", \"age\", \"teacher_id\", \"is_deleted\") VALUES (4, 'David', 25, 2, 0)");
            stmt.execute("INSERT INTO \"t_student\" (\"id\", \"student_name\", \"age\", \"teacher_id\", \"is_deleted\") VALUES (5, 'Eve', 18, 2, 0)");

            // 初始化层级部门数据
            stmt.execute("INSERT INTO \"t_department\" (\"id\", \"parent_id\", \"name\") VALUES (1, NULL, 'Headquarters')");
            stmt.execute("INSERT INTO \"t_department\" (\"id\", \"parent_id\", \"name\") VALUES (2, 1, 'Engineering')");
            stmt.execute("INSERT INTO \"t_department\" (\"id\", \"parent_id\", \"name\") VALUES (3, 1, 'Marketing')");
            stmt.execute("INSERT INTO \"t_department\" (\"id\", \"parent_id\", \"name\") VALUES (4, 2, 'Core Platform')");
        }
    }

    @After
    public void cleanup() {
        RecordingContext.clear();
    }

    /**
     * 1. 基础强类型 CTE 定义与查询测试：
     * 定义 CTE "adult_students" 查询 age > 20 的学生，外层主查询过滤 age < 25。
     */
    @Test
    public void testBasicCteSelect() {
        Student s = orm.proxy(Student.class);

        // 1. 定义 CTE
        Cte<Student> adultCte = orm.cte("adult_students",
                orm.select(Student.class).where().and(s.getAge()).gt(20));

        // 2. 强类型查询 CTE
        Student cteProxy = adultCte.proxy();
        Query<Student> mainQuery = orm.select(adultCte);
        mainQuery.where().and(cteProxy.getAge()).lt(25);

        String sql = mainQuery.toSql();
        assertTrue("SQL should start with WITH \"adult_students\" AS",
                sql.startsWith("WITH \"adult_students\" AS (SELECT t0.* FROM \"t_student\" t0 WHERE t0.\"is_deleted\" = ? AND (t0.\"age\" > ?))"));
        assertTrue("SQL should select from CTE",
                sql.contains("SELECT t0.* FROM \"adult_students\" t0 WHERE t0.\"age\" < ?"));

        List<Student> list = mainQuery.toList();
        assertNotNull(list);
        // Bob(22) 与 Charlie(22)
        assertEquals(2, list.size());
        for (Student item : list) {
            assertEquals(Integer.valueOf(22), item.getAge());
        }
    }

    /**
     * 2. 通过 Query 对象的 asCte(...) 快捷方法定义 CTE 并执行查询
     */
    @Test
    public void testQueryAsCteFluent() {
        Student s = orm.proxy(Student.class);

        Cte<Student> cte = orm.select(Student.class)
                .where().and(s.getAge()).ge(22)
                .asCte("cte_ge22");

        List<Student> list = orm.select(cte).toList();
        // Bob(22), Charlie(22), David(25)
        assertEquals(3, list.size());
    }

    /**
     * 3. CTE 绑定 DTO 投影查询测试
     */
    @Test
    public void testCteWithDtoProjection() {
        Student s = orm.proxy(Student.class);

        Cte<StudentDto> cte = orm.select(StudentDto.class, s.getId(), s.getStudentName(), s.getAge())
                .from(Student.class)
                .where(w -> w.and(s.getAge()).ge(20))
                .asCte("dto_cte");

        StudentDto dtoProxy = cte.proxy();
        Query<StudentDto> q = orm.select(cte);
        q.where().and(dtoProxy.getAge()).lt(25);
        List<StudentDto> result = q.toList();

        assertEquals(3, result.size()); // Alice(20), Bob(22), Charlie(22)
        assertEquals("Alice", result.get(0).getStudentName());
        assertEquals(Integer.valueOf(20), result.get(0).getAge());
    }

    /**
     * 4. 多 CTE 链式依赖测试（cte2 依赖 cte1，外部主查询查询 cte2）
     */
    @Test
    public void testMultipleDependentCtes() {
        Student s = orm.proxy(Student.class);

        // cte1: age >= 20
        Cte<Student> cte1 = orm.cte("c1", orm.select(Student.class).where().and(s.getAge()).ge(20));

        // cte2: 基于 cte1 筛选 age <= 22
        Student s1 = cte1.proxy();
        Cte<Student> cte2 = orm.cte("c2", orm.select(cte1).where().and(s1.getAge()).le(22));

        // 主查询查询 cte2
        Query<Student> mainQuery = orm.select(cte2);
        String sql = mainQuery.toSql();

        // 验证拓扑依赖顺序：c1 在前，c2 在后，且 c2 内部不重复声明 WITH
        assertTrue(sql.contains("WITH \"c1\" AS (SELECT t0.* FROM \"t_student\" t0 WHERE t0.\"is_deleted\" = ? AND (t0.\"age\" >= ?)), \"c2\" AS (SELECT t0.* FROM \"c1\" t0 WHERE t0.\"age\" <= ?) SELECT t0.* FROM \"c2\" t0"));

        List<Student> list = mainQuery.toList();
        // Alice(20), Bob(22), Charlie(22)
        assertEquals(3, list.size());
    }

    /**
     * 5. CTE 与物理实体表强类型关联（LEFT JOIN）测试
     */
    @Test
    public void testCteJoinWithRegularTable() {
        Student s = orm.proxy(Student.class);
        Cte<Student> adultCte = orm.cte("adults",
                orm.select(Student.class).where().and(s.getAge()).ge(22));

        // 主查询查 Teacher，LEFT JOIN adultCte
        Query<Teacher> query = orm.select(Teacher.class)
                .with(adultCte)
                .leftJoin(adultCte, (on, teacher, student) -> on.eq(student.getTeacherId(), teacher.getId()));

        String sql = query.toSql();
        assertTrue("SQL should have WITH clause", sql.startsWith("WITH \"adults\" AS"));
        assertTrue("SQL should join adults CTE", sql.contains("LEFT JOIN \"adults\" t1 ON t1.\"teacher_id\" = t0.\"id\""));

        List<Teacher> list = query.toList();
        assertNotNull(list);
        assertEquals(3, list.size());
    }

    /**
     * 6. 使用 JoinBuilder 流式 .on(...) 语法糖进行 CTE 连接
     */
    @Test
    public void testJoinBuilderFluentOn() {
        Student s = orm.proxy(Student.class);
        Cte<Student> adultCte = orm.cte("adults",
                orm.select(Student.class).where().and(s.getAge()).ge(25));

        Query<Teacher> query = orm.select(Teacher.class)
                .innerJoin(adultCte).on((on, teacher, student) -> on.eq(student.getTeacherId(), teacher.getId()));

        String sql = query.toSql();
        assertTrue(sql.contains("INNER JOIN \"adults\" t1 ON t1.\"teacher_id\" = t0.\"id\""));

        List<Teacher> teachers = query.toList();
        // 只有 teacherId = 2 (Dr. Johnson) 关联了 David(25)
        assertEquals(1, teachers.size());
        assertEquals("Dr. Johnson", teachers.get(0).getTeacherName());
    }

    /**
     * 7. orm.with(...) 入口语法测试
     */
    @Test
    public void testWithStageQuery() {
        Student s = orm.proxy(Student.class);
        Cte<Student> cte = orm.cte("cte_students",
                orm.select(Student.class).where().and(s.getAge()).gt(18));

        Student cteProxy = cte.proxy();
        Query<Student> q = orm.with(cte).select(cte);
        q.where().and(cteProxy.getAge()).eq(20);
        List<Student> list = q.toList();

        assertEquals(1, list.size());
        assertEquals("Alice", list.get(0).getStudentName());
    }

    /**
     * 8. 强类型递归 CTE（WITH RECURSIVE）层级树查询测试
     */
    @Test
    public void testRecursiveCteTreeQuery() {
        Department d = orm.proxy(Department.class);

        // 使用递归函数式定义树形查询
        Cte<Department> deptTreeCte = orm.cteRecursive("dept_tree", Department.class, self -> {
            // 锚点查询：根节点 parentId 为空
            Query<Department> anchor = orm.select(Department.class);
            anchor.where().and(d.getParentId()).isNull();

            // 递归迭代查询：当前部门的 parentId 等于上一级部门的 id
            Query<Department> recursive = orm.select(Department.class)
                    .innerJoin(self, (on, cur, prev) -> on.eq(cur.getParentId(), prev.getId()));

            return anchor.unionAll(recursive);
        }).columns("id", "parent_id", "name");

        Query<Department> query = orm.select(deptTreeCte);

        // 验证各数据库方言的渲染：H2/MySQL/PG/SQLite 包含 RECURSIVE
        H2Dialect h2 = new H2Dialect();
        String h2Sql = h2.buildSelect(query.getStatement()).getSql();
        assertTrue("H2 should render WITH RECURSIVE", h2Sql.contains("WITH RECURSIVE \"dept_tree\""));

        MySQLDialect mysql = new MySQLDialect();
        String mysqlSql = mysql.buildSelect(query.getStatement()).getSql();
        assertTrue("MySQL should render WITH RECURSIVE", mysqlSql.contains("WITH RECURSIVE `dept_tree`"));

        // Oracle 不支持 RECURSIVE 关键字，必须省略 RECURSIVE
        OracleDialect oracle = new OracleDialect();
        String oracleSql = oracle.buildSelect(query.getStatement()).getSql();
        assertTrue("Oracle should render WITH without RECURSIVE", oracleSql.contains("WITH \"dept_tree\""));
        assertFalse("Oracle should NOT contain RECURSIVE", oracleSql.contains("RECURSIVE"));

        // 在 H2 真实执行递归查询验证
        List<Department> allDepts = query.toList();
        assertNotNull(allDepts);
        assertEquals(4, allDepts.size());
    }

    /**
     * 9. 显式外部投影列名支持：WITH cte (c1, c2, c3) AS (...)
     */
    @Test
    public void testCteExplicitColumnNames() {
        Student s = orm.proxy(Student.class);
        Cte<Student> cte = orm.cte("custom_cols",
                orm.select(Student.class).where().and(s.getAge()).ge(20))
                .columns("c_id", "c_name", "c_age");

        Query<Student> query = orm.select(cte);
        String sql = query.toSql();
        assertTrue("SQL should define column names in CTE",
                sql.contains("WITH \"custom_cols\" (\"c_id\", \"c_name\", \"c_age\") AS"));
    }

    /**
     * 10. CTE 统计查询 count() 测试
     */
    @Test
    public void testCteCount() {
        Student s = orm.proxy(Student.class);
        Cte<Student> adultCte = orm.cte("adults",
                orm.select(Student.class).where().and(s.getAge()).ge(22));

        long count = orm.select(adultCte).count();
        // Bob(22), Charlie(22), David(25) = 3
        assertEquals(3L, count);
    }
}
