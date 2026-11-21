package com.javaorm.core.sqlmatrix;

import com.javaorm.core.pagination.PageResult;
import com.javaorm.core.query.SelectBuilder;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Consumer;

/**
 * Phase 11: include (实体关联装配) 与 selectAutoInclude (DTO 自动关联装配) 全方言全模式测试矩阵。
 * <p>
 * 严谨验证点：
 * 1. include ManyToOne / OneToMany / 多级嵌套装配：SQL 全文本精确断言 + MySQL/PG/Oracle 三大物理隔离模式真实数据装配校验；
 * 2. selectAutoInclude DTO 专属约束校验：严禁在 Entity 实体类上直接使用，强制抛出强校验异常；
 * 3. selectAutoInclude 字段裁剪投影（Selective Projection）：5 大方言全文本精确断言，仅查 DTO 标量列 + 关联 Key 列；
 * 4. selectAutoInclude 隐式关联键自动映射：即使 DTO 没声明外键/主键，也通过底层行数据上下文精准关联装配；
 * 5. selectAutoInclude 多层级递归级联装配（User -> Orders -> Items）：在所有数据库模式下真实执行并严格比对多级数据；
 * 6. 链式终结与分页支持：结合 where、orderBy、toPageResult、firstOrNull、single 在所有数据库模式下稳定运行。
 */
public class Phase11IncludeAndAutoIncludeMatrixTest extends AbstractSqlMatrixTest {

    // =========================================================================
    // DTO / VO 类定义（严禁标注 @JoTable）
    // =========================================================================

    public static class MatrixUserDto {
        private Long id;
        private String username;
        private String email;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }

    public static class MatrixOrderDto {
        private Long id;
        private String orderNo;
        private BigDecimal amount;
        private MatrixUserDto user;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getOrderNo() { return orderNo; }
        public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public MatrixUserDto getUser() { return user; }
        public void setUser(MatrixUserDto user) { this.user = user; }
    }

    public static class MatrixOrderItemDto {
        private Long id;
        private String prodName;
        private BigDecimal price;
        private Integer quantity;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getProdName() { return prodName; }
        public void setProdName(String prodName) { this.prodName = prodName; }
        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
    }

    public static class MatrixOrderWithItemsDto {
        private Long id;
        private String orderNo;
        private BigDecimal amount;
        private List<MatrixOrderItemDto> items;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getOrderNo() { return orderNo; }
        public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public List<MatrixOrderItemDto> getItems() { return items; }
        public void setItems(List<MatrixOrderItemDto> items) { this.items = items; }
    }

    /**
     * 3 级嵌套级联 DTO：User -> Orders -> Items
     */
    public static class MatrixUserWithOrdersAndItemsDto {
        private Long id;
        private String username;
        private List<MatrixOrderWithItemsDto> orders;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public List<MatrixOrderWithItemsDto> getOrders() { return orders; }
        public void setOrders(List<MatrixOrderWithItemsDto> orders) { this.orders = orders; }
    }

    /**
     * 隐式 Key 测试 DTO：不声明 userId 或 id 属性，依然精准回填关联 user
     */
    public static class MatrixOrderWithoutFkDto {
        private String orderNo;
        private BigDecimal amount;
        private MatrixUserDto user;

        public String getOrderNo() { return orderNo; }
        public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public MatrixUserDto getUser() { return user; }
        public void setUser(MatrixUserDto user) { this.user = user; }
    }

    // =========================================================================
    // 1. include 实体关联装配矩阵测试 (ManyToOne, OneToMany, 级联嵌套)
    // =========================================================================

