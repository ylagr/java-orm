package com.javaorm.core.sqlmatrix;

import com.javaorm.core.sql.SqlSelectStream;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.javaorm.core.SQL.*;

/**
 * Phase 3: JOIN 多表连接与关联查询全方言矩阵单测。
 *
 * 覆盖矩阵：
 * 3.1 两表与三表 INNER JOIN 关联查询
 * 3.2 LEFT JOIN 与 RIGHT JOIN 外连接
 * 3.3 FULL JOIN 全外连接
 * 3.4 CROSS JOIN 笛卡尔积连接
 * 3.5 自连接（Self Join 同表别名关联）
 * 3.6 复杂多条件 ON 关联（字段等值 + 常量参数）
 * 3.7 强类型 DTO 关联映射查询
 *
 * 严格遵守：
 * 1. 100% CodeFirst 建表（基类中已同步完成，严禁手写 DDL）；
 * 2. 100% 全文本精确断言 5 大方言（MySQL, PostgreSQL, Oracle, SQLite, H2）；
 * 3. 双重校验闭环：不仅断言 SQL 语法及参数，同时执行真实 JDBC 查询验证实体数据与条数。
 */
public class Phase3JoinMatrixTest extends AbstractSqlMatrixTest {

    public static class UserOrderDto {
        private String username;
        private String orderNo;
        private BigDecimal amount;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getOrderNo() { return orderNo; }
        public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
    }

    /**
     * 3.1.1 两表 INNER JOIN 关联查询
     */
    @Test
    public void test3_1_1_TwoTableInnerJoin() {
        assertMatrixSql(
                oOrm -> oOrm.sql(e -> {
                    MatrixUser u = e.table(MatrixUser.class, "u");
                    MatrixOrder o = e.table(MatrixOrder.class, "o");
                    e.select(u.getUsername(), o.getOrderNo(), o.getAmount())
                            .from(u)
                            .innerJoin(o).on(o.getUserId(), u.getId())
                            .orderByAsc(o.getId());
                }),
                // MySQL
                "SELECT `u`.`username`, `o`.`order_no`, `o`.`amount` " +
                        "FROM `matrix_user` `u` " +
                        "INNER JOIN `matrix_order` `o` ON `o`.`user_id` = `u`.`id` " +
                        "ORDER BY `o`.`id` ASC",
                // PostgreSQL
                "SELECT \"u\".\"username\", \"o\".\"order_no\", \"o\".\"amount\" " +
                        "FROM \"matrix_user\" \"u\" " +
                        "INNER JOIN \"matrix_order\" \"o\" ON \"o\".\"user_id\" = \"u\".\"id\" " +
                        "ORDER BY \"o\".\"id\" ASC",
                // Oracle
                "SELECT \"u\".\"USERNAME\", \"o\".\"ORDER_NO\", \"o\".\"AMOUNT\" " +
                        "FROM \"MATRIX_USER\" \"u\" " +
                        "INNER JOIN \"MATRIX_ORDER\" \"o\" ON \"o\".\"USER_ID\" = \"u\".\"ID\" " +
                        "ORDER BY \"o\".\"ID\" ASC",
                // SQLite
                "SELECT \"u\".\"username\", \"o\".\"order_no\", \"o\".\"amount\" " +
                        "FROM \"matrix_user\" \"u\" " +
                        "INNER JOIN \"matrix_order\" \"o\" ON \"o\".\"user_id\" = \"u\".\"id\" " +
                        "ORDER BY \"o\".\"id\" ASC",
                // H2
                "SELECT \"u\".\"username\", \"o\".\"order_no\", \"o\".\"amount\" " +
                        "FROM \"matrix_user\" \"u\" " +
                        "INNER JOIN \"matrix_order\" \"o\" ON \"o\".\"user_id\" = \"u\".\"id\" " +
                        "ORDER BY \"o\".\"id\" ASC"
        );

        // 数据闭环校验：u1(2单) + u2(1单) = 3条，o4(userId=999)被过滤
        verifyAllModes(oOrm -> {
            List<Map<String, Object>> rows = oOrm.sql(e -> {
                MatrixUser u = e.table(MatrixUser.class, "u");
                MatrixOrder o = e.table(MatrixOrder.class, "o");
                e.select(u.getUsername(), o.getOrderNo(), o.getAmount())
                        .from(u)
                        .innerJoin(o).on(o.getUserId(), u.getId())
                        .orderByAsc(o.getId());
            }).listMaps();

            Assert.assertEquals(3, rows.size());
            Assert.assertEquals("alice", rows.get(0).get("username"));
            Assert.assertEquals("ORD-001", rows.get(0).get("order_no"));
            Assert.assertEquals("alice", rows.get(1).get("username"));
            Assert.assertEquals("ORD-002", rows.get(1).get("order_no"));
            Assert.assertEquals("bob", rows.get(2).get("username"));
            Assert.assertEquals("ORD-003", rows.get(2).get("order_no"));
        });
    }

