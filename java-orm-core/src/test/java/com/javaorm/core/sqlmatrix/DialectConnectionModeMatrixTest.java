package com.javaorm.core.sqlmatrix;

import com.javaorm.core.JavaOrm;
import com.javaorm.core.annotation.GenerationType;
import com.javaorm.core.annotation.JoColumn;
import com.javaorm.core.annotation.JoGeneratedValue;
import com.javaorm.core.annotation.JoId;
import com.javaorm.core.annotation.JoTable;
import com.javaorm.core.codefirst.CodeFirstMode;
import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.config.UpperUnderlinedNameConversion;
import com.javaorm.core.dialect.MySQLDialect;
import com.javaorm.core.dialect.OracleDialect;
import com.javaorm.core.dialect.PostgreSQLDialect;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * 数据库方言连接模式与大小写/Quote 引用严格隔离矩阵测试。
 *
 * 核心验证：
 * 1. MySQL 连接模式 (MODE=MySQL) + MySQLDialect：
 *    - 验证纯反引号 (`) 物理建表、插入与查询，绝不使用双引号，避免 MySQL 1064 语法错误；
 * 2. PostgreSQL 连接模式 (MODE=PostgreSQL) + PostgreSQLDialect：
 *    - 验证小写双引号 (") 物理建表、插入与查询，与 PG 小写数据字典无缝对接；
 * 3. 严格 Oracle 连接模式 (MODE=Oracle，无 DATABASE_TO_LOWER，无 CASE_INSENSITIVE_IDENTIFIERS)：
 *    - 自动适配 UpperUnderlinedNameConversion；
 *    - 验证纯大写双引号 (") 物理建表、插入与查询，与 Oracle 默认大写数据字典 100% 契合；
 * 4. 大小写敏感陷阱验证 (Negative Test)：
 *    - 在严格 Oracle 模式下，验证若使用小写双引号 ("username") 访问大写列，必然抛出找不到列异常。
 */
public class DialectConnectionModeMatrixTest {

    // ========== 测试实体定义 ==========

    public static class MySqlStrictUser {
        @JoId
        @JoGeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @JoColumn(length = 64, nullable = false)
        private String username;

        private Integer age;
        private BigDecimal score;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public Integer getAge() { return age; }
        public void setAge(Integer age) { this.age = age; }
        public BigDecimal getScore() { return score; }
        public void setScore(BigDecimal score) { this.score = score; }
    }

    public static class PgStrictUser {
        @JoId
        @JoGeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @JoColumn(length = 64, nullable = false)
        private String username;

        private Integer age;
        private BigDecimal score;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public Integer getAge() { return age; }
        public void setAge(Integer age) { this.age = age; }
        public BigDecimal getScore() { return score; }
        public void setScore(BigDecimal score) { this.score = score; }
    }

    public static class OracleStrictUser {
        @JoId
        @JoGeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @JoColumn(length = 64, nullable = false)
        private String username;

        private Integer age;
        private BigDecimal score;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public Integer getAge() { return age; }
        public void setAge(Integer age) { this.age = age; }
        public BigDecimal getScore() { return score; }
        public void setScore(BigDecimal score) { this.score = score; }
    }

    /**
     * 1. 真实 MySQL 模式连接 + MySQLDialect (纯反引号物理执行闭环)
     */
    @Test
    public void testMySQLConnectionMode_StrictBackticks() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:mysql_strict_test;DB_CLOSE_DELAY=-1;MODE=MySQL");
        ds.setUser("sa");
        ds.setPassword("");

        OrmConfig config = new OrmConfig()
                .setCodeFirstMode(CodeFirstMode.UPDATE)
                .setPrintSql(false);

        MySQLDialect dialect = new MySQLDialect();
        JavaOrm mysqlOrm = JavaOrm.create(ds, dialect, config);

        // 1. CodeFirst 建表（纯反引号）
        mysqlOrm.codeFirst().syncTable(MySqlStrictUser.class);

        // 2. 插入测试数据
        MySqlStrictUser user = new MySqlStrictUser();
        user.setUsername("mysql_tester");
        user.setAge(28);
        user.setScore(new BigDecimal("99.50"));
        int rows = mysqlOrm.insert(user).exec();
        Assert.assertEquals(1, rows);
        Assert.assertNotNull(user.getId());

        // 3. 查询并校验 SQL 全文本（必须为 MySQL 反引号）
        String sql = mysqlOrm.select(MySqlStrictUser.class).where(w -> w.eq(MySqlStrictUser::getUsername, "mysql_tester")).toSql();
        Assert.assertTrue("MySQL 方言 SQL 必须使用反引号包裹", sql.contains("`my_sql_strict_user`"));
        Assert.assertTrue("MySQL 方言字段必须使用反引号包裹", sql.contains("`username`"));
        Assert.assertFalse("MySQL 方言严禁包含双引号界定符", sql.contains("\""));

        // 4. 真实物理数据查询断言
        List<MySqlStrictUser> list = mysqlOrm.select(MySqlStrictUser.class).list();
        Assert.assertEquals(1, list.size());
        Assert.assertEquals("mysql_tester", list.get(0).getUsername());
        Assert.assertEquals(Integer.valueOf(28), list.get(0).getAge());
    }

    /**
     * 2. 真实 PostgreSQL 模式连接 + PostgreSQLDialect (小写双引号物理执行闭环)
     */
    @Test
    public void testPostgreSQLConnectionMode_LowerCaseDoubleQuotes() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:pg_strict_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
        ds.setUser("sa");
        ds.setPassword("");

        OrmConfig config = new OrmConfig()
                .setCodeFirstMode(CodeFirstMode.UPDATE)
                .setPrintSql(false);

        PostgreSQLDialect dialect = new PostgreSQLDialect();
        JavaOrm pgOrm = JavaOrm.create(ds, dialect, config);

        // 1. CodeFirst 建表（小写双引号）
        pgOrm.codeFirst().syncTable(PgStrictUser.class);

        // 2. 插入测试数据
        PgStrictUser user = new PgStrictUser();
        user.setUsername("pg_tester");
        user.setAge(26);
        user.setScore(new BigDecimal("91.00"));
        int rows = pgOrm.insert(user).exec();
        Assert.assertEquals(1, rows);
        Assert.assertNotNull(user.getId());

        // 3. 查询并校验 SQL 全文本（必须为 PG 双引号，且为小写）
        String sql = pgOrm.select(PgStrictUser.class).where(w -> w.eq(PgStrictUser::getUsername, "pg_tester")).toSql();
        Assert.assertTrue("PostgreSQL 方言表名必须使用小写双引号", sql.contains("\"pg_strict_user\""));
        Assert.assertTrue("PostgreSQL 方言字段必须使用小写双引号", sql.contains("\"username\""));

        // 4. 真实物理数据查询断言
        List<PgStrictUser> list = pgOrm.select(PgStrictUser.class).list();
        Assert.assertEquals(1, list.size());
        Assert.assertEquals("pg_tester", list.get(0).getUsername());
        Assert.assertEquals(Integer.valueOf(26), list.get(0).getAge());
    }

    /**
     * 3. 严格 Oracle 模式连接 + OracleDialect + UpperUnderlinedNameConversion (大写双引号物理执行闭环)
     * 绝不添加 DATABASE_TO_LOWER，绝不添加 CASE_INSENSITIVE_IDENTIFIERS！
     */
    @Test
    public void testOracleStrictConnectionMode_UpperCaseDoubleQuotes() {
        JdbcDataSource ds = new JdbcDataSource();
        // 严格模式：无 DATABASE_TO_LOWER，无 CASE_INSENSITIVE_IDENTIFIERS
        ds.setURL("jdbc:h2:mem:oracle_strict_test;DB_CLOSE_DELAY=-1;MODE=Oracle");
        ds.setUser("sa");
        ds.setPassword("");

        OrmConfig config = new OrmConfig()
                .setCodeFirstMode(CodeFirstMode.UPDATE)
                .setNameConversion(new UpperUnderlinedNameConversion())
                .setPrintSql(false);

        OracleDialect dialect = new OracleDialect();
        JavaOrm oracleOrm = JavaOrm.create(ds, dialect, config);
        Assert.assertTrue("Oracle 方言应用 UpperUnderlinedNameConversion",
                config.getNameConversion() instanceof UpperUnderlinedNameConversion);

        // 1. CodeFirst 物理建表（纯大写双引号）
        oracleOrm.codeFirst().syncTable(OracleStrictUser.class);

        // 2. 插入测试数据
        OracleStrictUser user = new OracleStrictUser();
        user.setUsername("oracle_tester");
        user.setAge(35);
        user.setScore(new BigDecimal("88.88"));
        int rows = oracleOrm.insert(user).exec();
        Assert.assertEquals(1, rows);
        Assert.assertNotNull(user.getId());

        // 3. 校验生成的 SQL 全文本：表名和列名均为全大写双引号！
        String sql = oracleOrm.select(OracleStrictUser.class).where(w -> w.eq(OracleStrictUser::getUsername, "oracle_tester")).toSql();
        Assert.assertTrue("Oracle 方言表名必须转换为全大写双引号", sql.contains("\"ORACLE_STRICT_USER\""));
        Assert.assertTrue("Oracle 方言字段必须转换为全大写双引号", sql.contains("\"USERNAME\""));
        Assert.assertTrue("Oracle 方言主键字段必须为全大写", sql.contains("\"ID\""));

        // 4. 严格 Oracle 物理执行查询与实体数据映射校验
        List<OracleStrictUser> list = oracleOrm.select(OracleStrictUser.class).list();
        Assert.assertEquals(1, list.size());
        Assert.assertEquals("oracle_tester", list.get(0).getUsername());
        Assert.assertEquals(Integer.valueOf(35), list.get(0).getAge());
        Assert.assertEquals(new BigDecimal("88.88"), list.get(0).getScore());
    }

    /**
     * 4. 大小写敏感陷阱反面复现测试 (Negative Test)
     * 证明：在严格 Oracle 模式下，若使用小写双引号访问大写表，数据库必报异常！
     */
    @Test
    public void testCaseSensitivityFailureWithoutProperCase() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        // 严格模式：大小写敏感
        ds.setURL("jdbc:h2:mem:case_sensitive_trap_test;DB_CLOSE_DELAY=-1;MODE=Oracle");
        ds.setUser("sa");
        ds.setPassword("");

        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {
            // 传统 Oracle 建表（未带双引号，字典自动记录为全大写 TEST_TABLE、ID、USER_NAME）
            stmt.execute("CREATE TABLE TEST_TABLE (ID BIGINT PRIMARY KEY, USER_NAME VARCHAR(64))");
            stmt.execute("INSERT INTO TEST_TABLE VALUES (1, 'alice')");

            // 1. 大写双引号能正常访问
            try (ResultSet rs = stmt.executeQuery("SELECT \"USER_NAME\" FROM \"TEST_TABLE\" WHERE \"ID\" = 1")) {
                Assert.assertTrue(rs.next());
                Assert.assertEquals("alice", rs.getString(1));
            }

            // 2. 小写双引号严格抛出语法/列未找到异常！
            try {
                stmt.executeQuery("SELECT \"user_name\" FROM \"test_table\" WHERE \"id\" = 1");
                Assert.fail("在严格模式下，小写双引号查询大写字典必须抛出异常！");
            } catch (SQLException e) {
                // 预期捕获：Table "test_table" not found 或 Column "user_name" not found
                Assert.assertTrue("应提示表或列未找到",
                        e.getMessage().contains("not found") || e.getMessage().contains("NOT FOUND") || e.getMessage().contains("找不到"));
            }
        }
    }
}
