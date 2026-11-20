package com.javaorm.core.test;

import com.javaorm.core.JavaOrm;
import com.javaorm.core.annotation.GenerationType;
import com.javaorm.core.annotation.JoColumn;
import com.javaorm.core.annotation.JoGeneratedValue;
import com.javaorm.core.annotation.JoId;
import com.javaorm.core.annotation.JoTable;
import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.connection.TransactionContext;
import com.javaorm.core.dialect.H2Dialect;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.Assert.*;

/**
 * 轻量级读写分离路由器 (MasterSlaveRouter) 全面测试套件
 */
public class MasterSlaveReadWriteSeparationTest {

    private JdbcDataSource masterDs;
    private JdbcDataSource slave1Ds;
    private JdbcDataSource slave2Ds;
    private JavaOrm orm;

    @JoTable("test_order")
    public static class TestOrder {
        @JoId
        @JoGeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @JoColumn("order_no")
        private String orderNo;

        @JoColumn("amount")
        private Double amount;

        @JoColumn("source_db")
        private String sourceDb; // 记录来自哪个库（MASTER / SLAVE1 / SLAVE2）

        public TestOrder() {}

        public TestOrder(Long id, String orderNo, Double amount, String sourceDb) {
            this.id = id;
            this.orderNo = orderNo;
            this.amount = amount;
            this.sourceDb = sourceDb;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getOrderNo() { return orderNo; }
        public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

        public Double getAmount() { return amount; }
        public void setAmount(Double amount) { this.amount = amount; }

        public String getSourceDb() { return sourceDb; }
        public void setSourceDb(String sourceDb) { this.sourceDb = sourceDb; }
    }

    @Before
    public void setUp() throws Exception {
        com.javaorm.core.datasource.ReadWriteContext.clear();
        com.javaorm.core.datasource.DataSourceContext.clear();

        masterDs = createDs("jdbc:h2:mem:rw_master;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false;MODE=MySQL");
        slave1Ds = createDs("jdbc:h2:mem:rw_slave1;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false;MODE=MySQL");
        slave2Ds = createDs("jdbc:h2:mem:rw_slave2;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false;MODE=MySQL");

        initSchema(masterDs, "MASTER");
        initSchema(slave1Ds, "SLAVE1");
        initSchema(slave2Ds, "SLAVE2");

        OrmConfig config = new OrmConfig();
        config.setPrintSql(true);
        config.getMasterSlaveManager()
                .setMaster("master", masterDs)
                .addSlave("slave1", slave1Ds, 1)
                .addSlave("slave2", slave2Ds, 1)
                .setPinMasterAfterWriteMillis(300); // 写后 300ms 粘性锁定主库

        orm = JavaOrm.create(masterDs, new H2Dialect(), config);
    }

    @After
    public void tearDown() {
        com.javaorm.core.datasource.ReadWriteContext.clear();
        com.javaorm.core.datasource.DataSourceContext.clear();
    }

    private JdbcDataSource createDs(String url) {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL(url);
        ds.setUser("sa");
        ds.setPassword("");
        return ds;
    }

    private void initSchema(JdbcDataSource ds, String tag) throws Exception {
        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS test_order");
            stmt.execute("CREATE TABLE test_order (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "order_no VARCHAR(64), " +
                    "amount DOUBLE, " +
                    "source_db VARCHAR(32))");
            // 预植入初始记录，用于识别读请求路由到了哪个数据源
            stmt.execute("INSERT INTO test_order (id, order_no, amount, source_db) " +
                    "VALUES (1, 'ORD_001', 100.0, '" + tag + "')");
        }
    }

    @Test
    public void testAutomaticReadWriteRouting() {
        // 1. 默认读操作：应自动路由到从库（SLAVE1 或 SLAVE2），而不是 MASTER
        TestOrder queried = orm.select(TestOrder.class).where().eq(TestOrder::getId, 1L).first();
        assertNotNull(queried);
        assertTrue("默认读操作必须路由到从库", "SLAVE1".equals(queried.getSourceDb()) || "SLAVE2".equals(queried.getSourceDb()));

        // 2. 写操作（INSERT）：必须强制路由到 MASTER
        TestOrder newOrder = new TestOrder(null, "ORD_NEW", 500.0, "INSERTED");
        orm.insert(newOrder);
        Long newId = newOrder.getId();
        assertNotNull(newId);

        // 验证 Master 库中确实存在该新增记录
        TestOrder masterOrder = orm.select(TestOrder.class).where().eq(TestOrder::getId, newId).useMaster().first();
        assertNotNull(masterOrder);
        assertEquals("ORD_NEW", masterOrder.getOrderNo());
    }

