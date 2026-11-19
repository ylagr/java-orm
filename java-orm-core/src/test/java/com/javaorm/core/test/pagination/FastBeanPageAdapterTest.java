package com.javaorm.core.test.pagination;

import com.javaorm.core.JavaOrm;
import com.javaorm.core.annotation.JoId;
import com.javaorm.core.annotation.JoTable;
import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.dialect.H2Dialect;
import com.javaorm.core.pagination.FastBeanPageAdapter;
import com.javaorm.core.pagination.PageAdapterRegistry;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * FastBeanPageAdapter 测试套件：
 * 验证对任意第三方分页对象（非内置、非 IPage）的 FastBean 机制探测、类型缓存与零反射高性能调用。
 */
public class FastBeanPageAdapterTest {

    private JavaOrm orm;

    @JoTable(name = "t_item")
    public static class Item {
        @JoId
        private Long id;
        private String name;
        private Integer price;

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

        public Integer getPrice() {
            return price;
        }

        public void setPrice(Integer price) {
            this.price = price;
        }
    }

    /**
     * 自定义第三方分页类 1：标准命名 (pageNum, pageSize, total, records)
     */
    public static class StandardCustomPage<T> {
        private long pageNum = 1;
        private long pageSize = 10;
        private long total;
        private List<T> records;

        public StandardCustomPage(long pageNum, long pageSize) {
            this.pageNum = pageNum;
            this.pageSize = pageSize;
        }

        public long getPageNum() {
            return pageNum;
        }

        public void setPageNum(long pageNum) {
            this.pageNum = pageNum;
        }

        public long getPageSize() {
            return pageSize;
        }

        public void setPageSize(long pageSize) {
            this.pageSize = pageSize;
        }

        public long getTotal() {
            return total;
        }

        public void setTotal(long total) {
            this.total = total;
        }

        public List<T> getRecords() {
            return records;
        }

        public void setRecords(List<T> records) {
            this.records = records;
        }
    }

    /**
     * 自定义第三方分页类 2：别名命名与 int 类型总数 (current, size, totalCount, data)
     */
    public static class AlternateCustomPage<T> {
        private int current = 1;
        private int size = 10;
        private int totalCount;
        private List<T> data;

        public AlternateCustomPage(int current, int size) {
            this.current = current;
            this.size = size;
        }

        public int getCurrent() {
            return current;
        }

        public void setCurrent(int current) {
            this.current = current;
        }

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = size;
        }

        public int getTotalCount() {
            return totalCount;
        }

        public void setTotalCount(int totalCount) {
            this.totalCount = totalCount;
        }

        public List<T> getData() {
            return data;
        }

        public void setData(List<T> data) {
            this.data = data;
        }
    }

    @Before
    public void setup() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:fast_page_test;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");

        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS \"t_item\"");
            stmt.execute("CREATE TABLE \"t_item\" (\"id\" BIGINT PRIMARY KEY, \"name\" VARCHAR(100), \"price\" INT)");

            for (int i = 1; i <= 25; i++) {
                stmt.execute(String.format("INSERT INTO \"t_item\" VALUES (%d, 'Item_%d', %d)", i, i, i * 10));
            }
        }

        this.orm = JavaOrm.create(ds, new H2Dialect(), new OrmConfig().setPrintSql(false));
    }

    @Test
    public void testStandardCustomPageWithFastBean() {
        StandardCustomPage<Item> page = new StandardCustomPage<>(2, 5);

        // 调用 orm.select(...).toPage(page)
        StandardCustomPage<Item> result = orm.select(Item.class)
                .where()
                .gt(Item::getPrice, 50)
                .toPage(page);

        Assert.assertSame(page, result);
        Assert.assertEquals(20, result.getTotal()); // > 50 的有 20 条 (6..25)
        Assert.assertNotNull(result.getRecords());
        Assert.assertEquals(5, result.getRecords().size());
        Assert.assertEquals("Item_11", result.getRecords().get(0).getName());
    }

    @Test
    public void testSqlEnvWithAlternateCustomPage() {
        AlternateCustomPage<Item> page = new AlternateCustomPage<>(3, 5);

        // 调用 orm.sql(e -> ...).toPage(page)
        AlternateCustomPage<Item> result = orm.sql(e -> {
            Item item = e.table(Item.class);
            e.select(item).where().gt(item.getPrice(), 50);
        }).toPage(page);

        Assert.assertSame(page, result);
        Assert.assertEquals(20, result.getTotalCount());
        Assert.assertNotNull(result.getData());
        Assert.assertEquals(5, result.getData().size());
        Assert.assertEquals("Item_16", result.getData().get(0).getName());
    }

    @Test
    public void testTypeCachingMechanism() {
        FastBeanPageAdapter.clearCache();
        Assert.assertEquals(0, FastBeanPageAdapter.getCachedCount());

        // 第一次调用 StandardCustomPage 实例：触发预热并缓存
        StandardCustomPage<Item> p1 = new StandardCustomPage<>(1, 3);
        orm.select(Item.class).toPage(p1);
        Assert.assertEquals(1, FastBeanPageAdapter.getCachedCount());

        // 第二次调用 StandardCustomPage 实例：命中已有缓存，缓存数量不增加
        StandardCustomPage<Item> p2 = new StandardCustomPage<>(2, 3);
        orm.select(Item.class).toPage(p2);
        Assert.assertEquals(1, FastBeanPageAdapter.getCachedCount());

        // 第三次调用另一个类型 AlternateCustomPage：新增第二个缓存
        AlternateCustomPage<Item> p3 = new AlternateCustomPage<>(1, 3);
        orm.select(Item.class).toPage(p3);
        Assert.assertEquals(2, FastBeanPageAdapter.getCachedCount());

        // 再次调用 AlternateCustomPage：仍然保持为 2
        AlternateCustomPage<Item> p4 = new AlternateCustomPage<>(2, 3);
        orm.select(Item.class).toPage(p4);
        Assert.assertEquals(2, FastBeanPageAdapter.getCachedCount());
    }
}
