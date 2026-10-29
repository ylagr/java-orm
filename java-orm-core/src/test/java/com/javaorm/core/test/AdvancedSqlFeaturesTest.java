package com.javaorm.core.test;

import com.javaorm.core.JavaOrm;
import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.dialect.SQLiteDialect;
import com.javaorm.core.test.dto.StatDto;
import com.javaorm.core.test.entity.Student;
import com.javaorm.core.test.entity.Teacher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sqlite.SQLiteDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.javaorm.core.SQL.*;
import static org.junit.Assert.*;

public class AdvancedSqlFeaturesTest {

    private JavaOrm orm;
    private SQLiteDataSource dataSource;
    private static final String DB_FILE = "target/test_advanced_sql.db";

    @Before
    public void setUp() throws Exception {
        com.javaorm.core.proxy.RecordingContext.clear();
        new File("target").mkdirs();
        new File(DB_FILE).delete();

        dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + DB_FILE);

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE t_teacher (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "teacher_name TEXT, " +
                    "age INTEGER, " +
                    "title TEXT, " +
                    "school_id INTEGER);");

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

        // 插入基础数据
        Teacher t1 = new Teacher();
        t1.setTeacherName("张教授");
        t1.setAge(45);
        t1.setTitle("教授");
        orm.insert(t1); // id = 1

        Teacher t2 = new Teacher();
        t2.setTeacherName("李讲师");
        t2.setAge(28);
        t2.setTitle("讲师");
        orm.insert(t2); // id = 2

        Teacher t3 = new Teacher();
        t3.setTeacherName("王副教授");
        t3.setAge(38);
        t3.setTitle("副教授");
        orm.insert(t3); // id = 3

        // 学生 1: 属于 t1, age 20
        Student s1 = new Student();
        s1.setStudentName("学生A");
        s1.setAge(20);
        s1.setTeacherId(t1.getId());
        s1.setIsDeleted(0);
        s1.setCreateTime(LocalDateTime.now());
        orm.insert(s1);

        // 学生 2: 属于 t1, age 22
        Student s2 = new Student();
        s2.setStudentName("学生B");
        s2.setAge(22);
        s2.setTeacherId(t1.getId());
        s2.setIsDeleted(0);
        s2.setCreateTime(LocalDateTime.now());
        orm.insert(s2);

        // 学生 3: 属于 t1, age 24
        Student s3 = new Student();
        s3.setStudentName("学生C");
        s3.setAge(24);
        s3.setTeacherId(t1.getId());
        s3.setIsDeleted(0);
        s3.setCreateTime(LocalDateTime.now());
        orm.insert(s3);

