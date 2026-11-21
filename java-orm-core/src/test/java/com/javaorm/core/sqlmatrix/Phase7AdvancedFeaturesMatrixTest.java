package com.javaorm.core.sqlmatrix;

import com.javaorm.core.ast.CaseWhenBuilder;
import com.javaorm.core.operator.SqlOperator;
import com.javaorm.core.query.Cte;
import com.javaorm.core.query.SelectBuilder;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.javaorm.core.SQL.*;

/**
 * 阶段七：高级 SQL 特性矩阵单测 (Phase 7)。
 *
 * 核心验证矩阵：
 * 1. CASE WHEN 条件分支表达式（简单 CASE 与搜索型 CASE WHEN ... THEN ... ELSE ... END）；
 * 2. 窗口函数 (Window Functions - ROW_NUMBER, RANK, DENSE_RANK, PARTITION BY, ORDER BY)；
 * 3. 聚合窗口函数 (Aggregate OVER - SUM / AVG OVER PARTITION BY)；
 * 4. 基础通用表表达式 (CTE - WITH cte AS (SELECT ...) SELECT ...)；
 * 5. CTE 多表 JOIN 查询 (CTE 与普通实体物理表连接)；
 * 6. 跨方言悲观锁机制：FOR UPDATE（行级排他锁）；
 * 7. 跨方言悲观锁机制：FOR UPDATE NOWAIT（非阻塞排他锁与方言自适应降级）；
 * 8. 跨方言悲观锁机制：FOR UPDATE SKIP LOCKED（跳过锁行与方言自适应降级）。
 *
 * 校验闭环：
 * 1. 5 大数据库方言 (MySQL, PostgreSQL, Oracle 12c+, SQLite, H2) 100% 全文本精确断言；
 * 2. 真实 JDBC 数据库执行闭环校验（MySQL, PostgreSQL, Oracle 严格模式）。
 */
public class Phase7AdvancedFeaturesMatrixTest extends AbstractSqlMatrixTest {

    private static final String ALL_USERS_MYSQL = "SELECT t0.`id`, t0.`username`, t0.`email`, t0.`age`, t0.`status`, t0.`score`, t0.`create_time`, t0.`is_deleted` FROM `matrix_user` t0";
    private static final String ALL_USERS_PG = "SELECT t0.\"id\", t0.\"username\", t0.\"email\", t0.\"age\", t0.\"status\", t0.\"score\", t0.\"create_time\", t0.\"is_deleted\" FROM \"matrix_user\" t0";
    private static final String ALL_USERS_ORACLE = "SELECT t0.\"ID\", t0.\"USERNAME\", t0.\"EMAIL\", t0.\"AGE\", t0.\"STATUS\", t0.\"SCORE\", t0.\"CREATE_TIME\", t0.\"IS_DELETED\" FROM \"MATRIX_USER\" t0";

