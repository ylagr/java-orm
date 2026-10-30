package com.javaorm.core.test;

import com.javaorm.core.JavaOrm;
import com.javaorm.core.annotation.*;
import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.dialect.H2Dialect;
import com.javaorm.core.metadata.ColumnMetadata;
import com.javaorm.core.metadata.EntityMetadata;
import com.javaorm.core.metadata.EntityMetadataManager;
import com.javaorm.core.metadata.ValueObjectMetadata;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Before;
import org.junit.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * ValueObject（嵌入式值对象）全面功能测试套件：
 * 验证对标 EasyQuery 规范的 @JoValueObject 支持：
 * 1. 递归元数据解析与平铺
 * 2. 字段直接映射（对标 EasyQuery 默认，无前缀，支持内部 @JoColumn 自定义）
 * 3. 嵌套 ValueObject 支持
 * 4. DDD 空值还原规则（所有平铺字段为 NULL 则 VO 保持为 null）
 * 5. 整对象查询展开（e.select(c.getId(), c.getAddress())）
 * 6. 细粒度叶子属性查询与条件过滤（e.select(c.getAddress().getCity()).where().and(...)）
 * 7. INSERT 与 UPDATE 实体持久化
 */
public class ValueObjectTest {

    private DataSource dataSource;
    private JavaOrm orm;

    public static class Area {
        private String code;
        private String name;

        public Area() {
        }

        public Area(String code, String name) {
            this.code = code;
            this.name = name;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class Address {
        private String province;
        private String city;

        @JoColumn("street_name") // 自定义物理列名
        private String street;

        @JoValueObject // 嵌套 ValueObject
        private Area area;

        public Address() {
        }

        public Address(String province, String city, String street, Area area) {
            this.province = province;
            this.city = city;
            this.street = street;
            this.area = area;
        }

        public String getProvince() {
            return province;
        }

        public void setProvince(String province) {
            this.province = province;
        }

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }

        public String getStreet() {
            return street;
        }

        public void setStreet(String street) {
            this.street = street;
        }

        public Area getArea() {
            return area;
        }

        public void setArea(Area area) {
            this.area = area;
        }
    }

    @JoTable("t_company")
    public static class Company {
        @JoId
        private Long id;
        private String companyName;

        @JoValueObject // 顶层 ValueObject
        private Address address;

        public Company() {
        }

        public Company(Long id, String companyName, Address address) {
            this.id = id;
            this.companyName = companyName;
            this.address = address;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getCompanyName() {
            return companyName;
        }

        public void setCompanyName(String companyName) {
            this.companyName = companyName;
        }

        public Address getAddress() {
            return address;
        }

        public void setAddress(Address address) {
            this.address = address;
        }
    }

    @Before
    public void setup() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:vo_test_db;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        this.dataSource = ds;
        this.orm = JavaOrm.create(dataSource, new H2Dialect(), new OrmConfig().setPrintSql(false));

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS \"t_company\"");
            stmt.execute("CREATE TABLE \"t_company\" (" +
                    "\"id\" BIGINT PRIMARY KEY, " +
                    "\"company_name\" VARCHAR(100), " +
                    "\"province\" VARCHAR(50), " +
                    "\"city\" VARCHAR(50), " +
                    "\"street_name\" VARCHAR(100), " +
                    "\"code\" VARCHAR(50), " +
                    "\"name\" VARCHAR(50)" +
                    ")");

