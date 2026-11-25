package com.javaorm.core.test;

import com.javaorm.core.JavaOrm;
import com.javaorm.core.annotation.JoId;
import com.javaorm.core.annotation.JoTable;
import com.javaorm.core.ast.SelectStatement;
import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.dialect.Dialect;
import com.javaorm.core.dialect.SQLiteDialect;
import com.javaorm.core.dialect.SqlAndParams;
import com.javaorm.core.executor.DefaultJdbcExecutor;
import com.javaorm.core.executor.SqlExecutor;
import com.javaorm.core.metadata.EntityMetadata;
import com.javaorm.core.query.IncludeSpec;
import com.javaorm.core.sharding.JoSharding;
import com.javaorm.core.sharding.JoShardingKey;
import com.javaorm.core.sharding.ModTableRouter;
import com.javaorm.core.sharding.MonthTableRouter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class ShardingTableTest {

    private JavaOrm orm;
    private SQLiteDataSource dataSource;
    private static final String DB_FILE = "target/test_sharding.db";

    @JoSharding(router = ModTableRouter.class, allowFullScan = false)
    @JoTable(name = "t_order")
    public static class ShardedOrder {
        @JoId
        private Long id;

        @JoShardingKey
        private Long orderId;

        private String orderNo;
        private Double amount;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getOrderId() {
            return orderId;
        }

        public void setOrderId(Long orderId) {
            this.orderId = orderId;
        }

        public String getOrderNo() {
            return orderNo;
        }

        public void setOrderNo(String orderNo) {
            this.orderNo = orderNo;
        }

        public Double getAmount() {
            return amount;
        }

        public void setAmount(Double amount) {
            this.amount = amount;
        }
    }

    @JoSharding(router = MonthTableRouter.class, allowFullScan = false)
    @JoTable(name = "t_audit_log")
    public static class ShardedAuditLog {
        @JoId
        private Long id;

        @JoShardingKey
        private String logDate;

        private String content;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getLogDate() {
            return logDate;
        }

        public void setLogDate(String logDate) {
            this.logDate = logDate;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }

    @JoTable(name = "t_single_config")
    public static class SingleConfig {
        @JoId
        private Long id;
        private String configKey;
        private String configValue;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getConfigKey() {
            return configKey;
        }

        public void setConfigKey(String configKey) {
            this.configKey = configKey;
        }

        public String getConfigValue() {
            return configValue;
        }

        public void setConfigValue(String configValue) {
            this.configValue = configValue;
        }
    }

    @Before
    public void setUp() throws Exception {
        new File("target").mkdirs();
        new File(DB_FILE).delete();

        dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + DB_FILE);

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            // 创建分表 t_order_0, t_order_1, t_order_2, t_order_3
            for (int i = 0; i < 4; i++) {
                stmt.execute("CREATE TABLE t_order_" + i + " (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "order_id INTEGER, " +
                        "order_no TEXT, " +
                        "amount REAL);");
            }

            // 创建按月分表 t_audit_log_202611, t_audit_log_202612
            stmt.execute("CREATE TABLE t_audit_log_202611 (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "log_date TEXT, " +
                    "content TEXT);");
            stmt.execute("CREATE TABLE t_audit_log_202612 (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "log_date TEXT, " +
                    "content TEXT);");

            // 创建单表 t_single_config
            stmt.execute("CREATE TABLE t_single_config (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "config_key TEXT, " +
                    "config_value TEXT);");
        }

        OrmConfig config = new OrmConfig().setPrintSql(true);
        orm = JavaOrm.create(dataSource, new SQLiteDialect(), config);
    }

    @After
    public void tearDown() {
        new File(DB_FILE).delete();
    }

    @Test
    public void testModInsertAndSingleTableRoute() {
        // 插入4条订单，分别对应 100 (->0), 101 (->1), 102 (->2), 103 (->3)
        ShardedOrder o0 = new ShardedOrder();
        o0.setOrderId(100L);
        o0.setOrderNo("ORD-100");
        o0.setAmount(100.0);
        orm.insert(o0).exec();

        ShardedOrder o1 = new ShardedOrder();
        o1.setOrderId(101L);
        o1.setOrderNo("ORD-101");
        o1.setAmount(200.0);
        orm.insert(o1).exec();

        ShardedOrder o2 = new ShardedOrder();
        o2.setOrderId(102L);
        o2.setOrderNo("ORD-102");
        o2.setAmount(300.0);
        orm.insert(o2).exec();

        ShardedOrder o3 = new ShardedOrder();
        o3.setOrderId(103L);
        o3.setOrderNo("ORD-103");
        o3.setAmount(400.0);
        orm.insert(o3).exec();

        // 精确单表点查：orderId = 101 -> 路由到 t_order_1
        List<ShardedOrder> list = orm.select(ShardedOrder.class)
                .where().eq(o -> o.getOrderId(), 101L)
                .toList();

        assertEquals(1, list.size());
        assertEquals("ORD-101", list.get(0).getOrderNo());
        assertEquals(200.0, list.get(0).getAmount(), 0.001);

        // 验证 count 单表路由
        long count = orm.select(ShardedOrder.class)
                .where().eq(o -> o.getOrderId(), 101L)
                .count();
        assertEquals(1L, count);
    }

    @Test
    public void testFullScanProhibitedByDefault() {
        // 默认 allowFullScan = false，在缺少分片键条件时必须抛出异常，防止全表全分片慢查询
        try {
            orm.select(ShardedOrder.class)
                    .where().gt(o -> o.getAmount(), 100.0)
                    .toList();
            fail("Should throw IllegalStateException when sharding key is missing and allowFullScan is false");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Full table scan across sharding tables is prohibited"));
        }
    }

    @Test
    public void testAllowFullScan() {
        // 插入2条不同分表的数据
        ShardedOrder o0 = new ShardedOrder();
        o0.setOrderId(100L);
        o0.setOrderNo("ORD-100");
        o0.setAmount(50.0);
        orm.insert(o0).exec();

        ShardedOrder o1 = new ShardedOrder();
        o1.setOrderId(101L);
        o1.setOrderNo("ORD-101");
        o1.setAmount(150.0);
        orm.insert(o1).exec();

        // 显式允许全表扫描：广播扫描 t_order_0 .. t_order_3 并归并
        List<ShardedOrder> all = orm.select(ShardedOrder.class)
                .allowFullShardingScan(true)
                .toList();

        assertEquals(2, all.size());

        // 全分片聚合 count 归并
        long count = orm.select(ShardedOrder.class)
                .allowFullShardingScan(true)
                .count();
        assertEquals(2L, count);
    }

    @Test
    public void testInConditionMultiTableRouteAndMerge() {
        ShardedOrder o0 = new ShardedOrder();
        o0.setOrderId(100L);
        o0.setOrderNo("ORD-100");
        o0.setAmount(100.0);
        orm.insert(o0).exec();

        ShardedOrder o1 = new ShardedOrder();
        o1.setOrderId(101L);
        o1.setOrderNo("ORD-101");
        o1.setAmount(200.0);
        orm.insert(o1).exec();

        ShardedOrder o2 = new ShardedOrder();
        o2.setOrderId(102L);
        o2.setOrderNo("ORD-102");
        o2.setAmount(300.0);
        orm.insert(o2).exec();

        // IN 查询跨越 t_order_0 和 t_order_1
        List<ShardedOrder> result = orm.select(ShardedOrder.class)
                .where().in(o -> o.getOrderId(), Arrays.asList(100L, 101L))
                .toList();

        assertEquals(2, result.size());
    }

    @Test
    public void testMonthTableRouter() {
        ShardedAuditLog log1 = new ShardedAuditLog();
        log1.setLogDate("2026-11-18");
        log1.setContent("Login success");
        orm.insert(log1).exec();

        ShardedAuditLog log2 = new ShardedAuditLog();
        log2.setLogDate("2026-12-05");
        log2.setContent("Password changed");
        orm.insert(log2).exec();

        // 按日期单表查询 -> 路由到 t_audit_log_202611
        List<ShardedAuditLog> listNov = orm.select(ShardedAuditLog.class)
                .where().eq(l -> l.getLogDate(), "2026-11-18")
                .toList();

        assertEquals(1, listNov.size());
        assertEquals("Login success", listNov.get(0).getContent());

        // 按范围区间跨月查询 -> 路由到 202611 和 202612 归并
        List<ShardedAuditLog> rangeLogs = orm.select(ShardedAuditLog.class)
                .where().between(l -> l.getLogDate(), "2026-11-01", "2026-12-31")
                .toList();

        assertEquals(2, rangeLogs.size());
    }

    @Test
    public void testUpdateAndDeleteRouting() {
        ShardedOrder o1 = new ShardedOrder();
        o1.setOrderId(101L);
        o1.setOrderNo("ORD-101");
        o1.setAmount(200.0);
        orm.insert(o1).exec();

        // 条件更新：只路由更新 t_order_1
        int updated = orm.update(ShardedOrder.class)
                .set(o -> o.getAmount(), 999.0)
                .where().eq(o -> o.getOrderId(), 101L)
                .execute();

        assertEquals(1, updated);

        ShardedOrder fetched = orm.select(ShardedOrder.class)
                .where().eq(o -> o.getOrderId(), 101L)
                .firstOrNull();
        assertNotNull(fetched);
        assertEquals(999.0, fetched.getAmount(), 0.001);

        // 条件删除：只路由删除 t_order_1
        int deleted = orm.delete(ShardedOrder.class)
                .where().eq(o -> o.getOrderId(), 101L)
                .execute();

        assertEquals(1, deleted);

        ShardedOrder afterDelete = orm.select(ShardedOrder.class)
                .where().eq(o -> o.getOrderId(), 101L)
                .firstOrNull();
        assertNull(afterDelete);
    }

    /**
     * 核心测试：验证分片处理在 SqlExecutor 之前完成，执行器完全解耦，第三方执行器（如 MyBatis）无需关心分片
     */
    @Test
    public void testCustomSqlExecutorPluggability() {
        // 自定义捕获执行器，模拟第三方引擎（如 MyBatis）
        class CapturingSqlExecutor implements SqlExecutor {
            private final SqlExecutor delegate = new DefaultJdbcExecutor();
            private String lastPhysicalTable;
            private int queryCount = 0;

            @Override
            public <R> List<R> executeQuery(Connection conn, SelectStatement statement, EntityMetadata metadata,
                                            List<IncludeSpec> includes, Dialect dialect, OrmConfig config, DataSource dataSource) {
                this.lastPhysicalTable = statement.getRootTableName();
                this.queryCount++;
                return delegate.executeQuery(conn, statement, metadata, includes, dialect, config, dataSource);
            }

            @Override
            public <DTO> List<DTO> executeDtoQuery(Connection conn, SelectStatement statement, Class<DTO> dtoClass,
                                                   Dialect dialect, OrmConfig config, DataSource dataSource) {
                this.lastPhysicalTable = statement.getRootTableName();
                return delegate.executeDtoQuery(conn, statement, dtoClass, dialect, config, dataSource);
            }

            @Override
            public List<Map<String, Object>> executeMaps(Connection conn, SelectStatement statement, Dialect dialect,
                                                         OrmConfig config, DataSource dataSource) {
                this.lastPhysicalTable = statement.getRootTableName();
                return delegate.executeMaps(conn, statement, dialect, config, dataSource);
            }

            @Override
            public <V> V executeScalar(Connection conn, SelectStatement statement, Class<V> targetType,
                                       Dialect dialect, OrmConfig config, DataSource dataSource) {
                this.lastPhysicalTable = statement.getRootTableName();
                return delegate.executeScalar(conn, statement, targetType, dialect, config, dataSource);
            }

            @Override
            public <V> List<V> executeScalarList(Connection conn, SelectStatement statement, Class<V> targetType,
                                                 Dialect dialect, OrmConfig config, DataSource dataSource) {
                this.lastPhysicalTable = statement.getRootTableName();
                return delegate.executeScalarList(conn, statement, targetType, dialect, config, dataSource);
            }

            @Override
            public long executeCount(Connection conn, SelectStatement statement, Dialect dialect, OrmConfig config, DataSource dataSource) {
                this.lastPhysicalTable = statement.getRootTableName();
                return delegate.executeCount(conn, statement, dialect, config, dataSource);
            }

            @Override
            public int executeUpdate(Connection conn, SqlAndParams sqlAndParams, String prefix,
                                     Dialect dialect, OrmConfig config, DataSource dataSource) {
                return delegate.executeUpdate(conn, sqlAndParams, prefix, dialect, config, dataSource);
            }

            @Override
            public int executeInsert(Connection conn, com.javaorm.core.ast.InsertStatement statement,
                                     EntityMetadata metadata, boolean returnKeys, Object entity,
                                     Dialect dialect, OrmConfig config, DataSource dataSource) {
                this.lastPhysicalTable = statement.getTableName();
                return delegate.executeInsert(conn, statement, metadata, returnKeys, entity, dialect, config, dataSource);
            }

            @Override
            public int[] executeBatchUpdate(Connection conn, String sql, List<List<Object>> batchParams,
                                            Dialect dialect, OrmConfig config, DataSource dataSource) {
                return delegate.executeBatchUpdate(conn, sql, batchParams, dialect, config, dataSource);
            }
        }

        CapturingSqlExecutor customExecutor = new CapturingSqlExecutor();
        OrmConfig customConfig = new OrmConfig().setSqlExecutor(customExecutor);
        JavaOrm customOrm = JavaOrm.create(dataSource, new SQLiteDialect(), customConfig);

        // 1. 测试分片实体查询：orderId = 102
        // 分片处理器在执行器前完成路由与物理表重写，执行器直接收到物理表 t_order_2
        customOrm.select(ShardedOrder.class)
                .where().eq(o -> o.getOrderId(), 102L)
                .toList();

        assertEquals("t_order_2", customExecutor.lastPhysicalTable);

        // 2. 测试单表实体查询（未启用分片）
        // 完全不进入分片流程，执行器直接接收逻辑单表名 t_single_config
        customOrm.select(SingleConfig.class)
                .where().eq(c -> c.getConfigKey(), "site_name")
                .toList();

        assertEquals("t_single_config", customExecutor.lastPhysicalTable);
    }
}