        // 学生 4: 属于 t2, age 18
        Student s4 = new Student();
        s4.setStudentName("学生D");
        s4.setAge(18);
        s4.setTeacherId(t2.getId());
        s4.setIsDeleted(0);
        s4.setCreateTime(LocalDateTime.now());
        orm.insert(s4);
    }

    @After
    public void tearDown() {
        com.javaorm.core.proxy.RecordingContext.clear();
        new File(DB_FILE).delete();
    }

    /**
     * 1. 测试自定义投影与 GroupBy / Having
     * orm.select(count(s.getId()), s.getTeacherId()).from(Student.class).groupBy(...).having(...)
     */
    @Test
    public void testCustomProjectionAndGroupByHaving() {
        Student s = orm.proxy(Student.class);

        List<Map<String, Object>> list = orm.select(count(s.getId()).as("total"), s.getTeacherId())
                .from(Student.class)
                .groupBy(s.getTeacherId())
                .having()
                    .and(count(s.getId())).ge(2)
                .orderBy(s.getTeacherId()).asc()
                .listMaps();

        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals(3L, ((Number) list.get(0).get("total")).longValue());
        assertEquals(1L, ((Number) list.get(0).get("teacher_id")).longValue());
    }

    /**
     * 2. 测试 DTO 投影模式
     * 方式 A: orm.select(StatDto.class).from(Student.class).select(...)
     * 方式 B: orm.select(StatDto.class, count(s.getId()).as("total"), ...).from(Student.class)
     */
    @Test
    public void testDtoProjection() {
        Student s = orm.proxy(Student.class);

        // 方式 A: select(DTO).from(Entity).select(...)
        List<StatDto> dtoListA = orm.select(StatDto.class)
                .from(Student.class)
                .select(count(s.getId()).as("total"), s.getTeacherId(), avg(s.getAge()).as("avgAge"))
                .groupBy(s.getTeacherId())
                .having()
                    .and(count(s.getId())).ge(2)
                .list();

        assertEquals(1, dtoListA.size());
        StatDto dtoA = dtoListA.get(0);
        assertEquals(Long.valueOf(1), dtoA.getTeacherId());
        assertEquals(Long.valueOf(3), dtoA.getTotal());
        assertEquals(22.0, dtoA.getAvgAge(), 0.01);

        // 方式 B: select(DTO, expr1, expr2...).from(Entity)
        List<StatDto> dtoListB = orm.select(StatDto.class, count(s.getId()).as("total"), s.getTeacherId(), avg(s.getAge()).as("avgAge"))
                .from(Student.class)
                .groupBy(s.getTeacherId())
                .orderBy(s.getTeacherId()).desc()
                .list();

        assertEquals(2, dtoListB.size());
        assertEquals(Long.valueOf(2), dtoListB.get(0).getTeacherId());
        assertEquals(Long.valueOf(1), dtoListB.get(0).getTotal());
        assertEquals(Long.valueOf(1), dtoListB.get(1).getTeacherId());
        assertEquals(Long.valueOf(3), dtoListB.get(1).getTotal());
    }

    /**
     * 3. 测试 WHERE 中的函数断言
     * w.and(min(s.getAge())).gt(18) 等
     */
    @Test
    public void testSqlFunctionsInWhere() {
        Student s = orm.proxy(Student.class);

        // 查询名字长度大于 2 的学生 (LENGTH(t0.student_name) > 2)
        List<Student> students = orm.select(Student.class)
                .where()
                    .and(length(s.getStudentName())).gt(2)
                .list();

        assertEquals(4, students.size());
    }

    /**
     * 4. 测试子查询 IN (SubQuery) 与 EXISTS (SubQuery)
     */
    @Test
    public void testSubQueryAndExists() {
        Student s = orm.proxy(Student.class);
        Teacher t = orm.proxy(Teacher.class);

        // 4.1 IN 子查询：查询指导教师年龄 > 40 的学生
        List<Student> studentsIn = orm.select(Student.class)
                .where()
                    .and(s.getTeacherId()).in(
                            orm.select(t.getId())
                               .from(Teacher.class)
                               .where()
                                   .and(t.getAge()).gt(40)
                    )
                .list();

        assertEquals(3, studentsIn.size()); // 属于 t1 (张教授 45岁)

        // 4.2 EXISTS 子查询
        List<Student> studentsExists = orm.select(Student.class)
                .where()
                    .andExists(
                            orm.select(t.getId())
                               .from(Teacher.class)
                               .where()
                                   .and(t.getId()).eq(s.getTeacherId())
                                   .and(t.getTitle()).eq("讲师")
                    )
                .list();

        assertEquals(1, studentsExists.size());
        assertEquals("学生D", studentsExists.get(0).getStudentName());
    }

    /**
     * 5. 测试 UNION 与 UNION ALL
     */
    @Test
    public void testUnionAndUnionAll() {
        Student s = orm.proxy(Student.class);

        // q1: age <= 20 (学生A 20, 学生D 18)
        // q2: age >= 22 (学生B 22, 学生C 24)
        List<Map<String, Object>> unionResult = orm.select(s.getStudentName())
                .from(Student.class)
                .where()
                    .and(s.getAge()).le(20)
                .union(
                        orm.select(s.getStudentName())
                           .from(Student.class)
                           .where()
                               .and(s.getAge()).ge(22)
                )
                .listMaps();

        assertEquals(4, unionResult.size());
    }

    /**
     * 6. 测试单标量与列表标量获取 (fetchOne / fetchList)
     */
    @Test
    public void testScalarFetch() {
        Student s = orm.proxy(Student.class);

        // fetchOne: 获取最大年龄
        Integer maxAge = orm.select(max(s.getAge()))
                .from(Student.class)
                .fetchOne(Integer.class);

        assertEquals(Integer.valueOf(24), maxAge);

        // fetchOne: 获取学生总数
        Long total = orm.select(count(s.getId()))
                .from(Student.class)
                .fetchOne(Long.class);

        assertEquals(Long.valueOf(4), total);

        // fetchList: 获取所有学生名字列表
        List<String> names = orm.select(s.getStudentName())
                .from(Student.class)
                .orderBy(s.getId()).asc()
                .fetchList(String.class);

        assertEquals(4, names.size());
        assertEquals("学生A", names.get(0));
        assertEquals("学生B", names.get(1));
        assertEquals("学生C", names.get(2));
        assertEquals("学生D", names.get(3));
    }

    /**
     * 7. 测试 UPDATE 高级语法（直接代理 set、increment/decrement、andGroup (w, s)、子查询）
     */
    @Test
    public void testAdvancedUpdate() {
        Student s = orm.proxy(Student.class);
        Teacher t = orm.proxy(Teacher.class);

        // 7.1 直接代理属性赋值 + 自增计算 increment(2)
        int affected1 = orm.update(Student.class)
                .set(s.getAge(), increment(2))
                .where()
                    .and(s.getStudentName()).eq("学生A")
                .execute();
        assertEquals(1, affected1);

        Student studentA = orm.select(Student.class).where().and(s.getStudentName()).eq("学生A").single();
        assertEquals(Integer.valueOf(22), studentA.getAge()); // 20 + 2 = 22

        // 7.2 自减计算 decrement(1) + andGroup((w, stu) -> ...) 无外部 proxy
        int affected2 = orm.update(Student.class)
                .set(s.getAge(), decrement(1))
                .where()
                    .andGroup((w, stu) -> {
                        w.and(stu.getStudentName()).eq("学生B");
                        w.or(stu.getAge()).gt(100);
                    })
                .execute();
        assertEquals(1, affected2);

        Student studentB = orm.select(Student.class).where().and(s.getStudentName()).eq("学生B").single();
        assertEquals(Integer.valueOf(21), studentB.getAge()); // 22 - 1 = 21

        // 7.3 基于子查询条件的批量更新
        int affected3 = orm.update(Student.class)
                .set(s.getAge(), 99)
                .where()
                    .and(s.getTeacherId()).in(
                            orm.select(t.getId())
                               .from(Teacher.class)
                               .where()
                                   .and(t.getTitle()).eq("讲师") // t2 (李讲师) 下的学生是 学生D
                    )
                .execute();
        assertEquals(1, affected3);

        Student studentD = orm.select(Student.class).where().and(s.getStudentName()).eq("学生D").single();
        assertEquals(Integer.valueOf(99), studentD.getAge());
    }

    /**
     * 8. 测试 DELETE 高级语法（andGroup (w, s)、函数条件过滤、EXISTS 子查询）
     */
    @Test
    public void testAdvancedDelete() {
        Student s = orm.proxy(Student.class);
        Teacher t = orm.proxy(Teacher.class);

        // 8.1 andGroup((w, stu) -> ...) 逻辑删除
        int affected1 = orm.delete(Student.class)
                .where()
                    .andGroup((w, stu) -> {
                        w.and(stu.getStudentName()).eq("学生A");
                    })
                .execute();
        assertEquals(1, affected1);

        // 验证逻辑删除生效（is_deleted = 1），查不出来
        Student deletedA = orm.select(Student.class).where().and(s.getStudentName()).eq("学生A").single();
        assertNull(deletedA);

        // 8.2 基于 EXISTS 子查询删除
        int affected2 = orm.delete(Student.class)
                .where()
                    .andExists(
                            orm.select(t.getId())
                               .from(Teacher.class)
                               .where()
                                   .and(t.getId()).eq(s.getTeacherId())
                                   .and(t.getTitle()).eq("讲师")
                    )
                .execute();
        assertEquals(1, affected2);

        Student deletedD = orm.select(Student.class).where().and(s.getStudentName()).eq("学生D").single();
        assertNull(deletedD);
    }
}
