package com.javaorm.core.test;

import com.javaorm.core.JavaOrm;
import com.javaorm.core.annotation.JoId;
import com.javaorm.core.annotation.JoTable;
import com.javaorm.core.annotation.JoVersion;
import com.javaorm.core.dialect.SQLiteDialect;
import com.javaorm.core.exception.OptimisticLockException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sqlite.SQLiteDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class BatchAndOptimisticLockTest {

    private JavaOrm orm;
    private SQLiteDataSource dataSource;
    private static final String DB_FILE = "target/test_batch_opt.db";

    @JoTable(name = "t_product")
    public static class Product {
        @JoId
        private Long id;
        private String name;
        private Double price;

        @JoVersion
        private Integer version;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Double getPrice() {
            return price;
        }

        public void setPrice(Double price) {
            this.price = price;
        }

        public Integer getVersion() {
            return version;
        }

        public void setVersion(Integer version) {
            this.version = version;
        }
    }

    @Before
    public void setUp() throws Exception {
        new File("target").mkdirs();
        new File(DB_FILE).delete();

        dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + DB_FILE);

        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE t_product (id INTEGER PRIMARY KEY, name TEXT, price REAL, version INTEGER)");
        }

        orm = JavaOrm.create(dataSource, new SQLiteDialect());
    }

    @After
    public void tearDown() {
        new File(DB_FILE).delete();
    }

    @Test
    public void testOptimisticLockSuccessAndConflict() {
        // 1. 插入新记录，版本号应自动初始化为 1
        Product p = new Product();
        p.setId(1001L);
        p.setName("MacBook Pro");
        p.setPrice(12999.0);
        orm.insert(p);

        assertEquals(Integer.valueOf(1), p.getVersion());

        // 2. 正常更新，版本号自动递增为 2
        p.setPrice(13999.0);
        int affected = orm.update(p);
        assertEquals(1, affected);
        assertEquals(Integer.valueOf(2), p.getVersion());

        // 验证数据库已是 version = 2
        Product dbP = orm.select(Product.class).where().eq(Product::getId, 1001L).firstOrNull();
        assertNotNull(dbP);
        assertEquals(Integer.valueOf(2), dbP.getVersion());
        assertEquals(Double.valueOf(13999.0), dbP.getPrice());

        // 3. 模拟并发冲突：另一个事务拿着旧版本号 version = 1 来更新
        Product oldVersionP = new Product();
        oldVersionP.setId(1001L);
        oldVersionP.setName("Hacked");
        oldVersionP.setPrice(9999.0);
        oldVersionP.setVersion(1); // 旧版本

        try {
            orm.update(oldVersionP);
            fail("Should throw OptimisticLockException when version does not match");
        } catch (OptimisticLockException e) {
            assertEquals(1001L, e.getId());
            assertEquals(1, e.getVersion());
        }
    }

    @Test
    public void testBatchInsertWithBatchSize() {
        List<Product> list = new ArrayList<>();
        for (long i = 1; i <= 35; i++) {
            Product p = new Product();
            p.setId(i);
            p.setName("Product_" + i);
            p.setPrice(10.0 + i);
            list.add(p);
        }

        // 分批插入，每批 10 条
        int total = orm.insertBatch(list).batchSize(10).execute();
        assertEquals(35, total);

        long count = orm.select(Product.class).count();
        assertEquals(35L, count);

        // 验证每条插入的记录版本号都已正确初始化为 1
        Product first = orm.select(Product.class).where().eq(Product::getId, 1L).firstOrNull();
        assertNotNull(first);
        assertEquals(Integer.valueOf(1), first.getVersion());
    }

    @Test
    public void testBatchUpdateWithOptimisticLock() {
        // 先插入 5 条数据
        List<Product> list = new ArrayList<>();
        for (long i = 1; i <= 5; i++) {
            Product p = new Product();
            p.setId(i);
            p.setName("Product_" + i);
            p.setPrice(100.0);
            p.setVersion(1);
            list.add(p);
        }
        orm.insertBatch(list).execute();

        // 批量更新价格，各版本号递增为 2
        for (Product p : list) {
            p.setPrice(200.0);
        }
        int updated = orm.updateBatch(list).batchSize(2).execute();
        assertEquals(5, updated);

        for (Product p : list) {
            assertEquals(Integer.valueOf(2), p.getVersion());
        }

        // 验证数据库中 version 均为 2
        List<Product> dbList = orm.select(Product.class).toList();
        for (Product p : dbList) {
            assertEquals(Integer.valueOf(2), p.getVersion());
            assertEquals(Double.valueOf(200.0), p.getPrice());
        }
    }

    @Test
    public void testBatchDelete() {
        List<Product> list = new ArrayList<>();
        for (long i = 1; i <= 15; i++) {
            Product p = new Product();
            p.setId(i);
            p.setName("Product_" + i);
            p.setPrice(50.0);
            list.add(p);
        }
        orm.insertBatch(list).execute();

        // 1. 批量按 ID 删除前 5 条
        int delCount1 = orm.deleteByIds(Product.class, Arrays.asList(1L, 2L, 3L, 4L, 5L));
        assertEquals(5, delCount1);
        assertEquals(10L, orm.select(Product.class).count());

        // 2. 批量按实体列表删除剩余 10 条
        List<Product> remain = orm.select(Product.class).toList();
        int delCount2 = orm.deleteBatch(remain).batchSize(3).execute();
        assertEquals(10, delCount2);
        assertEquals(0L, orm.select(Product.class).count());
    }
}