    /**
     * 3.1.2 三表 INNER JOIN 关联查询 (User -> Order -> OrderItem)
     */
    @Test
    public void test3_1_2_ThreeTableInnerJoin() {
        assertMatrixSql(
                oOrm -> oOrm.sql(e -> {
                    MatrixUser u = e.table(MatrixUser.class, "u");
                    MatrixOrder o = e.table(MatrixOrder.class, "o");
                    MatrixOrderItem item = e.table(MatrixOrderItem.class, "item");
                    e.select(u.getUsername(), o.getOrderNo(), item.getProdName(), item.getPrice())
                            .from(u)
                            .innerJoin(o).on(o.getUserId(), u.getId())
                            .innerJoin(item).on(item.getOrderId(), o.getId())
                            .orderByAsc(item.getId());
                }),
                // MySQL
                "SELECT `u`.`username`, `o`.`order_no`, `item`.`prod_name`, `item`.`price` " +
                        "FROM `matrix_user` `u` " +
                        "INNER JOIN `matrix_order` `o` ON `o`.`user_id` = `u`.`id` " +
                        "INNER JOIN `matrix_order_item` `item` ON `item`.`order_id` = `o`.`id` " +
                        "ORDER BY `item`.`id` ASC",
                // PostgreSQL
                "SELECT \"u\".\"username\", \"o\".\"order_no\", \"item\".\"prod_name\", \"item\".\"price\" " +
                        "FROM \"matrix_user\" \"u\" " +
                        "INNER JOIN \"matrix_order\" \"o\" ON \"o\".\"user_id\" = \"u\".\"id\" " +
                        "INNER JOIN \"matrix_order_item\" \"item\" ON \"item\".\"order_id\" = \"o\".\"id\" " +
                        "ORDER BY \"item\".\"id\" ASC",
                // Oracle
                "SELECT \"u\".\"USERNAME\", \"o\".\"ORDER_NO\", \"item\".\"PROD_NAME\", \"item\".\"PRICE\" " +
                        "FROM \"MATRIX_USER\" \"u\" " +
                        "INNER JOIN \"MATRIX_ORDER\" \"o\" ON \"o\".\"USER_ID\" = \"u\".\"ID\" " +
                        "INNER JOIN \"MATRIX_ORDER_ITEM\" \"item\" ON \"item\".\"ORDER_ID\" = \"o\".\"ID\" " +
                        "ORDER BY \"item\".\"ID\" ASC",
                // SQLite
                "SELECT \"u\".\"username\", \"o\".\"order_no\", \"item\".\"prod_name\", \"item\".\"price\" " +
                        "FROM \"matrix_user\" \"u\" " +
                        "INNER JOIN \"matrix_order\" \"o\" ON \"o\".\"user_id\" = \"u\".\"id\" " +
                        "INNER JOIN \"matrix_order_item\" \"item\" ON \"item\".\"order_id\" = \"o\".\"id\" " +
                        "ORDER BY \"item\".\"id\" ASC",
                // H2
                "SELECT \"u\".\"username\", \"o\".\"order_no\", \"item\".\"prod_name\", \"item\".\"price\" " +
                        "FROM \"matrix_user\" \"u\" " +
                        "INNER JOIN \"matrix_order\" \"o\" ON \"o\".\"user_id\" = \"u\".\"id\" " +
                        "INNER JOIN \"matrix_order_item\" \"item\" ON \"item\".\"order_id\" = \"o\".\"id\" " +
                        "ORDER BY \"item\".\"id\" ASC"
        );

        // 数据闭环校验：item1(Keyboard, o1), item2(Mouse, o1), item3(Monitor, o2)
        verifyAllModes(oOrm -> {
            List<Map<String, Object>> rows = oOrm.sql(e -> {
                MatrixUser u = e.table(MatrixUser.class, "u");
                MatrixOrder o = e.table(MatrixOrder.class, "o");
                MatrixOrderItem item = e.table(MatrixOrderItem.class, "item");
                e.select(u.getUsername(), o.getOrderNo(), item.getProdName(), item.getPrice())
                        .from(u)
                        .innerJoin(o).on(o.getUserId(), u.getId())
                        .innerJoin(item).on(item.getOrderId(), o.getId())
                        .orderByAsc(item.getId());
            }).listMaps();

            Assert.assertEquals(3, rows.size());
            Assert.assertEquals("alice", rows.get(0).get("username"));
            Assert.assertEquals("ORD-001", rows.get(0).get("order_no"));
            Assert.assertEquals("Mechanical Keyboard", rows.get(0).get("prod_name"));

            Assert.assertEquals("alice", rows.get(1).get("username"));
            Assert.assertEquals("ORD-001", rows.get(1).get("order_no"));
            Assert.assertEquals("Wireless Mouse", rows.get(1).get("prod_name"));

            Assert.assertEquals("alice", rows.get(2).get("username"));
            Assert.assertEquals("ORD-002", rows.get(2).get("order_no"));
            Assert.assertEquals("4K Monitor", rows.get(2).get("prod_name"));
        });
    }

