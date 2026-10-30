package com.javaorm.core.test;

import com.javaorm.core.JavaOrm;
import com.javaorm.core.SQL;
import com.javaorm.core.annotation.JoId;
import com.javaorm.core.annotation.JoTable;
import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.dialect.H2Dialect;
import com.javaorm.core.query.Cte;
import com.javaorm.core.test.entity.Student;
import com.javaorm.core.test.entity.Teacher;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Before;
import org.junit.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * SqlEnv 声明式 DSL 语法全面测试套件：
 * 验证 orm.sql(e -> { ... }) 对 SELECT, UPDATE, DELETE 的完整链式流支持。
 */
public class SqlEnvDslTest {

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
        ds.setURL("jdbc:h2:mem:sql_env_test_db;DB_CLOSE_DELAY=-1");
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

            stmt.execute("INSERT INTO \"t_teacher\" VALUES (1, 'Dr. Smith', 45)");
            stmt.execute("INSERT INTO \"t_teacher\" VALUES (2, 'Prof. Wang', 50)");

            stmt.execute("INSERT INTO \"t_student\" VALUES (1, 'Alice', 20, 1, 0, CURRENT_TIMESTAMP)");
            stmt.execute("INSERT INTO \"t_student\" VALUES (2, 'Bob', 22, 1, 0, CURRENT_TIMESTAMP)");
            stmt.execute("INSERT INTO \"t_student\" VALUES (3, 'Charlie', 20, 1, 0, CURRENT_TIMESTAMP)");
            stmt.execute("INSERT INTO \"t_student\" VALUES (4, 'David', 25, 2, 0, CURRENT_TIMESTAMP)");

