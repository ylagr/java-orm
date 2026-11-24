package com.javaorm.bridge.test;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.IEnum;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DynamicTableNameInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.javaorm.bridge.mp.MyBatisPlusAdapter;
import com.javaorm.bridge.mp.MyBatisPlusInterceptorBridge;
import com.javaorm.bridge.mp.MyBatisPlusMetaObjectHandlerBridge;
import com.javaorm.bridge.mybatis.JavaOrmMyBatisSupport;
import com.javaorm.core.JavaOrm;
import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.dialect.Dialect;
import com.javaorm.core.dialect.SqlAndParams;
import com.javaorm.core.exception.OptimisticLockException;
import com.javaorm.core.interceptor.SqlInterceptor;
import com.javaorm.core.metadata.ColumnMetadata;
import com.javaorm.core.metadata.EntityMetadata;
import com.javaorm.core.metadata.EntityMetadataManager;
import com.javaorm.core.pagination.PageResult;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
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

public class MyBatisPlusBridgeTest {

    private static DataSource dataSource;
    private static JavaOrm orm;
    private static SqlSessionFactory sqlSessionFactory;

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

    @TableName("t_mp_auto_user")
    public static class MpAutoUser {
        @TableId(type = IdType.AUTO)
        private Long id;

        private String name;

        @TableField(fill = FieldFill.INSERT)
        private String createBy;

        @TableField(fill = FieldFill.INSERT_UPDATE)
        private String updateBy;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getCreateBy() { return createBy; }
        public void setCreateBy(String createBy) { this.createBy = createBy; }
        public String getUpdateBy() { return updateBy; }
        public void setUpdateBy(String updateBy) { this.updateBy = updateBy; }
    }

    @TableName("t_mp_order")
    public static class MpOrder {
        @TableId(value = "order_id", type = IdType.AUTO)
        private Long id;

        @TableField("order_no")
        private String orderNo;

        @com.baomidou.mybatisplus.annotation.Version
        private Integer version;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getOrderNo() { return orderNo; }
        public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
        public Integer getVersion() { return version; }
        public void setVersion(Integer version) { this.version = version; }
    }

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
        ds.setURL("jdbc:h2:mem:mp_bridge_test;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE");
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

            stmt.execute("DROP TABLE IF EXISTS t_mp_auto_user");
            stmt.execute("CREATE TABLE t_mp_auto_user (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "name VARCHAR(50), " +
                    "create_by VARCHAR(50), " +
                    "update_by VARCHAR(50))");

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

            stmt.execute("DROP TABLE IF EXISTS t_mp_order");
            stmt.execute("CREATE TABLE t_mp_order (" +
                    "order_id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "order_no VARCHAR(50), " +
                    "version INT DEFAULT 1)");