    /**
     * 1.1 include ManyToOne 实体装配：orm.proxy(MatrixOrder.class) -> include(ord.getUser())
     * 校验点：
     * 1. 主 SQL 5 大方言全文本精确断言；
     * 2. 在 MySQL, PostgreSQL, Oracle 3 大真实隔离模式下执行，严格校验返回的每个订单及回填的用户数据。
     */
    @Test
    public void testEntityIncludeManyToOne() {
        assertMatrixSql(
                o -> {
                    MatrixOrder ord = o.proxy(MatrixOrder.class);
                    return o.select(MatrixOrder.class).orderBy(ord.getId()).asc().include(ord.getUser());
                },
                "SELECT t0.`id`, t0.`user_id`, t0.`order_no`, t0.`amount`, t0.`status`, t0.`create_time` FROM `matrix_order` t0 ORDER BY t0.`id` ASC",
                "SELECT t0.\"id\", t0.\"user_id\", t0.\"order_no\", t0.\"amount\", t0.\"status\", t0.\"create_time\" FROM \"matrix_order\" t0 ORDER BY t0.\"id\" ASC",
                "SELECT t0.\"ID\", t0.\"USER_ID\", t0.\"ORDER_NO\", t0.\"AMOUNT\", t0.\"STATUS\", t0.\"CREATE_TIME\" FROM \"MATRIX_ORDER\" t0 ORDER BY t0.\"ID\" ASC",
                "SELECT t0.\"id\", t0.\"user_id\", t0.\"order_no\", t0.\"amount\", t0.\"status\", t0.\"create_time\" FROM \"matrix_order\" t0 ORDER BY t0.\"id\" ASC",
                "SELECT t0.\"id\", t0.\"user_id\", t0.\"order_no\", t0.\"amount\", t0.\"status\", t0.\"create_time\" FROM \"matrix_order\" t0 ORDER BY t0.\"id\" ASC"
        );

        verifyAllModes(o -> {
            MatrixOrder ord = o.proxy(MatrixOrder.class);
            List<MatrixOrder> orders = o.select(MatrixOrder.class)
                    .orderBy(ord.getId()).asc()
                    .include(ord.getUser())
                    .list();

            Assert.assertNotNull(orders);
            Assert.assertEquals(4, orders.size());

            // ORD-001 (userId=1, alice)
            MatrixOrder o1 = orders.get(0);
            Assert.assertEquals("ORD-001", o1.getOrderNo());
            Assert.assertNotNull("Order 1 user must be hydrated", o1.getUser());
            Assert.assertEquals("alice", o1.getUser().getUsername());
            Assert.assertEquals("alice@example.com", o1.getUser().getEmail());

            // ORD-002 (userId=1, alice)
            MatrixOrder o2 = orders.get(1);
            Assert.assertEquals("ORD-002", o2.getOrderNo());
            Assert.assertNotNull(o2.getUser());
            Assert.assertEquals("alice", o2.getUser().getUsername());

            // ORD-003 (userId=2, bob)
            MatrixOrder o3 = orders.get(2);
            Assert.assertEquals("ORD-003", o3.getOrderNo());
            Assert.assertNotNull(o3.getUser());
            Assert.assertEquals("bob", o3.getUser().getUsername());

            // ORD-999 (userId=999, 无匹配用户)
            MatrixOrder o4 = orders.get(3);
            Assert.assertEquals("ORD-999", o4.getOrderNo());
            Assert.assertNull("Order 4 user should be null for non-existent userId 999", o4.getUser());
        });
    }

    /**
     * 1.2 include OneToMany 实体集合装配：orm.proxy(MatrixOrder.class) -> include(ord.getItems())
     */
    @Test
    public void testEntityIncludeOneToMany() {
        assertMatrixSql(
                o -> {
                    MatrixOrder ord = o.proxy(MatrixOrder.class);
                    return o.select(MatrixOrder.class).orderBy(ord.getId()).asc().include(ord.getItems());
                },
                "SELECT t0.`id`, t0.`user_id`, t0.`order_no`, t0.`amount`, t0.`status`, t0.`create_time` FROM `matrix_order` t0 ORDER BY t0.`id` ASC",
                "SELECT t0.\"id\", t0.\"user_id\", t0.\"order_no\", t0.\"amount\", t0.\"status\", t0.\"create_time\" FROM \"matrix_order\" t0 ORDER BY t0.\"id\" ASC",
                "SELECT t0.\"ID\", t0.\"USER_ID\", t0.\"ORDER_NO\", t0.\"AMOUNT\", t0.\"STATUS\", t0.\"CREATE_TIME\" FROM \"MATRIX_ORDER\" t0 ORDER BY t0.\"ID\" ASC",
                "SELECT t0.\"id\", t0.\"user_id\", t0.\"order_no\", t0.\"amount\", t0.\"status\", t0.\"create_time\" FROM \"matrix_order\" t0 ORDER BY t0.\"id\" ASC",
                "SELECT t0.\"id\", t0.\"user_id\", t0.\"order_no\", t0.\"amount\", t0.\"status\", t0.\"create_time\" FROM \"matrix_order\" t0 ORDER BY t0.\"id\" ASC"
        );

        verifyAllModes(o -> {
            MatrixOrder ord = o.proxy(MatrixOrder.class);
            List<MatrixOrder> orders = o.select(MatrixOrder.class)
                    .orderBy(ord.getId()).asc()
                    .include(ord.getItems())
                    .list();

            Assert.assertNotNull(orders);
            Assert.assertEquals(4, orders.size());

            // ORD-001 拥有 2 个明细项
            MatrixOrder o1 = orders.get(0);
            Assert.assertNotNull(o1.getItems());
            Assert.assertEquals(2, o1.getItems().size());
            Assert.assertEquals("Mechanical Keyboard", o1.getItems().get(0).getProdName());
            Assert.assertEquals("Wireless Mouse", o1.getItems().get(1).getProdName());

            // ORD-002 拥有 1 个明细项
            MatrixOrder o2 = orders.get(1);
            Assert.assertNotNull(o2.getItems());
            Assert.assertEquals(1, o2.getItems().size());
            Assert.assertEquals("4K Monitor", o2.getItems().get(0).getProdName());

            // ORD-003 无明细项（空集合）
            MatrixOrder o3 = orders.get(2);
            Assert.assertNotNull(o3.getItems());
            Assert.assertTrue(o3.getItems().isEmpty());

            // ORD-999 无明细项（空集合）
            MatrixOrder o4 = orders.get(3);
            Assert.assertNotNull(o4.getItems());
            Assert.assertTrue(o4.getItems().isEmpty());
        });
    }