            // 预植入初始数据
            stmt.execute("INSERT INTO \"t_company\" VALUES (1, 'Tencent', 'Guangdong', 'Shenzhen', 'Hi-Tech Park St', '518000', 'Nanshan')");
            stmt.execute("INSERT INTO \"t_company\" VALUES (2, 'Alibaba', 'Zhejiang', 'Hangzhou', 'Yu Hang St', '310000', 'Yuhang')");
            stmt.execute("INSERT INTO \"t_company\" VALUES (3, 'NullAddressCorp', NULL, NULL, NULL, NULL, NULL)");
            stmt.execute("INSERT INTO \"t_company\" VALUES (4, 'PartialAddressCorp', 'Beijing', 'Beijing', NULL, NULL, NULL)");
        }
    }

    /**
     * 1. 验证元数据解析：平铺列与 ValueObject 层次结构
     */
    @Test
    public void testValueObjectMetadata() {
        EntityMetadata metadata = EntityMetadataManager.get(Company.class);
        assertNotNull(metadata);
        assertTrue(metadata.hasValueObjects());

        // 顶层 VO 与嵌套 VO
        ValueObjectMetadata addrVo = metadata.getValueObject("address");
        assertNotNull(addrVo);
        assertEquals(Address.class, addrVo.getValueObjectClass());

        ValueObjectMetadata areaVo = metadata.getValueObject("address.area");
        assertNotNull(areaVo);
        assertEquals(Area.class, areaVo.getValueObjectClass());

        // 验证叶子列
        ColumnMetadata provinceCol = metadata.getColumnByProperty("address.province");
        assertNotNull(provinceCol);
        assertEquals("province", provinceCol.getColumnName());
        assertTrue(provinceCol.isValueObjectProperty());

        ColumnMetadata streetCol = metadata.getColumnByProperty("address.street");
        assertNotNull(streetCol);
        assertEquals("street_name", streetCol.getColumnName());

        ColumnMetadata areaNameCol = metadata.getColumnByProperty("address.area.name");
        assertNotNull(areaNameCol);
        assertEquals("name", areaNameCol.getColumnName());
    }

    private Company findById(Long id) {
        return orm.select(Company.class).whereById(id).first();
    }

    /**
     * 2. 测试查询实体并级联装配 ValueObject
     */
    @Test
    public void testQueryEntityWithNestedValueObject() {
        Company company = findById(1L);
        assertNotNull(company);
        assertEquals("Tencent", company.getCompanyName());

        Address address = company.getAddress();
        assertNotNull(address);
        assertEquals("Guangdong", address.getProvince());
        assertEquals("Shenzhen", address.getCity());
        assertEquals("Hi-Tech Park St", address.getStreet());

        Area area = address.getArea();
        assertNotNull(area);
        assertEquals("518000", area.getCode());
        assertEquals("Nanshan", area.getName());
    }

    /**
     * 3. 验证 DDD 空值还原规则：
     * 当平铺字段全部为 NULL 时，宿主实体的 VO 属性必须为 null
     */
    @Test
    public void testDddNullHydrationAllNull() {
        Company company = findById(3L);
        assertNotNull(company);
        assertEquals("NullAddressCorp", company.getCompanyName());
        assertNull("All VO columns are NULL in DB, address should be null", company.getAddress());
    }

    /**
     * 4. 验证 DDD 部分空值还原规则：
     * 当部分平铺字段为非空时，创建 VO 实例；若嵌套 VO 全部为空，嵌套 VO 为 null
     */
    @Test
    public void testDddNullHydrationPartial() {
        Company company = findById(4L);
        assertNotNull(company);
        assertEquals("PartialAddressCorp", company.getCompanyName());

        Address address = company.getAddress();
        assertNotNull("Partial VO columns are not null, address should be instantiated", address);
        assertEquals("Beijing", address.getProvince());
        assertEquals("Beijing", address.getCity());
        assertNull(address.getStreet());
        assertNull("All nested Area columns are null in DB, area should be null", address.getArea());
    }

    /**
     * 5. 验证 INSERT 持久化包含 ValueObject 的实体
     */
    @Test
    public void testInsertEntityWithValueObject() {
        Area area = new Area("200000", "Pudong");
        Address address = new Address("Shanghai", "Shanghai", "Century Ave", area);
        Company company = new Company(5L, "Bilibili", address);

        int rows = orm.insert(company);
        assertEquals(1, rows);

        Company queried = findById(5L);
        assertNotNull(queried);
        assertEquals("Bilibili", queried.getCompanyName());
        assertNotNull(queried.getAddress());
        assertEquals("Century Ave", queried.getAddress().getStreet());
        assertNotNull(queried.getAddress().getArea());
        assertEquals("Pudong", queried.getAddress().getArea().getName());
    }

    /**
     * 6. 验证 INSERT 空 VO 实体
     */
    @Test
    public void testInsertEntityWithNullValueObject() {
        Company company = new Company(6L, "ByteDance", null);
        int rows = orm.insert(company);
        assertEquals(1, rows);

        Company queried = findById(6L);
        assertNotNull(queried);
        assertEquals("ByteDance", queried.getCompanyName());
        assertNull(queried.getAddress());
    }

    /**
     * 7. 验证 UPDATE 修改 ValueObject 属性
     */
    @Test
    public void testUpdateEntityWithValueObject() {
        Company company = findById(1L);
        assertNotNull(company);
        company.getAddress().setCity("Dongguan");
        company.getAddress().getArea().setName("Songshan Lake");

        int rows = orm.update(company);
        assertEquals(1, rows);

        Company queried = findById(1L);
        assertNotNull(queried);
        assertEquals("Dongguan", queried.getAddress().getCity());
        assertEquals("Songshan Lake", queried.getAddress().getArea().getName());
    }

    /**
     * 8. 验证整对象查询展开：e.select(c.getId(), c.getAddress())
     * 自动展开 Address 及其嵌套 Area 的所有平铺叶子物理列
     */
    @Test
    public void testSelectWholeValueObjectExpansion() {
        List<Map<String, Object>> rows = orm.sql(e -> {
            Company c = e.table(Company.class);
            e.select(c.getId(), c.getAddress())
                    .from(c)
                    .where().and(c.getId()).eq(1L);
        }).listMaps();

        assertEquals(1, rows.size());
        Map<String, Object> row = rows.get(0);
        // 验证展开后的物理字段均被选取
        assertEquals(1L, ((Number) row.get("id")).longValue());
        assertTrue(row.containsKey("province") || row.containsKey("PROVINCE"));
        assertTrue(row.containsKey("city") || row.containsKey("CITY"));
        assertTrue(row.containsKey("street_name") || row.containsKey("STREET_NAME"));
        assertTrue(row.containsKey("code") || row.containsKey("CODE"));
        assertTrue(row.containsKey("name") || row.containsKey("NAME"));
    }

    /**
     * 9. 验证细粒度叶子属性 SELECT 与 WHERE 过滤（单表平铺无 JOIN）
     */
    @Test
    public void testSelectAndWhereLeafProperty() {
        List<Company> list = orm.sql(e -> {
            Company c = e.table(Company.class);
            e.select(c)
                    .from(c)
                    .where()
                    .and(c.getAddress().getCity()).eq("Shenzhen")
                    .and(c.getAddress().getArea().getCode()).eq("518000")
                    .orderBy(c.getAddress().getStreet()).asc();
        }).toList();

        assertEquals(1, list.size());
        assertEquals("Tencent", list.get(0).getCompanyName());
    }

    /**
     * 10. 验证指定整嵌套 VO 查询：e.select(c.getId(), c.getAddress().getArea())
     */
    @Test
    public void testSelectWholeNestedValueObject() {
        List<Map<String, Object>> rows = orm.sql(e -> {
            Company c = e.table(Company.class);
            e.select(c.getId(), c.getAddress().getArea())
                    .from(c)
                    .where().and(c.getId()).eq(2L);
        }).listMaps();

        assertEquals(1, rows.size());
        Map<String, Object> row = rows.get(0);
        assertEquals(2L, ((Number) row.get("id")).longValue());
        assertTrue(row.containsKey("code") || row.containsKey("CODE"));
        assertTrue(row.containsKey("name") || row.containsKey("NAME"));
        assertFalse(row.containsKey("province") || row.containsKey("PROVINCE"));
    }
}
