package com.javaorm.core.sqlmatrix;

import com.javaorm.core.JavaOrm;
import com.javaorm.core.annotation.GenerationType;
import com.javaorm.core.annotation.JoColumn;
import com.javaorm.core.annotation.JoGeneratedValue;
import com.javaorm.core.annotation.JoId;
import com.javaorm.core.annotation.JoTable;
import com.javaorm.core.codefirst.CodeFirstMode;
import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.dialect.Dialect;
import com.javaorm.core.dialect.H2Dialect;
import com.javaorm.core.dialect.MySQLDialect;
import com.javaorm.core.dialect.OracleDialect;
import com.javaorm.core.dialect.PostgreSQLDialect;
import com.javaorm.core.dialect.SqlAndParams;
import com.javaorm.core.dialect.SQLiteDialect;
import com.javaorm.core.query.Query;
import com.javaorm.core.query.SelectBuilder;
import com.javaorm.core.query.WhereBuilder;
import com.javaorm.core.sql.SqlJoinClause;
import com.javaorm.core.sql.SqlSelectStream;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Assert;
import org.junit.BeforeClass;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/**
 * SQL 全方言全覆盖矩阵单测基类。
 * 1. 严格禁止手写 DDL 建表 SQL，100% 采用 CodeFirst 方式自动同步实体到数据库；
 * 2. 统一管理 5 大数据库方言实例 (MySQL, PostgreSQL, Oracle, SQLite, H2)；
 * 3. 提供 100% 全文本精确匹配断言工具方法，拒绝模糊匹配。
 */
public abstract class AbstractSqlMatrixTest {

    public static final MySQLDialect MYSQL = new MySQLDialect();
    public static final PostgreSQLDialect PG = new PostgreSQLDialect();
    public static final OracleDialect ORACLE = new OracleDialect();
    public static final SQLiteDialect SQLITE = new SQLiteDialect();
    public static final H2Dialect H2 = new H2Dialect();

    protected static DataSource dataSource;
    protected static JavaOrm orm;

    // 测试实体 1：用户表
    @JoTable(name = "matrix_user")
    public static class MatrixUser {
        @JoId
        @JoGeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @JoColumn(length = 64, nullable = false)
        private String username;

        @JoColumn(length = 128)
        private String email;

        private Integer age;

        private Integer status;

        @JoColumn(precision = 10, scale = 2)
        private BigDecimal score;

        private LocalDateTime createTime;

        private Integer isDeleted;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public Integer getAge() { return age; }
        public void setAge(Integer age) { this.age = age; }
        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
        public BigDecimal getScore() { return score; }
        public void setScore(BigDecimal score) { this.score = score; }
        public LocalDateTime getCreateTime() { return createTime; }
        public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
        public Integer getIsDeleted() { return isDeleted; }
        public void setIsDeleted(Integer isDeleted) { this.isDeleted = isDeleted; }
    }

    // 测试实体 2：订单表
    @JoTable(name = "matrix_order")
    public static class MatrixOrder {
        @JoId
        @JoGeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        private Long userId;

        @JoColumn(length = 64, nullable = false)
        private String orderNo;

        @JoColumn(precision = 12, scale = 2)
        private BigDecimal amount;

        private Integer status;

        private LocalDateTime createTime;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getOrderNo() { return orderNo; }
        public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
        public LocalDateTime getCreateTime() { return createTime; }
        public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    }

    // 测试实体 3：订单明细项表
    @JoTable(name = "matrix_order_item")
    public static class MatrixOrderItem {
        @JoId
        @JoGeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        private Long orderId;

        @JoColumn(length = 64, nullable = false)
        private String prodName;

        @JoColumn(precision = 10, scale = 2)
        private BigDecimal price;

        private Integer quantity;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getOrderId() { return orderId; }
        public void setOrderId(Long orderId) { this.orderId = orderId; }
        public String getProdName() { return prodName; }
        public void setProdName(String prodName) { this.prodName = prodName; }
        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
    }

    // 测试实体 4：分类表（用于自连接与笛卡尔积测试）
    @JoTable(name = "matrix_category")
    public static class MatrixCategory {
        @JoId
        @JoGeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @JoColumn(length = 64, nullable = false)
        private String name;

        private Long parentId;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Long getParentId() { return parentId; }
        public void setParentId(Long parentId) { this.parentId = parentId; }
    }

    @BeforeClass
    public static void initMatrixEnvironment() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:sql_matrix_db;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE");
        ds.setUser("sa");
        ds.setPassword("");
        dataSource = ds;

        OrmConfig config = new OrmConfig()
                .setCodeFirstMode(CodeFirstMode.UPDATE)
                .setPrintSql(false);
        orm = JavaOrm.create(dataSource, H2, config);