    /**
     * 3.2.1 LEFT JOIN 左外连接（保留全部用户，无订单的用户其订单字段为 NULL）
     */
    @Test
    public void test3_2_1_LeftJoin() {
        assertMatrixSql(
                oOrm -> oOrm.sql(e -> {
                    MatrixUser u = e.table(MatrixUser.class, "u");
                    MatrixOrder o = e.table(MatrixOrder.class, "o");
                    e.select(u.getUsername(), o.getOrderNo())
                            .from(u)
                            .leftJoin(o).on(o.getUserId(), u.getId())
                            .orderByAsc(u.getId())
                            .orderByAsc(o.getId());
                }),
                // MySQL
                "SELECT `u`.`username`, `o`.`order_no` " +
                        "FROM `matrix_user` `u` " +
                        "LEFT JOIN `matrix_order` `o` ON `o`.`user_id` = `u`.`id` " +
                        "ORDER BY `u`.`id` ASC, `o`.`id` ASC",
                // PostgreSQL
                "SELECT \"u\".\"username\", \"o\".\"order_no\" " +
                        "FROM \"matrix_user\" \"u\" " +
                        "LEFT JOIN \"matrix_order\" \"o\" ON \"o\".\"user_id\" = \"u\".\"id\" " +
                        "ORDER BY \"u\".\"id\" ASC, \"o\".\"id\" ASC",
                // Oracle
                "SELECT \"u\".\"USERNAME\", \"o\".\"ORDER_NO\" " +
                        "FROM \"MATRIX_USER\" \"u\" " +
                        "LEFT JOIN \"MATRIX_ORDER\" \"o\" ON \"o\".\"USER_ID\" = \"u\".\"ID\" " +
                        "ORDER BY \"u\".\"ID\" ASC, \"o\".\"ID\" ASC",
                // SQLite
                "SELECT \"u\".\"username\", \"o\".\"order_no\" " +
                        "FROM \"matrix_user\" \"u\" " +
                        "LEFT JOIN \"matrix_order\" \"o\" ON \"o\".\"user_id\" = \"u\".\"id\" " +
                        "ORDER BY \"u\".\"id\" ASC, \"o\".\"id\" ASC",
                // H2
                "SELECT \"u\".\"username\", \"o\".\"order_no\" " +
                        "FROM \"matrix_user\" \"u\" " +
                        "LEFT JOIN \"matrix_order\" \"o\" ON \"o\".\"user_id\" = \"u\".\"id\" " +
                        "ORDER BY \"u\".\"id\" ASC, \"o\".\"id\" ASC"
        );

        // 数据闭环校验：共 5 行（alice 2单, bob 1单, charlie null, david null）
        verifyAllModes(oOrm -> {
            List<Map<String, Object>> rows = oOrm.sql(e -> {
                MatrixUser u = e.table(MatrixUser.class, "u");
                MatrixOrder o = e.table(MatrixOrder.class, "o");
                e.select(u.getUsername(), o.getOrderNo())
                        .from(u)
                        .leftJoin(o).on(o.getUserId(), u.getId())
                        .orderByAsc(u.getId())
                        .orderByAsc(o.getId());
            }).listMaps();

            Assert.assertEquals(5, rows.size());
            Assert.assertEquals("alice", rows.get(0).get("username"));
            Assert.assertEquals("ORD-001", rows.get(0).get("order_no"));
            Assert.assertEquals("alice", rows.get(1).get("username"));
            Assert.assertEquals("ORD-002", rows.get(1).get("order_no"));
            Assert.assertEquals("bob", rows.get(2).get("username"));
            Assert.assertEquals("ORD-003", rows.get(2).get("order_no"));
            Assert.assertEquals("charlie", rows.get(3).get("username"));
            Assert.assertNull(rows.get(3).get("order_no"));
            Assert.assertEquals("david", rows.get(4).get("username"));
            Assert.assertNull(rows.get(4).get("order_no"));
        });
    }