            stmt.execute("DROP TABLE IF EXISTS t_mp_complex");
            stmt.execute("CREATE TABLE t_mp_complex (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "tags VARCHAR(255), " +
                    "simple_status INT, " +
                    "pure_enum VARCHAR(50), " +
                    "deleted INT DEFAULT 0)");
        }

        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
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

        MpUser queried = orm.findById(MpUser.class, user.getId());
        Assert.assertNotNull(queried);
        Assert.assertEquals(UserStatus.ACTIVE, queried.getStatus());
        Assert.assertEquals(UserGrade.VIP, queried.getGrade());

        user.setAge(21);
        int updated = orm.update(user);
        Assert.assertEquals(1, updated);
        Assert.assertEquals(Integer.valueOf(2), user.getVersion());

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

        int deleted = orm.delete(user);
        Assert.assertEquals(1, deleted);

        MpUser found = orm.findById(MpUser.class, user.getId());
        Assert.assertNull(found);
    }

    @Test
    public void testPaginationWithCustomPageAndMyBatisPlusPage() {
        List<MpUser> batch = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            MpUser u = new MpUser();
            u.setName("PageUser_" + i);
            u.setAge(20 + i);
            batch.add(u);
        }
        orm.insertBatch(batch).execute();

        PageResult<MpUser> standardPage = orm.select(MpUser.class)
                .where()
                .like(MpUser::getName, "PageUser_%")
                .toPage(1, 3);
        Assert.assertEquals(7, standardPage.getTotal());
        Assert.assertEquals(3, standardPage.getRecords().size());
        Assert.assertEquals(3, standardPage.getTotalPages());
        Assert.assertTrue(standardPage.hasNext());

        CustomResultVo<MpUser> customPage = orm.select(MpUser.class)
                .where()
                .like(MpUser::getName, "PageUser_%")
                .toPage(2, 3, CustomResultVo::new);
        Assert.assertEquals(7, customPage.getTotal());
        Assert.assertEquals(3, customPage.getItems().size());
        Assert.assertEquals(2, customPage.getPage());
        Assert.assertEquals(3, customPage.getSize());
        Assert.assertEquals("PageUser_4", customPage.getItems().get(0).getName());

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

        List<MpUser> list = orm.select(MpUser.class).where().eq(MpUser::getName, "TenantUser").list();
        Assert.assertFalse(list.isEmpty());

        MpUser found = tenantOrm.select(MpUser.class).where().eq(MpUser::getName, "TenantUser").first();
        Assert.assertNotNull(found);
    }

    @Test
    public void testMyBatisPlusBlockAttackInnerInterceptor() {
        BlockAttackInnerInterceptor blockAttackInterceptor = new BlockAttackInnerInterceptor();
        OrmConfig config = new OrmConfig();
        config.addInterceptor(new MyBatisPlusInterceptorBridge(blockAttackInterceptor));
        JavaOrm blockOrm = JavaOrm.create(dataSource, config);

        boolean blocked = false;
        try {
            blockOrm.update(MpOrder.class)
                    .set(MpOrder::getOrderNo, "hacked_no")
                    .execute();
        } catch (Exception e) {
            if (e.toString().contains("Prohibition of table update operation")) {
                blocked = true;
            }
        }
        Assert.assertTrue("Should be blocked by BlockAttackInnerInterceptor", blocked);

        int rows = blockOrm.update(MpOrder.class)
                .set(MpOrder::getOrderNo, "valid_no")
                .where()
                .eq(MpOrder::getId, 1L)
                .execute();
        Assert.assertTrue(rows >= 0);
    }

    @Test
    public void testMyBatisPlusInterceptorContainerAndMyBatisSupport() {
        MybatisPlusInterceptor mpInterceptor = new MybatisPlusInterceptor();
        mpInterceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());

        Configuration mybatisConfig = sqlSessionFactory.getConfiguration();
        mybatisConfig.addInterceptor(mpInterceptor);

        try (SqlSession session = sqlSessionFactory.openSession()) {
            JavaOrm mybatisOrm = JavaOrmMyBatisSupport.from(session);
            boolean attackBlocked = false;
            try {
                mybatisOrm.update(MpOrder.class).set(MpOrder::getOrderNo, "hacked_no").execute();
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
        JavaOrm adapterOrm = MyBatisPlusAdapter.create(sqlSessionFactory);

        MpUser user = new MpUser();
        user.setName("AdapterUser");
        user.setAge(28);
        user.setStatus(UserStatus.ACTIVE);
        user.setGrade(UserGrade.VIP);
        adapterOrm.insert(user);
        Assert.assertNotNull(user.getId());
        Assert.assertEquals(Integer.valueOf(1), user.getVersion());
        Assert.assertEquals(Integer.valueOf(0), user.getDeleted());

        com.javaorm.core.pagination.Page<MpUser> page = adapterOrm.select(MpUser.class)
                .where()
                .eq(MpUser::getName, "AdapterUser")
                .toPage(1, 10);
        Assert.assertEquals(1, page.getTotal());
        Assert.assertEquals(1, page.getRecords().size());
        Assert.assertEquals(UserStatus.ACTIVE, page.getRecords().get(0).getStatus());
        Assert.assertEquals(UserGrade.VIP, page.getRecords().get(0).getGrade());

        user.setAge(29);
        adapterOrm.update(user);
        Assert.assertEquals(Integer.valueOf(2), user.getVersion());

        adapterOrm.delete(user);
        MpUser afterDel = adapterOrm.select(MpUser.class)
                .where()
                .eq(MpUser::getId, user.getId())
                .firstOrNull();
        Assert.assertNull(afterDel);
    }

    @Test
    public void testMyBatisPlusMetaObjectHandlerBridge() {
        MetaObjectHandler handler = new MetaObjectHandler() {
            @Override
            public void insertFill(org.apache.ibatis.reflection.MetaObject metaObject) {
                this.strictInsertFill(metaObject, "createBy", String.class, "admin_insert");
                this.strictInsertFill(metaObject, "updateBy", String.class, "admin_insert");
            }

            @Override
            public void updateFill(org.apache.ibatis.reflection.MetaObject metaObject) {
                this.setFieldValByName("updateBy", "admin_update", metaObject);
            }
        };

        OrmConfig config = new OrmConfig()
                .autoFillHandler(new MyBatisPlusMetaObjectHandlerBridge(handler));
        JavaOrm fillOrm = JavaOrm.create(dataSource, config);

        MpAutoUser user = new MpAutoUser();
        user.setName("Jack");
        fillOrm.insert(user);

        Assert.assertNotNull(user.getId());
        Assert.assertEquals("admin_insert", user.getCreateBy());
        Assert.assertEquals("admin_insert", user.getUpdateBy());

        user.setName("Jackson");
        fillOrm.update(user);
        Assert.assertEquals("admin_update", user.getUpdateBy());
        Assert.assertEquals("admin_insert", user.getCreateBy());
    }

    public enum SimpleStatus {
        OFF(0), ON(1);
        private final int code;
        SimpleStatus(int code) { this.code = code; }
        public int getCode() { return code; }
    }

    public enum PureEnum {
        FOO("foo"), BAR("bar");

        @com.baomidou.mybatisplus.annotation.EnumValue
        private final String val;

        PureEnum(String val) { this.val = val; }
        public String getVal() { return val; }
    }

    public static class ListStringTypeHandler extends org.apache.ibatis.type.BaseTypeHandler<List<String>> {
        @Override
        public void setNonNullParameter(java.sql.PreparedStatement ps, int i, List<String> parameter, org.apache.ibatis.type.JdbcType jdbcType) throws java.sql.SQLException {
            StringBuilder sb = new StringBuilder();
            for (int k = 0; k < parameter.size(); k++) {
                if (k > 0) sb.append(",");
                sb.append(parameter.get(k));
            }
            ps.setString(i, sb.toString());
        }

        @Override
        public List<String> getNullableResult(java.sql.ResultSet rs, String columnName) throws java.sql.SQLException {
            return parse(rs.getString(columnName));
        }

        @Override
        public List<String> getNullableResult(java.sql.ResultSet rs, int columnIndex) throws java.sql.SQLException {
            return parse(rs.getString(columnIndex));
        }

        @Override
        public List<String> getNullableResult(java.sql.CallableStatement cs, int columnIndex) throws java.sql.SQLException {
            return parse(cs.getString(columnIndex));
        }

        private List<String> parse(String val) {
            if (val == null || val.trim().isEmpty()) return new ArrayList<>();
            return Arrays.asList(val.split(","));
        }
    }

    @TableName("t_mp_complex")
    public static class MpComplexEntity {
        @TableId(type = IdType.AUTO)
        private Long id;

        @TableField(value = "tags", typeHandler = ListStringTypeHandler.class)
        private List<String> tags;

        @TableField("simple_status")
        private SimpleStatus simpleStatus;

        @TableField(value = "pure_enum", typeHandler = com.baomidou.mybatisplus.core.handlers.MybatisEnumTypeHandler.class)
        private PureEnum pureEnum;

        @TableLogic
        private Integer deleted;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public List<String> getTags() { return tags; }
        public void setTags(List<String> tags) { this.tags = tags; }
        public SimpleStatus getSimpleStatus() { return simpleStatus; }
        public void setSimpleStatus(SimpleStatus simpleStatus) { this.simpleStatus = simpleStatus; }
        public PureEnum getPureEnum() { return pureEnum; }
        public void setPureEnum(PureEnum pureEnum) { this.pureEnum = pureEnum; }
        public Integer getDeleted() { return deleted; }
        public void setDeleted(Integer deleted) { this.deleted = deleted; }
    }

    @Test
    public void testTableFieldTypeHandlerAndEnumSupport() {
        MpComplexEntity entity = new MpComplexEntity();
        entity.setTags(Arrays.asList("java", "orm", "mybatis-plus"));
        entity.setSimpleStatus(SimpleStatus.ON);
        entity.setPureEnum(PureEnum.BAR);

        int rows = orm.insert(entity);
        Assert.assertEquals(1, rows);
        Assert.assertNotNull(entity.getId());

        MpComplexEntity found = orm.select(MpComplexEntity.class)
                .where()
                .eq(MpComplexEntity::getId, entity.getId())
                .first();

        Assert.assertNotNull(found);
        Assert.assertEquals(3, found.getTags().size());
        Assert.assertEquals("java", found.getTags().get(0));
        Assert.assertEquals("orm", found.getTags().get(1));
        Assert.assertEquals("mybatis-plus", found.getTags().get(2));
        Assert.assertEquals(SimpleStatus.ON, found.getSimpleStatus());
        Assert.assertEquals(PureEnum.BAR, found.getPureEnum());

        // 测试逻辑删除（默认 0 未删，1 已删）
        orm.delete(found);
        MpComplexEntity afterDelete = orm.select(MpComplexEntity.class)
                .where()
                .eq(MpComplexEntity::getId, entity.getId())
                .first();
        Assert.assertNull(afterDelete);
    }
}