    @Test
    public void testSlaveLoadBalancing() {
        // 连续多次读请求，应在从库 slave1 和 slave2 之间轮询
        int countSlave1 = 0;
        int countSlave2 = 0;
        for (int i = 0; i < 10; i++) {
            TestOrder order = orm.select(TestOrder.class).where().eq(TestOrder::getId, 1L).first();
            assertNotNull(order);
            if ("SLAVE1".equals(order.getSourceDb())) {
                countSlave1++;
            } else if ("SLAVE2".equals(order.getSourceDb())) {
                countSlave2++;
            }
        }
        assertTrue("多从库负载均衡必须被有效分发到两个节点", countSlave1 > 0 && countSlave2 > 0);
        assertEquals(10, countSlave1 + countSlave2);
    }

    @Test
    public void testFluentExplicitRouting() {
        // 1. 流式显式指定走 Master 主库
        TestOrder masterOrder = orm.select(TestOrder.class)
                .where().eq(TestOrder::getId, 1L)
                .useMaster()
                .first();
        assertNotNull(masterOrder);
        assertEquals("MASTER", masterOrder.getSourceDb());

        // 2. 流式显式指定走特定的从库 slave1
        TestOrder slave1Order = orm.select(TestOrder.class)
                .where().eq(TestOrder::getId, 1L)
                .useSlave("slave1")
                .first();
        assertNotNull(slave1Order);
        assertEquals("SLAVE1", slave1Order.getSourceDb());

        // 3. 流式显式指定走特定的从库 slave2
        TestOrder slave2Order = orm.select(TestOrder.class)
                .where().eq(TestOrder::getId, 1L)
                .useSlave("slave2")
                .first();
        assertNotNull(slave2Order);
        assertEquals("SLAVE2", slave2Order.getSourceDb());
    }

    @Test
    public void testTransactionLocksToMaster() throws Exception {
        // 模拟事务环境开启：设置 TransactionContext
        try (Connection conn = masterDs.getConnection()) {
            TransactionContext.setConnection(conn);
            try {
                // 在事务激活期间，即使执行普通的 SELECT，也必须 100% 强制锁定 Master，杜绝跨数据源事务与脏读
                TestOrder txOrder = orm.select(TestOrder.class).where().eq(TestOrder::getId, 1L).first();
                assertNotNull(txOrder);
                assertEquals("事务环境中读操作必须强制走主库", "MASTER", txOrder.getSourceDb());
            } finally {
                TransactionContext.clear();
            }
        }

        // 事务退出后，读操作应恢复自动路由走从库
        TestOrder postTxOrder = orm.select(TestOrder.class).where().eq(TestOrder::getId, 1L).first();
        assertTrue("事务退出后必须恢复路由到从库", "SLAVE1".equals(postTxOrder.getSourceDb()) || "SLAVE2".equals(postTxOrder.getSourceDb()));
    }

    @Test
    public void testPinMasterAfterWriteLagWindow() throws Exception {
        // 1. 在没有写操作前，读请求走从库
        TestOrder before = orm.select(TestOrder.class).where().eq(TestOrder::getId, 1L).first();
        assertTrue("SLAVE1".equals(before.getSourceDb()) || "SLAVE2".equals(before.getSourceDb()));

        // 2. 执行一次写操作（更新主库）
        TestOrder toUpdate = new TestOrder(1L, "ORD_MODIFIED", 999.0, "MASTER");
        orm.update(toUpdate);

        // 3. 紧随其后的读请求：因处于 300ms 粘性窗口期内，应自动智能 Pin 到 Master 主库！
        TestOrder immediateAfter = orm.select(TestOrder.class).where().eq(TestOrder::getId, 1L).first();
        assertNotNull(immediateAfter);
        assertEquals("写后时间窗口内读操作必须自动 Pin 到 Master 主库以抗复制延迟", "MASTER", immediateAfter.getSourceDb());

        // 4. 等待 350ms，超过窗口期
        Thread.sleep(350);

        // 5. 窗口过期后，读请求自动恢复走 Slave 从库
        TestOrder expiredAfter = orm.select(TestOrder.class).where().eq(TestOrder::getId, 1L).first();
        assertNotNull(expiredAfter);
        assertTrue("超过写后粘性窗口期后必须恢复走从库", "SLAVE1".equals(expiredAfter.getSourceDb()) || "SLAVE2".equals(expiredAfter.getSourceDb()));
    }

    @Test
    public void testFallbackToMasterWhenNoSlave() {
        // 移除所有从库
        orm.getConfig().getMasterSlaveManager().removeSlave("slave1").removeSlave("slave2");
        assertTrue(orm.getConfig().getMasterSlaveManager().getSlaves().isEmpty());

        // 此时读操作应平滑安全回退到 Master 主库，绝不崩溃
        TestOrder fallbackOrder = orm.select(TestOrder.class).where().eq(TestOrder::getId, 1L).first();
        assertNotNull(fallbackOrder);
        assertEquals("无可用从库时必须平滑降级回退到 Master 主库", "MASTER", fallbackOrder.getSourceDb());
    }
}