    /**
     * 7.1 CASE WHEN 搜索型条件分支表达式
     * 场景：根据用户 age 分组：
     * CASE WHEN age >= 30 THEN 'Senior' WHEN age >= 25 THEN 'Middle' ELSE 'Junior' END AS age_group
     */
    @Test
    public void test7_1_CaseWhenExpression() {
        assertMatrixSql(
                oOrm -> {
                    MatrixUser u = oOrm.proxy(MatrixUser.class);
                    CaseWhenBuilder<?> caseWhenExpr = caseWhen()
                            .when(u.getAge(), SqlOperator.GE, 30, "Senior")
                            .when(u.getAge(), SqlOperator.GE, 25, "Middle")
                            .elseVal("Junior")
                            .as("age_group");

                    return oOrm.select(u.getId(), u.getUsername(), caseWhenExpr)
                            .from(MatrixUser.class)
                            .orderByAsc(u.getId());
                },
                // MySQL
                "SELECT t0.`id`, t0.`username`, CASE WHEN t0.`age` >= ? THEN ? WHEN t0.`age` >= ? THEN ? ELSE ? END AS `age_group` FROM `matrix_user` t0 ORDER BY t0.`id` ASC",
                // PostgreSQL
                "SELECT t0.\"id\", t0.\"username\", CASE WHEN t0.\"age\" >= ? THEN ? WHEN t0.\"age\" >= ? THEN ? ELSE ? END AS \"age_group\" FROM \"matrix_user\" t0 ORDER BY t0.\"id\" ASC",
                // Oracle
                "SELECT t0.\"ID\", t0.\"USERNAME\", CASE WHEN t0.\"AGE\" >= ? THEN ? WHEN t0.\"AGE\" >= ? THEN ? ELSE ? END AS \"age_group\" FROM \"MATRIX_USER\" t0 ORDER BY t0.\"ID\" ASC",
                // SQLite
                "SELECT t0.\"id\", t0.\"username\", CASE WHEN t0.\"age\" >= ? THEN ? WHEN t0.\"age\" >= ? THEN ? ELSE ? END AS \"age_group\" FROM \"matrix_user\" t0 ORDER BY t0.\"id\" ASC",
                // H2
                "SELECT t0.\"id\", t0.\"username\", CASE WHEN t0.\"age\" >= ? THEN ? WHEN t0.\"age\" >= ? THEN ? ELSE ? END AS \"age_group\" FROM \"matrix_user\" t0 ORDER BY t0.\"id\" ASC",
                30, "Senior", 25, "Middle", "Junior"
        );

        // 真实物理数据库执行闭环：
        // Alice (age 20) -> Junior
        // Bob (age 25) -> Middle
        // Charlie (age 30) -> Senior
        // David (age 20) -> Junior
        verifyAllModes(targetOrm -> {
            MatrixUser u = targetOrm.proxy(MatrixUser.class);
            CaseWhenBuilder<?> caseWhenExpr = caseWhen()
                    .when(u.getAge(), SqlOperator.GE, 30, "Senior")
                    .when(u.getAge(), SqlOperator.GE, 25, "Middle")
                    .elseVal("Junior")
                    .as("age_group");

            List<Map<String, Object>> rows = targetOrm.select(u.getId(), u.getUsername(), caseWhenExpr)
                    .from(MatrixUser.class)
                    .orderByAsc(u.getId())
                    .listMaps();

            Assert.assertEquals(4, rows.size());

            Map<String, Object> r1 = rows.get(0);
            Assert.assertEquals("alice", r1.get("username") != null ? r1.get("username") : r1.get("USERNAME"));
            Assert.assertEquals("Junior", r1.get("age_group") != null ? r1.get("age_group") : r1.get("AGE_GROUP"));

            Map<String, Object> r2 = rows.get(1);
            Assert.assertEquals("bob", r2.get("username") != null ? r2.get("username") : r2.get("USERNAME"));
            Assert.assertEquals("Middle", r2.get("age_group") != null ? r2.get("age_group") : r2.get("AGE_GROUP"));

            Map<String, Object> r3 = rows.get(2);
            Assert.assertEquals("charlie", r3.get("username") != null ? r3.get("username") : r3.get("USERNAME"));
            Assert.assertEquals("Senior", r3.get("age_group") != null ? r3.get("age_group") : r3.get("AGE_GROUP"));

            Map<String, Object> r4 = rows.get(3);
            Assert.assertEquals("david", r4.get("username") != null ? r4.get("username") : r4.get("USERNAME"));
            Assert.assertEquals("Junior", r4.get("age_group") != null ? r4.get("age_group") : r4.get("AGE_GROUP"));

            // 模式 2: orm.sql() 模式
            List<Map<String, Object>> sqlRows = targetOrm.sql(e -> {
                MatrixUser su = e.table(MatrixUser.class);
                CaseWhenBuilder<?> scw = caseWhen()
                        .when(su.getAge(), SqlOperator.GE, 30, "Senior")
                        .when(su.getAge(), SqlOperator.GE, 25, "Middle")
                        .elseVal("Junior")
                        .as("age_group");
                e.select(su.getId(), su.getUsername(), scw).from(su).orderByAsc(su.getId());
            }).maps();
            Assert.assertEquals(4, sqlRows.size());
            Assert.assertEquals("Junior", sqlRows.get(0).get("age_group") != null ? sqlRows.get(0).get("age_group") : sqlRows.get(0).get("AGE_GROUP"));
        });
    }

