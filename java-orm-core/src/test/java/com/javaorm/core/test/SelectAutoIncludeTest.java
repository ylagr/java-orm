package com.javaorm.core.test;

import com.javaorm.core.JavaOrm;
import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.dialect.H2Dialect;
import com.javaorm.core.pagination.PageResult;
import com.javaorm.core.test.entity.School;
import com.javaorm.core.test.entity.Student;
import com.javaorm.core.test.entity.Teacher;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Before;
import org.junit.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.Assert.*;

/**
 * EasyQuery 风格 selectAutoInclude 功能专项完整测试套件。
 * <p>
 * 严谨验证：
 * 1. 强约束校验：严禁在 Entity 实体类上直接使用，必须抛出 IllegalArgumentException；
 * 2. 字段投影裁剪（Selective Projection）：只查询 DTO 声明的标量属性与关联装配所需的 Key 列；
 * 3. 关联关系装配（Auto Include）：自动检测 ManyToOne / OneToMany，杜绝笛卡尔积；
 * 4. 隐式键支持：DTO 未声明外键/主键属性时，依托底层行数据映射依然精准装配；
 * 5. 多层级递归嵌套：支持 StudentDTO -> TeacherDTO -> SchoolDTO 无限层级级联自动加载；
 * 6. 链式操作与分页终止：无缝衔接 where、firstOrNull、single、toPageResult。
 */
public class SelectAutoIncludeTest {

    private DataSource dataSource;
    private JavaOrm orm;

    // ==========================================
    // 测试用 DTO / VO 类定义（严禁使用 @JoTable）
    // ==========================================

    public static class StudentPureDTO {
        private Long id;
        private String studentName;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getStudentName() {
            return studentName;
        }

        public void setStudentName(String studentName) {
            this.studentName = studentName;
        }
    }

    public static class TeacherPureDTO {
        private Long id;
        private String teacherName;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getTeacherName() {
            return teacherName;
        }

        public void setTeacherName(String teacherName) {
            this.teacherName = teacherName;
        }
    }

    public static class SchoolPureDTO {
        private Long id;
        private String schoolName;
        private String city;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getSchoolName() {
            return schoolName;
        }

