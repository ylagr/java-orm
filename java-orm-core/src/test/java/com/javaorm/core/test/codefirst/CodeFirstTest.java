package com.javaorm.core.test.codefirst;

import com.javaorm.core.JavaOrm;
import com.javaorm.core.annotation.GenerationType;
import com.javaorm.core.annotation.JoColumn;
import com.javaorm.core.annotation.JoGeneratedValue;
import com.javaorm.core.annotation.JoId;
import com.javaorm.core.annotation.JoIndex;
import com.javaorm.core.annotation.JoTable;
import com.javaorm.core.codefirst.CodeFirstMode;
import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.dialect.H2Dialect;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import com.javaorm.core.codefirst.CodeFirstAlterMode;
import com.javaorm.core.codefirst.PhysicalColumnDesc;
import com.javaorm.core.codefirst.SchemaInspector;

/**
 * Code-First 架构同步全面测试套件：
 * 验证对未创建表的自动生成、已有表字段增量补齐、开关模式控制以及 DDL 生成与执行。
 */
public class CodeFirstTest {

    private JdbcDataSource dataSource;

    @JoTable(name = "t_cf_order")
    public static class OrderEntity {
        @JoId
        @JoGeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @JoColumn(length = 64, nullable = false, comment = "订单流水号")
        private String orderNo;

        @JoColumn(precision = 12, scale = 2)
        private BigDecimal totalAmount;

        @JoColumn(nullable = false, defaultValue = "1")
        private Integer status;

        private Boolean paid;

        private LocalDateTime createTime;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getOrderNo() {
            return orderNo;
        }

        public void setOrderNo(String orderNo) {
            this.orderNo = orderNo;
        }

        public BigDecimal getTotalAmount() {
            return totalAmount;
        }

        public void setTotalAmount(BigDecimal totalAmount) {
            this.totalAmount = totalAmount;
        }

        public Integer getStatus() {
            return status;
        }

        public void setStatus(Integer status) {
            this.status = status;
        }

        public Boolean getPaid() {
            return paid;
        }

        public void setPaid(Boolean paid) {
            this.paid = paid;
        }

        public LocalDateTime getCreateTime() {
            return createTime;
        }

        public void setCreateTime(LocalDateTime createTime) {
            this.createTime = createTime;
        }
    }

    /**
     * 演示实体演进模型：模拟业务迭代升级，新增了 age, email, nickname 等字段
     */
    @JoTable(name = "t_cf_user")
    public static class UserEntity {
        @JoId
        private Long id;

        private String username;

        // 实体演进新增字段
        @JoColumn(length = 128)
        private String email;

        private Integer age;

        @JoColumn(defaultValue = "'default_nick'")
        private String nickname;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public Integer getAge() {
            return age;
        }

        public void setAge(Integer age) {
            this.age = age;
        }

        public String getNickname() {
            return nickname;
        }

        public void setNickname(String nickname) {
            this.nickname = nickname;
        }
    }

    /**
     * 演示实体演进模型 V2：
     * 1. username 字段长度从原本的 50 扩容变更为 150
     * 2. nickname 字段默认值从原本的 'default_nick' 变更为 'vip_user'
     */
    @JoTable(name = "t_cf_user")
    public static class UserEntityV2 {
        @JoId
        private Long id;

        // 长度从 50 变更至 150
        @JoColumn(length = 150)
        private String username;

        @JoColumn(length = 128)
        private String email;

        private Integer age;

        // 默认值变更至 'vip_user'
        @JoColumn(defaultValue = "'vip_user'")
        private String nickname;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public Integer getAge() {
            return age;
        }

        public void setAge(Integer age) {
            this.age = age;
        }

        public String getNickname() {
            return nickname;
        }

        public void setNickname(String nickname) {
            this.nickname = nickname;
        }
    }