    /**
     * 1.3 双向与多级嵌套实体装配：User -> Orders -> Items
     * 采用 EasyQuery 规范：单实体 include，集合实体 includes (复数) + (q, childProxy) -> ... 零强转规范
     */
    @Test
    public void testEntityIncludeMultiLevelNested() {
        verifyAllModes(o -> {
            MatrixUser u = o.proxy(MatrixUser.class);
            List<MatrixUser> users = o.select(MatrixUser.class)
                    .where().eq(u.getUsername(), "alice")
                    .includes(u.getOrders(), (q, ord) -> q.includes(ord.getItems()))
                    .list();

            Assert.assertNotNull(users);
            Assert.assertEquals(1, users.size());

            MatrixUser alice = users.get(0);
            Assert.assertEquals("alice", alice.getUsername());
            Assert.assertNotNull(alice.getOrders());
            Assert.assertEquals(2, alice.getOrders().size());

            // 检查 alice 的订单及各订单下的明细
            MatrixOrder firstOrder = alice.getOrders().stream().filter(ord -> "ORD-001".equals(ord.getOrderNo())).findFirst().orElse(null);
            Assert.assertNotNull(firstOrder);
            Assert.assertNotNull(firstOrder.getItems());
            Assert.assertEquals(2, firstOrder.getItems().size());

            MatrixOrder secondOrder = alice.getOrders().stream().filter(ord -> "ORD-002".equals(ord.getOrderNo())).findFirst().orElse(null);
            Assert.assertNotNull(secondOrder);
            Assert.assertNotNull(secondOrder.getItems());
            Assert.assertEquals(1, secondOrder.getItems().size());
        });
    }

    /**
     * 1.4 带有独立子条件过滤的 include 装配：只回填金额 >= 200 的订单
     * 采用 EasyQuery 集合 includes(relation, (q, ord) -> ...) 零强转规范
     */
    @Test
    public void testEntityIncludeWithCondition() {
        verifyAllModes(o -> {
            MatrixUser u = o.proxy(MatrixUser.class);
            List<MatrixUser> users = o.select(MatrixUser.class)
                    .where().eq(u.getUsername(), "alice")
                    .includes(u.getOrders(), (q, ord) -> q.where().ge(ord.getAmount(), new BigDecimal("200.00")))
                    .list();

            Assert.assertNotNull(users);
            Assert.assertEquals(1, users.size());

            MatrixUser alice = users.get(0);
            Assert.assertNotNull(alice.getOrders());
            // ORD-001 (199.90) 被过滤，只有 ORD-002 (299.00) 被回填
            Assert.assertEquals(1, alice.getOrders().size());
            Assert.assertEquals("ORD-002", alice.getOrders().get(0).getOrderNo());
        });
    }