    /**
     * 7.2 窗口函数：ROW_NUMBER() 与 RANK() 开窗排序
     * 场景：按 status 分区，按 score 倒序生成 row_number
     */
    @Test
    public void test7_2_WindowFunctionRowNumberAndRank() {
        assertMatrixSql(
                oOrm -> {
                    MatrixUser u = oOrm.proxy(MatrixUser.class);
                    return oOrm.select(
                            u.getId(),
                            u.getUsername(),
                            u.getStatus(),
                            u.getScore(),
                            rowNumber().over().partitionBy(u.getStatus()).orderBy(u.getScore()).desc().as("rn"),
                            rank().over().partitionBy(u.getStatus()).orderBy(u.getAge()).asc().as("rk")
                    ).from(MatrixUser.class).orderByAsc(u.getId());
                },
                // MySQL
                "SELECT t0.`id`, t0.`username`, t0.`status`, t0.`score`, ROW_NUMBER() OVER (PARTITION BY t0.`status` ORDER BY t0.`score` DESC) AS `rn`, RANK() OVER (PARTITION BY t0.`status` ORDER BY t0.`age` ASC) AS `rk` FROM `matrix_user` t0 ORDER BY t0.`id` ASC",
                // PostgreSQL
                "SELECT t0.\"id\", t0.\"username\", t0.\"status\", t0.\"score\", ROW_NUMBER() OVER (PARTITION BY t0.\"status\" ORDER BY t0.\"score\" DESC) AS \"rn\", RANK() OVER (PARTITION BY t0.\"status\" ORDER BY t0.\"age\" ASC) AS \"rk\" FROM \"matrix_user\" t0 ORDER BY t0.\"id\" ASC",
                // Oracle
                "SELECT t0.\"ID\", t0.\"USERNAME\", t0.\"STATUS\", t0.\"SCORE\", ROW_NUMBER() OVER (PARTITION BY t0.\"STATUS\" ORDER BY t0.\"SCORE\" DESC) AS \"rn\", RANK() OVER (PARTITION BY t0.\"STATUS\" ORDER BY t0.\"AGE\" ASC) AS \"rk\" FROM \"MATRIX_USER\" t0 ORDER BY t0.\"ID\" ASC",
                // SQLite
                "SELECT t0.\"id\", t0.\"username\", t0.\"status\", t0.\"score\", ROW_NUMBER() OVER (PARTITION BY t0.\"status\" ORDER BY t0.\"score\" DESC) AS \"rn\", RANK() OVER (PARTITION BY t0.\"status\" ORDER BY t0.\"age\" ASC) AS \"rk\" FROM \"matrix_user\" t0 ORDER BY t0.\"id\" ASC",
                // H2
                "SELECT t0.\"id\", t0.\"username\", t0.\"status\", t0.\"score\", ROW_NUMBER() OVER (PARTITION BY t0.\"status\" ORDER BY t0.\"score\" DESC) AS \"rn\", RANK() OVER (PARTITION BY t0.\"status\" ORDER BY t0.\"age\" ASC) AS \"rk\" FROM \"matrix_user\" t0 ORDER BY t0.\"id\" ASC"
        );

        // 真实物理数据库执行闭环：
        verifyAllModes(targetOrm -> {
            MatrixUser u = targetOrm.proxy(MatrixUser.class);
            List<Map<String, Object>> rows = targetOrm.select(
                    u.getId(),
                    u.getUsername(),
                    u.getStatus(),
                    u.getScore(),
                    rowNumber().over().partitionBy(u.getStatus()).orderBy(u.getScore()).desc().as("rn"),
                    rank().over().partitionBy(u.getStatus()).orderBy(u.getAge()).asc().as("rk")
            ).from(MatrixUser.class).orderByAsc(u.getId()).listMaps();

            Assert.assertEquals(4, rows.size());
            for (Map<String, Object> row : rows) {
                Assert.assertNotNull(row.get("rn") != null ? row.get("rn") : row.get("RN"));
                Assert.assertNotNull(row.get("rk") != null ? row.get("rk") : row.get("RK"));
            }

            // 模式 2: orm.sql() 模式
            List<Map<String, Object>> sqlRows = targetOrm.sql(e -> {
                MatrixUser su = e.table(MatrixUser.class);
                e.select(
                        su.getId(),
                        su.getUsername(),
                        su.getStatus(),
                        su.getScore(),
                        rowNumber().over().partitionBy(su.getStatus()).orderBy(su.getScore()).desc().as("rn"),
                        rank().over().partitionBy(su.getStatus()).orderBy(su.getAge()).asc().as("rk")
                ).from(su).orderByAsc(su.getId());
            }).maps();
            Assert.assertEquals(4, sqlRows.size());
        });
    }