    @Before
    public void setup() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:code_first_test;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        this.dataSource = ds;

        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {
            // 清理测试表
            stmt.execute("DROP TABLE IF EXISTS \"t_cf_order\"");
            stmt.execute("DROP TABLE IF EXISTS \"t_cf_user\"");
            stmt.execute("DROP TABLE IF EXISTS \"t_cf_article\"");

            // 预先创建 t_cf_user 表，但故意缺少 email, age, nickname 字段
            stmt.execute("CREATE TABLE \"t_cf_user\" (\"id\" BIGINT PRIMARY KEY, \"username\" VARCHAR(50))");
            stmt.execute("INSERT INTO \"t_cf_user\" VALUES (1, 'alice')");
        }
    }

    /**
     * 演示包含类级别多列复合索引与字段级别唯一索引的实体模型
     */
    @JoTable(name = "t_cf_article")
    @JoIndex(name = "idx_art_status_time", columns = {"status", "create_time"})
    public static class IndexedArticleEntity {
        @JoId
        @JoGeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @JoIndex(name = "uk_art_code", unique = true)
        @JoColumn(length = 64, nullable = false)
        private String articleCode;

        private Integer status;

        private LocalDateTime createTime;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getArticleCode() {
            return articleCode;
        }

        public void setArticleCode(String articleCode) {
            this.articleCode = articleCode;
        }

        public Integer getStatus() {
            return status;
        }

        public void setStatus(Integer status) {
            this.status = status;
        }

        public LocalDateTime getCreateTime() {
            return createTime;
        }

        public void setCreateTime(LocalDateTime createTime) {
            this.createTime = createTime;
        }
    }

    /**
     * 1. 测试未建表时自动生成建表语句并执行（CREATE TABLE）
     */
    @Test
    public void testAutoCreateTable() throws Exception {
        OrmConfig config = new OrmConfig()
                .setCodeFirstMode(CodeFirstMode.UPDATE)
                .setPrintSql(true);
        JavaOrm orm = JavaOrm.create(dataSource, new H2Dialect(), config);

        // 验证表在同步前不存在
        try (Connection conn = dataSource.getConnection()) {
            Assert.assertFalse(com.javaorm.core.codefirst.SchemaInspector.getExistingTables(conn, null).contains("t_cf_order"));
        }

        // 执行表结构同步
        orm.codeFirst().syncTable(OrderEntity.class);

        // 验证表已成功创建
        try (Connection conn = dataSource.getConnection()) {
            Assert.assertTrue("t_cf_order 表应该被自动创建",
                    com.javaorm.core.codefirst.SchemaInspector.getExistingTables(conn, null).contains("t_cf_order"));
        }

        // 验证新创建的表可以正常插入与查询
        OrderEntity order = new OrderEntity();
        order.setOrderNo("ORD-2026-001");
        order.setTotalAmount(new BigDecimal("99.90"));
        order.setStatus(1);
        order.setPaid(true);
        order.setCreateTime(LocalDateTime.now());
        orm.insert(order);

        Assert.assertNotNull(order.getId());
        OrderEntity loaded = orm.select(OrderEntity.class).where().eq(OrderEntity::getOrderNo, "ORD-2026-001").first();
        Assert.assertNotNull(loaded);
        Assert.assertEquals("ORD-2026-001", loaded.getOrderNo());
        Assert.assertTrue(loaded.getPaid());
    }

    /**
     * 2. 测试已有表自动检测并增量补齐缺失的字段（ALTER TABLE ADD COLUMN）
     */
    @Test
    public void testAutoAddMissingColumns() throws Exception {
        OrmConfig config = new OrmConfig()
                .setCodeFirstMode(CodeFirstMode.UPDATE)
                .setPrintSql(true);
        JavaOrm orm = JavaOrm.create(dataSource, new H2Dialect(), config);

        // 检查同步前，t_cf_user 缺少 email, age 字段
        try (Connection conn = dataSource.getConnection()) {
            Assert.assertFalse("同步前不应存在 EMAIL 字段",
                    com.javaorm.core.codefirst.SchemaInspector.getExistingColumns(conn, null, "t_cf_user").contains("email"));
        }

        // 同步 User 表结构
        orm.codeFirst().syncTable(UserEntity.class);

        // 验证缺失的字段已被自动补齐
        try (Connection conn = dataSource.getConnection()) {
            Assert.assertTrue("同步后应该已成功补齐 EMAIL 字段",
                    com.javaorm.core.codefirst.SchemaInspector.getExistingColumns(conn, null, "t_cf_user").contains("email"));
            Assert.assertTrue("同步后应该已成功补齐 AGE 字段",
                    com.javaorm.core.codefirst.SchemaInspector.getExistingColumns(conn, null, "t_cf_user").contains("age"));
        }

        // 验证已有记录数据安全完好
        UserEntity alice = orm.select(UserEntity.class).where().eq(UserEntity::getId, 1L).first();
        Assert.assertNotNull(alice);
        Assert.assertEquals("alice", alice.getUsername());

        // 验证新增字段可以正常更新并读取
        orm.sql(e -> {
            UserEntity u = e.table(UserEntity.class);
            e.update(u)
                    .set(u.getEmail(), "alice@example.com")
                    .set(u.getAge(), 25)
                    .where().eq(u.getId(), 1L);
        }).exec();

        UserEntity updatedAlice = orm.select(UserEntity.class).where().eq(UserEntity::getId, 1L).first();
        Assert.assertEquals("alice@example.com", updatedAlice.getEmail());
        Assert.assertEquals(Integer.valueOf(25), updatedAlice.getAge());
    }

    /**
     * 3. 测试 DDL 生成模式（仅生成 SQL 语句，不执行）
     */
    @Test
    public void testGenerateDdlOnly() {
        OrmConfig config = new OrmConfig().setCodeFirstMode(CodeFirstMode.UPDATE);
        JavaOrm orm = JavaOrm.create(dataSource, new H2Dialect(), config);

        List<String> ddls = orm.codeFirst().generateDdl(OrderEntity.class, UserEntity.class);
        Assert.assertNotNull(ddls);
        Assert.assertTrue(ddls.size() >= 2);

        // 应该包含 t_cf_order 的 CREATE TABLE
        boolean hasCreateTable = ddls.stream().anyMatch(sql -> sql.toUpperCase().contains("CREATE TABLE") && sql.toUpperCase().contains("T_CF_ORDER"));
        Assert.assertTrue("DDL 列表中应包含 t_cf_order 的 CREATE TABLE", hasCreateTable);

        // 应该包含 t_cf_user 的 ALTER TABLE ADD COLUMN
        boolean hasAddColumn = ddls.stream().anyMatch(sql -> sql.toUpperCase().contains("ALTER TABLE") && sql.toUpperCase().contains("ADD COLUMN"));
        Assert.assertTrue("DDL 列表中应包含 t_cf_user 的 ALTER TABLE ADD COLUMN", hasAddColumn);
    }

    /**
     * 4. 测试开关模式：NONE 模式下完全不执行任何同步
     */
    @Test
    public void testCodeFirstModeNone() throws Exception {
        OrmConfig config = new OrmConfig()
                .setCodeFirstMode(CodeFirstMode.NONE)
                .addCodeFirstEntities(OrderEntity.class);
        // 初始化客户端，由于配置为 NONE，不自动建表
        JavaOrm orm = JavaOrm.create(dataSource, new H2Dialect(), config);

        // 验证未建表
        try (Connection conn = dataSource.getConnection();
             ResultSet rs = conn.getMetaData().getTables(null, null, "T_CF_ORDER", null)) {
            Assert.assertFalse(rs.next());
        }
    }

    /**
     * 5. 测试启动自动同步（在 OrmConfig 中配置实体与 UPDATE 模式）
     */
    @Test
    public void testAutoSyncOnStartup() throws Exception {
        OrmConfig config = new OrmConfig()
                .setCodeFirstMode(CodeFirstMode.UPDATE)
                .addCodeFirstEntities(OrderEntity.class, UserEntity.class);

        // 创建 JavaOrm 时自动触发 syncAll()
        JavaOrm orm = JavaOrm.create(dataSource, new H2Dialect(), config);

        // 验证 t_cf_order 已被自动创建
        try (Connection conn = dataSource.getConnection()) {
            Assert.assertTrue(com.javaorm.core.codefirst.SchemaInspector.getExistingTables(conn, null).contains("t_cf_order"));
        }

        // 验证 t_cf_user 的新增列已被自动补齐
        try (Connection conn = dataSource.getConnection()) {
            Assert.assertTrue(com.javaorm.core.codefirst.SchemaInspector.getExistingColumns(conn, null, "t_cf_user").contains("email"));
        }
    }

    /**
     * 6. 测试 UPDATE 模式下已有字段的长度变更与默认值变更受 CodeFirstAlterMode 细粒度位掩码安全控制
     */
    @Test
    public void testAlterColumnLengthAndDefaultValue() throws Exception {
        OrmConfig config = new OrmConfig()
                .setCodeFirstMode(CodeFirstMode.UPDATE)
                .setPrintSql(true);
        JavaOrm orm = JavaOrm.create(dataSource, new H2Dialect(), config);

        // 1. 验证初始表结构：t_cf_user 的 username 长度为 50
        try (Connection conn = dataSource.getConnection()) {
            Map<String, PhysicalColumnDesc> details = SchemaInspector.getExistingColumnDetails(conn, null, "t_cf_user");
            Assert.assertEquals(50, details.get("username").getColumnSize());
        }

        // 2. 默认模式下（CodeFirstAlterMode.NONE），只增量补齐缺失字段，绝不修改已有字段
        orm.codeFirst().syncTable(UserEntity.class);

        // 3. 验证默认 NONE 模式下，针对已存在的 username/nickname，即使 UserEntityV2 发生了长度和默认值变更，也不会生成任何 ALTER
        List<String> noneDdls = orm.codeFirst().generateDdl(UserEntityV2.class);
        Assert.assertTrue("默认 NONE 模式下不应产生任何修改已有列的 DDL", noneDdls.isEmpty());

        // 4. 测试位运算单个控制：仅允许长度扩容 LENGTH_EXPAND
        config.codeFirstAlterMode(CodeFirstAlterMode.LENGTH_EXPAND);
        List<String> lengthOnlyDdls = orm.codeFirst().generateDdl(UserEntityV2.class);
        Assert.assertEquals(1, lengthOnlyDdls.size());
        Assert.assertTrue("仅允许长度扩容时应生成 username 扩容语句", lengthOnlyDdls.get(0).contains("150"));

        // 5. 测试位运算多选组合：通过按位或（|）同时允许长度扩容与默认值变更
        // 方式 A：varargs 多参数传入
        config.codeFirstAlterMode(CodeFirstAlterMode.LENGTH_EXPAND, CodeFirstAlterMode.DEFAULT_VALUE);
        List<String> combinedDdls = orm.codeFirst().generateDdl(UserEntityV2.class);
        Assert.assertEquals(2, combinedDdls.size());

        // 方式 B：通过 or() 位运算方法组合
        config.codeFirstAlterMode(CodeFirstAlterMode.LENGTH_EXPAND.or(CodeFirstAlterMode.DEFAULT_VALUE));
        List<String> orDdls = orm.codeFirst().generateDdl(UserEntityV2.class);
        Assert.assertEquals(2, orDdls.size());

        // 方式 C：使用常用预设枚举 EXPAND_ONLY / SAFE
        config.codeFirstAlterMode(CodeFirstAlterMode.EXPAND_ONLY);
        List<String> alterDdls = orm.codeFirst().generateDdl(UserEntityV2.class);

        boolean hasLengthAlter = alterDdls.stream().anyMatch(sql -> sql.toUpperCase().contains("SET DATA TYPE") && sql.contains("150"));
        Assert.assertTrue("DDL 列表中应包含 username 长度扩容到 150 的 ALTER 语句", hasLengthAlter);

        boolean hasDefaultAlter = alterDdls.stream().anyMatch(sql -> sql.toUpperCase().contains("SET DEFAULT") && sql.contains("vip_user"));
        Assert.assertTrue("DDL 列表中应包含 nickname 默认值变更到 vip_user 的 ALTER 语句", hasDefaultAlter);

        // 6. 执行架构同步
        orm.codeFirst().syncTable(UserEntityV2.class);

        // 7. 校验数据库物理元数据是否已成功更新
        try (Connection conn = dataSource.getConnection()) {
            Map<String, PhysicalColumnDesc> details = SchemaInspector.getExistingColumnDetails(conn, null, "t_cf_user");
            Assert.assertEquals("username 字段长度应该已变更为 150", 150, details.get("username").getColumnSize());
            Assert.assertTrue("nickname 字段默认值应该已变更为 vip_user", details.get("nickname").getDefaultValue().contains("vip_user"));
        }

        // 8. 验证执行后数据读写完好，超过 50 字符的用户名正常入库
        orm.sql(e -> {
            UserEntityV2 u = e.table(UserEntityV2.class);
            e.update(u)
                    .set(u.getUsername(), "a_very_long_username_that_exceeds_fifty_characters_0123456789_abcdefg")
                    .where().eq(u.getId(), 1L);
        }).exec();

        UserEntityV2 alice = orm.select(UserEntityV2.class).where().eq(UserEntityV2::getId, 1L).first();
        Assert.assertNotNull(alice);
        Assert.assertEquals("a_very_long_username_that_exceeds_fifty_characters_0123456789_abcdefg", alice.getUsername());
    }

    /**
     * 7. 测试 @JoIndex 索引元数据解析、DDL 自动生成及 Code-First 自动建索引与幂等性
     */
    @Test
    public void testAutoCreateIndexes() throws Exception {
        OrmConfig config = new OrmConfig()
                .setCodeFirstMode(CodeFirstMode.UPDATE)
                .setPrintSql(true);
        JavaOrm orm = JavaOrm.create(dataSource, new H2Dialect(), config);

        // 1. 生成 DDL 检查
        List<String> ddls = orm.codeFirst().generateDdl(IndexedArticleEntity.class);
        Assert.assertNotNull(ddls);
        Assert.assertEquals("首次建表应生成 1 条建表 DDL + 2 条索引 DDL", 3, ddls.size());

        boolean hasCreateTable = ddls.stream().anyMatch(sql -> sql.toUpperCase().contains("CREATE TABLE") && sql.toUpperCase().contains("T_CF_ARTICLE"));
        Assert.assertTrue("应包含 CREATE TABLE", hasCreateTable);

        boolean hasUniqueIndex = ddls.stream().anyMatch(sql -> sql.toUpperCase().contains("CREATE UNIQUE INDEX") && sql.contains("uk_art_code"));
        Assert.assertTrue("应包含 CREATE UNIQUE INDEX uk_art_code", hasUniqueIndex);

        boolean hasCompoundIndex = ddls.stream().anyMatch(sql -> sql.toUpperCase().contains("CREATE INDEX") && sql.contains("idx_art_status_time"));
        Assert.assertTrue("应包含 CREATE INDEX idx_art_status_time", hasCompoundIndex);

        // 2. 执行表结构与索引同步
        orm.codeFirst().syncTable(IndexedArticleEntity.class);

        // 3. 校验数据库中的物理索引
        try (Connection conn = dataSource.getConnection()) {
            java.util.Set<String> indexes = SchemaInspector.getExistingIndexes(conn, null, "t_cf_article");
            Assert.assertTrue("数据库应包含唯一索引 uk_art_code", indexes.contains("uk_art_code"));
            Assert.assertTrue("数据库应包含复合索引 idx_art_status_time", indexes.contains("idx_art_status_time"));
        }

        // 4. 再次执行 generateDdl，由于索引已在数据库中存在，应具备幂等性（生成 0 条 DDL）
        List<String> secondDdls = orm.codeFirst().generateDdl(IndexedArticleEntity.class);
        Assert.assertTrue("索引与表已存在时再次同步应生成 0 条 DDL（具备幂等性）", secondDdls.isEmpty());

        // 5. 验证数据操作与唯一索引约束生效
        IndexedArticleEntity a1 = new IndexedArticleEntity();
        a1.setArticleCode("ART_001");
        a1.setStatus(1);
        a1.setCreateTime(LocalDateTime.now());
        orm.insert(a1);

        IndexedArticleEntity a2 = new IndexedArticleEntity();
        a2.setArticleCode("ART_001"); // 重复 code，应当触发唯一约束冲突
        a2.setStatus(1);
        a2.setCreateTime(LocalDateTime.now());
        boolean uniqueConflict = false;
        try {
            orm.insert(a2);
        } catch (Exception e) {
            uniqueConflict = true;
        }
        Assert.assertTrue("重复插入唯一索引字段值应当触发约束异常", uniqueConflict);
    }

    /**
     * 8. 测试已有表在 UPDATE 模式下增量补齐缺失索引，且受 CodeFirstAlterMode.INDEX 控制
     */
    @Test
    public void testExistingTableIndexAlterMode() throws Exception {
        // 先手动创建无索引的 t_cf_article 表
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE \"t_cf_article\" (\"id\" BIGINT PRIMARY KEY, \"article_code\" VARCHAR(64), \"status\" INT, \"create_time\" TIMESTAMP)");
        }

        OrmConfig config = new OrmConfig()
                .setCodeFirstMode(CodeFirstMode.UPDATE)
                .codeFirstAlterMode(CodeFirstAlterMode.NONE); // 默认严格模式：禁止对已有表修改
        JavaOrm orm = JavaOrm.create(dataSource, new H2Dialect(), config);

        // NONE 模式下，即便缺失索引，也不生成
        List<String> noneDdls = orm.codeFirst().generateDdl(IndexedArticleEntity.class);
        Assert.assertTrue("NONE 模式下不应对已有表增量添加索引", noneDdls.isEmpty());

        // 允许 INDEX 位掩码
        config.codeFirstAlterMode(CodeFirstAlterMode.INDEX);
        List<String> indexDdls = orm.codeFirst().generateDdl(IndexedArticleEntity.class);
        Assert.assertEquals("开启 INDEX 模式后应对已有表补齐 2 个缺失索引", 2, indexDdls.size());

        // 执行同步并校验
        orm.codeFirst().syncTable(IndexedArticleEntity.class);
        try (Connection conn = dataSource.getConnection()) {
            java.util.Set<String> indexes = SchemaInspector.getExistingIndexes(conn, null, "t_cf_article");
            Assert.assertTrue(indexes.contains("uk_art_code"));
            Assert.assertTrue(indexes.contains("idx_art_status_time"));
        }
    }
}
