package com.javaorm.core.test;

import com.javaorm.core.JavaOrm;
import com.javaorm.core.annotation.*;
import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.dialect.H2Dialect;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * 实体快照与变脏检测 (Dirty Checking / 差异增量更新) 全面测试套件
 */
public class DirtyCheckingTest {

    private JdbcDataSource dataSource;
    private JavaOrm orm;

    @JoTable("test_dirty_user")
    public static class DirtyUser {
        @JoId
        @JoGeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @JoColumn("user_name")
        private String userName;

        @JoColumn("email")
        private String email;

        @JoColumn("age")
        private Integer age;

        @JoColumn("balance")
        private BigDecimal balance;

        @JoVersion
        @JoColumn("version")
        private Integer version;

        public DirtyUser() {}

        public DirtyUser(Long id, String userName, String email, Integer age, BigDecimal balance, Integer version) {
            this.id = id;
            this.userName = userName;
            this.email = email;
            this.age = age;
            this.balance = balance;
            this.version = version;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getUserName() { return userName; }
        public void setUserName(String userName) { this.userName = userName; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public Integer getAge() { return age; }
        public void setAge(Integer age) { this.age = age; }

        public BigDecimal getBalance() { return balance; }
        public void setBalance(BigDecimal balance) { this.balance = balance; }

        public Integer getVersion() { return version; }
        public void setVersion(Integer version) { this.version = version; }

        // 重写 equals/hashCode 用于验证基于内存地址判等的抗冲突能力
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DirtyUser user = (DirtyUser) o;
            return Objects.equals(id, user.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    @Before
    public void setUp() throws Exception {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:dirty_test;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false;MODE=MySQL");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS test_dirty_user");
            stmt.execute("CREATE TABLE test_dirty_user (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "user_name VARCHAR(64), " +
                    "email VARCHAR(128), " +
                    "age INT, " +
                    "balance DECIMAL(18, 4), " +
                    "version INT DEFAULT 1)");
        }

        OrmConfig config = new OrmConfig();
        config.setPrintSql(true);
        orm = JavaOrm.create(dataSource, new H2Dialect(), config);
    }

    @Test
    public void testBasicSnapshotAndDirtyDetection() {
        DirtyUser user = new DirtyUser(1L, "Alice", "alice@example.com", 25, new BigDecimal("100.50"), 1);
        orm.attach(user);

        assertTrue(orm.isTracking(user));
        assertFalse("刚拍摄快照时绝不应变脏", orm.isDirty(user));
        assertTrue(orm.getDirtyProperties(user).isEmpty());
        assertTrue(orm.getDirtyColumns(user).isEmpty());

        // 修改为相同属性值（不应变脏）
        user.setUserName("Alice");
        assertFalse(orm.isDirty(user));

        // BigDecimal 精度不同但数值等价（100.5 vs 100.5000）不应变脏
        user.setBalance(new BigDecimal("100.5000"));
        assertFalse(orm.isDirty(user));

        // 修改属性值（应变脏）
        user.setAge(26);
        assertTrue(orm.isDirty(user));
        List<String> dirtyProps = orm.getDirtyProperties(user);
        assertEquals(1, dirtyProps.size());
        assertTrue(dirtyProps.contains("age"));
        Set<String> dirtyCols = orm.getDirtyColumnNames(user);
        assertTrue(dirtyCols.contains("age"));

        // 再修改一个字段
        user.setEmail("alice_new@example.com");
        dirtyProps = orm.getDirtyProperties(user);
        assertEquals(2, dirtyProps.size());
        assertTrue(dirtyProps.contains("age"));
        assertTrue(dirtyProps.contains("email"));
        dirtyCols = orm.getDirtyColumnNames(user);
        assertTrue(dirtyCols.contains("email"));

        // 回滚某个属性为初始值
        user.setAge(25);
        dirtyProps = orm.getDirtyProperties(user);
        assertEquals(1, dirtyProps.size());
        assertFalse(dirtyProps.contains("age"));
        assertTrue(dirtyProps.contains("email"));

        // detach 后不再追踪
        orm.detach(user);
        assertFalse(orm.isTracking(user));
        assertFalse(orm.isDirty(user));
    }

    @Test
    public void testQueryTrackingAndSelectiveIncrementalUpdate() {
        // 插入初始数据
        DirtyUser initial = new DirtyUser(null, "Bob", "bob@example.com", 30, new BigDecimal("200.00"), 1);
        orm.insert(initial).exec();
        Long id = initial.getId();
        assertNotNull(id);

        // 1. 使用 .tracking() 查询实体，自动挂载快照
        DirtyUser queried = orm.select(DirtyUser.class).where().eq("id", id).tracking().firstOrNull();
        assertNotNull(queried);
        assertTrue("通过 .tracking() 查询的实体应被自动追踪", orm.isTracking(queried));
        assertFalse(orm.isDirty(queried));

        // 2. 未作任何修改直接 update：应直接跳过 SQL 执行并返回 0
        int affectedZero = orm.update(queried).exec();
        assertEquals("无字段修改时，脏检查应直接跳过执行并返回 0", 0, affectedZero);

        // 验证数据库里的 version 仍然是 1（没有被乐观锁递增）
        DirtyUser unchangedDb = orm.select(DirtyUser.class).where().eq("id", id).firstOrNull();
        assertEquals(Integer.valueOf(1), unchangedDb.getVersion());

        // 3. 仅修改 age
        queried.setAge(31);
        assertTrue(orm.isDirty(queried));
        assertEquals(1, orm.getDirtyProperties(queried).size());
        assertTrue(orm.getDirtyProperties(queried).contains("age"));

        int affectedOne = orm.update(queried).exec();
        assertEquals(1, affectedOne);
        // 乐观锁版本号自动递增到 2
        assertEquals(Integer.valueOf(2), queried.getVersion());

        // 4. 更新完成后，快照已自动刷新为最新状态，再次更新应直接返回 0
        assertFalse("更新成功后快照应自动同步，不应变脏", orm.isDirty(queried));
        int affectedSecond = orm.update(queried).exec();
        assertEquals("再次更新未修改的实体，应返回 0", 0, affectedSecond);

        // 5. 校验数据库中的最新值
        DirtyUser updatedDb = orm.select(DirtyUser.class).where().eq("id", id).firstOrNull();
        assertEquals(Integer.valueOf(31), updatedDb.getAge());
        assertEquals("bob@example.com", updatedDb.getEmail());
        assertEquals("Bob", updatedDb.getUserName());
        assertEquals(Integer.valueOf(2), updatedDb.getVersion());
    }

    @Test
    public void testAutoTrackGlobalConfig() {
        // 插入初始数据
        DirtyUser u = new DirtyUser(null, "Charlie", "charlie@test.com", 22, new BigDecimal("50.00"), 1);
        orm.insert(u).exec();
        Long id = u.getId();

        // 开启全局自动追踪
        orm.getConfig().setAutoTrack(true);

        try {
            // 普通 query 不需要手动调用 .tracking()
            DirtyUser fetched = orm.select(DirtyUser.class).where().eq("id", id).firstOrNull();
            assertNotNull(fetched);
            assertTrue("开启全局 autoTrack 时，查询出来的实体应自动被追踪", orm.isTracking(fetched));
            assertFalse(orm.isDirty(fetched));

            fetched.setUserName("Charlie Brown");
            assertTrue(orm.isDirty(fetched));

            int rows = orm.update(fetched).exec();
            assertEquals(1, rows);

            DirtyUser dbUser = orm.select(DirtyUser.class).where().eq("id", id).firstOrNull();
            assertEquals("Charlie Brown", dbUser.getUserName());
        } finally {
            orm.getConfig().setAutoTrack(false);
        }
    }

    @Test
    public void testIdentityWeakKeyConflictResistance() {
        // 验证即使重写了 equals 和 hashCode，基于对象物理地址判等（== 与 identityHashCode）绝不互相覆盖
        DirtyUser u1 = new DirtyUser(100L, "User1", "u1@test.com", 20, BigDecimal.TEN, 1);
        DirtyUser u2 = new DirtyUser(100L, "User2", "u2@test.com", 20, BigDecimal.TEN, 1);

        // u1.equals(u2) 为 true（因为我们重写了按 id 判等）
        assertEquals(u1, u2);
        assertNotSame(u1, u2);

        orm.attach(u1);
        orm.attach(u2);

        assertTrue(orm.isTracking(u1));
        assertTrue(orm.isTracking(u2));

        u1.setUserName("User1_Modified");
        assertTrue(orm.isDirty(u1));
        assertFalse(orm.isDirty(u2)); // u2 决不能被 u1 污染或覆盖

        u2.setAge(99);
        assertTrue(orm.isDirty(u2));
        assertTrue(orm.getDirtyProperties(u1).contains("userName"));
        assertFalse(orm.getDirtyProperties(u1).contains("age"));
        assertTrue(orm.getDirtyProperties(u2).contains("age"));
        assertFalse(orm.getDirtyProperties(u2).contains("userName"));
    }
}