    /**
     * 7.3 聚合窗口函数：SUM(...) OVER 与 AVG(...) OVER
     * 场景：按 status 分区，计算分区内的总 score 与平均 age
     */
    @Test
    public void test7_3_WindowFunctionAggregateOver() {
        assertMatrixSql(
                oOrm -> {
                    MatrixUser u = oOrm.proxy(MatrixUser.class);
                    return oOrm.select(
                            u.getId(),
                            u.getUsername(),
                            u.getStatus(),
                            sum(u.getScore()).over().partitionBy(u.getStatus()).as("sum_score"),
                            avg(u.getAge()).over().partitionBy(u.getStatus()).as("avg_age")
                    ).from(MatrixUser.class).orderByAsc(u.getId());
                },
                // MySQL
                "SELECT t0.`id`, t0.`username`, t0.`status`, SUM(t0.`score`) OVER (PARTITION BY t0.`status`) AS `sum_score`, AVG(t0.`age`) OVER (PARTITION BY t0.`status`) AS `avg_age` FROM `matrix_user` t0 ORDER BY t0.`id` ASC",
                // PostgreSQL
                "SELECT t0.\"id\", t0.\"username\", t0.\"status\", SUM(t0.\"score\") OVER (PARTITION BY t0.\"status\") AS \"sum_score\", AVG(t0.\"age\") OVER (PARTITION BY t0.\"status\") AS \"avg_age\" FROM \"matrix_user\" t0 ORDER BY t0.\"id\" ASC",
                // Oracle
                "SELECT t0.\"ID\", t0.\"USERNAME\", t0.\"STATUS\", SUM(t0.\"SCORE\") OVER (PARTITION BY t0.\"STATUS\") AS \"sum_score\", AVG(t0.\"AGE\") OVER (PARTITION BY t0.\"STATUS\") AS \"avg_age\" FROM \"MATRIX_USER\" t0 ORDER BY t0.\"ID\" ASC",
                // SQLite
                "SELECT t0.\"id\", t0.\"username\", t0.\"status\", SUM(t0.\"score\") OVER (PARTITION BY t0.\"status\") AS \"sum_score\", AVG(t0.\"age\") OVER (PARTITION BY t0.\"status\") AS \"avg_age\" FROM \"matrix_user\" t0 ORDER BY t0.\"id\" ASC",
                // H2
                "SELECT t0.\"id\", t0.\"username\", t0.\"status\", SUM(t0.\"score\") OVER (PARTITION BY t0.\"status\") AS \"sum_score\", AVG(t0.\"age\") OVER (PARTITION BY t0.\"status\") AS \"avg_age\" FROM \"matrix_user\" t0 ORDER BY t0.\"id\" ASC"
        );

        // 真实物理数据库执行闭环：
        verifyAllModes(targetOrm -> {
            MatrixUser u = targetOrm.proxy(MatrixUser.class);
            List<Map<String, Object>> rows = targetOrm.select(
                    u.getId(),
                    u.getUsername(),
                    u.getStatus(),
                    sum(u.getScore()).over().partitionBy(u.getStatus()).as("sum_score"),
                    avg(u.getAge()).over().partitionBy(u.getStatus()).as("avg_age")
            ).from(MatrixUser.class).orderByAsc(u.getId()).listMaps();

            Assert.assertEquals(4, rows.size());
            for (Map<String, Object> row : rows) {
                Assert.assertNotNull(row.get("sum_score") != null ? row.get("sum_score") : row.get("SUM_SCORE"));
                Assert.assertNotNull(row.get("avg_age") != null ? row.get("avg_age") : row.get("AVG_AGE"));
            }

            // 模式 2: orm.sql() 模式
            List<Map<String, Object>> sqlRows = targetOrm.sql(e -> {
                MatrixUser su = e.table(MatrixUser.class);
                e.select(
                        su.getId(),
                        su.getUsername(),
                        su.getStatus(),
                        sum(su.getScore()).over().partitionBy(su.getStatus()).as("sum_score"),
                        avg(su.getAge()).over().partitionBy(su.getStatus()).as("avg_age")
                ).from(su).orderByAsc(su.getId());
            }).maps();
            Assert.assertEquals(4, sqlRows.size());
        });
    }