        public void setSchoolName(String schoolName) {
            this.schoolName = schoolName;
        }

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }
    }

    public static class StudentWithTeacherDTO {
        private Long id;
        private String studentName;
        private TeacherPureDTO teacher;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getStudentName() {
            return studentName;
        }

        public void setStudentName(String studentName) {
            this.studentName = studentName;
        }

        public TeacherPureDTO getTeacher() {
            return teacher;
        }

        public void setTeacher(TeacherPureDTO teacher) {
            this.teacher = teacher;
        }
    }

    /**
     * 不包含任何外键（teacherId）或主键（id）属性的极端 DTO
     */
    public static class StudentWithoutFkDTO {
        private String studentName;
        private TeacherPureDTO teacher;

        public String getStudentName() {
            return studentName;
        }

        public void setStudentName(String studentName) {
            this.studentName = studentName;
        }

        public TeacherPureDTO getTeacher() {
            return teacher;
        }

        public void setTeacher(TeacherPureDTO teacher) {
            this.teacher = teacher;
        }
    }

    public static class TeacherWithStudentsDTO {
        private Long id;
        private String teacherName;
        private List<StudentPureDTO> students;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getTeacherName() {
            return teacherName;
        }

        public void setTeacherName(String teacherName) {
            this.teacherName = teacherName;
        }

        public List<StudentPureDTO> getStudents() {
            return students;
        }

        public void setStudents(List<StudentPureDTO> students) {
            this.students = students;
        }
    }

    public static class TeacherDetailDTO {
        private Long id;
        private String teacherName;
        private SchoolPureDTO school;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getTeacherName() {
            return teacherName;
        }

        public void setTeacherName(String teacherName) {
            this.teacherName = teacherName;
        }

        public SchoolPureDTO getSchool() {
            return school;
        }

        public void setSchool(SchoolPureDTO school) {
            this.school = school;
        }
    }

    public static class StudentDetailDTO {
        private Long id;
        private String studentName;
        private TeacherDetailDTO teacher;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getStudentName() {
            return studentName;
        }

        public void setStudentName(String studentName) {
            this.studentName = studentName;
        }

        public TeacherDetailDTO getTeacher() {
            return teacher;
        }

        public void setTeacher(TeacherDetailDTO teacher) {
            this.teacher = teacher;
        }
    }

    @Before
    public void setup() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:auto_include_test;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        this.dataSource = ds;
        this.orm = JavaOrm.create(dataSource, new H2Dialect(), new OrmConfig().setPrintSql(false));

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS \"t_student\"");
            stmt.execute("DROP TABLE IF EXISTS \"t_teacher\"");
            stmt.execute("DROP TABLE IF EXISTS \"t_school\"");

            stmt.execute("CREATE TABLE \"t_school\" (\"id\" BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, \"school_name\" VARCHAR(100), \"city\" VARCHAR(100))");
            stmt.execute("CREATE TABLE \"t_teacher\" (\"id\" BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, \"teacher_name\" VARCHAR(100), \"age\" INT, \"title\" VARCHAR(50), \"school_id\" BIGINT)");
            stmt.execute("CREATE TABLE \"t_student\" (\"id\" BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, \"student_name\" VARCHAR(100), \"age\" INT, \"teacher_id\" BIGINT, \"is_deleted\" INT DEFAULT 0, \"create_time\" TIMESTAMP)");

            // 插入学校测试数据
            stmt.execute("INSERT INTO \"t_school\" (\"id\", \"school_name\", \"city\") VALUES (1, '北京大学', '北京')");
            stmt.execute("INSERT INTO \"t_school\" (\"id\", \"school_name\", \"city\") VALUES (2, '清华大学', '北京')");

            // 插入教师测试数据
            stmt.execute("INSERT INTO \"t_teacher\" (\"id\", \"teacher_name\", \"age\", \"title\", \"school_id\") VALUES (1, '张教授', 45, '教授', 1)");
            stmt.execute("INSERT INTO \"t_teacher\" (\"id\", \"teacher_name\", \"age\", \"title\", \"school_id\") VALUES (2, '李老师', 35, '讲师', 2)");

            // 插入学生测试数据
            stmt.execute("INSERT INTO \"t_student\" (\"id\", \"student_name\", \"age\", \"teacher_id\", \"is_deleted\") VALUES (1, '小明', 20, 1, 0)");
            stmt.execute("INSERT INTO \"t_student\" (\"id\", \"student_name\", \"age\", \"teacher_id\", \"is_deleted\") VALUES (2, '小红', 22, 1, 0)");
            stmt.execute("INSERT INTO \"t_student\" (\"id\", \"student_name\", \"age\", \"teacher_id\", \"is_deleted\") VALUES (3, '小华', 19, 2, 0)");
            stmt.execute("INSERT INTO \"t_student\" (\"id\", \"student_name\", \"age\", \"teacher_id\", \"is_deleted\") VALUES (4, '已毕业', 25, 1, 1)");
        }
    }

    // =========================================================================
    // 1. 强约束校验：严禁在 Entity 实体类上直接使用 selectAutoInclude
    // =========================================================================

    @Test
    public void testSelectAutoInclude_EntityClassForbidden() {
        // 传入实体类 Student.class 必须强校验失败
        try {
            orm.select(Student.class).selectAutoInclude(Student.class);
            fail("Expected IllegalArgumentException when passing Entity class to selectAutoInclude");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("selectAutoInclude only supports DTO/VO class, cannot be used on Entity class"));
            assertTrue(e.getMessage().contains(Student.class.getName()));
        }

        // 传入实体类 Teacher.class 必须强校验失败
        try {
            orm.select(Student.class).selectAutoInclude(Teacher.class);
            fail("Expected IllegalArgumentException when passing Teacher entity class to selectAutoInclude");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("selectAutoInclude only supports DTO/VO class, cannot be used on Entity class"));
            assertTrue(e.getMessage().contains(Teacher.class.getName()));
        }

        // 传入实体类 School.class 必须强校验失败
        try {
            orm.select(Teacher.class).selectAutoInclude(School.class);
            fail("Expected IllegalArgumentException when passing School entity class to selectAutoInclude");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("selectAutoInclude only supports DTO/VO class, cannot be used on Entity class"));
        }

        // 传入 null 必须失败
        try {
            orm.select(Student.class).selectAutoInclude(null);
            fail("Expected IllegalArgumentException when passing null to selectAutoInclude");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("dtoClass must not be null"));
        }
    }

    @Test
    public void testSelectAutoInclude_WhereBuilder_EntityClassForbidden() {
        // 在 WhereBuilder 链式调用后调用 selectAutoInclude(Entity) 同样严禁
        try {
            orm.select(Student.class)
                    .where().gt(Student::getAge, 18)
                    .selectAutoInclude(Student.class);
            fail("Expected IllegalArgumentException when passing Entity class to WhereBuilder.selectAutoInclude");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("selectAutoInclude only supports DTO/VO class, cannot be used on Entity class"));
        }
    }

    // =========================================================================
    // 2. 字段投影裁剪测试（Selective Projection）
    // =========================================================================

    @Test
    public void testSelectiveProjection_ScalarOnly() {
        // StudentPureDTO 只有 id 和 studentName
        String sql = orm.select(Student.class)
                .selectAutoInclude(StudentPureDTO.class)
                .toSql();

        // 投影列表中必须只包含 id 和 student_name，不包含未声明的 age, create_time 等
        assertTrue("SQL should project id", sql.contains("\"id\"") || sql.contains("id"));
        assertTrue("SQL should project student_name", sql.contains("\"student_name\"") || sql.contains("student_name"));
        assertFalse("SQL should not project unneeded age column", sql.toLowerCase().contains("age,") || sql.toLowerCase().contains(", age"));
        assertFalse("SQL should not project unneeded create_time", sql.contains("create_time"));
    }

    @Test
    public void testSelectiveProjection_ManyToOneKeyInclusion() {
        // StudentWithTeacherDTO 包含 id, studentName, TeacherPureDTO teacher
        String sql = orm.select(Student.class)
                .selectAutoInclude(StudentWithTeacherDTO.class)
                .toSql();

        // SQL 必须自动带上关联所需的外键 teacher_id，并且绝不能在主 SELECT 中查询不存在的 teacher 列
        assertTrue("SQL should project teacher_id for relation hydration", sql.contains("teacher_id"));
        assertFalse("SQL should NOT project non-existent column teacher", sql.toLowerCase().contains("teacher,") || sql.toLowerCase().contains(".teacher "));
    }

    // =========================================================================
    // 3. ManyToOne 关联自动批量回填装配测试
    // =========================================================================

    @Test
    public void testManyToOneAutoHydration() {
        List<StudentWithTeacherDTO> list = orm.select(Student.class)
                .where().gt(Student::getAge, 18)
                .orderBy(Student::getId).asc()
                .selectAutoInclude(StudentWithTeacherDTO.class)
                .list();

        assertNotNull(list);
        assertEquals(3, list.size());

        // 小明关联张教授
        StudentWithTeacherDTO s1 = list.get(0);
        assertEquals("小明", s1.getStudentName());
        assertNotNull("Teacher must be auto-included", s1.getTeacher());
        assertEquals("张教授", s1.getTeacher().getTeacherName());
        assertEquals(Long.valueOf(1L), s1.getTeacher().getId());

        // 小红关联张教授
        StudentWithTeacherDTO s2 = list.get(1);
        assertEquals("小红", s2.getStudentName());
        assertNotNull(s2.getTeacher());
        assertEquals("张教授", s2.getTeacher().getTeacherName());

        // 小华关联李老师
        StudentWithTeacherDTO s3 = list.get(2);
        assertEquals("小华", s3.getStudentName());
        assertNotNull(s3.getTeacher());
        assertEquals("李老师", s3.getTeacher().getTeacherName());
        assertEquals(Long.valueOf(2L), s3.getTeacher().getId());
    }

    @Test
    public void testManyToOneWithoutFkInDto() {
        // StudentWithoutFkDTO 根本没有 teacherId 属性，也没有 id 属性
        List<StudentWithoutFkDTO> list = orm.select(Student.class)
                .where().gt(Student::getAge, 18)
                .orderBy(Student::getId).asc()
                .selectAutoInclude(StudentWithoutFkDTO.class)
                .list();

        assertNotNull(list);
        assertEquals(3, list.size());

        StudentWithoutFkDTO s1 = list.get(0);
        assertEquals("小明", s1.getStudentName());
        assertNotNull("Teacher must be hydrated even when DTO has no teacherId field", s1.getTeacher());
        assertEquals("张教授", s1.getTeacher().getTeacherName());
    }

    // =========================================================================
    // 4. OneToMany 关联集合自动批量装配测试
    // =========================================================================

    @Test
    public void testOneToManyAutoHydration() {
        List<TeacherWithStudentsDTO> teachers = orm.select(Teacher.class)
                .orderBy(Teacher::getId).asc()
                .selectAutoInclude(TeacherWithStudentsDTO.class)
                .list();

        assertNotNull(teachers);
        assertEquals(2, teachers.size());

        // 张教授：关联小明和小红（逻辑删除的已毕业不包含）
        TeacherWithStudentsDTO t1 = teachers.get(0);
        assertEquals("张教授", t1.getTeacherName());
        assertNotNull(t1.getStudents());
        assertEquals(2, t1.getStudents().size());
        assertEquals("小明", t1.getStudents().get(0).getStudentName());
        assertEquals("小红", t1.getStudents().get(1).getStudentName());

        // 李老师：关联小华
        TeacherWithStudentsDTO t2 = teachers.get(1);
        assertEquals("李老师", t2.getTeacherName());
        assertNotNull(t2.getStudents());
        assertEquals(1, t2.getStudents().size());
        assertEquals("小华", t2.getStudents().get(0).getStudentName());
    }

    // =========================================================================
    // 5. 多层级递归嵌套关联测试 (Student -> Teacher -> School)
    // =========================================================================

    @Test
    public void testMultiLevelNestedRecursiveAutoHydration() {
        List<StudentDetailDTO> list = orm.select(Student.class)
                .where().gt(Student::getAge, 18)
                .orderBy(Student::getId).asc()
                .selectAutoInclude(StudentDetailDTO.class)
                .list();

        assertNotNull(list);
        assertEquals(3, list.size());

        // 验证第 1 层：Student
        StudentDetailDTO s1 = list.get(0);
        assertEquals("小明", s1.getStudentName());

        // 验证第 2 层：Teacher
        TeacherDetailDTO teacher = s1.getTeacher();
        assertNotNull("Teacher level 1 hydration must succeed", teacher);
        assertEquals("张教授", teacher.getTeacherName());

        // 验证第 3 层：School (递归 selectAutoInclude 自动装配)
        SchoolPureDTO school = teacher.getSchool();
        assertNotNull("School level 2 recursive hydration must succeed", school);
        assertEquals("北京大学", school.getSchoolName());
        assertEquals("北京", school.getCity());

        // 验证李老师的学校（清华大学）
        StudentDetailDTO s3 = list.get(2);
        assertNotNull(s3.getTeacher());
        assertNotNull(s3.getTeacher().getSchool());
        assertEquals("清华大学", s3.getTeacher().getSchool().getSchoolName());
    }

    // =========================================================================
    // 6. 链式终结操作与分页测试 (firstOrNull, single, toPageResult)
    // =========================================================================

    @Test
    public void testFirstOrNullAndSingle() {
        StudentWithTeacherDTO student = orm.select(Student.class)
                .where().eq(Student::getId, 1L)
                .selectAutoInclude(StudentWithTeacherDTO.class)
                .firstOrNull();

        assertNotNull(student);
        assertEquals("小明", student.getStudentName());
        assertNotNull(student.getTeacher());
        assertEquals("张教授", student.getTeacher().getTeacherName());

        // single 测试
        StudentWithTeacherDTO single = orm.select(Student.class)
                .where().eq(Student::getId, 3L)
                .selectAutoInclude(StudentWithTeacherDTO.class)
                .single();

        assertNotNull(single);
        assertEquals("小华", single.getStudentName());
        assertEquals("李老师", single.getTeacher().getTeacherName());
    }

    @Test
    public void testToPageResult() {
        PageResult<StudentWithTeacherDTO> page = orm.select(Student.class)
                .where().gt(Student::getAge, 18)
                .orderBy(Student::getId).asc()
                .selectAutoInclude(StudentWithTeacherDTO.class)
                .toPageResult(1, 2);

        assertNotNull(page);
        assertEquals(3L, page.getTotal());
        assertEquals(2, page.getList().size());
        assertEquals(1L, page.getPageNum());
        assertEquals(2L, page.getPageSize());

        // 第一页第一条记录
        StudentWithTeacherDTO s1 = page.getList().get(0);
        assertEquals("小明", s1.getStudentName());
        assertNotNull(s1.getTeacher());
        assertEquals("张教授", s1.getTeacher().getTeacherName());

        // 第一页第二条记录
        StudentWithTeacherDTO s2 = page.getList().get(1);
        assertEquals("小红", s2.getStudentName());
        assertNotNull(s2.getTeacher());
        assertEquals("张教授", s2.getTeacher().getTeacherName());
    }
}