    /**
     * 1.5 ManyToOne 带有独立子条件过滤的 include 装配：只有指定条件的关联方才回填
     * 采用 include(ord.getUser(), (q, userProxy) -> ...) 零强转规范
     */
    @Test
    public void testEntityIncludeManyToOneWithCondition() {
        verifyAllModes(o -> {
            MatrixOrder ord = o.proxy(MatrixOrder.class);
            List<MatrixOrder> orders = o.select(MatrixOrder.class)
                    .orderBy(ord.getId()).asc()
                    .include(ord.getUser(), (q, u) -> q.where().eq(u.getUsername(), "bob"))
                    .list();

            Assert.assertNotNull(orders);
            Assert.assertEquals(4, orders.size());

            // ORD-001 (user is alice, does not match filter bob -> null)
            Assert.assertNull(orders.get(0).getUser());
            // ORD-002 (user is alice -> null)
            Assert.assertNull(orders.get(1).getUser());
            // ORD-003 (user is bob -> hydrated)
            Assert.assertNotNull(orders.get(2).getUser());
            Assert.assertEquals("bob", orders.get(2).getUser().getUsername());
            // ORD-999 -> null
            Assert.assertNull(orders.get(3).getUser());
        });
    }

    // =========================================================================
    // 2. selectAutoInclude DTO 专属约束与投影测试
    // =========================================================================

    /**
     * 2.1 强约束校验：严禁在 Entity 实体类上直接使用 selectAutoInclude
     */
    @Test
    public void testSelectAutoIncludeEntityForbidden() {
        verifyAllModes(o -> {
            // 实体类 MatrixUser 必须强校验拦截
            try {
                o.select(MatrixUser.class).selectAutoInclude(MatrixUser.class);
                Assert.fail("Expected IllegalArgumentException for Entity class");
            } catch (IllegalArgumentException e) {
                Assert.assertTrue(e.getMessage().contains("selectAutoInclude only supports DTO/VO class, cannot be used on Entity class"));
                Assert.assertTrue(e.getMessage().contains(MatrixUser.class.getName()));
            }

            // 实体类 MatrixOrder 必须强校验拦截
            try {
                o.select(MatrixOrder.class).selectAutoInclude(MatrixOrder.class);
                Assert.fail("Expected IllegalArgumentException for MatrixOrder");
            } catch (IllegalArgumentException e) {
                Assert.assertTrue(e.getMessage().contains("selectAutoInclude only supports DTO/VO class, cannot be used on Entity class"));
            }

            // null 校验
            try {
                o.select(MatrixUser.class).selectAutoInclude(null);
                Assert.fail("Expected IllegalArgumentException for null");
            } catch (IllegalArgumentException e) {
                Assert.assertTrue(e.getMessage().contains("dtoClass must not be null"));
            }

            // 在 WhereBuilder 链式后调用实体同样拦截
            try {
                MatrixUser u = o.proxy(MatrixUser.class);
                o.select(MatrixUser.class).where().eq(u.getId(), 1L).selectAutoInclude(MatrixUser.class);
                Assert.fail("Expected IllegalArgumentException on WhereBuilder");
            } catch (IllegalArgumentException e) {
                Assert.assertTrue(e.getMessage().contains("selectAutoInclude only supports DTO/VO class, cannot be used on Entity class"));
            }
        });
    }

    /**
     * 2.2 字段裁剪投影（Selective Projection）：5 大方言全文本精确断言
     * 校验点：
     * 1. 仅投影 DTO 声明的标量属性（id, orderNo, amount）+ 关联回填所需外键（userId）；
     * 2. 严禁投影无用列（status, createTime）；
     * 3. 严禁在 SELECT 中查询不存在的关系列（user）。
     */
    @Test
    public void testSelectAutoIncludeProjectionMatrixSql() {
        assertMatrixSql(
                o -> o.select(MatrixOrder.class).selectAutoInclude(MatrixOrderDto.class),
                "SELECT t0.`id`, t0.`order_no`, t0.`amount`, t0.`user_id` FROM `matrix_order` t0",
                "SELECT t0.\"id\", t0.\"order_no\", t0.\"amount\", t0.\"user_id\" FROM \"matrix_order\" t0",
                "SELECT t0.\"ID\", t0.\"ORDER_NO\", t0.\"AMOUNT\", t0.\"USER_ID\" FROM \"MATRIX_ORDER\" t0",
                "SELECT t0.\"id\", t0.\"order_no\", t0.\"amount\", t0.\"user_id\" FROM \"matrix_order\" t0",
                "SELECT t0.\"id\", t0.\"order_no\", t0.\"amount\", t0.\"user_id\" FROM \"matrix_order\" t0"
        );
    }

    // =========================================================================
    // 3. selectAutoInclude 真实执行与跨模式数据校验
    // =========================================================================

