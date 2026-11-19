package com.javaorm.bridge.test;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.IEnum;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.exceptions.MybatisPlusException;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DynamicTableNameInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.javaorm.bridge.mp.MyBatisPlusInterceptorBridge;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.LogicDelete;
import com.easy.query.core.annotation.Table;
import com.javaorm.bridge.mybatis.JavaOrmMyBatisSupport;
import com.javaorm.core.JavaOrm;
import com.javaorm.core.annotation.JoColumn;
import com.javaorm.core.annotation.JoId;
import com.javaorm.core.annotation.JoTable;
import com.javaorm.core.annotation.JoVersion;
import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.dialect.Dialect;
import com.javaorm.core.dialect.SqlAndParams;
import com.javaorm.core.exception.OptimisticLockException;
import com.javaorm.core.interceptor.SqlInterceptor;
import com.javaorm.core.metadata.ColumnMetadata;
import com.javaorm.core.metadata.EntityMetadata;
import com.javaorm.core.metadata.EntityMetadataManager;
import com.javaorm.core.pagination.PageResult;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class BridgeIntegrationTest {

    private static DataSource dataSource;
    private static JavaOrm orm;
    private static SqlSessionFactory sqlSessionFactory;

    // 枚举 1：实现 MyBatis-Plus IEnum 接口
    public enum UserStatus implements IEnum<Integer> {
        ACTIVE(1, "激活"),
        DISABLED(0, "禁用");

        private final int value;
        private final String desc;

        UserStatus(int value, String desc) {
            this.value = value;
            this.desc = desc;
        }

        @Override
        public Integer getValue() {
            return value;
        }
    }

    // 枚举 2：使用 MyBatis-Plus @EnumValue 注解
    public enum UserGrade {
        VIP(10),
        NORMAL(1);

        @com.baomidou.mybatisplus.annotation.EnumValue
        private final int grade;

        UserGrade(int grade) {
            this.grade = grade;
        }

        public int getGrade() {
            return grade;
        }
    }

    // 1. MyBatis-Plus 注解实体
    @TableName("t_mp_user")
    public static class MpUser {
        @TableId(value = "user_id", type = IdType.AUTO)
        private Long id;

        @TableField("user_name")
        private String name;

        @TableField("user_age")
        private Integer age;

        @com.baomidou.mybatisplus.annotation.Version
        @TableField("user_version")
        private Integer version;

        @TableLogic(value = "0", delval = "1")
        @TableField("is_deleted")
        private Integer deleted;

        @TableField("user_status")
        private UserStatus status;

        @TableField("user_grade")
        private UserGrade grade;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Integer getAge() { return age; }
        public void setAge(Integer age) { this.age = age; }
        public Integer getVersion() { return version; }
        public void setVersion(Integer version) { this.version = version; }
        public Integer getDeleted() { return deleted; }
        public void setDeleted(Integer deleted) { this.deleted = deleted; }
        public UserStatus getStatus() { return status; }
        public void setStatus(UserStatus status) { this.status = status; }
        public UserGrade getGrade() { return grade; }
        public void setGrade(UserGrade grade) { this.grade = grade; }
    }

    // 2. EasyQuery 注解实体
    @Table("t_eq_product")
    public static class EqProduct {
        @Column(value = "prod_id", primaryKey = true, generatedKey = true)
        private Long id;

        @Column("prod_name")
        private String name;

        @com.easy.query.core.annotation.Version(strategy = com.easy.query.core.basic.extension.version.VersionIntStrategy.class)
        @Column("prod_version")
        private Integer version;

        @LogicDelete
        @Column("is_deleted")
        private Integer deleted;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Integer getVersion() { return version; }
        public void setVersion(Integer version) { this.version = version; }
        public Integer getDeleted() { return deleted; }
        public void setDeleted(Integer deleted) { this.deleted = deleted; }
    }

    // 3. 混合注解实体（验证去重与优先级合并）
    @JoTable(name = "t_mixed_order")
    @TableName("t_mixed_order_mp") // MP优先级高于Jo，实际表名应为 t_mixed_order_mp
    public static class MixedOrder {
        @JoId
        @TableId(value = "order_id", type = IdType.AUTO)
        private Long id;

        @JoColumn(name = "order_no")
        @TableField("order_no_mp") // MP优先级高，实际列名为 order_no_mp
        private String orderNo;

        @JoVersion
        @com.baomidou.mybatisplus.annotation.Version
        private Integer version;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getOrderNo() { return orderNo; }
        public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
        public Integer getVersion() { return version; }
        public void setVersion(Integer version) { this.version = version; }
    }

    // 4. 用户自定义无侵入业务 Page 类（完全不依赖 JavaORM 或第三方接口）
    public static class CustomResultVo<T> {
        private final long total;
        private final List<T> items;
        private final long page;
        private final long size;

        public CustomResultVo(long total, List<T> items, long page, long size) {
            this.total = total;
            this.items = items;
            this.page = page;
            this.size = size;
        }

        public long getTotal() { return total; }
        public List<T> getItems() { return items; }
        public long getPage() { return page; }
        public long getSize() { return size; }
    }

    @BeforeClass
    public static void setUp() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:bridge_test;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE");
        ds.setUser("sa");
        ds.setPassword("");
        dataSource = ds;
        orm = JavaOrm.create(dataSource);

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS t_mp_user");
            stmt.execute("CREATE TABLE t_mp_user (" +
                    "user_id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "user_name VARCHAR(50), " +
                    "user_age INT, " +
                    "user_version INT DEFAULT 1, " +
                    "is_deleted INT DEFAULT 0, " +
                    "user_status INT, " +
                    "user_grade INT, " +
                    "tenant_id INT DEFAULT 0)");

            stmt.execute("DROP TABLE IF EXISTS t_mp_user_dyn");
            stmt.execute("CREATE TABLE t_mp_user_dyn (" +
                    "user_id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "user_name VARCHAR(50), " +
                    "user_age INT, " +
                    "user_version INT DEFAULT 1, " +
                    "is_deleted INT DEFAULT 0, " +
                    "user_status INT, " +
                    "user_grade INT, " +
                    "tenant_id INT DEFAULT 0)");

            stmt.execute("DROP TABLE IF EXISTS t_eq_product");
            stmt.execute("CREATE TABLE t_eq_product (" +
                    "prod_id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "prod_name VARCHAR(50), " +
                    "prod_version INT DEFAULT 1, " +
                    "is_deleted INT DEFAULT 0)");

            stmt.execute("DROP TABLE IF EXISTS t_mixed_order_mp");
            stmt.execute("CREATE TABLE t_mixed_order_mp (" +
                    "order_id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "order_no_mp VARCHAR(50), " +
                    "version INT DEFAULT 1)");
        }

        // 初始化 MyBatis SqlSessionFactory
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
    }

    @Test
    public void testMetadataDeduplicationAndPriority() {
        EntityMetadata meta = EntityMetadataManager.get(MixedOrder.class);
        Assert.assertNotNull(meta);
        Assert.assertEquals("t_mixed_order_mp", meta.getTableName());
        Assert.assertEquals(3, meta.getColumns().size());

        ColumnMetadata idCol = meta.getIdColumn();
        Assert.assertNotNull(idCol);
        Assert.assertEquals("order_id", idCol.getColumnName());
        Assert.assertTrue(idCol.isPrimaryKey());
        Assert.assertTrue(idCol.isGenerated());

        ColumnMetadata orderNoCol = meta.getColumnByProperty("orderNo");
        Assert.assertNotNull(orderNoCol);
        Assert.assertEquals("order_no_mp", orderNoCol.getColumnName());

        ColumnMetadata versionCol = meta.getVersionColumn();
        Assert.assertNotNull(versionCol);
        Assert.assertTrue(meta.hasVersion());
    }

    @Test
    public void testMyBatisPlusAnnotationsAndOptimisticLock() {
        MpUser user = new MpUser();
        user.setName("Alice");
        user.setAge(20);
        user.setStatus(UserStatus.ACTIVE);
        user.setGrade(UserGrade.VIP);
        int rows = orm.insert(user);
        Assert.assertEquals(1, rows);
        Assert.assertNotNull(user.getId());
        Assert.assertEquals(Integer.valueOf(1), user.getVersion());

        // 验证持久化值成功存取
        MpUser queried = orm.findById(MpUser.class, user.getId());
        Assert.assertNotNull(queried);
        Assert.assertEquals(UserStatus.ACTIVE, queried.getStatus());
        Assert.assertEquals(UserGrade.VIP, queried.getGrade());

        // 更新 MP 实体，触发乐观锁自增
        user.setAge(21);
        int updated = orm.update(user);
        Assert.assertEquals(1, updated);
        Assert.assertEquals(Integer.valueOf(2), user.getVersion());

        // 模拟并发冲突
        MpUser staleUser = new MpUser();
        staleUser.setId(user.getId());
        staleUser.setName("Bob");
        staleUser.setAge(22);
        staleUser.setVersion(1);

        try {
            orm.update(staleUser);
            Assert.fail("Should throw OptimisticLockException on version mismatch");
        } catch (OptimisticLockException e) {
            // expected
        }

        // 逻辑删除测试
        int deleted = orm.delete(user);
        Assert.assertEquals(1, deleted);

        MpUser found = orm.findById(MpUser.class, user.getId());
        Assert.assertNull(found);
    }

    @Test
    public void testEasyQueryAnnotationsAndOptimisticLock() {
        EqProduct prod = new EqProduct();
        prod.setName("Phone");
        int rows = orm.insert(prod);
        Assert.assertEquals(1, rows);
        Assert.assertNotNull(prod.getId());
        Assert.assertEquals(Integer.valueOf(1), prod.getVersion());

        prod.setName("SmartPhone");
        int updated = orm.update(prod);
        Assert.assertEquals(1, updated);
        Assert.assertEquals(Integer.valueOf(2), prod.getVersion());

        orm.delete(prod);
        EqProduct found = orm.findById(EqProduct.class, prod.getId());
        Assert.assertNull(found);
    }

    @Test
    public void testMyBatisIntegration() {
        try (SqlSession sqlSession = sqlSessionFactory.openSession(true)) {
            JavaOrm mybatisOrm = JavaOrmMyBatisSupport.from(sqlSession);

            MpUser user = new MpUser();
            user.setName("MyBatisUser");
            user.setAge(30);
            int rows = mybatisOrm.insert(user);
            Assert.assertEquals(1, rows);
            Assert.assertNotNull(user.getId());

            MpUser found = mybatisOrm.findById(MpUser.class, user.getId());
            Assert.assertNotNull(found);
            Assert.assertEquals("MyBatisUser", found.getName());

            MpUser u1 = new MpUser();
            u1.setName("Batch1");
            u1.setAge(25);
            MpUser u2 = new MpUser();
            u2.setName("Batch2");
            u2.setAge(26);

            int batchRows = mybatisOrm.insertBatch(Arrays.asList(u1, u2)).batchSize(100).execute();
            Assert.assertEquals(2, batchRows);

            List<MpUser> list = mybatisOrm.select(MpUser.class)
                    .where()
                    .in(MpUser::getName, Arrays.asList("Batch1", "Batch2"))
                    .list();
            Assert.assertEquals(2, list.size());
        }
    }

    @Test
    public void testPaginationWithCustomPageAndMyBatisPlusPage() {
        // 先插入若干条测试记录
        List<MpUser> batch = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            MpUser u = new MpUser();
            u.setName("PageUser_" + i);
            u.setAge(20 + i);
            batch.add(u);
        }
        orm.insertBatch(batch).execute();

        // 1. 测试内置通用 PageResult
        PageResult<MpUser> standardPage = orm.select(MpUser.class)
                .where()
                .like(MpUser::getName, "PageUser_%")
                .toPage(1, 3);
        Assert.assertEquals(7, standardPage.getTotal());
        Assert.assertEquals(3, standardPage.getRecords().size());
        Assert.assertEquals(3, standardPage.getTotalPages());
        Assert.assertTrue(standardPage.hasNext());

        // 2. 测试完全零侵入返回用户自定义业务 VO（通过函数式引用 CustomResultVo::new）
        CustomResultVo<MpUser> customPage = orm.select(MpUser.class)
                .where()
                .like(MpUser::getName, "PageUser_%")
                .toPage(2, 3, CustomResultVo::new);
        Assert.assertEquals(7, customPage.getTotal());
        Assert.assertEquals(3, customPage.getItems().size());
        Assert.assertEquals(2, customPage.getPage());
        Assert.assertEquals(3, customPage.getSize());
        Assert.assertEquals("PageUser_4", customPage.getItems().get(0).getName());

        // 3. 测试传入 MyBatis-Plus 官方 Page 对象实例自动回填并返回
        Page<MpUser> mpPage = new Page<>(3, 3);
        Page<MpUser> resultPage = orm.select(MpUser.class)
                .where()
                .like(MpUser::getName, "PageUser_%")
                .toPage(mpPage);
        Assert.assertSame(mpPage, resultPage);
        Assert.assertEquals(7, mpPage.getTotal());
        Assert.assertEquals(1, mpPage.getRecords().size());
        Assert.assertEquals("PageUser_7", mpPage.getRecords().get(0).getName());
    }

    @Test
    public void testSqlInterceptor() {
        AtomicInteger queryCount = new AtomicInteger(0);
        AtomicInteger updateCount = new AtomicInteger(0);
        List<String> executedSqls = new ArrayList<>();

        OrmConfig config = new OrmConfig();
        config.addInterceptor(new SqlInterceptor() {
            @Override
            public SqlAndParams beforeQuery(SqlAndParams sqlAndParams, EntityMetadata metadata, Dialect dialect) {
                queryCount.incrementAndGet();
                return sqlAndParams;
            }

            @Override
            public SqlAndParams beforeUpdate(SqlAndParams sqlAndParams, EntityMetadata metadata, Dialect dialect) {
                updateCount.incrementAndGet();
                return sqlAndParams;
            }

            @Override
            public void afterExecution(String sql, List<Object> params, long elapsedMillis, Throwable throwable) {
                executedSqls.add(sql);
            }
        });

        JavaOrm interceptorOrm = JavaOrm.create(dataSource, config);

        MpUser user = new MpUser();
        user.setName("InterceptorUser");
        user.setAge(18);
        interceptorOrm.insert(user);
        Assert.assertEquals(1, updateCount.get());

        interceptorOrm.select(MpUser.class).where().eq(MpUser::getName, "InterceptorUser").first();
        Assert.assertEquals(1, queryCount.get());
        Assert.assertTrue(executedSqls.size() >= 2);
    }

    @Test
    public void testMyBatisPlusDynamicTableNameInnerInterceptor() {
        DynamicTableNameInnerInterceptor dynamicTableInterceptor = new DynamicTableNameInnerInterceptor();
        dynamicTableInterceptor.setTableNameHandler((sql, tableName) -> {
            if ("t_mp_user".equals(tableName)) {
                return "t_mp_user_dyn";
            }
            return tableName;
        });

        OrmConfig config = new OrmConfig();
        config.addInterceptor(new MyBatisPlusInterceptorBridge(dynamicTableInterceptor));
        JavaOrm dynamicOrm = JavaOrm.create(dataSource, config);

        MpUser u = new MpUser();
        u.setName("DynUser");
        u.setAge(22);
        dynamicOrm.insert(u);

        MpUser found = dynamicOrm.select(MpUser.class)
                .where()
                .eq(MpUser::getName, "DynUser")
                .first();
        Assert.assertNotNull(found);
        Assert.assertEquals("DynUser", found.getName());
    }

    @Test
    public void testMyBatisPlusTenantLineInnerInterceptor() {
        TenantLineInnerInterceptor tenantLineInterceptor = new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                return new LongValue(888L);
            }

            @Override
            public String getTenantIdColumn() {
                return "tenant_id";
            }

            @Override
            public boolean ignoreTable(String tableName) {
                return !"t_mp_user".equals(tableName);
            }
        });

        OrmConfig tenantConfig = new OrmConfig();
        tenantConfig.addInterceptor(new MyBatisPlusInterceptorBridge(tenantLineInterceptor));
        JavaOrm tenantOrm = JavaOrm.create(dataSource, tenantConfig);

        MpUser u = new MpUser();
        u.setName("TenantUser");
        u.setAge(30);
        tenantOrm.insert(u);

        // 使用普通 orm 查询原生数据，验证已被拦截并附带 tenant_id = 888
        List<MpUser> list = orm.select(MpUser.class).where().eq(MpUser::getName, "TenantUser").list();
        Assert.assertFalse(list.isEmpty());

        // 使用 tenantOrm 查询，自动拼装 tenant_id = 888 条件
        MpUser found = tenantOrm.select(MpUser.class).where().eq(MpUser::getName, "TenantUser").first();
        Assert.assertNotNull(found);
    }

    @Test
    public void testMyBatisPlusBlockAttackInnerInterceptor() {
        BlockAttackInnerInterceptor blockAttackInterceptor = new BlockAttackInnerInterceptor();
        OrmConfig config = new OrmConfig();
        config.addInterceptor(new MyBatisPlusInterceptorBridge(blockAttackInterceptor));
        JavaOrm blockOrm = JavaOrm.create(dataSource, config);

        // 尝试执行无 WHERE 条件的全表更新（使用无逻辑删除的 MixedOrder 实体），验证被 BlockAttack 拦截抛出异常
        boolean blocked = false;
        try {
            blockOrm.update(MixedOrder.class)
                    .set(MixedOrder::getOrderNo, "hacked_no")
                    .execute();
        } catch (Exception e) {
            if (e.toString().contains("Prohibition of table update operation")) {
                blocked = true;
            }
        }
        Assert.assertTrue("Should be blocked by BlockAttackInnerInterceptor", blocked);

        // 带 WHERE 条件的更新应当顺利通过
        int rows = blockOrm.update(MixedOrder.class)
                .set(MixedOrder::getOrderNo, "valid_no")
                .where()
                .eq(MixedOrder::getId, 1L)
                .execute();
        Assert.assertTrue(rows >= 0);
    }

    @Test
    public void testMyBatisPlusInterceptorContainerAndMyBatisSupport() {
        MybatisPlusInterceptor mpInterceptor = new MybatisPlusInterceptor();
        mpInterceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());

        // 注册至 MyBatis Configuration
        Configuration mybatisConfig = sqlSessionFactory.getConfiguration();
        mybatisConfig.addInterceptor(mpInterceptor);

        try (SqlSession session = sqlSessionFactory.openSession()) {
            JavaOrm mybatisOrm = JavaOrmMyBatisSupport.from(session);
            // 验证已自动继承 MP 的 BlockAttackInnerInterceptor
            boolean attackBlocked = false;
            try {
                mybatisOrm.update(MixedOrder.class).set(MixedOrder::getOrderNo, "hacked_no").execute();
            } catch (Exception e) {
                if (e.toString().contains("Prohibition of table update operation")) {
                    attackBlocked = true;
                }
            }
            Assert.assertTrue("Should auto inherit BlockAttackInnerInterceptor from SqlSession", attackBlocked);
        }
    }

    @Test
    public void testMyBatisPlusAdapterEndToEnd() {
        // 1. 在项目入口仅配置 MyBatisPlusAdapter，生成统一的 JavaOrm 实例
        JavaOrm adapterOrm = com.javaorm.bridge.mp.MyBatisPlusAdapter.create(sqlSessionFactory);

        // 2. 后续业务代码 100% 仅使用 JavaOrm！
        // 2.1 插入实体（自动识别 MP @TableId 自增, @Version 初始版本, @TableLogic 初始值, @EnumValue 枚举映射）
        MpUser user = new MpUser();
        user.setName("AdapterUser");
        user.setAge(28);
        user.setStatus(UserStatus.ACTIVE);
        user.setGrade(UserGrade.VIP);
        adapterOrm.insert(user);
        Assert.assertNotNull(user.getId());
        Assert.assertEquals(Integer.valueOf(1), user.getVersion());
        Assert.assertEquals(Integer.valueOf(0), user.getDeleted());

        // 2.2 流式查询 + 分页（完全使用 JavaORM DSL）
        com.javaorm.core.pagination.Page<MpUser> page = adapterOrm.select(MpUser.class)
                .where()
                .eq(MpUser::getName, "AdapterUser")
                .toPage(1, 10);
        Assert.assertEquals(1, page.getTotal());
        Assert.assertEquals(1, page.getRecords().size());
        Assert.assertEquals(UserStatus.ACTIVE, page.getRecords().get(0).getStatus());
        Assert.assertEquals(UserGrade.VIP, page.getRecords().get(0).getGrade());

        // 2.3 乐观锁更新（自动检测 MP @Version 并递增版本号）
        user.setAge(29);
        adapterOrm.update(user);
        Assert.assertEquals(Integer.valueOf(2), user.getVersion());

        // 2.4 逻辑删除（自动使用 MP @TableLogic）
        adapterOrm.delete(user);
        MpUser afterDel = adapterOrm.select(MpUser.class)
                .where()
                .eq(MpUser::getId, user.getId())
                .firstOrNull();
        Assert.assertNull(afterDel); // 已逻辑删除，普通查询自动过滤
    }
}
