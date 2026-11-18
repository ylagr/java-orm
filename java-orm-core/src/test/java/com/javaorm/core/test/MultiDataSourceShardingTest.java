package com.javaorm.core.test;

import com.javaorm.core.JavaOrm;
import com.javaorm.core.annotation.JoId;
import com.javaorm.core.annotation.JoTable;
import com.javaorm.core.datasource.DataSourceContext;
import com.javaorm.core.dialect.SQLiteDialect;
import com.javaorm.core.sharding.JoSharding;
import com.javaorm.core.sharding.JoShardingKey;
import com.javaorm.core.sharding.ModDataSourceRouter;
import com.javaorm.core.sharding.ModTableRouter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sqlite.SQLiteDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.Assert.*;

public class MultiDataSourceShardingTest {

    private JavaOrm orm;
    private SQLiteDataSource dsMaster;
    private SQLiteDataSource dsSlave;
    private SQLiteDataSource ds0;
    private SQLiteDataSource ds1;

    private static final String MASTER_FILE = "target/test_ds_master.db";
    private static final String SLAVE_FILE = "target/test_ds_slave.db";
    private static final String DS0_FILE = "target/test_ds_0.db";
    private static final String DS1_FILE = "target/test_ds_1.db";

    /**
     * 场景 1: 静态绑定只读从库
     */
    @JoSharding(dataSource = "ds_slave")
    @JoTable(name = "t_report")
    public static class SlaveReport {
        @JoId
        private Long id;
        private String title;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }
    }

    /**
     * 场景 2: 动态分库（通过 ModDataSourceRouter 对 2 取模，分到 ds_0 / ds_1）
     * 省略 @JoShardingKey，自动回退使用 @JoId 作为分片键！
     */
    @JoSharding(dataSourceRouter = ModDataSourceRouter.class, allowFullScan = true)
    @JoTable(name = "t_user")
    public static class ShardedUser {
        @JoId
        private Long userId;
        private String username;

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }
    }

    /**
     * 场景 3: 既分库又分表（ModDataSourceRouter 取模到 ds_0/ds_1，ModTableRouter 取模到 t_trade_0/t_trade_1）
     * 显式指定 @JoShardingKey
     */
    @JoSharding(dataSourceRouter = ModDataSourceRouter.class, tableRouter = ModTableRouter.class, allowFullScan = true)
    @JoTable(name = "t_trade")
    public static class ShardedTrade {
        @JoId
        private Long id;

        @JoShardingKey
        private Long tradeId;

        private Double amount;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getTradeId() {
            return tradeId;
        }

        public void setTradeId(Long tradeId) {
            this.tradeId = tradeId;
        }

        public Double getAmount() {
            return amount;
        }

        public void setAmount(Double amount) {
            this.amount = amount;
        }
    }

    @Before
    public void setUp() throws Exception {
        new File("target").mkdirs();
        new File(MASTER_FILE).delete();
        new File(SLAVE_FILE).delete();
        new File(DS0_FILE).delete();
        new File(DS1_FILE).delete();

        dsMaster = new SQLiteDataSource();
        dsMaster.setUrl("jdbc:sqlite:" + MASTER_FILE);

        dsSlave = new SQLiteDataSource();
        dsSlave.setUrl("jdbc:sqlite:" + SLAVE_FILE);

        ds0 = new SQLiteDataSource();
        ds0.setUrl("jdbc:sqlite:" + DS0_FILE);

        ds1 = new SQLiteDataSource();
        ds1.setUrl("jdbc:sqlite:" + DS1_FILE);

        // 初始化 master 和 slave 数据库结构
        try (Connection c = dsMaster.getConnection(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE t_report (id INTEGER PRIMARY KEY, title TEXT)");
            s.execute("INSERT INTO t_report (id, title) VALUES (1, 'Master Report')");
        }
        try (Connection c = dsSlave.getConnection(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE t_report (id INTEGER PRIMARY KEY, title TEXT)");
            s.execute("INSERT INTO t_report (id, title) VALUES (1, 'Slave Report')");
        }

        // 初始化 ds_0 和 ds_1 数据库结构
        for (SQLiteDataSource ds : new SQLiteDataSource[]{ds0, ds1}) {
            try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
                s.execute("CREATE TABLE t_user (user_id INTEGER PRIMARY KEY, username TEXT)");
                s.execute("CREATE TABLE t_trade_0 (id INTEGER PRIMARY KEY, trade_id INTEGER, amount REAL)");
                s.execute("CREATE TABLE t_trade_1 (id INTEGER PRIMARY KEY, trade_id INTEGER, amount REAL)");
            }
        }

        orm = JavaOrm.create(dsMaster, new SQLiteDialect());
        orm.registerDataSource("master", dsMaster)
                .registerDataSource("ds_slave", dsSlave)
                .registerDataSource("ds_0", ds0)
                .registerDataSource("ds_1", ds1);
    }

    @After
    public void tearDown() {
        DataSourceContext.clear();
        new File(MASTER_FILE).delete();
        new File(SLAVE_FILE).delete();
        new File(DS0_FILE).delete();
        new File(DS1_FILE).delete();
    }

    @Test
    public void testStaticDataSourceAnnotation() {
        // SlaveReport 上标注了 @JoSharding(dataSource = "ds_slave")
        SlaveReport report = orm.select(SlaveReport.class)
                .where().eq(SlaveReport::getId, 1L)
                .firstOrNull();

        assertNotNull(report);
        assertEquals("Slave Report", report.getTitle());
    }

    @Test
    public void testDynamicDataSourceRoutingWithJoIdFallback() {
        // userId = 100 -> 100 % 2 = 0 -> 应路由到 ds_0
        // userId = 101 -> 101 % 2 = 1 -> 应路由到 ds_1
        ShardedUser u1 = new ShardedUser();
        u1.setUserId(100L);
        u1.setUsername("Alice_DS0");
        orm.insert(u1);

        ShardedUser u2 = new ShardedUser();
        u2.setUserId(101L);
        u2.setUsername("Bob_DS1");
        orm.insert(u2);

        // 验证精准路由查询
        ShardedUser found0 = orm.select(ShardedUser.class)
                .where().eq(ShardedUser::getUserId, 100L)
                .firstOrNull();
        assertNotNull(found0);
        assertEquals("Alice_DS0", found0.getUsername());

        ShardedUser found1 = orm.select(ShardedUser.class)
                .where().eq(ShardedUser::getUserId, 101L)
                .firstOrNull();
        assertNotNull(found1);
        assertEquals("Bob_DS1", found1.getUsername());

        // 验证广播跨库聚合查询（allowFullScan = true）
        List<ShardedUser> allUsers = orm.select(ShardedUser.class).toList();
        assertEquals(2, allUsers.size());
    }

    @Test
    public void testCombinedDataSourceAndTableSharding() {
        // tradeId = 200:
        // dsRouter: 200 % 2 = 0 -> ds_0
        // tableRouter: 200 % 2 = 0 -> t_trade_0
        ShardedTrade t0 = new ShardedTrade();
        t0.setId(1L);
        t0.setTradeId(200L);
        t0.setAmount(99.5);
        orm.insert(t0);

        // tradeId = 201:
        // dsRouter: 201 % 2 = 1 -> ds_1
        // tableRouter: 201 % 2 = 1 -> t_trade_1
        ShardedTrade t1 = new ShardedTrade();
        t1.setId(2L);
        t1.setTradeId(201L);
        t1.setAmount(199.5);
        orm.insert(t1);

        // 精准单库单表查询
        ShardedTrade res0 = orm.select(ShardedTrade.class)
                .where().eq(ShardedTrade::getTradeId, 200L)
                .firstOrNull();
        assertNotNull(res0);
        assertEquals(Double.valueOf(99.5), res0.getAmount());

        ShardedTrade res1 = orm.select(ShardedTrade.class)
                .where().eq(ShardedTrade::getTradeId, 201L)
                .firstOrNull();
        assertNotNull(res1);
        assertEquals(Double.valueOf(199.5), res1.getAmount());

        // 跨库跨表更新
        int updated = orm.update(ShardedTrade.class)
                .set(ShardedTrade::getAmount, 888.0)
                .where().eq(ShardedTrade::getTradeId, 200L)
                .execute();
        assertEquals(1, updated);

        ShardedTrade updatedTrade = orm.select(ShardedTrade.class)
                .where().eq(ShardedTrade::getTradeId, 200L)
                .firstOrNull();
        assertEquals(Double.valueOf(888.0), updatedTrade.getAmount());

        // 跨库跨表删除
        int deleted = orm.delete(ShardedTrade.class)
                .where().eq(ShardedTrade::getTradeId, 201L)
                .execute();
        assertEquals(1, deleted);

        assertNull(orm.select(ShardedTrade.class)
                .where().eq(ShardedTrade::getTradeId, 201L)
                .firstOrNull());
    }

    @Test
    public void testThreadLevelDataSourceSwitching() {
        // 使用 orm.useDataSource 临时切换到从库
        String titleInSlave = orm.useDataSource("ds_slave", () ->
                orm.select(SlaveReport.class).where().eq(SlaveReport::getId, 1L).firstOrNull().getTitle()
        );
        assertEquals("Slave Report", titleInSlave);

        // 使用 orm.useDataSource 临时切换到主库
        String titleInMaster = orm.useDataSource("master", () ->
                orm.select(SlaveReport.class)
                        .dataSource("master") // DSL 覆盖静态注解
                        .where().eq(SlaveReport::getId, 1L).firstOrNull().getTitle()
        );
        assertEquals("Master Report", titleInMaster);
    }

    @Test
    public void testDslDataSourceOverride() {
        // 实体未指定分库注解，默认查 master，但 DSL 指定查 ds_slave
        SlaveReport report = orm.select(SlaveReport.class)
                .dataSource("master") // 显式覆盖注解中的 ds_slave
                .where().eq(SlaveReport::getId, 1L)
                .firstOrNull();
        assertNotNull(report);
        assertEquals("Master Report", report.getTitle());
    }
}
