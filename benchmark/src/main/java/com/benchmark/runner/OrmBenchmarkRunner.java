package com.benchmark.runner;

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
import com.javaorm.core.query.WhereBuilder;
import org.sqlite.SQLiteDataSource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 综合性能 Benchmark 运行器：
 * 比较 javaorm-core 与 easy-query (proxy 模式) 在各关键维度的性能表现。
 */
public class OrmBenchmarkRunner {

    public static void main(String[] args) throws Exception {
        boolean isQuick = args.length > 0 && "quick".equalsIgnoreCase(args[0]);
        int warmupRounds = isQuick ? 1 : 3;
        int measureRounds = isQuick ? 1 : 5;

        System.out.println("=========================================================================================");
        System.out.println("         ORM Benchmark: javaorm-core vs easy-query (Proxy Mode)" + (isQuick ? " [Quick Mode]" : ""));
        System.out.println("=========================================================================================");
        System.out.println("JVM: " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");
        System.out.println("OS: " + System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        System.out.println("Warmup Rounds: " + warmupRounds + ", Measurement Rounds: " + measureRounds);
        System.out.println("-----------------------------------------------------------------------------------------\n");

        SQLiteDataSource dataSource = BenchmarkDbHelper.createDataSource();
        // 初始化测试数据: 50名教师, 500名学生
        BenchmarkDbHelper.initializeDatabase(dataSource, 50, 500);

        // 初始化 JavaORM (关闭 SQL 打印以纯测性能)
        OrmConfig javaOrmConfig = new OrmConfig().setPrintSql(false);
        JavaOrm javaOrm = JavaOrm.create(dataSource, new SQLiteDialect(), javaOrmConfig);

        // 初始化 EasyQuery Proxy Client (关闭 SQL 打印)
        EasyQueryClient eqClient = EasyQueryBootstrapper.defaultBuilderConfiguration()
                .setDefaultDataSource(dataSource)
                .optionConfigure(op -> op.setPrintSql(false))
                .useDatabaseConfigure(new SQLiteDatabaseConfiguration())
                .build();
        EasyEntityQuery easyQuery = new DefaultEasyEntityQuery(eqClient);

        List<BenchmarkResult> results = new ArrayList<>();

        // 1. Proxy 属性调用/拦截开销基准
        results.add(benchProxyPropertyCall(javaOrm, easyQuery, isQuick ? 1_000_000 : 10_000_000, warmupRounds, measureRounds));

        // 2. 纯 SQL / AST 表达式树构建开销 (无 DB 开销)
        results.add(benchQueryBuild(javaOrm, easyQuery, isQuick ? 5_000 : 50_000, warmupRounds, measureRounds));

        // 3. 主键点查单条记录性能
        results.add(benchPointQuery(javaOrm, easyQuery, isQuick ? 200 : 10_000, warmupRounds, measureRounds));

        // 4. 单表条件批量查询与对象装配 (100条记录/次)
        results.add(benchListQuery(javaOrm, easyQuery, isQuick ? 100 : 2_000, warmupRounds, measureRounds));

        // 5. 多表关联导航查询性能 (student -> teacher.age > 35)
        results.add(benchJoinQuery(javaOrm, easyQuery, isQuick ? 100 : 5_000, warmupRounds, measureRounds));

        // 6. 实体插入与主键回填性能
        results.add(benchInsert(javaOrm, easyQuery, isQuick ? 100 : 2_000, warmupRounds, measureRounds));

        // 输出最终格式化报表
        printSummaryTable(results);
    }

    /**
     * 维度 1: Proxy 属性调用/拦截开销对比
     */
    private static BenchmarkResult benchProxyPropertyCall(JavaOrm javaOrm, EasyEntityQuery easyQuery, int iterations, int warmup, int rounds) {
        System.out.println(">> [1/6] 运行基准测试: Proxy 属性访问开销 (每轮 " + iterations + " 次调用)...");

        com.benchmark.entity.javaorm.Student javaOrmProxy = ProxyFactory.getProxy(com.benchmark.entity.javaorm.Student.class);
        StudentProxy eqProxy = StudentProxy.TABLE.createTable();
        com.benchmark.entity.javaorm.Student rawStudent = new com.benchmark.entity.javaorm.Student();
        rawStudent.setAge(20);

        // Baseline (POJO getter)
        runLoop(() -> {
            Integer age = 0;
            for (int i = 0; i < iterations; i++) {
                age = rawStudent.getAge();
            }
            consume(age);
        }, warmup, 1);

        // JavaOrm Proxy (ByteBuddy 动态拦截)
        WhereBuilder<com.benchmark.entity.javaorm.Student> where = javaOrm.select(com.benchmark.entity.javaorm.Student.class).where();
        long javaOrmTime = measure(() -> {
            Integer age = 0;
            for (int i = 0; i < iterations; i++) {
                where.and(javaOrmProxy.getAge());
            }
            consume(age);
        }, warmup, rounds);

        // EasyQuery Proxy (APT 静态生成属性列获取)
        long eqTime = measure(() -> {
            Object col = null;
            for (int i = 0; i < iterations; i++) {
                col = eqProxy.age();
            }
            consume(col);
        }, warmup, rounds);

        return new BenchmarkResult("1. Proxy属性调用 (" + iterations + "次)", iterations, javaOrmTime, eqTime, "ns/op");
    }

    /**
     * 维度 2: 纯 SQL / AST 表达式树构建开销
     */
    private static BenchmarkResult benchQueryBuild(JavaOrm javaOrm, EasyEntityQuery easyQuery, int iterations, int warmup, int rounds) {
        System.out.println(">> [2/6] 运行基准测试: 纯 AST/SQL 查询构建 (每轮 " + iterations + " 次)...");

        long javaOrmTime = measure(() -> {
            for (int i = 0; i < iterations; i++) {
                javaOrm.select(com.benchmark.entity.javaorm.Student.class)
                        .where((w, s) -> {
                            w.and(s.getAge()).ge(18)
                                    .and(s.getStudentName()).like("王%")
                                    .orderBy(s.getAge()).desc();
                        });

            }
        }, warmup, rounds);

        long eqTime = measure(() -> {
            for (int i = 0; i < iterations; i++) {
                easyQuery.queryable(com.benchmark.entity.easyquery.Student.class)
                        .where(s -> {
                            s.age().ge(18);
                            s.studentName().like("王%");
                        })
                        .orderBy(s -> s.age().desc())
                        .toSQL();
            }
        }, warmup, rounds);

        return new BenchmarkResult("2. 查询语句/AST构建 (" + iterations + "次)", iterations, javaOrmTime, eqTime, "us/op");
    }

    /**
     * 维度 3: 主键点查单条记录
     */
    private static BenchmarkResult benchPointQuery(JavaOrm javaOrm, EasyEntityQuery easyQuery, int iterations, int warmup, int rounds) {
        System.out.println(">> [3/6] 运行基准测试: 主键点查 (每轮 " + iterations + " 次)...");

        long javaOrmTime = measure(() -> {
            for (int i = 0; i < iterations; i++) {
                long id = (i % 400) + 1;
                com.benchmark.entity.javaorm.Student s = javaOrm.select(com.benchmark.entity.javaorm.Student.class)
                        .where()
                        .and(st -> st.getId()).eq(id)
                        .single();
                consume(s);
            }
        }, warmup, rounds);

        long eqTime = measure(() -> {
            for (int i = 0; i < iterations; i++) {
                long id = (i % 400) + 1;
                com.benchmark.entity.easyquery.Student s = easyQuery.queryable(com.benchmark.entity.easyquery.Student.class)
                        .where(st -> st.id().eq(id))
                        .singleOrNull();
                consume(s);
            }
        }, warmup, rounds);

        return new BenchmarkResult("3. 主键单记录查询 (" + iterations + "次)", iterations, javaOrmTime, eqTime, "us/op");
    }

    /**
     * 维度 4: 单表批量条件查询与对象装配
     */
    private static BenchmarkResult benchListQuery(JavaOrm javaOrm, EasyEntityQuery easyQuery, int iterations, int warmup, int rounds) {
        System.out.println(">> [4/6] 运行基准测试: 单表批量查询与对象装配 (每轮 " + iterations + " 次)...");

        long javaOrmTime = measure(() -> {
            for (int i = 0; i < iterations; i++) {
                List<com.benchmark.entity.javaorm.Student> list = javaOrm.select(com.benchmark.entity.javaorm.Student.class)
                        .where()
                        .and(s -> s.getAge()).ge(22)
                        .list();
                consume(list.size());
            }
        }, warmup, rounds);

        long eqTime = measure(() -> {
            for (int i = 0; i < iterations; i++) {
                List<com.benchmark.entity.easyquery.Student> list = easyQuery.queryable(com.benchmark.entity.easyquery.Student.class)
                        .where(s -> s.age().ge(22))
                        .toList();
                consume(list.size());
            }
        }, warmup, rounds);

        return new BenchmarkResult("4. 批量查询与装配 (" + iterations + "次*100条)", iterations, javaOrmTime, eqTime, "us/op");
    }

    /**
     * 维度 5: 多表级联导航与隐式关联查询
     */
    private static BenchmarkResult benchJoinQuery(JavaOrm javaOrm, EasyEntityQuery easyQuery, int iterations, int warmup, int rounds) {
        System.out.println(">> [5/6] 运行基准测试: 级联导航过滤关联查询 (每轮 " + iterations + " 次)...");

        long javaOrmTime = measure(() -> {
            for (int i = 0; i < iterations; i++) {
                List<com.benchmark.entity.javaorm.Student> list = javaOrm.select(com.benchmark.entity.javaorm.Student.class)
                        .where()
                        .and(s -> s.getTeacher().getAge()).gt(35)
                        .list();
                consume(list.size());
            }
        }, warmup, rounds);

        long eqTime = measure(() -> {
            for (int i = 0; i < iterations; i++) {
                List<com.benchmark.entity.easyquery.Student> list = easyQuery.queryable(com.benchmark.entity.easyquery.Student.class)
                        .where(s -> s.teacher().age().gt(35))
                        .toList();
                consume(list.size());
            }
        }, warmup, rounds);

        return new BenchmarkResult("5. 级联导航关联查询 (" + iterations + "次)", iterations, javaOrmTime, eqTime, "us/op");
    }

    /**
     * 维度 6: 实体插入与自增主键回填
     */
    private static BenchmarkResult benchInsert(JavaOrm javaOrm, EasyEntityQuery easyQuery, int iterations, int warmup, int rounds) {
        System.out.println(">> [6/6] 运行基准测试: 实体插入与主键回填 (每轮 " + iterations + " 次)...");

        long javaOrmTime = measure(() -> {
            for (int i = 0; i < iterations; i++) {
                com.benchmark.entity.javaorm.Teacher t = new com.benchmark.entity.javaorm.Teacher();
                t.setTeacherName("测试插入_" + i);
                t.setAge(30 + (i % 20));
                t.setTitle("讲师");
                javaOrm.insert(t);
                consume(t.getId());
            }
        }, warmup, rounds);

        long eqTime = measure(() -> {
            for (int i = 0; i < iterations; i++) {
                com.benchmark.entity.easyquery.Teacher t = new com.benchmark.entity.easyquery.Teacher();
                t.setTeacherName("测试插入_" + i);
                t.setAge(30 + (i % 20));
                t.setTitle("讲师");
                easyQuery.insertable(t).executeRows(true);
                consume(t.getId());
            }
        }, warmup, rounds);

        return new BenchmarkResult("6. 实体插入与主键回填 (" + iterations + "次)", iterations, javaOrmTime, eqTime, "us/op");
    }

    private static long measure(Runnable task, int warmup, int rounds) {
        runLoop(task, warmup, 0);
        long minTime = Long.MAX_VALUE;
        long totalTime = 0;
        for (int r = 1; r <= rounds; r++) {
            long t1 = System.currentTimeMillis();
            task.run();
            long t2 = System.currentTimeMillis();
            long elapsed = t2 - t1;
            totalTime += elapsed;
            if (elapsed < minTime) {
                minTime = elapsed;
            }
        }
        return totalTime / rounds;
    }

    private static void runLoop(Runnable task, int rounds, int delayMs) {
        for (int i = 0; i < rounds; i++) {
            task.run();
            if (delayMs > 0) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    private static volatile Object blackHole;

    private static void consume(Object obj) {
        blackHole = obj;
    }

    private static void printSummaryTable(List<BenchmarkResult> list) {
        System.out.println("\n=======================================================================================================================");
        System.out.printf("%-32s | %-16s | %-16s | %-16s | %-18s%n",
                "基准测试场景", "JavaORM (平均耗时)", "EasyQuery (平均耗时)", "Throughput (ops/s)", "性能对比与倍率");
        System.out.println("---------------------------------+------------------+------------------+------------------+-----------------------------------");

        for (BenchmarkResult res : list) {
            String joAvg = res.javaOrmTimeMs + " ms (" + formatUnit(res.javaOrmTimeMs, res.iterations, res.unit) + ")";
            String eqAvg = res.easyQueryTimeMs + " ms (" + formatUnit(res.easyQueryTimeMs, res.iterations, res.unit) + ")";
            String ops = String.format("JO:%d / EQ:%d",
                    calcOps(res.iterations, res.javaOrmTimeMs),
                    calcOps(res.iterations, res.easyQueryTimeMs));

            double ratio;
            String comparison;
            if (res.javaOrmTimeMs <= res.easyQueryTimeMs) {
                ratio = res.javaOrmTimeMs == 0 ? 1.0 : (double) res.easyQueryTimeMs / res.javaOrmTimeMs;
                comparison = String.format("JavaORM 快 %.2fx", ratio);
            } else {
                ratio = res.easyQueryTimeMs == 0 ? 1.0 : (double) res.javaOrmTimeMs / res.easyQueryTimeMs;
                comparison = String.format("EasyQuery 快 %.2fx", ratio);
            }

            System.out.printf("%-32s | %-16s | %-16s | %-16s | %-18s%n",
                    res.name, joAvg, eqAvg, ops, comparison);
        }
        System.out.println("=======================================================================================================================\n");
    }

    private static long calcOps(int iterations, long timeMs) {
        if (timeMs <= 0) return iterations * 1000L;
        return (long) ((double) iterations / timeMs * 1000);
    }

    private static String formatUnit(long timeMs, int iterations, String unit) {
        double perOp = (double) timeMs * 1_000_000 / iterations; // in ns
        if ("ns/op".equals(unit)) {
            return String.format("%.1f ns/op", perOp);
        }
        return String.format("%.2f us/op", perOp / 1000.0);
    }

    public static class BenchmarkResult {
        public final String name;
        public final int iterations;
        public final long javaOrmTimeMs;
        public final long easyQueryTimeMs;
        public final String unit;

        public BenchmarkResult(String name, int iterations, long javaOrmTimeMs, long easyQueryTimeMs, String unit) {
            this.name = name;
            this.iterations = iterations;
            this.javaOrmTimeMs = javaOrmTimeMs;
            this.easyQueryTimeMs = easyQueryTimeMs;
            this.unit = unit;
        }
    }
}