    /**
     * 7.4 基础通用表表达式 (CTE)
     * 场景：WITH high_scorers AS (SELECT ... FROM matrix_user WHERE score >= 80.00) SELECT * FROM high_scorers
     */
    @Test
    public void test7_4_CteBasic() {
        assertMatrixSql(
                oOrm -> {
                    MatrixUser u = oOrm.proxy(MatrixUser.class);
                    Cte<MatrixUser> highScorers = oOrm.cte("high_scorers",
                            oOrm.select(MatrixUser.class).where().ge(MatrixUser::getScore, new BigDecimal("80.00")));

                    MatrixUser cteProxy = highScorers.proxy();
                    return oOrm.with(highScorers)
                            .select(highScorers)
                            .where().eq(cteProxy.getStatus(), 1)
                            .orderByAsc(cteProxy.getId());
                },
                // MySQL
                "WITH `high_scorers` AS (" + ALL_USERS_MYSQL + " WHERE t0.`score` >= ?) SELECT t0.`id`, t0.`username`, t0.`email`, t0.`age`, t0.`status`, t0.`score`, t0.`create_time`, t0.`is_deleted` FROM `high_scorers` t0 WHERE t0.`status` = ? ORDER BY t0.`id` ASC",
                // PostgreSQL
                "WITH \"high_scorers\" AS (" + ALL_USERS_PG + " WHERE t0.\"score\" >= ?) SELECT t0.\"id\", t0.\"username\", t0.\"email\", t0.\"age\", t0.\"status\", t0.\"score\", t0.\"create_time\", t0.\"is_deleted\" FROM \"high_scorers\" t0 WHERE t0.\"status\" = ? ORDER BY t0.\"id\" ASC",
                // Oracle
                "WITH \"high_scorers\" AS (" + ALL_USERS_ORACLE + " WHERE t0.\"SCORE\" >= ?) SELECT t0.\"ID\", t0.\"USERNAME\", t0.\"EMAIL\", t0.\"AGE\", t0.\"STATUS\", t0.\"SCORE\", t0.\"CREATE_TIME\", t0.\"IS_DELETED\" FROM \"high_scorers\" t0 WHERE t0.\"STATUS\" = ? ORDER BY t0.\"ID\" ASC",
                // SQLite
                "WITH \"high_scorers\" AS (" + ALL_USERS_PG + " WHERE t0.\"score\" >= ?) SELECT t0.\"id\", t0.\"username\", t0.\"email\", t0.\"age\", t0.\"status\", t0.\"score\", t0.\"create_time\", t0.\"is_deleted\" FROM \"high_scorers\" t0 WHERE t0.\"status\" = ? ORDER BY t0.\"id\" ASC",
                // H2
                "WITH \"high_scorers\" AS (" + ALL_USERS_PG + " WHERE t0.\"score\" >= ?) SELECT t0.\"id\", t0.\"username\", t0.\"email\", t0.\"age\", t0.\"status\", t0.\"score\", t0.\"create_time\", t0.\"is_deleted\" FROM \"high_scorers\" t0 WHERE t0.\"status\" = ? ORDER BY t0.\"id\" ASC",
                new BigDecimal("80.00"), 1
        );

        // 真实物理数据库执行闭环：Alice(95.50, status 1) 和 Bob(88.00, status 1)
        verifyAllModes(targetOrm -> {
            Cte<MatrixUser> highScorers = targetOrm.cte("high_scorers",
                    targetOrm.select(MatrixUser.class).where().ge(MatrixUser::getScore, new BigDecimal("80.00")));

            MatrixUser cteProxy = highScorers.proxy();
            List<MatrixUser> list = targetOrm.with(highScorers)
                    .select(highScorers)
                    .where().eq(cteProxy.getStatus(), 1)
                    .orderByAsc(cteProxy.getId())
                    .toList();

            Assert.assertEquals(2, list.size());
            Assert.assertEquals("alice", list.get(0).getUsername());
            Assert.assertEquals("bob", list.get(1).getUsername());

            // 模式 2: orm.sql() 模式
            List<MatrixUser> sqlList = targetOrm.sql(e -> {
                Cte<MatrixUser> sc = targetOrm.cte("high_scorers",
                        targetOrm.select(MatrixUser.class).where().ge(MatrixUser::getScore, new BigDecimal("80.00")));
                MatrixUser scProxy = sc.proxy();
                e.select(sc).with(sc).where().eq(scProxy.getStatus(), 1).orderByAsc(scProxy.getId());
            }).list(MatrixUser.class);
            Assert.assertEquals(2, sqlList.size());
        });
    }

