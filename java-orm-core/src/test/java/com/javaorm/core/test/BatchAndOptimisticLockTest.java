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
            s.execute("CREATE TABLE t_item (id INTEGER PRIMARY KEY, title TEXT, price REAL DEFAULT 88.8, status INTEGER DEFAULT 1)");
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
        orm.insert(p).exec();

        assertEquals(Integer.valueOf(1), p.getVersion());

        // 2. 正常更新，版本号自动递增为 2
        p.setPrice(13999.0);
        int affected = orm.update(p).exec();
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
            orm.update(oldVersionP).exec();
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
        int delCount1 = orm.deleteByIds(Product.class, Arrays.asList(1L, 2L, 3L, 4L, 5L)).exec();
        assertEquals(5, delCount1);
        assertEquals(10L, orm.select(Product.class).count());

        // 2. 批量按实体列表删除剩余 10 条
        List<Product> remain = orm.select(Product.class).toList();
        int delCount2 = orm.deleteBatch(remain).batchSize(3).execute();
        assertEquals(10, delCount2);
        assertEquals(0L, orm.select(Product.class).count());
    }

    @JoTable(name = "t_item")
    public static class Item {
        @JoId
        private Long id;
        private String title;
        private Double price;
        private Integer status;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public Double getPrice() { return price; }
        public void setPrice(Double price) { this.price = price; }
        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
    }

    @Test
    public void testEasyQuerySmartBatchInsertWithDefaults() {
        Item item1 = new Item();
        item1.setId(1L);
        item1.setTitle("Item1"); // price 和 status 为 null

        Item item2 = new Item();
        item2.setId(2L);
        item2.setPrice(99.9); // title 和 status 为 null

        Item item3 = new Item();
        item3.setId(3L);
        item3.setTitle("Item3"); // price 和 status 为 null

        // 默认 EasyQuery 模式 (selective=true, keepOrder=false)：按非空列指纹聚类合并
        int rows = orm.insertBatch(Arrays.asList(item1, item2, item3)).execute();
        assertEquals(3, rows);

        Item db1 = orm.select(Item.class).where().eq(Item::getId, 1L).firstOrNull();
        assertNotNull(db1);
        assertEquals("Item1", db1.getTitle());
        // 关键验证：由于剔除了 null 字段，数据库的 DEFAULT 88.8 与 DEFAULT 1 正常生效！
        assertEquals(Double.valueOf(88.8), db1.getPrice());
        assertEquals(Integer.valueOf(1), db1.getStatus());

        Item db2 = orm.select(Item.class).where().eq(Item::getId, 2L).firstOrNull();
        assertNotNull(db2);
        assertNull(db2.getTitle());
        assertEquals(Double.valueOf(99.9), db2.getPrice());
        assertEquals(Integer.valueOf(1), db2.getStatus());

        Item db3 = orm.select(Item.class).where().eq(Item::getId, 3L).firstOrNull();
        assertNotNull(db3);
        assertEquals("Item3", db3.getTitle());
        assertEquals(Double.valueOf(88.8), db3.getPrice());
        assertEquals(Integer.valueOf(1), db3.getStatus());
    }

    @Test
    public void testStrictKeepOrderBatchInsertWithDefaults() {
        Item item10 = new Item();
        item10.setId(10L);
        item10.setTitle("Order10");

        Item item11 = new Item();
        item11.setId(11L);
        item11.setPrice(101.5);

        Item item12 = new Item();
        item12.setId(12L);
        item12.setTitle("Order12");

        // 严格保持顺序插入 (keepOrder=true)：严格遵从原始入参顺序，同时剔除 null 字段
        int rows = orm.insertBatch(Arrays.asList(item10, item11, item12))
                .keepOrder(true)
                .execute();
        assertEquals(3, rows);

        Item db10 = orm.select(Item.class).where().eq(Item::getId, 10L).firstOrNull();
        assertNotNull(db10);
        assertEquals("Order10", db10.getTitle());
        assertEquals(Double.valueOf(88.8), db10.getPrice()); // 默认值生效
        assertEquals(Integer.valueOf(1), db10.getStatus());  // 默认值生效

        Item db11 = orm.select(Item.class).where().eq(Item::getId, 11L).firstOrNull();
        assertNotNull(db11);
        assertEquals(Double.valueOf(101.5), db11.getPrice());
        assertEquals(Integer.valueOf(1), db11.getStatus());

        Item db12 = orm.select(Item.class).where().eq(Item::getId, 12L).firstOrNull();
        assertNotNull(db12);
        assertEquals("Order12", db12.getTitle());
        assertEquals(Double.valueOf(88.8), db12.getPrice());
    }

    @Test
    public void testEasyQuerySmartBatchUpdateSelective() {
        // 先插入两条基准数据
        Item item20 = new Item();
        item20.setId(20L);
        item20.setTitle("OldTitle20");
        item20.setPrice(200.0);
        item20.setStatus(10);

        Item item21 = new Item();
        item21.setId(21L);
        item21.setTitle("OldTitle21");
        item21.setPrice(210.0);
        item21.setStatus(10);

        orm.insertBatch(Arrays.asList(item20, item21)).execute();

        // 准备更新：item20 仅更新 title；item21 仅更新 price
        Item update20 = new Item();
        update20.setId(20L);
        update20.setTitle("NewTitle20"); // price 和 status 为 null，不能覆盖数据库已有值

        Item update21 = new Item();
        update21.setId(21L);
        update21.setPrice(999.0); // title 和 status 为 null，不能覆盖数据库已有值

        // EasyQuery 智能批处理更新 (selective=true)
        int updated = orm.updateBatch(Arrays.asList(update20, update21)).execute();
        assertEquals(2, updated);

        Item after20 = orm.select(Item.class).where().eq(Item::getId, 20L).firstOrNull();
        assertNotNull(after20);
        assertEquals("NewTitle20", after20.getTitle());
        assertEquals(Double.valueOf(200.0), after20.getPrice()); // 原值未被覆盖！
        assertEquals(Integer.valueOf(10), after20.getStatus());  // 原值未被覆盖！

        Item after21 = orm.select(Item.class).where().eq(Item::getId, 21L).firstOrNull();
        assertNotNull(after21);
        assertEquals("OldTitle21", after21.getTitle());         // 原值未被覆盖！
        assertEquals(Double.valueOf(999.0), after21.getPrice()); // 成功更新
        assertEquals(Integer.valueOf(10), after21.getStatus());
    }

    @Test
    public void testStrictKeepOrderBatchUpdateSelective() {
        Item item30 = new Item();
        item30.setId(30L);
        item30.setTitle("Old30");
        item30.setPrice(300.0);

        Item item31 = new Item();
        item31.setId(31L);
        item31.setTitle("Old31");
        item31.setPrice(310.0);

        orm.insertBatch(Arrays.asList(item30, item31)).execute();

        Item u30 = new Item();
        u30.setId(30L);
        u30.setPrice(666.0);

        Item u31 = new Item();
        u31.setId(31L);
        u31.setTitle("New31");

        int updated = orm.updateBatch(Arrays.asList(u30, u31))
                .keepOrder(true)
                .execute();
        assertEquals(2, updated);

        Item after30 = orm.select(Item.class).where().eq(Item::getId, 30L).firstOrNull();
        assertNotNull(after30);
        assertEquals("Old30", after30.getTitle());
        assertEquals(Double.valueOf(666.0), after30.getPrice());

        Item after31 = orm.select(Item.class).where().eq(Item::getId, 31L).firstOrNull();
        assertNotNull(after31);
        assertEquals("New31", after31.getTitle());
        assertEquals(Double.valueOf(310.0), after31.getPrice());
    }

    @Test
    public void testForceOrderFluentSwitch() {
        Item item40 = new Item();
        item40.setId(40L);
        item40.setTitle("Item40");

        Item item41 = new Item();
        item41.setId(41L);
        item41.setPrice(444.0);

        // 使用 .keepOrder() 链式调用
        int rows = orm.insertBatch(Arrays.asList(item40, item41))
                .keepOrder()
                .execute();
        assertEquals(2, rows);

        Item db40 = orm.select(Item.class).where().eq(Item::getId, 40L).firstOrNull();
        assertNotNull(db40);
        assertEquals("Item40", db40.getTitle());
        assertEquals(Double.valueOf(88.8), db40.getPrice()); // 默认值生效
    }

    @Test
    public void testGlobalConfigBatchKeepOrder() {
        com.javaorm.core.config.OrmConfig cfg = new com.javaorm.core.config.OrmConfig();
        cfg.setBatchKeepOrder(true); // 全局开启严格保序

        JavaOrm orderedOrm = JavaOrm.create(dataSource, new SQLiteDialect(), cfg);

        Item item50 = new Item();
        item50.setId(50L);
        item50.setTitle("Item50");

        Item item51 = new Item();
        item51.setId(51L);
        item51.setPrice(555.0);

        // 验证即使直接调用 orm.insertBatch(list)，也会继承全局的 batchKeepOrder = true
        int rows = orderedOrm.insertBatch(Arrays.asList(item50, item51)).exec();
        assertEquals(2, rows);

        Item db50 = orderedOrm.select(Item.class).where().eq(Item::getId, 50L).firstOrNull();
        assertNotNull(db50);
        assertEquals("Item50", db50.getTitle());
        assertEquals(Double.valueOf(88.8), db50.getPrice());
    }
}