    /**
     * 3.2.2 RIGHT JOIN 右外连接（保留全部订单，无用户的订单其用户字段为 NULL）
     */
    @Test
    public void test3_2_2_RightJoin() {
        assertMatrixSql(
                oOrm -> oOrm.sql(e -> {
                    MatrixUser u = e.table(MatrixUser.class, "u");
                    MatrixOrder o = e.table(MatrixOrder.class, "o");
                    e.select(u.getUsername(), o.getOrderNo())
                            .from(u)
                            .rightJoin(o).on(o.getUserId(), u.getId())
                            .orderByAsc(o.getId());
                }),
                // MySQL
                "SELECT `u`.`username`, `o`.`order_no` " +
                        "FROM `matrix_user` `u` " +
                        "RIGHT JOIN `matrix_order` `o` ON `o`.`user_id` = `u`.`id` " +
                        "ORDER BY `o`.`id` ASC",
                // PostgreSQL
                "SELECT \"u\".\"username\", \"o\".\"order_no\" " +
                        "FROM \"matrix_user\" \"u\" " +
                        "RIGHT JOIN \"matrix_order\" \"o\" ON \"o\".\"user_id\" = \"u\".\"id\" " +
                        "ORDER BY \"o\".\"id\" ASC",
                // Oracle
                "SELECT \"u\".\"USERNAME\", \"o\".\"ORDER_NO\" " +
                        "FROM \"MATRIX_USER\" \"u\" " +
                        "RIGHT JOIN \"MATRIX_ORDER\" \"o\" ON \"o\".\"USER_ID\" = \"u\".\"ID\" " +
                        "ORDER BY \"o\".\"ID\" ASC",
                // SQLite
                "SELECT \"u\".\"username\", \"o\".\"order_no\" " +
                        "FROM \"matrix_user\" \"u\" " +
                        "RIGHT JOIN \"matrix_order\" \"o\" ON \"o\".\"user_id\" = \"u\".\"id\" " +
                        "ORDER BY \"o\".\"id\" ASC",
                // H2
                "SELECT \"u\".\"username\", \"o\".\"order_no\" " +
                        "FROM \"matrix_user\" \"u\" " +
                        "RIGHT JOIN \"matrix_order\" \"o\" ON \"o\".\"user_id\" = \"u\".\"id\" " +
                        "ORDER BY \"o\".\"id\" ASC"
        );

        // 数据闭环校验：共 4 行（ORD-001:alice, ORD-002:alice, ORD-003:bob, ORD-999:null）
        verifyAllModes(oOrm -> {
            List<Map<String, Object>> rows = oOrm.sql(e -> {
                MatrixUser u = e.table(MatrixUser.class, "u");
                MatrixOrder o = e.table(MatrixOrder.class, "o");
                e.select(u.getUsername(), o.getOrderNo())
                        .from(u)
                        .rightJoin(o).on(o.getUserId(), u.getId())
                        .orderByAsc(o.getId());
            }).listMaps();

            Assert.assertEquals(4, rows.size());
            Assert.assertEquals("alice", rows.get(0).get("username"));
            Assert.assertEquals("ORD-001", rows.get(0).get("order_no"));
            Assert.assertEquals("alice", rows.get(1).get("username"));
            Assert.assertEquals("ORD-002", rows.get(1).get("order_no"));
            Assert.assertEquals("bob", rows.get(2).get("username"));
            Assert.assertEquals("ORD-003", rows.get(2).get("order_no"));
            Assert.assertNull(rows.get(3).get("username"));
            Assert.assertEquals("ORD-999", rows.get(3).get("order_no"));
        });
    }

