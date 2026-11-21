package com.javaorm.core.test;

import com.javaorm.core.JavaOrm;
import com.javaorm.core.annotation.JoId;
import com.javaorm.core.annotation.JoTable;
import com.javaorm.core.annotation.RelationType;
import com.javaorm.core.bridge.MetadataBridgeRegistry;
import com.javaorm.core.bridge.RelationInfo;
import com.javaorm.core.config.OrmConfig;
import com.javaorm.core.dialect.H2Dialect;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.sql.DataSource;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

/**
 * 关联关系/导航注解桥接完整专项测试。
 * 验证 EasyQuery 风格注解（@Navigate）以及 JPA 风格注解（@ManyToOne / @OneToMany / @JoinColumn）
 * 能否通过 JavaORM 的 MetadataBridge SPI 自动解析，并正确驱动 selectAutoInclude 自动装配。
 */
public class RelationMetadataBridgeTest {

    // =========================================================================
    // 1. EasyQuery 仿真注解定义 (无需在 pom 中引入真实外部 jar，模拟其包路径与属性)
    // =========================================================================

    public enum EasyRelationTypeEnum {
        OneToOne,
        OneToMany,
        ManyToOne,
        ManyToMany
    }

    @Target({ElementType.FIELD, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface MockEasyNavigate {
        EasyRelationTypeEnum value() default EasyRelationTypeEnum.ManyToOne;
        String selfProperty() default "";
        String targetProperty() default "";
        Class<?> mappingClass() default void.class;
        String selfMappingProperty() default "";
        String targetMappingProperty() default "";
        String extraFilter() default "";
    }

    // =========================================================================
    // 2. JPA 仿真注解定义
    // =========================================================================

    @Target({ElementType.FIELD, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface MockJpaManyToOne {
        Class<?> targetEntity() default void.class;
    }

    @Target({ElementType.FIELD, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface MockJpaOneToMany {
        String mappedBy() default "";
        Class<?> targetEntity() default void.class;
    }

    @Target({ElementType.FIELD, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface MockJpaJoinColumn {
        String name() default "";
        String referencedColumnName() default "";
    }

    // =========================================================================
    // 3. 实体类定义 (分别使用 EasyQuery 注解与 JPA 注解)
    // =========================================================================

    @JoTable(name = "t_bridge_user")
    public static class BridgeUser {
        @JoId
        private Long id;
        private String name;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    /**
     * 使用仿真 EasyQuery @Navigate 注解的实体
     */
    @JoTable(name = "t_bridge_order")
    public static class EasyOrderEntity {
        @JoId
        private Long id;
        private Long userId;
        private String orderNo;
        private BigDecimal amount;

        @MockEasyNavigate(value = EasyRelationTypeEnum.ManyToOne, selfProperty = "userId", targetProperty = "id")
        private BridgeUser user;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getOrderNo() { return orderNo; }
        public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public BridgeUser getUser() { return user; }
        public void setUser(BridgeUser user) { this.user = user; }
    }

    /**
     * 使用仿真 JPA 注解的实体
     */
    @JoTable(name = "t_bridge_order")
    public static class JpaOrderEntity {
        @JoId
        private Long id;
        private Long userId;
        private String orderNo;

        @MockJpaManyToOne
        @MockJpaJoinColumn(name = "user_id", referencedColumnName = "id")
        private BridgeUser user;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getOrderNo() { return orderNo; }
        public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
        public BridgeUser getUser() { return user; }
        public void setUser(BridgeUser user) { this.user = user; }
    }

    // =========================================================================
    // 4. DTO 定义 (非实体)
    // =========================================================================

    public static class BridgeUserDto {
        private Long id;
        private String name;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    public static class EasyOrderDto {
        private Long id;
        private String orderNo;
        private BridgeUserDto user;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getOrderNo() { return orderNo; }
        public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
        public BridgeUserDto getUser() { return user; }
        public void setUser(BridgeUserDto user) { this.user = user; }
    }

    /**
     * DTO 自身直接标注 @Navigate 的场景
     */
    public static class DtoWithDirectNavigate {
        private String orderNo;

        @MockEasyNavigate(value = EasyRelationTypeEnum.ManyToOne, selfProperty = "userId", targetProperty = "id")
        private BridgeUserDto user;

        public String getOrderNo() { return orderNo; }
        public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
        public BridgeUserDto getUser() { return user; }
        public void setUser(BridgeUserDto user) { this.user = user; }
    }

    // =========================================================================
    // 5. 初始化与数据准备
    // =========================================================================

    private DataSource dataSource;
    private JavaOrm orm;

    @Before
    public void setup() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:bridge_test;DB_CLOSE_DELAY=-1;MODE=MySQL");
        ds.setUser("sa");
        ds.setPassword("");
        this.dataSource = ds;
        this.orm = JavaOrm.create(dataSource, new H2Dialect(), new OrmConfig().setPrintSql(false));

        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS \"t_bridge_order\"");
            stmt.execute("DROP TABLE IF EXISTS \"t_bridge_user\"");

            stmt.execute("CREATE TABLE \"t_bridge_user\" (\"id\" BIGINT PRIMARY KEY, \"name\" VARCHAR(64))");
            stmt.execute("CREATE TABLE \"t_bridge_order\" (\"id\" BIGINT PRIMARY KEY, \"user_id\" BIGINT, \"order_no\" VARCHAR(64), \"amount\" DECIMAL(10,2))");

            stmt.execute("INSERT INTO \"t_bridge_user\" VALUES (1, 'Alice'), (2, 'Bob')");
            stmt.execute("INSERT INTO \"t_bridge_order\" VALUES (101, 1, 'ORD-EASY-1', 99.00), (102, 2, 'ORD-EASY-2', 199.00)");
        }
    }

    // =========================================================================
    // 6. 测试用例
    // =========================================================================

    /**
     * 1. 验证 MetadataBridge SPI 能直接解析 EasyQuery 风格的 @Navigate 注解
     */
    @Test
    public void testBridgeResolveEasyQueryNavigate() throws Exception {
        java.lang.reflect.Field field = EasyOrderEntity.class.getDeclaredField("user");
        RelationInfo info = MetadataBridgeRegistry.resolveRelation(EasyOrderEntity.class, field, null);

        Assert.assertNotNull("RelationInfo must not be null for @Navigate", info);
        Assert.assertEquals(RelationType.MANY_TO_ONE, info.getRelationType());
        Assert.assertEquals(BridgeUser.class, info.getTargetEntityClass());
        Assert.assertEquals("userId", info.getSelfProperty());
        Assert.assertEquals("id", info.getTargetProperty());
    }

    /**
     * 2. 验证 MetadataBridge SPI 能直接解析 JPA 风格的 @ManyToOne + @JoinColumn 注解
     */
    @Test
    public void testBridgeResolveJpaManyToOne() throws Exception {
        java.lang.reflect.Field field = JpaOrderEntity.class.getDeclaredField("user");
        RelationInfo info = MetadataBridgeRegistry.resolveRelation(JpaOrderEntity.class, field, null);

        Assert.assertNotNull("RelationInfo must not be null for JPA ManyToOne", info);
        Assert.assertEquals(RelationType.MANY_TO_ONE, info.getRelationType());
        Assert.assertEquals(BridgeUser.class, info.getTargetEntityClass());
        Assert.assertEquals("user_id", info.getSelfProperty());
        Assert.assertEquals("id", info.getTargetProperty());
    }

    /**
     * 3. 验证 EasyQuery @Navigate 驱动 selectAutoInclude 真实执行与数据水合
     */
    @Test
    public void testSelectAutoIncludeWithEasyQueryAnnotation() {
        EasyOrderEntity eo = orm.proxy(EasyOrderEntity.class);
        List<EasyOrderDto> list = orm.select(EasyOrderEntity.class)
                .orderBy(eo.getId()).asc()
                .selectAutoInclude(EasyOrderDto.class)
                .list();

        Assert.assertNotNull(list);
        Assert.assertEquals(2, list.size());

        EasyOrderDto o1 = list.get(0);
        Assert.assertEquals("ORD-EASY-1", o1.getOrderNo());
        Assert.assertNotNull("User DTO must be auto included via bridge", o1.getUser());
        Assert.assertEquals("Alice", o1.getUser().getName());

        EasyOrderDto o2 = list.get(1);
        Assert.assertEquals("ORD-EASY-2", o2.getOrderNo());
        Assert.assertNotNull(o2.getUser());
        Assert.assertEquals("Bob", o2.getUser().getName());
    }

    /**
     * 4. 验证 DTO 自身直接标注 @Navigate 时的精准装配
     */
    @Test
    public void testSelectAutoIncludeWithDirectDtoNavigate() {
        List<DtoWithDirectNavigate> list = orm.sql(e -> {
            EasyOrderEntity o = e.table(EasyOrderEntity.class);
            e.select(EasyOrderEntity.class).orderBy(o.getId()).asc();
        }).selectAutoInclude(DtoWithDirectNavigate.class).list();

        Assert.assertNotNull(list);
        Assert.assertEquals(2, list.size());
        Assert.assertEquals("ORD-EASY-1", list.get(0).getOrderNo());
        Assert.assertNotNull(list.get(0).getUser());
        Assert.assertEquals("Alice", list.get(0).getUser().getName());
    }
}
