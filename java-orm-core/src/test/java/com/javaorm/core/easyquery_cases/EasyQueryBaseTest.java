package com.javaorm.core.easyquery_cases;

import com.javaorm.core.JavaOrm;
import com.javaorm.core.query.SelectBuilder;
import org.junit.After;
import org.junit.Before;
import org.sqlite.SQLiteDataSource;

import java.sql.Connection;
import java.sql.Statement;

/**
 * EasyQuery 官方单测 1:1 复刻基础支持类
 * 提供标准 SQLite 嵌入式环境与完整基准测试数据集
 */
public abstract class EasyQueryBaseTest {

    protected JavaOrm orm;
    protected SQLiteDataSource dataSource;

    @Before
    public void setUp() throws Exception {
        dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:target/easyquery_cases_test.db");
        orm = JavaOrm.create(dataSource);

        initSchemaAndData();
    }

    @After
    public void tearDown() {
        SelectBuilder.resetAliasSeq();
    }

    protected void initSchemaAndData() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // 1. 创建表结构
            stmt.execute("DROP TABLE IF EXISTS t_topic");
            stmt.execute("CREATE TABLE t_topic (" +
                    "id VARCHAR(50) PRIMARY KEY, " +
                    "stars INTEGER, " +
                    "title VARCHAR(200), " +
                    "is_deleted INTEGER DEFAULT 0, " +
                    "create_time TIMESTAMP)");

            stmt.execute("DROP TABLE IF EXISTS t_blog");
            stmt.execute("CREATE TABLE t_blog (" +
                    "id VARCHAR(50) PRIMARY KEY, " +
                    "title VARCHAR(200), " +
                    "content TEXT, " +
                    "star INTEGER, " +
                    "topic_id VARCHAR(50), " +
                    "create_time TIMESTAMP)");

            stmt.execute("DROP TABLE IF EXISTS t_company");
            stmt.execute("CREATE TABLE t_company (" +
                    "id VARCHAR(50) PRIMARY KEY, " +
                    "name VARCHAR(200), " +
                    "create_time TIMESTAMP)");

            stmt.execute("DROP TABLE IF EXISTS t_sys_user");
            stmt.execute("CREATE TABLE t_sys_user (" +
                    "id VARCHAR(50) PRIMARY KEY, " +
                    "company_id VARCHAR(50), " +
                    "name VARCHAR(100), " +
                    "age INTEGER, " +
                    "phone VARCHAR(50), " +
                    "create_time TIMESTAMP)");

            stmt.execute("DROP TABLE IF EXISTS t_user_extra");
            stmt.execute("CREATE TABLE t_user_extra (" +
                    "id VARCHAR(50) PRIMARY KEY, " +
                    "user_id VARCHAR(50), " +
                    "id_card VARCHAR(100), " +
                    "city VARCHAR(100))");

            // 2. 插入初始化基准数据集
            // Topic 数据: 7 条（包含 NULL、空白、已逻辑删除）
            stmt.execute("INSERT INTO t_topic (id, stars, title, is_deleted, create_time) VALUES " +
                    "('1', 10, 'Java ORM Guide', 0, '2026-01-01 10:00:00'), " +
                    "('2', 20, 'EasyQuery Parity', 0, '2026-01-02 10:00:00'), " +
                    "('3', 30, 'Deep Dive SQL', 0, '2026-01-03 10:00:00'), " +
                    "('4', 40, '', 0, '2026-01-04 10:00:00'), " +
                    "('5', 50, '   ', 0, '2026-01-05 10:00:00'), " +
                    "('6', 60, NULL, 0, '2026-01-06 10:00:00'), " +
                    "('7', 70, 'Deleted Topic', 1, '2026-01-07 10:00:00')");

            // Blog 数据: 5 条
            stmt.execute("INSERT INTO t_blog (id, title, content, star, topic_id, create_time) VALUES " +
                    "('b1', 'Blog 1', 'Content 1', 5, '1', '2026-01-01 11:00:00'), " +
                    "('b2', 'Blog 2', 'Content 2', 15, '1', '2026-01-01 12:00:00'), " +
                    "('b3', 'Blog 3', 'Content 3', 25, '2', '2026-01-02 11:00:00'), " +
                    "('b4', 'Blog 4', 'Content 4', 35, '3', '2026-01-03 11:00:00'), " +
                    "('b5', 'Blog 5', 'Content 5', 45, '999', '2026-01-04 11:00:00')");

            // Company 数据: 2 条
            stmt.execute("INSERT INTO t_company (id, name, create_time) VALUES " +
                    "('c1', 'Antigravity Corp', '2026-01-01 09:00:00'), " +
                    "('c2', 'Deepmind Lab', '2026-01-02 09:00:00')");

            // SysUser 数据: 4 条
            stmt.execute("INSERT INTO t_sys_user (id, company_id, name, age, phone, create_time) VALUES " +
                    "('u1', 'c1', 'Alice', 25, '13800000001', '2026-01-01 10:00:00'), " +
                    "('u2', 'c1', 'Bob', 30, '13800000002', '2026-01-01 11:00:00'), " +
                    "('u3', 'c2', 'Charlie', 28, '13800000003', '2026-01-02 10:00:00'), " +
                    "('u4', 'c2', 'David', 35, '13800000004', '2026-01-02 11:00:00')");

            // UserExtra 数据: 3 条
            stmt.execute("INSERT INTO t_user_extra (id, user_id, id_card, city) VALUES " +
                    "('e1', 'u1', '110101199001010001', 'Beijing'), " +
                    "('e2', 'u2', '310101199002020002', 'Shanghai'), " +
                    "('e3', 'u3', '440101199003030003', 'Guangzhou')");
        }
    }
}
