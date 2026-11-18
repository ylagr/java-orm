package com.javaorm.bridge.test;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.LogicDelete;
import com.easy.query.core.annotation.Table;
import com.javaorm.bridge.mybatis.JavaOrmMyBatisSupport;
import com.javaorm.core.JavaOrm;
import com.javaorm.core.annotation.JoColumn;
import com.javaorm.core.annotation.JoId;
import com.javaorm.core.annotation.JoTable;
import com.javaorm.core.annotation.JoVersion;
import com.javaorm.core.exception.OptimisticLockException;
import com.javaorm.core.metadata.ColumnMetadata;
import com.javaorm.core.metadata.EntityMetadata;
import com.javaorm.core.metadata.EntityMetadataManager;
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
import java.util.Arrays;
import java.util.List;

public class BridgeIntegrationTest {

    private static DataSource dataSource;
    private static JavaOrm orm;
    private static SqlSessionFactory sqlSessionFactory;

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
                    "is_deleted INT DEFAULT 0)");

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
        // 验证混合实体的元数据去重与优先级合并
        EntityMetadata meta = EntityMetadataManager.get(MixedOrder.class);
        Assert.assertNotNull(meta);
        // 表名由更高优先级的 MyBatis-Plus 注解生效
        Assert.assertEquals("t_mixed_order_mp", meta.getTableName());

        // 字段列表去重：必须只有 3 个字段 (id, orderNo, version)，不能因为两个注解产生两份
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
        // 插入 MP 实体
        MpUser user = new MpUser();
        user.setName("Alice");
        user.setAge(20);
        int rows = orm.insert(user);
        Assert.assertEquals(1, rows);
        Assert.assertNotNull(user.getId());
        Assert.assertEquals(Integer.valueOf(1), user.getVersion());

        // 更新 MP 实体，触发乐观锁自增
        user.setAge(21);
        int updated = orm.update(user);
        Assert.assertEquals(1, updated);
        Assert.assertEquals(Integer.valueOf(2), user.getVersion());

        // 模拟并发更新冲突：使用陈旧的版本号更新
        MpUser staleUser = new MpUser();
        staleUser.setId(user.getId());
        staleUser.setName("Bob");
        staleUser.setAge(22);
        staleUser.setVersion(1); // 此时数据库版本已经是 2

        try {
            orm.update(staleUser);
            Assert.fail("Should throw OptimisticLockException on version mismatch");
        } catch (OptimisticLockException e) {
            // expected
        }

        // 逻辑删除测试
        int deleted = orm.delete(user);
        Assert.assertEquals(1, deleted);

        // 逻辑删除后查询应该查不到
        MpUser found = orm.findById(MpUser.class, user.getId());
        Assert.assertNull(found);
    }

    @Test
    public void testEasyQueryAnnotationsAndOptimisticLock() {
        // 插入 EasyQuery 实体
        EqProduct prod = new EqProduct();
        prod.setName("Phone");
        int rows = orm.insert(prod);
        Assert.assertEquals(1, rows);
        Assert.assertNotNull(prod.getId());
        Assert.assertEquals(Integer.valueOf(1), prod.getVersion());

        // 更新
        prod.setName("SmartPhone");
        int updated = orm.update(prod);
        Assert.assertEquals(1, updated);
        Assert.assertEquals(Integer.valueOf(2), prod.getVersion());

        // 逻辑删除
        orm.delete(prod);
        EqProduct found = orm.findById(EqProduct.class, prod.getId());
        Assert.assertNull(found);
    }

    @Test
    public void testMyBatisIntegration() {
        // 通过 SqlSession 开启 JavaORM 实例 (autoCommit = true)
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

            // 批处理插入测试
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
}