            // 部门层级结构：1 (总部) -> 2 (研发部), 3 (运营部) -> 4 (后端组，属于研发部)
            stmt.execute("INSERT INTO \"t_department\" VALUES (1, NULL, '集团总部')");
            stmt.execute("INSERT INTO \"t_department\" VALUES (2, 1, '研发部')");
            stmt.execute("INSERT INTO \"t_department\" VALUES (3, 1, '运营部')");
            stmt.execute("INSERT INTO \"t_department\" VALUES (4, 2, '后端组')");
        }
    }

    /**
     * 1. 测试单表基础查询（自动推断根表）
     */
    @Test
    public void testSingleTableSelect() {
        List<Student> list = orm.sql(e -> {
            Student s = e.table(Student.class);
            e.select(s)
             .where().and(s.getAge()).ge(22)
             .orderBy(s.getId()).desc();
        }).toList();

        assertEquals(2, list.size()); // David(25), Bob(22)
        assertEquals("David", list.get(0).getStudentName());
        assertEquals("Bob", list.get(1).getStudentName());
    }

    /**
     * 2. 测试多表关联（LEFT JOIN）与字段投影查询
     */
    @Test
    public void testMultiTableJoinWithProjection() {
        List<Map<String, Object>> rows = orm.sql(e -> {
            Student s = e.table(Student.class, "s");
            Teacher t = e.table(Teacher.class, "t");

            e.select(s.getStudentName(), t.getTeacherName())
             .from(s)
             .leftJoin(t).on(s.getTeacherId(), t.getId())
             .where().and(s.getAge()).eq(25);
        }).listMaps();

        assertEquals(1, rows.size());
        Map<String, Object> row = rows.get(0);
        Object sName = row.get("student_name") != null ? row.get("student_name") : row.get("STUDENT_NAME");
        Object tName = row.get("teacher_name") != null ? row.get("teacher_name") : row.get("TEACHER_NAME");
        assertEquals("David", sName);
        assertEquals("Prof. Wang", tName);
    }

    /**
     * 3. 测试用户给出的经典自连接案例（Self Join）：
     * orm.sql(e -> {
     *     Department parent = e.table(Department.class, "d_parent");
     *     Department child  = e.table(Department.class, "d_child");
     *     e.select(parent.getName(), child.getName()).from(parent).leftJoin(child).where()...
     * })
     */
    @Test
    public void testDepartmentSelfJoin() {
        List<Map<String, Object>> rows = orm.sql(e -> {
            Department parent = e.table(Department.class, "d_parent");
            Department child  = e.table(Department.class, "d_child");

            e.select(parent.getName(), child.getName())
             .from(parent)
             .leftJoin(child).on(child.getParentId(), parent.getId())
             .where().and(parent.getName()).eq("研发部")
             .orderBy(child.getId()).asc();
        }).listMaps();

        assertEquals(1, rows.size());
        Map<String, Object> row = rows.get(0);
        assertTrue(row.containsValue("研发部"));
        assertTrue(row.containsValue("后端组"));
    }

    /**
     * 4. 测试自定义 DTO 映射查询
     */
    @Test
    public void testSelectDtoMapping() {
        List<StudentDto> dtos = orm.sql(e -> {
            Student s = e.table(Student.class);
            e.select(StudentDto.class, s.getId(), s.getStudentName(), s.getAge())
             .from(s)
             .where().and(s.getAge()).ge(22)
             .orderBy(s.getAge()).asc();
        }).toList();

        assertEquals(2, dtos.size());
        assertEquals("Bob", dtos.get(0).getStudentName());
        assertEquals("David", dtos.get(1).getStudentName());
    }

    /**
     * 5. 测试动态 CTE 临时表在 SqlEnv 中作为表参与查询
     */
    @Test
    public void testDynamicCteInSqlEnv() {
        Cte<?> rawCte = orm.cte("senior_students", "SELECT \"id\", \"student_name\" AS \"name\", \"age\" FROM \"t_student\" WHERE \"age\" >= 22");

        List<Map<String, Object>> rows = orm.sql(e -> {
            Cte<?> temp = e.table(rawCte, "sn");
            e.select(temp.col("name"))
             .from(temp)
             .where().and(temp.col("name")).eq("David");
        }).listMaps();

        assertEquals(1, rows.size());
        Object val = rows.get(0).get("name") != null ? rows.get(0).get("name") : rows.get(0).get("NAME");
        assertEquals("David", val);
    }

    /**
     * 6. 测试 UPDATE 更新操作与 .exec() 执行
     */
    @Test
    public void testUpdateOperation() {
        int updated = orm.sql(e -> {
            Student s = e.table(Student.class);
            e.update(s)
             .set(s.getAge(), 35)
             .set(s.getStudentName(), "Alice_Updated")
             .where().and(s.getId()).eq(1L);
        }).exec();

        assertEquals(1, updated);

        // 验证已更新
        Student alice = orm.select(Student.class).where().and(SQL.$(Student.class).getId()).eq(1L).firstOrNull();
        assertNotNull(alice);
        assertEquals(Integer.valueOf(35), alice.getAge());
        assertEquals("Alice_Updated", alice.getStudentName());
    }

    /**
     * 7. 测试动态条件 SET（满足条件才执行 set 赋值）
     */
    @Test
    public void testConditionalSetUpdate() {
        Integer newAge = null;
        String newName = "Charlie_Renamed";

        int updated = orm.sql(e -> {
            Student s = e.table(Student.class);
            e.update(s)
             .set(newAge != null, s.getAge(), newAge)
             .set(newName != null, s.getStudentName(), newName)
             .where().and(s.getId()).eq(3L);
        }).exec();

        assertEquals(1, updated);

        Student charlie = orm.select(Student.class).where().and(SQL.$(Student.class).getId()).eq(3L).firstOrNull();
        assertNotNull(charlie);
        assertEquals(Integer.valueOf(20), charlie.getAge()); // 原来的年龄未被改变
        assertEquals("Charlie_Renamed", charlie.getStudentName());
    }

    /**
     * 8. 测试 DELETE 删除操作与 .exec() 执行
     */
    @Test
    public void testDeleteOperation() {
        int deleted = orm.sql(e -> {
            Student s = e.table(Student.class);
            e.delete(s)
             .where().and(s.getId()).eq(4L);
        }).exec();

        assertEquals(1, deleted);

        long count = orm.select(Student.class).where().and(SQL.$(Student.class).getId()).eq(4L).count();
        assertEquals(0L, count);
    }

    /**
     * 9. 测试辅助方法：toSql(), count(), firstOrNull()
     */
    @Test
    public void testAuxiliaryMethods() {
        // toSql 验证
        String selectSql = orm.sql(e -> {
            Student s = e.table(Student.class);
            e.select(s).where().and(s.getAge()).gt(18);
        }).toSql();
        assertNotNull(selectSql);
        assertTrue(selectSql.toUpperCase().contains("SELECT"));
        assertTrue(selectSql.toUpperCase().contains("WHERE"));

        // count 验证
        long count = orm.sql(e -> {
            Student s = e.table(Student.class);
            e.select(s).where().and(s.getAge()).ge(20);
        }).count();
        assertEquals(4L, count);

        // firstOrNull 验证
        Student first = orm.sql(e -> {
            Student s = e.table(Student.class);
            e.select(s).where().and(s.getId()).eq(2L);
        }).firstOrNull();
        assertNotNull(first);
        assertEquals("Bob", first.getStudentName());
    }

    /**
     * 10. 测试防呆异常机制
     */
    @Test
    public void testSafetyExceptions() {
        // SELECT 操作误调 exec() 应抛出 IllegalStateException
        try {
            orm.sql(e -> {
                Student s = e.table(Student.class);
                e.select(s);
            }).exec();
            fail("Should fail when calling exec() on SELECT");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Cannot call exec()"));
        }

        // UPDATE 操作误调 toList() 应抛出 IllegalStateException
        try {
            orm.sql(e -> {
                Student s = e.table(Student.class);
                e.update(s).set(s.getAge(), 20).where().and(s.getId()).eq(1L);
            }).toList();
            fail("Should fail when calling toList() on UPDATE");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Cannot call toList()"));
        }
    }

    /**
     * 11. 测试 toList() 强类型推导与类型安全判断
     */
    @Test
    public void testStrongTypeJudgmentInToList() {
        // 1. orm.sql(ResultClass.class, e -> ...) 强类型编译时 + 运行时绑定
        List<StudentDto> dtoList = orm.sql(StudentDto.class, e -> {
            Student s = e.table(Student.class);
            e.select(s.getId(), s.getStudentName(), s.getAge())
             .from(s)
             .where().and(s.getAge()).ge(22)
             .orderBy(s.getAge()).asc();
        }).toList();
        assertEquals(2, dtoList.size());
        assertEquals("Bob", dtoList.get(0).getStudentName());
        assertEquals("David", dtoList.get(1).getStudentName());

        // 2. orm.sql(e -> ...).toList(TargetClass.class) 显式强类型映射
        List<StudentDto> explicitDtoList = orm.sql(e -> {
            Student s = e.table(Student.class);
            e.select(s.getId(), s.getStudentName(), s.getAge())
             .from(s)
             .where().and(s.getAge()).ge(22)
             .orderBy(s.getAge()).asc();
        }).toList(StudentDto.class);
        assertEquals(2, explicitDtoList.size());
        assertEquals("Bob", explicitDtoList.get(0).getStudentName());

        // 3. 显式映射到包含 @JoColumn/@JoId 注解的实体类
        List<Student> studentList = orm.sql(e -> {
            Student s = e.table(Student.class);
            e.select(s.getId(), s.getStudentName(), s.getAge())
             .from(s)
             .where().and(s.getAge()).ge(22)
             .orderBy(s.getAge()).asc();
        }).toList(Student.class);
        assertEquals(2, studentList.size());
        assertEquals("Bob", studentList.get(0).getStudentName());

        // 4. 单标量值强类型映射（String, Integer, Long 等）
        List<String> names = orm.sql(e -> {
            Student s = e.table(Student.class);
            e.select(s.getStudentName())
             .from(s)
             .where().and(s.getAge()).ge(22)
             .orderBy(s.getAge()).asc();
        }).toList(String.class);
        assertEquals(2, names.size());
        assertEquals("Bob", names.get(0));
        assertEquals("David", names.get(1));

        // 5. firstOrNull 标量与对象强类型映射
        String firstName = orm.sql(e -> {
            Student s = e.table(Student.class);
            e.select(s.getStudentName())
             .from(s)
             .where().and(s.getId()).eq(2L);
        }).firstOrNull(String.class);
        assertEquals("Bob", firstName);

        StudentDto firstDto = orm.sql(StudentDto.class, e -> {
            Student s = e.table(Student.class);
            e.select(s.getId(), s.getStudentName(), s.getAge())
             .from(s)
             .where().and(s.getId()).eq(2L);
        }).firstOrNull();
        assertNotNull(firstDto);
        assertEquals("Bob", firstDto.getStudentName());

        // 6. Map 类型强类型映射
        List<Map> maps = orm.sql(e -> {
            Student s = e.table(Student.class);
            e.select(s.getId(), s.getStudentName()).from(s);
        }).toList(Map.class);
        assertFalse(maps.isEmpty());
        assertNotNull(maps.get(0).get("STUDENT_NAME") != null ? maps.get(0).get("STUDENT_NAME") : maps.get(0).get("student_name"));

        // 7. 单表投影未显式传 DTO 时自动推导单表实体类型
        List<Student> autoInferred = orm.sql(e -> {
            Student s = e.table(Student.class);
            e.select(s.getStudentName(), s.getAge())
             .from(s)
             .where().and(s.getId()).eq(1L);
        }).toList();
        assertEquals(1, autoInferred.size());
        assertEquals("Alice", autoInferred.get(0).getStudentName());

        // 8. 多表无目标类型投影调用 toList() 时触发强类型防呆安全校验抛出 IllegalStateException
        try {
            orm.sql(e -> {
                Student s = e.table(Student.class, "s");
                Teacher t = e.table(Teacher.class, "t");
                e.select(s.getStudentName(), t.getTeacherName())
                 .from(s)
                 .leftJoin(t).on(s.getTeacherId(), t.getId());
            }).toList();
            fail("Multi-table projection without target class should throw IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("multi-table or untyped projection query"));
        }
    }

    /**
     * 12. 测试 orm.sql(e -> ... return entity.class).toList() 语法
     */
    @Test
    public void testReturnEntityClassSyntax() {
        // 1. 全实体查询：return Student.class
        List<Student> fullStudents = orm.sql(e -> {
            Student s = e.table(Student.class);
            e.select(s)
             .where().and(s.getAge()).ge(22)
             .orderBy(s.getId()).desc();
            return Student.class;
        }).toList();

        assertEquals(2, fullStudents.size());
        assertEquals("David", fullStudents.get(0).getStudentName());
        assertEquals("Bob", fullStudents.get(1).getStudentName());

        // 2. 自定义字段投影查询并映射至实体：return Student.class
        List<Student> projectedStudents = orm.sql(e -> {
            Student s = e.table(Student.class);
            e.select(s.getStudentName(), s.getAge())
             .from(s)
             .where().and(s.getAge()).ge(22)
             .orderBy(s.getAge()).asc();
            return Student.class;
        }).toList();

        assertEquals(2, projectedStudents.size());
        assertEquals("Bob", projectedStudents.get(0).getStudentName());
        assertEquals(Integer.valueOf(22), projectedStudents.get(0).getAge());
        assertEquals("David", projectedStudents.get(1).getStudentName());
        assertEquals(Integer.valueOf(25), projectedStudents.get(1).getAge());

        // 3. 多表关联投影并映射至 DTO：return StudentDto.class
        List<StudentDto> dtos = orm.sql(e -> {
            Student s = e.table(Student.class, "s");
            Teacher t = e.table(Teacher.class, "t");
            e.select(s.getId(), s.getStudentName(), s.getAge())
             .from(s)
             .leftJoin(t).on(s.getTeacherId(), t.getId())
             .where().and(s.getAge()).ge(22)
             .orderBy(s.getAge()).asc();
            return StudentDto.class;
        }).toList();

        assertEquals(2, dtos.size());
        assertEquals("Bob", dtos.get(0).getStudentName());
        assertEquals("David", dtos.get(1).getStudentName());

        // 4. 单标量值查询：return String.class
        List<String> names = orm.sql(e -> {
            Student s = e.table(Student.class);
            e.select(s.getStudentName())
             .from(s)
             .where().and(s.getAge()).ge(22)
             .orderBy(s.getAge()).asc();
            return String.class;
        }).toList();

        assertEquals(2, names.size());
        assertEquals("Bob", names.get(0));
        assertEquals("David", names.get(1));

        // 5. firstOrNull 配合 return 实体类
        Student firstStudent = orm.sql(e -> {
            Student s = e.table(Student.class);
            e.select(s)
             .where().and(s.getId()).eq(2L);
            return Student.class;
        }).firstOrNull();

        assertNotNull(firstStudent);
        assertEquals("Bob", firstStudent.getStudentName());

        // 6. firstOrNull 配合 return 标量类型
        String firstName = orm.sql(e -> {
            Student s = e.table(Student.class);
            e.select(s.getStudentName())
             .from(s)
             .where().and(s.getId()).eq(2L);
            return String.class;
        }).firstOrNull();

        assertEquals("Bob", firstName);
    }

    /**
     * 13. 测试 return e.select(s).where().and(...).orderBy(...).desc() 管道直接返回语法
     */
    @Test
    public void testReturnQueryPipelineDirectly() {
        // 1. 用户要求语法：return e.select(s).where().and(s.getAge()).ge(22).orderBy(s.getId()).desc();
        List<Student> list = orm.sql(e -> {
            Student s = e.table(Student.class);
            return e.select(s)
                    .where().and(s.getAge()).ge(22)
                    .orderBy(s.getId()).desc();
        }).toList();

        assertEquals(2, list.size());
        assertEquals("David", list.get(0).getStudentName());
        assertEquals("Bob", list.get(1).getStudentName());

        // 2. return e.select(s).where()... 配合 firstOrNull()
        Student first = orm.sql(e -> {
            Student s = e.table(Student.class);
            return e.select(s)
                    .where().and(s.getId()).eq(2L);
        }).firstOrNull();

        assertNotNull(first);
        assertEquals("Bob", first.getStudentName());

        // 3. return e.select(StudentDto.class, ...).from(s).where()...
        List<StudentDto> dtos = orm.sql(e -> {
            Student s = e.table(Student.class);
            return e.select(StudentDto.class, s.getId(), s.getStudentName(), s.getAge())
                    .from(s)
                    .where().and(s.getAge()).ge(22)
                    .orderBy(s.getAge()).asc();
        }).toList();

        assertEquals(2, dtos.size());
        assertEquals("Bob", dtos.get(0).getStudentName());
        assertEquals("David", dtos.get(1).getStudentName());

        // 4. return e.update(s).set(...).where()... 配合 .exec()
        int updated = orm.sql(e -> {
            Student s = e.table(Student.class);
            return e.update(s)
                    .set(s.getAge(), 26)
                    .where().and(s.getId()).eq(4L); // David
        }).exec();

        assertEquals(1, updated);

        // 还原数据
        orm.sql(e -> {
            Student s = e.table(Student.class);
            return e.update(s)
                    .set(s.getAge(), 25)
                    .where().and(s.getId()).eq(4L);
        }).exec();
    }
}