        // 清理旧表（仅做 drop table if exists 清理，严禁手写 create table）
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS matrix_user");
            stmt.execute("DROP TABLE IF EXISTS matrix_order");
            stmt.execute("DROP TABLE IF EXISTS matrix_order_item");
            stmt.execute("DROP TABLE IF EXISTS matrix_category");
        }

        // 100% 通过 CodeFirst 纯实体建表！
        orm.codeFirst().syncTable(MatrixUser.class);
        orm.codeFirst().syncTable(MatrixOrder.class);
        orm.codeFirst().syncTable(MatrixOrderItem.class);
        orm.codeFirst().syncTable(MatrixCategory.class);

        // 100% 通过 CodeFirst 方式初始化测试种子数据（绝不手写原生 INSERT SQL）
        initSeedData();
    }

    private static void initSeedData() {
        LocalDateTime now = LocalDateTime.now();

        MatrixUser u1 = new MatrixUser();
        u1.setUsername("alice");
        u1.setEmail("alice@example.com");
        u1.setAge(20);
        u1.setStatus(1);
        u1.setScore(new BigDecimal("95.50"));
        u1.setCreateTime(now);
        u1.setIsDeleted(0);

        MatrixUser u2 = new MatrixUser();
        u2.setUsername("bob");
        u2.setEmail("bob@example.com");
        u2.setAge(25);
        u2.setStatus(1);
        u2.setScore(new BigDecimal("88.00"));
        u2.setCreateTime(now);
        u2.setIsDeleted(0);

        MatrixUser u3 = new MatrixUser();
        u3.setUsername("charlie");
        u3.setEmail("charlie@example.com");
        u3.setAge(30);
        u3.setStatus(0);
        u3.setScore(new BigDecimal("72.50"));
        u3.setCreateTime(now);
        u3.setIsDeleted(0);

        MatrixUser u4 = new MatrixUser();
        u4.setUsername("david");
        u4.setEmail("david@example.com");
        u4.setAge(20);
        u4.setStatus(1);
        u4.setScore(new BigDecimal("60.00"));
        u4.setCreateTime(now);
        u4.setIsDeleted(0);

        orm.insertBatch(Arrays.asList(u1, u2, u3, u4)).execute();

        MatrixOrder o1 = new MatrixOrder();
        o1.setUserId(u1.getId());
        o1.setOrderNo("ORD-001");
        o1.setAmount(new BigDecimal("199.90"));
        o1.setStatus(1);
        o1.setCreateTime(now);

        MatrixOrder o2 = new MatrixOrder();
        o2.setUserId(u1.getId());
        o2.setOrderNo("ORD-002");
        o2.setAmount(new BigDecimal("299.00"));
        o2.setStatus(2);
        o2.setCreateTime(now);

        MatrixOrder o3 = new MatrixOrder();
        o3.setUserId(u2.getId());
        o3.setOrderNo("ORD-003");
        o3.setAmount(new BigDecimal("99.00"));
        o3.setStatus(1);
        o3.setCreateTime(now);

        // o4 的 userId 为 999（无对应用户，专门用于验证 RIGHT JOIN 与 FULL JOIN）
        MatrixOrder o4 = new MatrixOrder();
        o4.setUserId(999L);
        o4.setOrderNo("ORD-999");
        o4.setAmount(new BigDecimal("50.00"));
        o4.setStatus(1);
        o4.setCreateTime(now);

        orm.insertBatch(Arrays.asList(o1, o2, o3, o4)).execute();

        // 订单明细
        MatrixOrderItem item1 = new MatrixOrderItem();
        item1.setOrderId(o1.getId());
        item1.setProdName("Mechanical Keyboard");
        item1.setPrice(new BigDecimal("99.90"));
        item1.setQuantity(1);

        MatrixOrderItem item2 = new MatrixOrderItem();
        item2.setOrderId(o1.getId());
        item2.setProdName("Wireless Mouse");
        item2.setPrice(new BigDecimal("100.00"));
        item2.setQuantity(1);

        MatrixOrderItem item3 = new MatrixOrderItem();
        item3.setOrderId(o2.getId());
        item3.setProdName("4K Monitor");
        item3.setPrice(new BigDecimal("299.00"));
        item3.setQuantity(1);

        orm.insertBatch(Arrays.asList(item1, item2, item3)).execute();

        // 分类（用于自连接）
        MatrixCategory c1 = new MatrixCategory();
        c1.setName("Electronics");
        c1.setParentId(null);

        MatrixCategory c2 = new MatrixCategory();
        c2.setName("Computer");
        c2.setParentId(1L);

        MatrixCategory c3 = new MatrixCategory();
        c3.setName("Phone");
        c3.setParentId(1L);

        MatrixCategory c4 = new MatrixCategory();
        c4.setName("Laptop");
        c4.setParentId(2L);

        orm.insertBatch(Arrays.asList(c1, c2, c3, c4)).execute();
    }

    /**
     * 100% 全文本精确断言 5 大数据库方言生成的 SQL 和参数
     */
    protected static void assertMatrixSql(Supplier<Query> querySupplier,
                                          String expectedMysql,
                                          String expectedPg,
                                          String expectedOracle,
                                          String expectedSqlite,
                                          String expectedH2,
                                          Object... expectedParams) {
        List<Object> expectedParamList = Arrays.asList(expectedParams);

        // 1. MySQL 断言
        SqlAndParams mysqlResult = toSqlAndParams(querySupplier.get(), MYSQL);
        Assert.assertEquals("MySQL 方言 SQL 不匹配", expectedMysql, mysqlResult.getSql());
        Assert.assertEquals("MySQL 方言参数不匹配", expectedParamList, mysqlResult.getParams());

        // 2. PostgreSQL 断言
        SqlAndParams pgResult = toSqlAndParams(querySupplier.get(), PG);
        Assert.assertEquals("PostgreSQL 方言 SQL 不匹配", expectedPg, pgResult.getSql());
        Assert.assertEquals("PostgreSQL 方言参数不匹配", expectedParamList, pgResult.getParams());

        // 3. Oracle 断言
        SqlAndParams oracleResult = toSqlAndParams(querySupplier.get(), ORACLE);
        Assert.assertEquals("Oracle 方言 SQL 不匹配", expectedOracle, oracleResult.getSql());
        Assert.assertEquals("Oracle 方言参数不匹配", expectedParamList, oracleResult.getParams());

        // 4. SQLite 断言
        SqlAndParams sqliteResult = toSqlAndParams(querySupplier.get(), SQLITE);
        Assert.assertEquals("SQLite 方言 SQL 不匹配", expectedSqlite, sqliteResult.getSql());
        Assert.assertEquals("SQLite 方言参数不匹配", expectedParamList, sqliteResult.getParams());

        // 5. H2 断言
        SqlAndParams h2Result = toSqlAndParams(querySupplier.get(), H2);
        Assert.assertEquals("H2 方言 SQL 不匹配", expectedH2, h2Result.getSql());
        Assert.assertEquals("H2 方言参数不匹配", expectedParamList, h2Result.getParams());
    }

    /**
     * 支持针对不同方言独立指定参数列表的全文本精确断言（例如分页在 MySQL 为 [limit, offset]，在 Oracle 为 [offset, limit]）。
     */
    protected static void assertMatrixSqlWithDialectParams(Supplier<Query> querySupplier,
                                                          String expectedMysql, List<Object> expectedMysqlParams,
                                                          String expectedPg, List<Object> expectedPgParams,
                                                          String expectedOracle, List<Object> expectedOracleParams,
                                                          String expectedSqlite, List<Object> expectedSqliteParams,
                                                          String expectedH2, List<Object> expectedH2Params) {
        // 1. MySQL 断言
        SqlAndParams mysqlResult = toSqlAndParams(querySupplier.get(), MYSQL);
        Assert.assertEquals("MySQL 方言 SQL 不匹配", expectedMysql, mysqlResult.getSql());
        Assert.assertEquals("MySQL 方言参数不匹配", expectedMysqlParams, mysqlResult.getParams());

        // 2. PostgreSQL 断言
        SqlAndParams pgResult = toSqlAndParams(querySupplier.get(), PG);
        Assert.assertEquals("PostgreSQL 方言 SQL 不匹配", expectedPg, pgResult.getSql());
        Assert.assertEquals("PostgreSQL 方言参数不匹配", expectedPgParams, pgResult.getParams());

        // 3. Oracle 断言
        SqlAndParams oracleResult = toSqlAndParams(querySupplier.get(), ORACLE);
        Assert.assertEquals("Oracle 方言 SQL 不匹配", expectedOracle, oracleResult.getSql());
        Assert.assertEquals("Oracle 方言参数不匹配", expectedOracleParams, oracleResult.getParams());

        // 4. SQLite 断言
        SqlAndParams sqliteResult = toSqlAndParams(querySupplier.get(), SQLITE);
        Assert.assertEquals("SQLite 方言 SQL 不匹配", expectedSqlite, sqliteResult.getSql());
        Assert.assertEquals("SQLite 方言参数不匹配", expectedSqliteParams, sqliteResult.getParams());

        // 5. H2 断言
        SqlAndParams h2Result = toSqlAndParams(querySupplier.get(), H2);
        Assert.assertEquals("H2 方言 SQL 不匹配", expectedH2, h2Result.getSql());
        Assert.assertEquals("H2 方言参数不匹配", expectedH2Params, h2Result.getParams());
    }

    protected static SqlAndParams toSqlAndParams(Query query, Dialect dialect) {
        if (query instanceof SelectBuilder) {
            return ((SelectBuilder<?>) query).toSqlAndParams(dialect);
        } else if (query instanceof WhereBuilder) {
            return ((WhereBuilder<?>) query).toSqlAndParams(dialect);
        } else if (query instanceof SqlSelectStream) {
            return ((SqlSelectStream<?>) query).toSqlAndParams(dialect);
        } else if (query instanceof SqlJoinClause) {
            return ((SqlJoinClause<?>) query).toSqlAndParams(dialect);
        } else if (query instanceof com.javaorm.core.sql.SqlExecution) {
            return ((com.javaorm.core.sql.SqlExecution<?>) query).toSqlAndParams(dialect);
        } else if (query instanceof com.javaorm.core.query.HavingBuilder) {
            return ((com.javaorm.core.query.HavingBuilder<?>) query).toSqlAndParams(dialect);
        }
        throw new UnsupportedOperationException("Unsupported query type for toSqlAndParams: " + query.getClass());
    }
}
