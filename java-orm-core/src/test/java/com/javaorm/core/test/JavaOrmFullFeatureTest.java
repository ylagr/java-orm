package com.javaorm.core.test;

import com.javaorm.core.JavaOrm;
import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.dialect.SQLiteDialect;
import com.javaorm.core.test.entity.Student;
import com.javaorm.core.test.entity.Teacher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sqlite.SQLiteDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.Assert.*;

public class JavaOrmFullFeatureTest {

    private JavaOrm orm;
    private SQLiteDataSource dataSource;
    private static final String DB_FILE = "target/test_java_orm.db";

    @Before
    public void setUp() throws Exception {
        new File("target").mkdirs();
        new File(DB_FILE).delete();

        dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + DB_FILE);

        // 初始化测试数据表
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE t_teacher (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "teacher_name TEXT, " +
                    "age INTEGER, " +
                    "title TEXT);");

            stmt.execute("CREATE TABLE t_student (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "student_name TEXT, " +
                    "age INTEGER, " +
                    "teacher_id INTEGER, " +
                    "is_deleted INTEGER DEFAULT 0, " +
                    "create_time TEXT);");
        }

        OrmConfig config = new OrmConfig().setPrintSql(true);
        orm = JavaOrm.create(dataSource, new SQLiteDialect(), config);
    }

    @After
    public void tearDown() {
        new File(DB_FILE).delete();
    }

    @Test
    public void testFullWorkflow() {
        // 1. 测试实体插入与主键回填
        Teacher t1 = new Teacher();
        t1.setTeacherName("张教授");
        t1.setAge(45);
        t1.setTitle("教授");
        orm.insert(t1);
        assertNotNull("Teacher 1 ID should be generated", t1.getId());

        Teacher t2 = new Teacher();
        t2.setTeacherName("李讲师");
        t2.setAge(28);
        t2.setTitle("讲师");
        orm.insert(t2);
        assertNotNull("Teacher 2 ID should be generated", t2.getId());

        Student s1 = new Student();
        s1.setStudentName("王小明");
        s1.setAge(20);
        s1.setTeacherId(t1.getId());
        orm.insert(s1);
        assertNotNull("Student 1 ID should be generated", s1.getId());

        Student s2 = new Student();
        s2.setStudentName("王大锤");
        s2.setAge(17);
        s2.setTeacherId(t1.getId());
        orm.insert(s2);

        Student s3 = new Student();
        s3.setStudentName("李小华");
        s3.setAge(22);
        s3.setTeacherId(t2.getId());
        orm.insert(s3);

        // 2. 基础条件查询与排序
        List<Student> adultStudents = orm.select(Student.class)
                .where()
                    .and(s -> s.getAge()).ge(18)
                    .and(s -> s.getStudentName()).like("王%")
                .orderBy(s -> s.getAge()).desc()
                .list();

        assertEquals(1, adultStudents.size());
        assertEquals("王小明", adultStudents.get(0).getStudentName());

        // 3. 级联导航字段过滤（自动隐式 LEFT JOIN）: 查出指导老师年龄 > 35 的学生
        List<Student> studentsWithSeniorTeacher = orm.select(Student.class)
                .where()
                    .and(s -> s.getTeacher().getAge()).gt(35)
                .orderBy(s -> s.getId()).asc()
                .list();

        assertEquals(2, studentsWithSeniorTeacher.size());
        assertEquals("王小明", studentsWithSeniorTeacher.get(0).getStudentName());
        assertEquals("王大锤", studentsWithSeniorTeacher.get(1).getStudentName());

        // 4. 关联作用域子块（whereRelation）
        List<Student> professorStudents = orm.select(Student.class)
                .whereRelation(s -> s.getTeacher(), t -> t
                    .where()
                        .and(Teacher::getTitle).eq("教授")
                )
                .list();
        assertEquals(2, professorStudents.size());

        // 5. 关联对象按需装配（Include）
        // 5.1 多对一装配：查学生时填充 Teacher
        List<Student> studentsWithIncludedTeacher = orm.select(Student.class)
                .include(s -> s.getTeacher())
                .where()
                    .and(s -> s.getStudentName()).eq("李小华")
                .list();

        assertEquals(1, studentsWithIncludedTeacher.size());
        assertNotNull("Teacher should be hydrated", studentsWithIncludedTeacher.get(0).getTeacher());
        assertEquals("李讲师", studentsWithIncludedTeacher.get(0).getTeacher().getTeacherName());

        // 5.2 一对多装配：查老师时填充名下学生集合 List<Student>
        List<Teacher> teachersWithStudents = orm.select(Teacher.class)
                .include(t -> t.getStudents())
                .where()
                    .and(t -> t.getId()).eq(t1.getId())
                .list();

        assertEquals(1, teachersWithStudents.size());
        List<Student> t1Students = teachersWithStudents.get(0).getStudents();
        assertNotNull("Students list should be hydrated", t1Students);
        assertEquals(2, t1Students.size());

        // 6. 流式批量条件更新
        int updated = orm.update(Student.class)
                .set(s -> s.getAge(), 99)
                .where()
                    .and(s -> s.getStudentName()).eq("王大锤")
                .execute();
        assertEquals(1, updated);

        Student s2Updated = orm.select(Student.class)
                .where()
                    .and(s -> s.getId()).eq(s2.getId())
                .single();
        assertEquals(Integer.valueOf(99), s2Updated.getAge());

        // 7. 逻辑删除验证
        int deleted = orm.delete(s1);
        assertEquals(1, deleted);

        // 再次查询学生列表，s1 应当被全局逻辑删除自动过滤
        List<Student> remaining = orm.select(Student.class).list();
        assertEquals(2, remaining.size());
        for (Student s : remaining) {
            assertNotEquals("王小明", s.getStudentName());
        }

        // 统计总数（逻辑删除自动过滤）
        long count = orm.select(Student.class).count();
        assertEquals(2L, count);
    }
}
