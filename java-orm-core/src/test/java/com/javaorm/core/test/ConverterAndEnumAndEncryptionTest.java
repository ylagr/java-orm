package com.javaorm.core.test;

import com.javaorm.core.JavaOrm;
import com.javaorm.core.annotation.*;
import com.javaorm.core.converter.*;
import com.javaorm.core.metadata.ColumnMetadata;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Before;
import org.junit.Test;

import javax.sql.DataSource;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.*;

import static org.junit.Assert.*;

/**
 * 字段值转换器、高性能枚举、敏感数据加密与 JSON 处理完整测试套件。
 */
public class ConverterAndEnumAndEncryptionTest {

    private DataSource dataSource;
    private JavaOrm orm;

    // --- 1. 自定义值转换器 ---
    public static class BooleanToIntConverter implements ValueConverter<Boolean, Integer> {
        @Override
        public Integer serialize(Boolean value, ColumnMetadata metadata) {
            if (value == null) return null;
            return value ? 1 : 0;
        }

        @Override
        public Boolean deserialize(Integer dbValue, ColumnMetadata metadata) {
            if (dbValue == null) return null;
            return dbValue == 1;
        }
    }

    // --- 2. 内部枚举（@JoEnumValue 零侵入零反射） ---
    public enum UserStatus {
        ACTIVE(1001, "已激活"),
        DISABLED(1002, "已冻结");

        @JoEnumValue
        private final int code;
        private final String description;

        UserStatus(int code, String description) {
            this.code = code;
            this.description = description;
        }