    /**
     * 3.1 ManyToOne 自动关联装配真实执行与数据校验
     */
    @Test
    public void testSelectAutoIncludeManyToOneExecution() {
        verifyAllModes(o -> {
            MatrixOrder ord = o.proxy(MatrixOrder.class);
            List<MatrixOrderDto> orders = o.select(MatrixOrder.class)
                    .orderBy(ord.getId()).asc()
                    .selectAutoInclude(MatrixOrderDto.class)
                    .list();

            Assert.assertNotNull(orders);
            Assert.assertEquals(4, orders.size());

            // ORD-001 -> alice
            MatrixOrderDto o1 = orders.get(0);
            Assert.assertEquals("ORD-001", o1.getOrderNo());
            Assert.assertEquals(new BigDecimal("199.90"), o1.getAmount());
            Assert.assertNotNull("User DTO must be auto-included", o1.getUser());
            Assert.assertEquals("alice", o1.getUser().getUsername());
            Assert.assertEquals("alice@example.com", o1.getUser().getEmail());

            // ORD-002 -> alice
            MatrixOrderDto o2 = orders.get(1);
            Assert.assertEquals("ORD-002", o2.getOrderNo());
            Assert.assertNotNull(o2.getUser());
            Assert.assertEquals("alice", o2.getUser().getUsername());

            // ORD-003 -> bob
            MatrixOrderDto o3 = orders.get(2);
            Assert.assertEquals("ORD-003", o3.getOrderNo());
            Assert.assertNotNull(o3.getUser());
            Assert.assertEquals("bob", o3.getUser().getUsername());

            // ORD-999 -> null
            MatrixOrderDto o4 = orders.get(3);
            Assert.assertEquals("ORD-999", o4.getOrderNo());
            Assert.assertNull(o4.getUser());
        });
    }

    /**
     * 3.2 隐式 Key（DTO 未显式声明 userId 或 id 属性）精准装配测试
     */
    @Test
    public void testSelectAutoIncludeWithoutFkInDto() {
        verifyAllModes(o -> {
            MatrixOrder ord = o.proxy(MatrixOrder.class);
            List<MatrixOrderWithoutFkDto> orders = o.select(MatrixOrder.class)
                    .orderBy(ord.getId()).asc()
                    .selectAutoInclude(MatrixOrderWithoutFkDto.class)
                    .list();

            Assert.assertNotNull(orders);
            Assert.assertEquals(4, orders.size());

            MatrixOrderWithoutFkDto o1 = orders.get(0);
            Assert.assertEquals("ORD-001", o1.getOrderNo());
            Assert.assertNotNull("User must be hydrated even without userId field in DTO", o1.getUser());
            Assert.assertEquals("alice", o1.getUser().getUsername());

            MatrixOrderWithoutFkDto o3 = orders.get(2);
            Assert.assertEquals("ORD-003", o3.getOrderNo());
            Assert.assertNotNull(o3.getUser());
            Assert.assertEquals("bob", o3.getUser().getUsername());
        });
    }

    /**
     * 3.3 OneToMany 集合自动批量装配真实执行与数据校验
     */
    @Test
    public void testSelectAutoIncludeOneToManyExecution() {
        verifyAllModes(o -> {
            MatrixOrder ord = o.proxy(MatrixOrder.class);
            List<MatrixOrderWithItemsDto> orders = o.select(MatrixOrder.class)
                    .orderBy(ord.getId()).asc()
                    .selectAutoInclude(MatrixOrderWithItemsDto.class)
                    .list();

            Assert.assertNotNull(orders);
            Assert.assertEquals(4, orders.size());

            // ORD-001
            MatrixOrderWithItemsDto o1 = orders.get(0);
            Assert.assertEquals("ORD-001", o1.getOrderNo());
            Assert.assertNotNull(o1.getItems());
            Assert.assertEquals(2, o1.getItems().size());
            Assert.assertEquals("Mechanical Keyboard", o1.getItems().get(0).getProdName());
            Assert.assertEquals(new BigDecimal("99.90"), o1.getItems().get(0).getPrice());
            Assert.assertEquals("Wireless Mouse", o1.getItems().get(1).getProdName());

            // ORD-002
            MatrixOrderWithItemsDto o2 = orders.get(1);
            Assert.assertNotNull(o2.getItems());
            Assert.assertEquals(1, o2.getItems().size());
            Assert.assertEquals("4K Monitor", o2.getItems().get(0).getProdName());

            // ORD-003
            MatrixOrderWithItemsDto o3 = orders.get(2);
            Assert.assertNotNull(o3.getItems());
            Assert.assertTrue(o3.getItems().isEmpty());
        });
    }