    /**
     * 7.5 通用表表达式 (CTE) 与普通物理表做 JOIN 连接查询
     * 场景：WITH high_orders AS (SELECT ... FROM matrix_order WHERE amount >= 200.00)
     *       SELECT u.* FROM matrix_user u INNER JOIN high_orders o ON o.user_id = u.id ORDER BY u.id ASC
     */
    @Test
    public void test7_5_CteJoin() {
        assertMatrixSql(
                oOrm -> {
                    Cte<MatrixOrder> highOrderCte = oOrm.cte("high_orders",
                            oOrm.select(MatrixOrder.class).where().ge(MatrixOrder::getAmount, new BigDecimal("200.00")));

                    return oOrm.select(MatrixUser.class)
                            .with(highOrderCte)
                            .innerJoin(highOrderCte, (on, user, order) -> on.eq(order.getUserId(), user.getId()))
                            .orderByAsc(MatrixUser::getId);
                },
                // MySQL
                "WITH `high_orders` AS (SELECT t0.`id`, t0.`user_id`, t0.`order_no`, t0.`amount`, t0.`status`, t0.`create_time` FROM `matrix_order` t0 WHERE t0.`amount` >= ?) " + ALL_USERS_MYSQL + " INNER JOIN `high_orders` t1 ON t1.`user_id` = t0.`id` ORDER BY t0.`id` ASC",
                // PostgreSQL
                "WITH \"high_orders\" AS (SELECT t0.\"id\", t0.\"user_id\", t0.\"order_no\", t0.\"amount\", t0.\"status\", t0.\"create_time\" FROM \"matrix_order\" t0 WHERE t0.\"amount\" >= ?) " + ALL_USERS_PG + " INNER JOIN \"high_orders\" t1 ON t1.\"user_id\" = t0.\"id\" ORDER BY t0.\"id\" ASC",
                // Oracle
                "WITH \"high_orders\" AS (SELECT t0.\"ID\", t0.\"USER_ID\", t0.\"ORDER_NO\", t0.\"AMOUNT\", t0.\"STATUS\", t0.\"CREATE_TIME\" FROM \"MATRIX_ORDER\" t0 WHERE t0.\"AMOUNT\" >= ?) " + ALL_USERS_ORACLE + " INNER JOIN \"high_orders\" t1 ON t1.\"USER_ID\" = t0.\"ID\" ORDER BY t0.\"ID\" ASC",
                // SQLite
                "WITH \"high_orders\" AS (SELECT t0.\"id\", t0.\"user_id\", t0.\"order_no\", t0.\"amount\", t0.\"status\", t0.\"create_time\" FROM \"matrix_order\" t0 WHERE t0.\"amount\" >= ?) " + ALL_USERS_PG + " INNER JOIN \"high_orders\" t1 ON t1.\"user_id\" = t0.\"id\" ORDER BY t0.\"id\" ASC",
                // H2
                "WITH \"high_orders\" AS (SELECT t0.\"id\", t0.\"user_id\", t0.\"order_no\", t0.\"amount\", t0.\"status\", t0.\"create_time\" FROM \"matrix_order\" t0 WHERE t0.\"amount\" >= ?) " + ALL_USERS_PG + " INNER JOIN \"high_orders\" t1 ON t1.\"user_id\" = t0.\"id\" ORDER BY t0.\"id\" ASC",
                new BigDecimal("200.00")
        );

        // 真实物理数据库执行闭环：只有 Alice 存在金额 >= 200.00 的订单 (ORD-002: 299.00)
        verifyAllModes(targetOrm -> {
            Cte<MatrixOrder> highOrderCte = targetOrm.cte("high_orders",
                    targetOrm.select(MatrixOrder.class).where().ge(MatrixOrder::getAmount, new BigDecimal("200.00")));

            List<MatrixUser> list = targetOrm.select(MatrixUser.class)
                    .with(highOrderCte)
                    .innerJoin(highOrderCte, (on, user, order) -> on.eq(order.getUserId(), user.getId()))
                    .orderByAsc(MatrixUser::getId)
                    .toList();

            Assert.assertEquals(1, list.size());
            Assert.assertEquals("alice", list.get(0).getUsername());

            // 模式 2: orm.sql() 模式
            List<MatrixUser> sqlList = targetOrm.sql(e -> {
                Cte<MatrixOrder> shoc = targetOrm.cte("high_orders",
                        targetOrm.select(MatrixOrder.class).where().ge(MatrixOrder::getAmount, new BigDecimal("200.00")));
                e.toExecutionFromReturn(targetOrm.select(MatrixUser.class)
                        .with(shoc)
                        .innerJoin(shoc, (on, user, order) -> on.eq(order.getUserId(), user.getId()))
                        .orderByAsc(MatrixUser::getId));
            }).list(MatrixUser.class);
            Assert.assertEquals(1, sqlList.size());
        });
    }

