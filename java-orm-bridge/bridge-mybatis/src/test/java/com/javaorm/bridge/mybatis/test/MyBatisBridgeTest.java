package com.javaorm.bridge.mybatis.test;

import com.javaorm.bridge.mybatis.JavaOrmMyBatisSupport;
import com.javaorm.core.JavaOrm;
import com.javaorm.core.annotation.JoColumn;
import com.javaorm.core.annotation.JoId;
import com.javaorm.core.annotation.JoTable;
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

public class MyBatisBridgeTest {

    private static DataSource dataSource;
    private static SqlSessionFactory sqlSessionFactory;

    @JoTable(name = "t_my_user")
    public static class MyUser {
        @JoId
        private Long id;

        @JoColumn(name = "user_name")
        private String name;

        @JoColumn(name = "user_age")
        private Integer age;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Integer getAge() { return age; }
        public void setAge(Integer age) { this.age = age; }
    }

    @BeforeClass
    public static void setUp() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:mybatis_test;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE");
        ds.setUser("sa");
        ds.setPassword("");
        dataSource = ds;

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS t_my_user");
            stmt.execute("CREATE TABLE t_my_user (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "user_name VARCHAR(50), " +
                    "user_age INT)");
        }

        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
    }

    @Test
    public void testMyBatisSessionOperations() {
        try (SqlSession sqlSession = sqlSessionFactory.openSession(true)) {
            JavaOrm mybatisOrm = JavaOrmMyBatisSupport.from(sqlSession);

            MyUser user = new MyUser();
            user.setName("MyBatisUser");
            user.setAge(30);
            int rows = mybatisOrm.insert(user);
            Assert.assertEquals(1, rows);
            Assert.assertNotNull(user.getId());

            MyUser found = mybatisOrm.findById(MyUser.class, user.getId());
            Assert.assertNotNull(found);
            Assert.assertEquals("MyBatisUser", found.getName());

            MyUser u1 = new MyUser();
            u1.setName("Batch1");
            u1.setAge(25);
            MyUser u2 = new MyUser();
            u2.setName("Batch2");
            u2.setAge(26);

            int batchRows = mybatisOrm.insertBatch(Arrays.asList(u1, u2)).batchSize(100).execute();
            Assert.assertEquals(2, batchRows);

            List<MyUser> list = mybatisOrm.select(MyUser.class)
                    .where()
                    .in(MyUser::getName, Arrays.asList("Batch1", "Batch2"))
                    .list();
            Assert.assertEquals(2, list.size());
        }
    }
}
