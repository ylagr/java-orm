package com.javaorm.core.test;

import com.javaorm.core.JavaOrm;
import com.javaorm.core.aot.AotCompiler;
import com.javaorm.core.aot.JavaOrmAotRegistry;
import com.javaorm.core.dialect.SQLiteDialect;
import com.javaorm.core.proxy.PropertyPath;
import com.javaorm.core.proxy.ProxyBytecodeBuilder;
import com.javaorm.core.proxy.ProxyFactory;
import com.javaorm.core.proxy.ProxyInstanceFactory;
import com.javaorm.core.proxy.RecordingContext;
import com.javaorm.core.test.entity.Student;
import com.javaorm.core.test.entity.Teacher;
import org.junit.Before;
import org.junit.Test;
import org.sqlite.SQLiteDataSource;

import java.io.File;

import static org.junit.Assert.*;

public class AotAndFastProxyTest {

    private JavaOrm orm;

    @Before
    public void setUp() {
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite::memory:");
        orm = JavaOrm.create(ds, new SQLiteDialect());
    }

    @Test
    public void testFastProxyZeroReflectionInvocation() {
        // 1. 获取单例代理
        Student s = ProxyFactory.getProxy(Student.class);
        assertNotNull(s);
        assertTrue("生成的代理类必须实现 ProxyInstanceFactory 接口", s instanceof ProxyInstanceFactory);

        // 2. 接口分发调用 createNew()，生成带别名的新代理对象（0 反射，且同一别名全局单例复用）
        Student aliasStudent = ProxyFactory.createProxy(Student.class, "s_alias");
        assertNotNull(aliasStudent);
        assertNotSame("带别名的代理必须与默认单例不同", s, aliasStudent);
        Student aliasStudent2 = ProxyFactory.createProxy(Student.class, "s_alias");
        assertSame("同一别名全局单例复用，0 GC 分配", aliasStudent, aliasStudent2);

        // 3. 测试常规叶子属性录制
        RecordingContext.start(Student.class);
        s.getAge();
        PropertyPath path1 = RecordingContext.finish();
        assertNotNull(path1);
        assertEquals("age", path1.getTerminalProperty());
        assertFalse(path1.hasRelations());

        // 4. 测试多级关联属性录制 (student -> teacher -> age)
        RecordingContext.start(Student.class);
        s.getTeacher().getAge();
        PropertyPath path2 = RecordingContext.finish();
        assertNotNull(path2);
        assertTrue(path2.hasRelations());
        assertEquals("teacher", path2.getRelationSteps().get(0));
        assertEquals("age", path2.getTerminalProperty());

        // 5. 测试别名代理录制
        RecordingContext.start(Student.class);
        aliasStudent.getStudentName();
        PropertyPath path3 = RecordingContext.finish();
        assertNotNull(path3);
        assertEquals("s_alias", path3.getExplicitAlias());
        assertEquals("studentName", path3.getTerminalProperty());
    }

    @Test
    public void testAotCompilerAndRegistry() throws Exception {
        // 1. 获取测试类的输出目录 (target/test-classes)
        File testClassesDir = new File("target/test-classes");
        if (!testClassesDir.exists()) {
            testClassesDir = new File("java-orm-core/target/test-classes");
        }

        // 2. 运行 AOT 预编译固化命令
        AotCompiler.main(new String[]{testClassesDir.getAbsolutePath()});

        // 3. 校验生成的 Student$JavaOrmFastRecorder.class 物理文件确实存在于磁盘
        String expectedProxyName = Student.class.getName().replace('.', '/') + ProxyBytecodeBuilder.PROXY_CLASS_SUFFIX + ".class";
        File generatedClassFile = new File(testClassesDir, expectedProxyName);
        assertTrue("AOT 编译必须在磁盘上固化生成 .class 文件: " + generatedClassFile.getAbsolutePath(),
                generatedClassFile.exists());

        // 4. 测试 AOT 预热注册中心
        JavaOrmAotRegistry.initialize();

        // 5. 验证从磁盘加载的 AOT 代理类依然具备完备的查询能力
        String sql = orm.select(Student.class)
                .where().and(s -> s.getAge()).gt(18)
                .toSql();
        assertTrue(sql.contains("\"age\" > ?"));
    }
}