        public int getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }
    }

    // --- 3. 第三方不可修改枚举（通过 @JoEnum 配置） ---
    public enum ThirdPartyRole {
        ADMIN("R_ADMIN"),
        USER("R_USER"),
        GUEST("R_GUEST"),
        UNKNOWN("R_UNKNOWN");

        private final String roleCode;

        ThirdPartyRole(String roleCode) {
            this.roleCode = roleCode;
        }

        public String getRoleCode() {
            return roleCode;
        }
    }

    // --- 4. AES 加密策略 ---
    public static class TestAesStrategy extends AesBase64EncryptionStrategy {
        private static final byte[] KEY = "1234567890123456".getBytes(StandardCharsets.UTF_8);

        @Override
        protected byte[] getKey() {
            return KEY;
        }
    }

    // --- 5. JSON 复杂对象与 Provider ---
    public static class UserProfile {
        private String bio;
        private int age;

        public UserProfile() {}
        public UserProfile(String bio, int age) {
            this.bio = bio;
            this.age = age;
        }
        public String getBio() { return bio; }
        public void setBio(String bio) { this.bio = bio; }
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }
    }

    public static class Hobby {
        private String name;
        private int level;

        public Hobby() {}
        public Hobby(String name, int level) {
            this.name = name;
            this.level = level;
        }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getLevel() { return level; }
        public void setLevel(int level) { this.level = level; }
    }

    /** 极简轻量级 JsonProvider 测试实现（避免在测试中硬引入大型依赖） */
    public static class SimpleTestJsonProvider implements JsonProvider {
        @Override
        public String toJson(Object obj) {
            if (obj == null) return null;
            if (obj instanceof UserProfile) {
                UserProfile p = (UserProfile) obj;
                return "bio=" + p.getBio() + ";age=" + p.getAge();
            }
            if (obj instanceof List) {
                List<?> list = (List<?>) obj;
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) sb.append("|");
                    Object item = list.get(i);
                    if (item instanceof Hobby) {
                        Hobby h = (Hobby) item;
                        sb.append(h.getName()).append(":").append(h.getLevel());
                    } else {
                        sb.append(item.toString());
                    }
                }
                return sb.toString();
            }
            return obj.toString();
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T fromJson(String json, Type type) {
            if (json == null) return null;
            if (type == UserProfile.class) {
                String[] parts = json.split(";");
                String bio = parts[0].split("=")[1];
                int age = Integer.parseInt(parts[1].split("=")[1]);
                return (T) new UserProfile(bio, age);
            }
            if (type instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType) type;
                if (pt.getRawType() == List.class) {
                    Type itemType = pt.getActualTypeArguments()[0];
                    String[] tokens = json.split("\\|");
                    List<Object> list = new ArrayList<>();
                    for (String token : tokens) {
                        if (itemType == Hobby.class) {
                            String[] hp = token.split(":");
                            list.add(new Hobby(hp[0], Integer.parseInt(hp[1])));
                        } else {
                            list.add(token);
                        }
                    }
                    return (T) list;
                }
            }
            return (T) json;
        }
    }

    // --- 6. 测试实体类 ---
    @JoTable("t_account")
    public static class Account {
        @JoId
        private Long id;

        private String username;

        // 显式绑定自定义转换器
        @JoConverter(BooleanToIntConverter.class)
        private Boolean enabled;

        // 内部枚举：带有 @JoEnumValue，零侵入
        private UserStatus status;

        // 第三方枚举：通过 @JoEnum 指定按属性提取，并提供未知值 fallback 兜底
        @JoEnum(property = "roleCode", fallback = "UNKNOWN")
        private ThirdPartyRole role;

        // 透明加解密敏感字段
        @JoEncrypt(TestAesStrategy.class)
        private String phone;

        // 复杂 POJO JSON
        @JoJson
        private UserProfile profile;

        // 泛型集合 JSON
        @JoJson
        private List<Hobby> hobbies;

        public Account() {}

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
        public UserStatus getStatus() { return status; }
        public void setStatus(UserStatus status) { this.status = status; }
        public ThirdPartyRole getRole() { return role; }
        public void setRole(ThirdPartyRole role) { this.role = role; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public UserProfile getProfile() { return profile; }
        public void setProfile(UserProfile profile) { this.profile = profile; }
        public List<Hobby> getHobbies() { return hobbies; }
        public void setHobbies(List<Hobby> hobbies) { this.hobbies = hobbies; }
    }

    @Before
    public void setUp() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:conv_test_" + System.currentTimeMillis() + ";DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        this.dataSource = ds;

        // 注册测试 JSON Provider
        JsonProviderManager.setDefaultProvider(new SimpleTestJsonProvider());

        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS \"t_account\"");
            stmt.execute("CREATE TABLE \"t_account\" (" +
                    "\"id\" BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "\"username\" VARCHAR(64), " +
                    "\"enabled\" INT, " +            // 转换器存为 INT (1/0)
                    "\"status\" INT, " +             // @JoEnumValue 存为 code (1001/1002)
                    "\"role\" VARCHAR(32), " +       // @JoEnum 存为 roleCode (R_ADMIN/R_USER)
                    "\"phone\" VARCHAR(255), " +     // AES 密文 Base64
                    "\"profile\" VARCHAR(255), " +   // JSON 字符串
                    "\"hobbies\" VARCHAR(255)" +     // 泛型 JSON 字符串
                    ")");
        }

        this.orm = JavaOrm.create(ds, new com.javaorm.core.dialect.H2Dialect(), new com.javaorm.core.config.OrmConfig());
    }

    /**
     * 1. 验证实体插入与底层物理字段转换（通过 listMaps 查看真实入库值）
     */
    @Test
    public void testInsertAndPhysicalStorage() {
        Account acc = new Account();
        acc.setUsername("alice");
        acc.setEnabled(true);
        acc.setStatus(UserStatus.ACTIVE);
        acc.setRole(ThirdPartyRole.ADMIN);
        acc.setPhone("13812345678");
        acc.setProfile(new UserProfile("Coder & Hacker", 25));
        acc.setHobbies(Arrays.asList(new Hobby("Basketball", 3), new Hobby("Guitar", 5)));

        int rows = orm.insert(acc);
        assertEquals(1, rows);
        assertNotNull(acc.getId());

        // 使用底层 Maps 直接查看 DB 物理存储形态
        Map<String, Object> map = orm.select(Account.class).where().eq("id", acc.getId()).maps().get(0);

        // a. @JoConverter: Boolean -> Integer (1)
        assertEquals(1, ((Number) map.get("enabled")).intValue());

        // b. @JoEnumValue: UserStatus.ACTIVE -> 1001 (int)
        assertEquals(1001, ((Number) map.get("status")).intValue());

        // c. @JoEnum(property = "roleCode"): ThirdPartyRole.ADMIN -> "R_ADMIN"
        assertEquals("R_ADMIN", map.get("role"));

        // d. @JoEncrypt: phone 不等于明文，且不是 null
        String dbPhone = (String) map.get("phone");
        assertNotNull(dbPhone);
        assertNotEquals("13812345678", dbPhone);

        // e. @JoJson: profile 序列化为字符串
        String dbProfile = (String) map.get("profile");
        assertTrue(dbProfile.contains("bio=Coder & Hacker"));
        assertTrue(dbProfile.contains("age=25"));

        // f. @JoJson: hobbies 序列化为字符串
        String dbHobbies = (String) map.get("hobbies");
        assertTrue(dbHobbies.contains("Basketball:3"));
        assertTrue(dbHobbies.contains("Guitar:5"));
    }

    /**
     * 2. 验证实体查询与反序列化还原（解密、还原 Enum、还原复杂 POJO 与泛型 List）
     */
    @Test
    public void testSelectAndHydration() {
        Account acc = new Account();
        acc.setUsername("bob");
        acc.setEnabled(false);
        acc.setStatus(UserStatus.DISABLED);
        acc.setRole(ThirdPartyRole.USER);
        acc.setPhone("13988889999");
        acc.setProfile(new UserProfile("Architect", 30));
        acc.setHobbies(Collections.singletonList(new Hobby("Reading", 4)));

        orm.insert(acc);

        // 实体查询
        Account selected = orm.select(Account.class).where().eq("id", acc.getId()).single();
        assertNotNull(selected);
        assertEquals("bob", selected.getUsername());
        assertEquals(Boolean.FALSE, selected.getEnabled());
        assertEquals(UserStatus.DISABLED, selected.getStatus());
        assertEquals(ThirdPartyRole.USER, selected.getRole());
        assertEquals("13988889999", selected.getPhone()); // 自动解密还原为明文

        assertNotNull(selected.getProfile());
        assertEquals("Architect", selected.getProfile().getBio());
        assertEquals(30, selected.getProfile().getAge());

        assertNotNull(selected.getHobbies());
        assertEquals(1, selected.getHobbies().size());
        assertEquals("Reading", selected.getHobbies().get(0).getName());
        assertEquals(4, selected.getHobbies().get(0).getLevel());
    }

    /**
     * 3. 验证 WHERE 查询条件中对参数的自动序列化（枚举转 code、手机号自动加密后匹配）
     */
    @Test
    public void testWhereConditionAutoSerialization() {
        Account acc = new Account();
        acc.setUsername("charlie");
        acc.setEnabled(true);
        acc.setStatus(UserStatus.ACTIVE);
        acc.setRole(ThirdPartyRole.ADMIN);
        acc.setPhone("13700001111");
        orm.insert(acc);

        Account $ = orm.proxy(Account.class);

        // a. 针对 @JoEnumValue 字段查询：直接传枚举对象，条件自动转为 status = 1001
        Account res1 = orm.select(Account.class)
                .where().eq($.getStatus(), UserStatus.ACTIVE)
                .single();
        assertNotNull(res1);
        assertEquals("charlie", res1.getUsername());

        // b. 针对 @JoEncrypt 字段查询：直接传明文手机号，条件自动加密后与 DB 密文精准等值匹配
        Account res2 = orm.select(Account.class)
                .where().eq($.getPhone(), "13700001111")
                .single();
        assertNotNull(res2);
        assertEquals("charlie", res2.getUsername());

        // c. 不匹配的手机号查不出数据
        Account res3 = orm.select(Account.class)
                .where().eq($.getPhone(), "13700002222")
                .single();
        assertNull(res3);

        // d. 针对 @JoConverter 字段查询：直接传 Boolean，自动转为 1 查出
        Account res4 = orm.select(Account.class)
                .where().eq($.getEnabled(), true)
                .single();
        assertNotNull(res4);
        assertEquals("charlie", res4.getUsername());

        // e. IN 集合查询自动批量序列化
        List<Account> res5 = orm.select(Account.class)
                .where().in($.getStatus(), Arrays.asList(UserStatus.ACTIVE, UserStatus.DISABLED))
                .list();
        assertFalse(res5.isEmpty());
    }

    /**
     * 4. 验证 UpdateBuilder 更新时转换器与加密自动生效
     */
    @Test
    public void testUpdateAutoSerialization() {
        Account acc = new Account();
        acc.setUsername("david");
        acc.setEnabled(false);
        acc.setStatus(UserStatus.DISABLED);
        acc.setPhone("13600000000");
        orm.insert(acc);

        Account $ = orm.proxy(Account.class);

        // 链式更新：更新 status 为 ACTIVE，更新 phone 为新号码
        int updated = orm.env().createUpdate(Account.class)
                .set($.getStatus(), UserStatus.ACTIVE)
                .set($.getPhone(), "13699999999")
                .where().eq($.getId(), acc.getId())
                .execute();
        assertEquals(1, updated);

        // 查询验证
        Account found = orm.select(Account.class).where().eq($.getId(), acc.getId()).single();
        assertEquals(UserStatus.ACTIVE, found.getStatus());
        assertEquals("13699999999", found.getPhone());

        // 查看底层物理存储确认已加密且 code 为 1001
        Map<String, Object> map = orm.select(Account.class).where().eq($.getId(), acc.getId()).maps().get(0);
        assertEquals(1001, ((Number) map.get("status")).intValue());
        assertNotEquals("13699999999", map.get("phone"));
    }

    /**
     * 5. 验证未知枚举值反序列化时的 fallback 兜底策略
     */
    @Test
    public void testEnumFallbackOnUnknownDbValue() throws Exception {
        // 直接用原始 SQL 插入一条脏数据：role 列存入未知编码 "R_SUPER_MASTER"
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO \"t_account\" (\"username\", \"role\") VALUES ('eve', 'R_SUPER_MASTER')");
        }

        // 实体查询：因为设置了 @JoEnum(fallback = "UNKNOWN")，必须安全兜底为 UNKNOWN，不抛异常也不为 null
        Account eve = orm.select(Account.class).where().eq("username", "eve").single();
        assertNotNull(eve);
        assertEquals(ThirdPartyRole.UNKNOWN, eve.getRole());
    }
}
