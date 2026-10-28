package com.benchmark;

import com.benchmark.common.BenchmarkDbHelper;
import com.benchmark.runner.OrmBenchmarkRunner;
import com.easy.query.api.proxy.client.DefaultEasyEntityQuery;
import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.easy.query.core.api.client.EasyQueryClient;
import com.easy.query.core.bootstrapper.EasyQueryBootstrapper;
import com.easy.query.sqlite.config.SQLiteDatabaseConfiguration;
import com.javaorm.core.JavaOrm;
import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.dialect.SQLiteDialect;
import org.junit.BeforeClass;
import org.junit.Test;
import org.sqlite.SQLiteDataSource;

import java.util.List;

import static org.junit.Assert.*;

public class OrmBenchmarkTest {

    private static JavaOrm javaOrm;
    private static EasyEntityQuery easyQuery;

    @BeforeClass
    public static void setUpAll() {
        SQLiteDataSource dataSource = BenchmarkDbHelper.createDataSource();
        BenchmarkDbHelper.initializeDatabase(dataSource, 50, 500);

        OrmConfig joConfig = new OrmConfig().setPrintSql(false);
        javaOrm = JavaOrm.create(dataSource, new SQLiteDialect(), joConfig);

        EasyQueryClient eqClient = EasyQueryBootstrapper.defaultBuilderConfiguration()
                .setDefaultDataSource(dataSource)
                .optionConfigure(op -> op.setPrintSql(false))
                .useDatabaseConfigure(new SQLiteDatabaseConfiguration())
                .build();
        easyQuery = new DefaultEasyEntityQuery(eqClient);
    }

    @Test
    public void testVerifyQueryEquivalence() {
        // 1. Point query
        com.benchmark.entity.javaorm.Student s1 = javaOrm.select(com.benchmark.entity.javaorm.Student.class)
                .where().and(s -> s.getId()).eq(1L)
                .single();
        com.benchmark.entity.easyquery.Student s2 = easyQuery.queryable(com.benchmark.entity.easyquery.Student.class)
                .where(s -> s.id().eq(1L))
                .singleOrNull();

        assertNotNull(s1);
        assertNotNull(s2);
        assertEquals(s1.getId(), s2.getId());
        assertEquals(s1.getStudentName(), s2.getStudentName());
        assertEquals(s1.getAge(), s2.getAge());

        // 2. Condition list query
        List<com.benchmark.entity.javaorm.Student> joList = javaOrm.select(com.benchmark.entity.javaorm.Student.class)
                .where()
                    .and(s -> s.getAge()).ge(22)
                    .and(s -> s.getStudentName()).like("王%")
                .list();

        List<com.benchmark.entity.easyquery.Student> eqList = easyQuery.queryable(com.benchmark.entity.easyquery.Student.class)
                .where(s -> {
                    s.age().ge(22);
                    s.studentName().like("王%");
                })
                .toList();

        assertEquals("Both ORMs must return the same matching count", joList.size(), eqList.size());
        assertTrue(joList.size() > 0);

        // 3. Join navigation query
        List<com.benchmark.entity.javaorm.Student> joJoin = javaOrm.select(com.benchmark.entity.javaorm.Student.class)
                .where()
                    .and(s -> s.getTeacher().getAge()).gt(35)
                .list();

        List<com.benchmark.entity.easyquery.Student> eqJoin = easyQuery.queryable(com.benchmark.entity.easyquery.Student.class)
                .where(s -> s.teacher().age().gt(35))
                .toList();

        assertEquals("Both ORMs must return the same join count", joJoin.size(), eqJoin.size());
        assertTrue(joJoin.size() > 0);
    }

    @Test
    public void testRunBenchmarkSuite() throws Exception {
        // Run the main runner in quick mode to verify everything works and print performance metrics
        OrmBenchmarkRunner.main(new String[]{"quick"});
    }
}