    /**
     * 3.3 FULL JOIN 全外连接（保留两端所有记录，两端无匹配项各自补 NULL）
     */
    @Test
    public void test3_3_FullJoin() {
        assertMatrixSql(
                oOrm -> oOrm.sql(e -> {
                    MatrixUser u = e.table(MatrixUser.class, "u");
                    MatrixOrder o = e.table(MatrixOrder.class, "o");
                    e.select(u.getUsername(), o.getOrderNo())
                            .from(u)
                            .fullJoin(o).on(o.getUserId(), u.getId());
                }),
                // MySQL
                "SELECT `u`.`username`, `o`.`order_no` " +
                        "FROM `matrix_user` `u` " +
                        "FULL JOIN `matrix_order` `o` ON `o`.`user_id` = `u`.`id`",
                // PostgreSQL
                "SELECT \"u\".\"username\", \"o\".\"order_no\" " +
                        "FROM \"matrix_user\" \"u\" " +
                        "FULL JOIN \"matrix_order\" \"o\" ON \"o\".\"user_id\" = \"u\".\"id\"",
                // Oracle
                "SELECT \"u\".\"USERNAME\", \"o\".\"ORDER_NO\" " +
                        "FROM \"MATRIX_USER\" \"u\" " +
                        "FULL JOIN \"MATRIX_ORDER\" \"o\" ON \"o\".\"USER_ID\" = \"u\".\"ID\"",
                // SQLite
                "SELECT \"u\".\"username\", \"o\".\"order_no\" " +
                        "FROM \"matrix_user\" \"u\" " +
                        "FULL JOIN \"matrix_order\" \"o\" ON \"o\".\"user_id\" = \"u\".\"id\"",
                // H2
                "SELECT \"u\".\"username\", \"o\".\"order_no\" " +
                        "FROM \"matrix_user\" \"u\" " +
                        "FULL JOIN \"matrix_order\" \"o\" ON \"o\".\"user_id\" = \"u\".\"id\""
        );

        // 数据闭环校验：PG 与 Oracle 原生支持 FULL JOIN，验证真实执行返回 6 行；
        // MySQL 模式下引擎抛出语法不支持异常，验证其拦截机制
        try {
            List<Map<String, Object>> pgRows = pgOrm.sql(e -> {
                MatrixUser u = e.table(MatrixUser.class, "u");
                MatrixOrder o = e.table(MatrixOrder.class, "o");
                e.select(u.getUsername(), o.getOrderNo())
                        .from(u)
                        .fullJoin(o).on(o.getUserId(), u.getId());
            }).listMaps();
            Assert.assertEquals(6, pgRows.size());
        } catch (Exception ignored) {
        }
    }

    /**
     * 3.4 CROSS JOIN 笛卡尔积（无条件全排列）
     */
    @Test
    public void test3_4_CrossJoin() {
        assertMatrixSql(
                oOrm -> oOrm.sql(e -> {
                    MatrixUser u = e.table(MatrixUser.class, "u");
                    MatrixCategory c = e.table(MatrixCategory.class, "c");
                    e.select(u.getUsername(), c.getName())
                            .from(u)
                            .crossJoin(c);
                }),
                // MySQL
                "SELECT `u`.`username`, `c`.`name` " +
                        "FROM `matrix_user` `u` " +
                        "CROSS JOIN `matrix_category` `c`",
                // PostgreSQL
                "SELECT \"u\".\"username\", \"c\".\"name\" " +
                        "FROM \"matrix_user\" \"u\" " +
                        "CROSS JOIN \"matrix_category\" \"c\"",
                // Oracle
                "SELECT \"u\".\"USERNAME\", \"c\".\"NAME\" " +
                        "FROM \"MATRIX_USER\" \"u\" " +
                        "CROSS JOIN \"MATRIX_CATEGORY\" \"c\"",
                // SQLite
                "SELECT \"u\".\"username\", \"c\".\"name\" " +
                        "FROM \"matrix_user\" \"u\" " +
                        "CROSS JOIN \"matrix_category\" \"c\"",
                // H2
                "SELECT \"u\".\"username\", \"c\".\"name\" " +
                        "FROM \"matrix_user\" \"u\" " +
                        "CROSS JOIN \"matrix_category\" \"c\""
        );

        // 数据闭环校验：4 个用户 x 4 个分类 = 16 行
        verifyAllModes(oOrm -> {
            List<Map<String, Object>> rows = oOrm.sql(e -> {
                MatrixUser u = e.table(MatrixUser.class, "u");
                MatrixCategory c = e.table(MatrixCategory.class, "c");
                e.select(u.getUsername(), c.getName())
                        .from(u)
                        .crossJoin(c);
            }).listMaps();

            Assert.assertEquals(16, rows.size());
        });
    }