    /**
     * 7.6 跨方言悲观锁机制：FOR UPDATE（行级排他锁）
     */
    @Test
    public void test7_6_PessimisticLockForUpdate() {
        assertMatrixSql(
                oOrm -> oOrm.select(MatrixUser.class).where().eq(MatrixUser::getId, 1L).forUpdate(),
                // MySQL
                ALL_USERS_MYSQL + " WHERE t0.`id` = ? FOR UPDATE",
                // PostgreSQL
                ALL_USERS_PG + " WHERE t0.\"id\" = ? FOR UPDATE",
                // Oracle
                ALL_USERS_ORACLE + " WHERE t0.\"ID\" = ? FOR UPDATE",
                // SQLite (退化为无锁)
                ALL_USERS_PG + " WHERE t0.\"id\" = ?",
                // H2
                ALL_USERS_PG + " WHERE t0.\"id\" = ? FOR UPDATE",
                1L
        );

        // 真实物理数据库执行闭环：
        verifyAllModes(targetOrm -> {
            List<MatrixUser> list = targetOrm.select(MatrixUser.class)
                    .where().eq(MatrixUser::getId, 1L)
                    .forUpdate()
                    .list();
            Assert.assertEquals(1, list.size());
            Assert.assertEquals("alice", list.get(0).getUsername());

            // 模式 2: orm.sql() 模式
            List<MatrixUser> sqlList = targetOrm.sql(e -> {
                MatrixUser su = e.table(MatrixUser.class);
                e.select(su).from(su).where().eq(su.getId(), 1L);
                e.forUpdate();
            }).list(MatrixUser.class);
            Assert.assertEquals(1, sqlList.size());
            Assert.assertEquals("alice", sqlList.get(0).getUsername());
        });
    }

