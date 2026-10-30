package com.benchmark.jmh;

import com.benchmark.common.BenchmarkDbHelper;
import com.benchmark.entity.easyquery.proxy.StudentProxy;
import com.easy.query.api.proxy.client.DefaultEasyEntityQuery;
import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.easy.query.core.api.client.EasyQueryClient;
import com.easy.query.core.bootstrapper.EasyQueryBootstrapper;
import com.easy.query.sqlite.config.SQLiteDatabaseConfiguration;
import com.javaorm.core.JavaOrm;
import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.dialect.SQLiteDialect;
import com.javaorm.core.proxy.ProxyFactory;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.sqlite.SQLiteDataSource;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * JMH 官方标准微基准测试：
 * 采用纳秒/吞吐量精细度测量 javaorm-core 与 easy-query (proxy 模式) 各核心操作。
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
public class OrmJmhBenchmark {

    private JavaOrm javaOrm;
    private EasyEntityQuery easyQuery;
    private com.benchmark.entity.javaorm.Student javaOrmProxy;
    private StudentProxy eqProxy;
    private AtomicLong idCounter;

    @Setup(Level.Trial)
    public void setup() {
        SQLiteDataSource dataSource = BenchmarkDbHelper.createDataSource();
        BenchmarkDbHelper.initializeDatabase(dataSource, 50, 500);

        OrmConfig javaOrmConfig = new OrmConfig().setPrintSql(false);
        this.javaOrm = JavaOrm.create(dataSource, new SQLiteDialect(), javaOrmConfig);

        EasyQueryClient eqClient = EasyQueryBootstrapper.defaultBuilderConfiguration()
                .setDefaultDataSource(dataSource)
                .optionConfigure(op -> op.setPrintSql(false))
                .useDatabaseConfigure(new SQLiteDatabaseConfiguration())
                .build();
        this.easyQuery = new DefaultEasyEntityQuery(eqClient);

        this.javaOrmProxy = ProxyFactory.getProxy(com.benchmark.entity.javaorm.Student.class);
        this.eqProxy = StudentProxy.TABLE.createTable();
        this.idCounter = new AtomicLong(1);
    }

    // ---------------------- 1. Proxy 属性调用 ----------------------

    @Benchmark
    public Object javaOrm_01_ProxyPropertyCall() {
        try {
            return javaOrmProxy.getAge();
        } finally {
            com.javaorm.core.proxy.RecordingContext.clearRecording();
        }
    }

    @Benchmark
    public Object easyQuery_01_ProxyPropertyCall() {
        return eqProxy.age();
    }

    // ---------------------- 2. 纯查询 AST / SQL 构建 ----------------------

    @Benchmark
    public Object javaOrm_02_QueryBuild() {
        return javaOrm.select(com.benchmark.entity.javaorm.Student.class)
                .where()
                .and(s -> s.getAge()).ge(18)
                .and(s -> s.getStudentName()).like("王%")
                .orderBy(s -> s.getAge()).desc()
                .toSql();
    }

    @Benchmark
    public Object easyQuery_02_QueryBuild() {
        return easyQuery.queryable(com.benchmark.entity.easyquery.Student.class)
                .where(s -> {
                    s.age().ge(18);
                    s.studentName().like("王%");
                })
                .orderBy(s -> s.age().desc())
                .toSQL();
    }

    // ---------------------- 3. 主键点查单条记录 ----------------------

    @Benchmark
    public Object javaOrm_03_PointQuery() {
        long id = (idCounter.getAndIncrement() % 400) + 1;
        return javaOrm.select(com.benchmark.entity.javaorm.Student.class)
                .where()
                .and(s -> s.getId()).eq(id)
                .single();
    }

    @Benchmark
    public Object easyQuery_03_PointQuery() {
        long id = (idCounter.getAndIncrement() % 400) + 1;
        return easyQuery.queryable(com.benchmark.entity.easyquery.Student.class)
                .where(s -> s.id().eq(id))
                .singleOrNull();
    }

    // ---------------------- 4. 单表批量查询 (100条装配) ----------------------

    @Benchmark
    public List<?> javaOrm_04_ListQuery() {
        return javaOrm.select(com.benchmark.entity.javaorm.Student.class)
                .where()
                .and(s -> s.getAge()).ge(22)
                .list();
    }

    @Benchmark
    public List<?> easyQuery_04_ListQuery() {
        return easyQuery.queryable(com.benchmark.entity.easyquery.Student.class)
                .where(s -> s.age().ge(22))
                .toList();
    }

    // ---------------------- 5. 多表级联导航关联查询 ----------------------

    @Benchmark
    public List<?> javaOrm_05_JoinQuery() {
        return javaOrm.select(com.benchmark.entity.javaorm.Student.class)
                .where()
                .and(s -> s.getTeacher().getAge()).gt(35)
                .list();
    }

    @Benchmark
    public List<?> easyQuery_05_JoinQuery() {
        return easyQuery.queryable(com.benchmark.entity.easyquery.Student.class)
                .where(s -> s.teacher().age().gt(35))
                .toList();
    }

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(OrmJmhBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