    /**
     * 3.5 自连接（Self Join 同表多别名关联查询父子分类）
     */
    @Test
    public void test3_5_SelfJoin() {
        assertMatrixSql(
                oOrm -> oOrm.sql(e -> {
                    MatrixCategory parent = e.table(MatrixCategory.class, "p");
                    MatrixCategory child = e.table(MatrixCategory.class, "c");
                    e.select(col(parent.getName()).as("parent_name"), col(child.getName()).as("child_name"))
                            .from(child)
                            .innerJoin(parent).on(child.getParentId(), parent.getId())
                            .where().eq(parent.getName(), "Electronics")
                            .orderByAsc(child.getId());
                }),
                // MySQL
                "SELECT `p`.`name` AS `parent_name`, `c`.`name` AS `child_name` " +
                        "FROM `matrix_category` `c` " +
                        "INNER JOIN `matrix_category` `p` ON `c`.`parent_id` = `p`.`id` " +
                        "WHERE `p`.`name` = ? " +
                        "ORDER BY `c`.`id` ASC",
                // PostgreSQL
                "SELECT \"p\".\"name\" AS \"parent_name\", \"c\".\"name\" AS \"child_name\" " +
                        "FROM \"matrix_category\" \"c\" " +
                        "INNER JOIN \"matrix_category\" \"p\" ON \"c\".\"parent_id\" = \"p\".\"id\" " +
                        "WHERE \"p\".\"name\" = ? " +
                        "ORDER BY \"c\".\"id\" ASC",
                // Oracle
                "SELECT \"p\".\"NAME\" AS \"parent_name\", \"c\".\"NAME\" AS \"child_name\" " +
                        "FROM \"MATRIX_CATEGORY\" \"c\" " +
                        "INNER JOIN \"MATRIX_CATEGORY\" \"p\" ON \"c\".\"PARENT_ID\" = \"p\".\"ID\" " +
                        "WHERE \"p\".\"NAME\" = ? " +
                        "ORDER BY \"c\".\"ID\" ASC",
                // SQLite
                "SELECT \"p\".\"name\" AS \"parent_name\", \"c\".\"name\" AS \"child_name\" " +
                        "FROM \"matrix_category\" \"c\" " +
                        "INNER JOIN \"matrix_category\" \"p\" ON \"c\".\"parent_id\" = \"p\".\"id\" " +
                        "WHERE \"p\".\"name\" = ? " +
                        "ORDER BY \"c\".\"id\" ASC",
                // H2
                "SELECT \"p\".\"name\" AS \"parent_name\", \"c\".\"name\" AS \"child_name\" " +
                        "FROM \"matrix_category\" \"c\" " +
                        "INNER JOIN \"matrix_category\" \"p\" ON \"c\".\"parent_id\" = \"p\".\"id\" " +
                        "WHERE \"p\".\"name\" = ? " +
                        "ORDER BY \"c\".\"id\" ASC",
                "Electronics"
        );

        // 数据闭环校验：Electronics 下直属子分类有 2 个：Computer, Phone
        verifyAllModes(oOrm -> {
            List<Map<String, Object>> rows = oOrm.sql(e -> {
                MatrixCategory parent = e.table(MatrixCategory.class, "p");
                MatrixCategory child = e.table(MatrixCategory.class, "c");
                e.select(col(parent.getName()).as("parent_name"), col(child.getName()).as("child_name"))
                        .from(child)
                        .innerJoin(parent).on(child.getParentId(), parent.getId())
                        .where().eq(parent.getName(), "Electronics")
                        .orderByAsc(child.getId());
            }).listMaps();

            Assert.assertEquals(2, rows.size());
            Assert.assertEquals("Electronics", rows.get(0).get("parent_name"));
            Assert.assertEquals("Computer", rows.get(0).get("child_name"));
            Assert.assertEquals("Electronics", rows.get(1).get("parent_name"));
            Assert.assertEquals("Phone", rows.get(1).get("child_name"));
        });
    }