    /**
     * 3.4 3 级多层级嵌套递归级联自动装配：User -> Orders -> Items
     * 在 MySQL、PostgreSQL、Oracle 三大隔离模式下完整验证无限递归级联加载与数据精准性！
     */
    @Test
    public void testSelectAutoIncludeMultiLevelRecursiveExecution() {
        verifyAllModes(o -> {
            MatrixUser u = o.proxy(MatrixUser.class);
            MatrixUserWithOrdersAndItemsDto alice = o.select(MatrixUser.class)
                    .where().eq(u.getUsername(), "alice")
                    .selectAutoInclude(MatrixUserWithOrdersAndItemsDto.class)
                    .firstOrNull();

            Assert.assertNotNull(alice);
            Assert.assertEquals("alice", alice.getUsername());

            // 第 2 层：订单列表
            List<MatrixOrderWithItemsDto> orders = alice.getOrders();
            Assert.assertNotNull("Orders list must be auto-included", orders);
            Assert.assertEquals(2, orders.size());

            // 第 3 层：订单明细项递归加载
            MatrixOrderWithItemsDto ord1 = orders.stream().filter(ord -> "ORD-001".equals(ord.getOrderNo())).findFirst().orElse(null);
            Assert.assertNotNull(ord1);
            Assert.assertNotNull("Order 1 items must be recursively auto-included", ord1.getItems());
            Assert.assertEquals(2, ord1.getItems().size());
            Assert.assertEquals("Mechanical Keyboard", ord1.getItems().get(0).getProdName());

            MatrixOrderWithItemsDto ord2 = orders.stream().filter(ord -> "ORD-002".equals(ord.getOrderNo())).findFirst().orElse(null);
            Assert.assertNotNull(ord2);
            Assert.assertNotNull("Order 2 items must be recursively auto-included", ord2.getItems());
            Assert.assertEquals(1, ord2.getItems().size());
            Assert.assertEquals("4K Monitor", ord2.getItems().get(0).getProdName());
        });
    }

    /**
     * 3.5 链式终结操作与分页支持：结合 where、firstOrNull、single、toPageResult
     */
    @Test
    public void testSelectAutoIncludeTerminalAndPaging() {
        verifyAllModes(o -> {
            MatrixOrder ord = o.proxy(MatrixOrder.class);
            // 1. firstOrNull
            MatrixOrderDto first = o.select(MatrixOrder.class)
                    .where().eq(ord.getOrderNo(), "ORD-001")
                    .selectAutoInclude(MatrixOrderDto.class)
                    .firstOrNull();
            Assert.assertNotNull(first);
            Assert.assertEquals("ORD-001", first.getOrderNo());
            Assert.assertNotNull(first.getUser());
            Assert.assertEquals("alice", first.getUser().getUsername());

            // 2. single
            MatrixOrderDto single = o.select(MatrixOrder.class)
                    .where().eq(ord.getOrderNo(), "ORD-003")
                    .selectAutoInclude(MatrixOrderDto.class)
                    .single();
            Assert.assertNotNull(single);
            Assert.assertEquals("ORD-003", single.getOrderNo());
            Assert.assertNotNull(single.getUser());
            Assert.assertEquals("bob", single.getUser().getUsername());

            // 3. toPageResult 分页
            PageResult<MatrixOrderDto> page = o.select(MatrixOrder.class)
                    .orderBy(ord.getId()).asc()
                    .selectAutoInclude(MatrixOrderDto.class)
                    .toPageResult(1, 2);

            Assert.assertNotNull(page);
            Assert.assertEquals(4L, page.getTotal());
            Assert.assertEquals(1L, page.getPageNum());
            Assert.assertEquals(2L, page.getPageSize());
            Assert.assertEquals(2, page.getList().size());

            // 分页数据中的关联字段装配验证
            Assert.assertEquals("ORD-001", page.getList().get(0).getOrderNo());
            Assert.assertNotNull(page.getList().get(0).getUser());
            Assert.assertEquals("alice", page.getList().get(0).getUser().getUsername());

            Assert.assertEquals("ORD-002", page.getList().get(1).getOrderNo());
            Assert.assertNotNull(page.getList().get(1).getUser());
            Assert.assertEquals("alice", page.getList().get(1).getUser().getUsername());
        });
    }
}