    /**
     * 7.7 跨方言悲观锁机制：FOR UPDATE NOWAIT（非阻塞排他锁与自适应降级）
     */
    @Test
    public void test7_7_PessimisticLockForUpdateNowait() {
        assertMatrixSql(
                oOrm -> oOrm.select(MatrixUser.class).where().eq(MatrixUser::getId, 1L).forUpdateNowait(),
                // MySQL
                ALL_USERS_MYSQL + " WHERE t0.`id` = ? FOR UPDATE NOWAIT",
                // PostgreSQL
                ALL_USERS_PG + " WHERE t0.\"id\" = ? FOR UPDATE NOWAIT",
                // Oracle
                ALL_USERS_ORACLE + " WHERE t0.\"ID\" = ? FOR UPDATE NOWAIT",
                // SQLite (退化为无锁)
                ALL_USERS_PG + " WHERE t0.\"id\" = ?",
                // H2 (NOWAIT 安全降级为 FOR UPDATE)
                ALL_USERS_PG + " WHERE t0.\"id\" = ? FOR UPDATE",
                1L
        );

        // 真实物理数据库执行闭环：
        verifyAllModes(targetOrm -> {
            List<MatrixUser> list = targetOrm.select(MatrixUser.class)
                    .where().eq(MatrixUser::getId, 1L)
                    .forUpdateNowait()
                    .list();
            Assert.assertEquals(1, list.size());
            Assert.assertEquals("alice", list.get(0).getUsername());

            // 模式 2: orm.sql() 模式
            List<MatrixUser> sqlList = targetOrm.sql(e -> {
                MatrixUser su = e.table(MatrixUser.class);
                e.select(su).from(su).where().eq(su.getId(), 1L);
                e.forUpdateNowait();
            }).list(MatrixUser.class);
            Assert.assertEquals(1, sqlList.size());
            Assert.assertEquals("alice", sqlList.get(0).getUsername());
        });
    }

    /**
     * 7.8 跨方言悲观锁机制：FOR UPDATE SKIP LOCKED（跳过锁行与自适应降级）
     */
    @Test
    public void test7_8_PessimisticLockForUpdateSkipLocked() {
        assertMatrixSql(
                oOrm -> oOrm.select(MatrixUser.class).where().eq(MatrixUser::getId, 1L).forUpdateSkipLocked(),
                // MySQL
                ALL_USERS_MYSQL + " WHERE t0.`id` = ? FOR UPDATE SKIP LOCKED",
                // PostgreSQL
                ALL_USERS_PG + " WHERE t0.\"id\" = ? FOR UPDATE SKIP LOCKED",
                // Oracle
                ALL_USERS_ORACLE + " WHERE t0.\"ID\" = ? FOR UPDATE SKIP LOCKED",
                // SQLite (退化为无锁)
                ALL_USERS_PG + " WHERE t0.\"id\" = ?",
                // H2 (SKIP LOCKED 安全降级为 FOR UPDATE)
                ALL_USERS_PG + " WHERE t0.\"id\" = ? FOR UPDATE",
                1L
        );

        // 真实物理数据库执行闭环：
        verifyAllModes(targetOrm -> {
            List<MatrixUser> list = targetOrm.select(MatrixUser.class)
                    .where().eq(MatrixUser::getId, 1L)
                    .forUpdateSkipLocked()
                    .list();
            Assert.assertEquals(1, list.size());
            Assert.assertEquals("alice", list.get(0).getUsername());

            // 模式 2: orm.sql() 模式
            List<MatrixUser> sqlList = targetOrm.sql(e -> {
                MatrixUser su = e.table(MatrixUser.class);
                e.select(su).from(su).where().eq(su.getId(), 1L);
                e.forUpdateSkipLocked();
            }).list(MatrixUser.class);
            Assert.assertEquals(1, sqlList.size());
            Assert.assertEquals("alice", sqlList.get(0).getUsername());
        });
    }
}