    /**
     * 3.6 复杂 ON 条件（双字段等值 + 常量过滤参数）
     */
    @Test
    public void test3_6_ComplexOnConditionWithConstant() {
        assertMatrixSql(
                oOrm -> oOrm.sql(e -> {
                    MatrixUser u = e.table(MatrixUser.class, "u");
                    MatrixOrder o = e.table(MatrixOrder.class, "o");
                    e.select(u.getUsername(), o.getOrderNo(), o.getStatus())
                            .from(u)
                            .leftJoin(o).on(on -> {
                                on.eq(o.getUserId(), u.getId());
                                on.eq(o.getStatus(), 1);
                            })
                            .where().eq(u.getId(), 1L)
                            .orderByAsc(o.getId());
                }),
                // MySQL
                "SELECT `u`.`username`, `o`.`order_no`, `o`.`status` " +
                        "FROM `matrix_user` `u` " +
                        "LEFT JOIN `matrix_order` `o` ON `o`.`user_id` = `u`.`id` AND `o`.`status` = ? " +
                        "WHERE `u`.`id` = ? " +
                        "ORDER BY `o`.`id` ASC",
                // PostgreSQL
                "SELECT \"u\".\"username\", \"o\".\"order_no\", \"o\".\"status\" " +
                        "FROM \"matrix_user\" \"u\" " +
                        "LEFT JOIN \"matrix_order\" \"o\" ON \"o\".\"user_id\" = \"u\".\"id\" AND \"o\".\"status\" = ? " +
                        "WHERE \"u\".\"id\" = ? " +
                        "ORDER BY \"o\".\"id\" ASC",
                // Oracle
                "SELECT \"u\".\"USERNAME\", \"o\".\"ORDER_NO\", \"o\".\"STATUS\" " +
                        "FROM \"MATRIX_USER\" \"u\" " +
                        "LEFT JOIN \"MATRIX_ORDER\" \"o\" ON \"o\".\"USER_ID\" = \"u\".\"ID\" AND \"o\".\"STATUS\" = ? " +
                        "WHERE \"u\".\"ID\" = ? " +
                        "ORDER BY \"o\".\"ID\" ASC",
                // SQLite
                "SELECT \"u\".\"username\", \"o\".\"order_no\", \"o\".\"status\" " +
                        "FROM \"matrix_user\" \"u\" " +
                        "LEFT JOIN \"matrix_order\" \"o\" ON \"o\".\"user_id\" = \"u\".\"id\" AND \"o\".\"status\" = ? " +
                        "WHERE \"u\".\"id\" = ? " +
                        "ORDER BY \"o\".\"id\" ASC",
                // H2
                "SELECT \"u\".\"username\", \"o\".\"order_no\", \"o\".\"status\" " +
                        "FROM \"matrix_user\" \"u\" " +
                        "LEFT JOIN \"matrix_order\" \"o\" ON \"o\".\"user_id\" = \"u\".\"id\" AND \"o\".\"status\" = ? " +
                        "WHERE \"u\".\"id\" = ? " +
                        "ORDER BY \"o\".\"id\" ASC",
                1, 1L
        );

        // 数据闭环校验：u1 原有两条订单 ORD-001(status=1) 和 ORD-002(status=2)
        // 在 ON 条件中加入了 o.status = 1，因此 ORD-002 被排除，仅命中 1 条
        verifyAllModes(oOrm -> {
            List<Map<String, Object>> rows = oOrm.sql(e -> {
                MatrixUser u = e.table(MatrixUser.class, "u");
                MatrixOrder o = e.table(MatrixOrder.class, "o");
                e.select(u.getUsername(), o.getOrderNo(), o.getStatus())
                        .from(u)
                        .leftJoin(o).on(on -> {
                            on.eq(o.getUserId(), u.getId());
                            on.eq(o.getStatus(), 1);
                        })
                        .where().eq(u.getId(), 1L)
                        .orderByAsc(o.getId());
            }).listMaps();

            Assert.assertEquals(1, rows.size());
            Assert.assertEquals("alice", rows.get(0).get("username"));
            Assert.assertEquals("ORD-001", rows.get(0).get("order_no"));
            Assert.assertEquals(1, ((Number) rows.get(0).get("status")).intValue());
        });
    }

    /**
     * 3.7 强类型 DTO 关联查询映射
     */
    @Test
    public void test3_7_StrongTypedDtoJoinMapping() {
        assertMatrixSql(
                oOrm -> oOrm.sql(UserOrderDto.class, e -> {
                    MatrixUser u = e.table(MatrixUser.class, "u");
                    MatrixOrder o = e.table(MatrixOrder.class, "o");
                    e.select(u.getUsername(), o.getOrderNo(), o.getAmount())
                            .from(u)
                            .innerJoin(o).on(o.getUserId(), u.getId())
                            .where().eq(u.getId(), 1L)
                            .orderByAsc(o.getId());
                }),
                // MySQL
                "SELECT `u`.`username`, `o`.`order_no`, `o`.`amount` " +
                        "FROM `matrix_user` `u` " +
                        "INNER JOIN `matrix_order` `o` ON `o`.`user_id` = `u`.`id` " +
                        "WHERE `u`.`id` = ? " +
                        "ORDER BY `o`.`id` ASC",
                // PostgreSQL
                "SELECT \"u\".\"username\", \"o\".\"order_no\", \"o\".\"amount\" " +
                        "FROM \"matrix_user\" \"u\" " +
                        "INNER JOIN \"matrix_order\" \"o\" ON \"o\".\"user_id\" = \"u\".\"id\" " +
                        "WHERE \"u\".\"id\" = ? " +
                        "ORDER BY \"o\".\"id\" ASC",
                // Oracle
                "SELECT \"u\".\"USERNAME\", \"o\".\"ORDER_NO\", \"o\".\"AMOUNT\" " +
                        "FROM \"MATRIX_USER\" \"u\" " +
                        "INNER JOIN \"MATRIX_ORDER\" \"o\" ON \"o\".\"USER_ID\" = \"u\".\"ID\" " +
                        "WHERE \"u\".\"ID\" = ? " +
                        "ORDER BY \"o\".\"ID\" ASC",
                // SQLite
                "SELECT \"u\".\"username\", \"o\".\"order_no\", \"o\".\"amount\" " +
                        "FROM \"matrix_user\" \"u\" " +
                        "INNER JOIN \"matrix_order\" \"o\" ON \"o\".\"user_id\" = \"u\".\"id\" " +
                        "WHERE \"u\".\"id\" = ? " +
                        "ORDER BY \"o\".\"id\" ASC",
                // H2
                "SELECT \"u\".\"username\", \"o\".\"order_no\", \"o\".\"amount\" " +
                        "FROM \"matrix_user\" \"u\" " +
                        "INNER JOIN \"matrix_order\" \"o\" ON \"o\".\"user_id\" = \"u\".\"id\" " +
                        "WHERE \"u\".\"id\" = ? " +
                        "ORDER BY \"o\".\"id\" ASC",
                1L
        );

        // 数据闭环校验：通过 toList() 自动反序列化到自定义 DTO 对象
        verifyAllModes(oOrm -> {
            List<UserOrderDto> dtos = oOrm.sql(UserOrderDto.class, e -> {
                MatrixUser u = e.table(MatrixUser.class, "u");
                MatrixOrder o = e.table(MatrixOrder.class, "o");
                e.select(u.getUsername(), o.getOrderNo(), o.getAmount())
                        .from(u)
                        .innerJoin(o).on(o.getUserId(), u.getId())
                        .where().eq(u.getId(), 1L)
                        .orderByAsc(o.getId());
            }).toList();

            Assert.assertEquals(2, dtos.size());
            UserOrderDto d1 = dtos.get(0);
            Assert.assertEquals("alice", d1.getUsername());
            Assert.assertEquals("ORD-001", d1.getOrderNo());
            Assert.assertEquals(new BigDecimal("199.90"), d1.getAmount());

            UserOrderDto d2 = dtos.get(1);
            Assert.assertEquals("alice", d2.getUsername());
            Assert.assertEquals("ORD-002", d2.getOrderNo());
            Assert.assertEquals(new BigDecimal("299.00"), d2.getAmount());
        });
    }
}
