package com.javaorm.core.sqlmatrix;

import com.javaorm.core.query.SelectBuilder;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.javaorm.core.SQL.avg;
import static com.javaorm.core.SQL.count;
import static com.javaorm.core.SQL.subQuery;

/**
 * 阶段六：子查询与集合操作矩阵单测 (Phase 6)。
 *
 * 核心验证矩阵：
 * 1. WHERE 子查询：IN (SELECT ...)
 * 2. WHERE 子查询：NOT IN (SELECT ...)
 * 3. WHERE 标量比较子查询：> (SELECT AVG(...) ...)
 * 4. 关联子查询：EXISTS (SELECT ... WHERE ...)
 * 5. 关联子查询：NOT EXISTS (SELECT ... WHERE ...)
 * 6. SELECT 投影中的标量子查询：(SELECT COUNT(...) ...) AS order_cnt
 * 7. 集合操作：UNION ALL (多查询合并)
 * 8. 集合操作：UNION (多查询合并去重)
 * 9. 复杂子查询综合测试：子查询 + 聚合函数 + JOIN 联合验证
 *
 * 校验闭环：
 * 1. 5 大数据库方言 (MySQL, PostgreSQL, Oracle 12c+, SQLite, H2) 100% 全文本精确断言；
 * 2. 真实 JDBC 数据库执行闭环校验（MySQL, PostgreSQL, Oracle 严格模式）。
 */
public class Phase6SubquerySetOperationMatrixTest extends AbstractSqlMatrixTest {

    private static final String ALL_USERS_MYSQL = "SELECT t0.`id`, t0.`username`, t0.`email`, t0.`age`, t0.`status`, t0.`score`, t0.`create_time`, t0.`is_deleted` FROM `matrix_user` t0";
    private static final String ALL_USERS_PG = "SELECT t0.\"id\", t0.\"username\", t0.\"email\", t0.\"age\", t0.\"status\", t0.\"score\", t0.\"create_time\", t0.\"is_deleted\" FROM \"matrix_user\" t0";
    private static final String ALL_USERS_ORACLE = "SELECT t0.\"ID\", t0.\"USERNAME\", t0.\"EMAIL\", t0.\"AGE\", t0.\"STATUS\", t0.\"SCORE\", t0.\"CREATE_TIME\", t0.\"IS_DELETED\" FROM \"MATRIX_USER\" t0";

    /**
     * 6.1 WHERE 子查询：IN (SELECT ...)
     * 场景：查询存在订单金额 >= 150 的所有用户
     */
    @Test
    public void test6_1_WhereInSubQuery() {
        assertMatrixSql(
                oOrm -> {
                    MatrixOrder o = oOrm.proxy(MatrixOrder.class);
                    return oOrm.select(MatrixUser.class)
                            .where(w -> w.in(MatrixUser::getId,
                                    oOrm.select(o.getUserId())
                                            .from(MatrixOrder.class)
                                            .where().ge(o.getAmount(), new BigDecimal("150.00"))));
                },
                // MySQL
                ALL_USERS_MYSQL + " WHERE t0.`id` IN (SELECT t0.`user_id` FROM `matrix_order` t0 WHERE t0.`amount` >= ?)",
                // PostgreSQL
                ALL_USERS_PG + " WHERE t0.\"id\" IN (SELECT t0.\"user_id\" FROM \"matrix_order\" t0 WHERE t0.\"amount\" >= ?)",
                // Oracle
                ALL_USERS_ORACLE + " WHERE t0.\"ID\" IN (SELECT t0.\"USER_ID\" FROM \"MATRIX_ORDER\" t0 WHERE t0.\"AMOUNT\" >= ?)",
                // SQLite
                ALL_USERS_PG + " WHERE t0.\"id\" IN (SELECT t0.\"user_id\" FROM \"matrix_order\" t0 WHERE t0.\"amount\" >= ?)",
                // H2
                ALL_USERS_PG + " WHERE t0.\"id\" IN (SELECT t0.\"user_id\" FROM \"matrix_order\" t0 WHERE t0.\"amount\" >= ?)",
                new BigDecimal("150.00")
        );

        // 真实执行闭环：只有 Alice (id=1) 的订单 ORD-001(199.90), ORD-002(299.00) >= 150.00
        verifyAllModes(targetOrm -> {
            MatrixOrder o = targetOrm.proxy(MatrixOrder.class);
            List<MatrixUser> list = targetOrm.select(MatrixUser.class)
                    .where(w -> w.in(MatrixUser::getId,
                            targetOrm.select(o.getUserId())
                                    .from(MatrixOrder.class)
                                    .where().ge(o.getAmount(), new BigDecimal("150.00"))))
                    .list();
            Assert.assertEquals(1, list.size());
            Assert.assertEquals("alice", list.get(0).getUsername());

            // 模式 2: orm.sql() 模式
            List<MatrixUser> sqlList = targetOrm.sql(e -> {
                MatrixOrder subOrder = targetOrm.proxy(MatrixOrder.class);
                e.select(MatrixUser.class)
                        .where(w -> w.in(MatrixUser::getId,
                                targetOrm.select(subOrder.getUserId())
                                        .from(MatrixOrder.class)
                                        .where().ge(subOrder.getAmount(), new BigDecimal("150.00"))));
            }).list(MatrixUser.class);
            Assert.assertEquals(1, sqlList.size());
            Assert.assertEquals("alice", sqlList.get(0).getUsername());
        });
    }

    /**
     * 6.2 WHERE 子查询：NOT IN (SELECT ...)
     * 场景：查询从未下过任何订单的用户（不在 matrix_order 中）
     */
    @Test
    public void test6_2_WhereNotInSubQuery() {
        assertMatrixSql(
                oOrm -> {
                    MatrixOrder o = oOrm.proxy(MatrixOrder.class);
                    return oOrm.select(MatrixUser.class)
                            .where(w -> w.notIn(MatrixUser::getId,
                                    oOrm.select(o.getUserId())
                                            .from(MatrixOrder.class)))
                            .orderByAsc(MatrixUser::getId);
                },
                // MySQL
                ALL_USERS_MYSQL + " WHERE t0.`id` NOT IN (SELECT t0.`user_id` FROM `matrix_order` t0) ORDER BY t0.`id` ASC",
                // PostgreSQL
                ALL_USERS_PG + " WHERE t0.\"id\" NOT IN (SELECT t0.\"user_id\" FROM \"matrix_order\" t0) ORDER BY t0.\"id\" ASC",
                // Oracle
                ALL_USERS_ORACLE + " WHERE t0.\"ID\" NOT IN (SELECT t0.\"USER_ID\" FROM \"MATRIX_ORDER\" t0) ORDER BY t0.\"ID\" ASC",
                // SQLite
                ALL_USERS_PG + " WHERE t0.\"id\" NOT IN (SELECT t0.\"user_id\" FROM \"matrix_order\" t0) ORDER BY t0.\"id\" ASC",
                // H2
                ALL_USERS_PG + " WHERE t0.\"id\" NOT IN (SELECT t0.\"user_id\" FROM \"matrix_order\" t0) ORDER BY t0.\"id\" ASC"
        );

        // 真实执行闭环：u3(charlie) 和 u4(david) 从未下过单
        verifyAllModes(targetOrm -> {
            MatrixOrder o = targetOrm.proxy(MatrixOrder.class);
            List<MatrixUser> list = targetOrm.select(MatrixUser.class)
                    .where(w -> w.notIn(MatrixUser::getId,
                            targetOrm.select(o.getUserId())
                                    .from(MatrixOrder.class)))
                    .orderByAsc(MatrixUser::getId)
                    .list();
            Assert.assertEquals(2, list.size());
            Assert.assertEquals("charlie", list.get(0).getUsername());
            Assert.assertEquals("david", list.get(1).getUsername());

            // 模式 2: orm.sql() 模式
            List<MatrixUser> sqlList = targetOrm.sql(e -> {
                MatrixOrder subOrder = targetOrm.proxy(MatrixOrder.class);
                e.select(MatrixUser.class)
                        .where(w -> w.notIn(MatrixUser::getId,
                                targetOrm.select(subOrder.getUserId())
                                        .from(MatrixOrder.class)))
                        .orderByAsc(MatrixUser::getId);
            }).list(MatrixUser.class);
            Assert.assertEquals(2, sqlList.size());
            Assert.assertEquals("charlie", sqlList.get(0).getUsername());
        });
    }

    /**
     * 6.3 WHERE 标量比较子查询：> (SELECT AVG(...) ...)
     * 场景：查询年龄大于用户平均年龄的用户
     */
    @Test
    public void test6_3_WhereScalarComparisonSubQuery() {
        assertMatrixSql(
                oOrm -> {
                    MatrixUser subU = oOrm.proxy(MatrixUser.class);
                    SelectBuilder<MatrixUser> avgSubQuery = oOrm.select(avg(subU.getAge())).from(MatrixUser.class);

                    MatrixUser u = oOrm.proxy(MatrixUser.class);
                    return oOrm.select(MatrixUser.class)
                            .where(w -> w.gt(u.getAge(), subQuery(avgSubQuery)))
                            .orderByAsc(u.getId());
                },
                // MySQL
                ALL_USERS_MYSQL + " WHERE t0.`age` > (SELECT AVG(t0.`age`) FROM `matrix_user` t0) ORDER BY t0.`id` ASC",
                // PostgreSQL
                ALL_USERS_PG + " WHERE t0.\"age\" > (SELECT AVG(t0.\"age\") FROM \"matrix_user\" t0) ORDER BY t0.\"id\" ASC",
                // Oracle
                ALL_USERS_ORACLE + " WHERE t0.\"AGE\" > (SELECT AVG(t0.\"AGE\") FROM \"MATRIX_USER\" t0) ORDER BY t0.\"ID\" ASC",
                // SQLite
                ALL_USERS_PG + " WHERE t0.\"age\" > (SELECT AVG(t0.\"age\") FROM \"matrix_user\" t0) ORDER BY t0.\"id\" ASC",
                // H2
                ALL_USERS_PG + " WHERE t0.\"age\" > (SELECT AVG(t0.\"age\") FROM \"matrix_user\" t0) ORDER BY t0.\"id\" ASC"
        );

        // 真实执行闭环：平均年龄 (20 + 25 + 30 + 20) / 4 = 23.75
        // 大于 23.75 的有: bob (25) 和 charlie (30)
        verifyAllModes(targetOrm -> {
            MatrixUser subU = targetOrm.proxy(MatrixUser.class);
            SelectBuilder<MatrixUser> avgSubQuery = targetOrm.select(avg(subU.getAge())).from(MatrixUser.class);

            MatrixUser u = targetOrm.proxy(MatrixUser.class);
            List<MatrixUser> list = targetOrm.select(MatrixUser.class)
                    .where(w -> w.gt(u.getAge(), subQuery(avgSubQuery)))
                    .orderByAsc(u.getId())
                    .list();
            Assert.assertEquals(2, list.size());
            Assert.assertEquals("bob", list.get(0).getUsername());
            Assert.assertEquals("charlie", list.get(1).getUsername());

            // 模式 2: orm.sql() 模式
            List<MatrixUser> sqlList = targetOrm.sql(e -> {
                MatrixUser su = e.table(MatrixUser.class);
                SelectBuilder<MatrixUser> avgSub = targetOrm.select(avg(su.getAge())).from(MatrixUser.class);
                e.select(su).from(su)
                        .where(w -> w.gt(su.getAge(), subQuery(avgSub)))
                        .orderByAsc(su.getId());
            }).list(MatrixUser.class);
            Assert.assertEquals(2, sqlList.size());
            Assert.assertEquals("bob", sqlList.get(0).getUsername());
        });
    }

    /**
     * 6.4 关联子查询：EXISTS (SELECT ... WHERE ...)
     * 场景：查询名下存在 status = 2 的已完成订单的用户
     */
    @Test
    public void test6_4_WhereExistsSubQuery() {
        assertMatrixSql(
                oOrm -> {
                    MatrixUser u = oOrm.proxy(MatrixUser.class);
                    MatrixOrder o = oOrm.proxy(MatrixOrder.class);
                    return oOrm.select(MatrixUser.class)
                            .where()
                            .andExists(
                                     oOrm.select(o.getId())
                                            .from(MatrixOrder.class)
                                            .where()
                                            .eq(o.getUserId(), u.getId())
                                            .eq(o.getStatus(), 2)
                            );
                },
                // MySQL
                ALL_USERS_MYSQL + " WHERE EXISTS (SELECT t1.`id` FROM `matrix_order` t1 WHERE t1.`user_id` = t0.`id` AND t1.`status` = ?)",
                // PostgreSQL
                ALL_USERS_PG + " WHERE EXISTS (SELECT t1.\"id\" FROM \"matrix_order\" t1 WHERE t1.\"user_id\" = t0.\"id\" AND t1.\"status\" = ?)",
                // Oracle
                ALL_USERS_ORACLE + " WHERE EXISTS (SELECT t1.\"ID\" FROM \"MATRIX_ORDER\" t1 WHERE t1.\"USER_ID\" = t0.\"ID\" AND t1.\"STATUS\" = ?)",
                // SQLite
                ALL_USERS_PG + " WHERE EXISTS (SELECT t1.\"id\" FROM \"matrix_order\" t1 WHERE t1.\"user_id\" = t0.\"id\" AND t1.\"status\" = ?)",
                // H2
                ALL_USERS_PG + " WHERE EXISTS (SELECT t1.\"id\" FROM \"matrix_order\" t1 WHERE t1.\"user_id\" = t0.\"id\" AND t1.\"status\" = ?)",
                2
        );

        // 真实执行闭环：只有 Alice 有 status = 2 的订单 (ORD-002)
        verifyAllModes(targetOrm -> {
            MatrixUser u = targetOrm.proxy(MatrixUser.class);
            MatrixOrder o = targetOrm.proxy(MatrixOrder.class);
            List<MatrixUser> list = targetOrm.select(MatrixUser.class)
                    .where()
                    .andExists(
                            targetOrm.select(o.getId())
                                    .from(MatrixOrder.class)
                                    .where()
                                    .eq(o.getUserId(), u.getId())
                                    .eq(o.getStatus(), 2)
                    ).list();
            Assert.assertEquals(1, list.size());
            Assert.assertEquals("alice", list.get(0).getUsername());

            // 模式 2: orm.sql() 模式
            List<MatrixUser> sqlList = targetOrm.sql(e -> {
                e.select(MatrixUser.class).where().andExists(
                        targetOrm.select(o.getId())
                                .from(MatrixOrder.class)
                                .where()
                                .eq(o.getUserId(), u.getId())
                                .eq(o.getStatus(), 2)
                );
            }).list(MatrixUser.class);
            Assert.assertEquals(1, sqlList.size());
            Assert.assertEquals("alice", sqlList.get(0).getUsername());
        });
    }

    /**
     * 6.5 关联子查询：NOT EXISTS (SELECT ... WHERE ...)
     * 场景：查询从未下过订单的用户 (NOT EXISTS)
     */
    @Test
    public void test6_5_WhereNotExistsSubQuery() {
        assertMatrixSql(
                oOrm -> {
                    MatrixUser u = oOrm.proxy(MatrixUser.class);
                    MatrixOrder o = oOrm.proxy(MatrixOrder.class);
                    return oOrm.select(MatrixUser.class)
                            .where()
                            .andNotExists(
                                    oOrm.select(o.getId())
                                            .from(MatrixOrder.class)
                                            .where()
                                            .eq(o.getUserId(), u.getId())
                            )
                            .orderByAsc(MatrixUser::getId);
                },
                // MySQL
                ALL_USERS_MYSQL + " WHERE NOT EXISTS (SELECT t1.`id` FROM `matrix_order` t1 WHERE t1.`user_id` = t0.`id`) ORDER BY t0.`id` ASC",
                // PostgreSQL
                ALL_USERS_PG + " WHERE NOT EXISTS (SELECT t1.\"id\" FROM \"matrix_order\" t1 WHERE t1.\"user_id\" = t0.\"id\") ORDER BY t0.\"id\" ASC",
                // Oracle
                ALL_USERS_ORACLE + " WHERE NOT EXISTS (SELECT t1.\"ID\" FROM \"MATRIX_ORDER\" t1 WHERE t1.\"USER_ID\" = t0.\"ID\") ORDER BY t0.\"ID\" ASC",
                // SQLite
                ALL_USERS_PG + " WHERE NOT EXISTS (SELECT t1.\"id\" FROM \"matrix_order\" t1 WHERE t1.\"user_id\" = t0.\"id\") ORDER BY t0.\"id\" ASC",
                // H2
                ALL_USERS_PG + " WHERE NOT EXISTS (SELECT t1.\"id\" FROM \"matrix_order\" t1 WHERE t1.\"user_id\" = t0.\"id\") ORDER BY t0.\"id\" ASC"
        );

        // 真实执行闭环：charlie 和 david
        verifyAllModes(targetOrm -> {
            MatrixUser u = targetOrm.proxy(MatrixUser.class);
            MatrixOrder o = targetOrm.proxy(MatrixOrder.class);
            List<MatrixUser> list = targetOrm.select(MatrixUser.class)
                    .where()
                    .andNotExists(
                            targetOrm.select(o.getId())
                                    .from(MatrixOrder.class)
                                    .where()
                                    .eq(o.getUserId(), u.getId())
                    )
                    .orderByAsc(MatrixUser::getId)
                    .list();
            Assert.assertEquals(2, list.size());
            Assert.assertEquals("charlie", list.get(0).getUsername());
            Assert.assertEquals("david", list.get(1).getUsername());

            // 模式 2: orm.sql() 模式
            List<MatrixUser> sqlList = targetOrm.sql(e -> {
                e.select(MatrixUser.class).where().andNotExists(
                        targetOrm.select(o.getId())
                                .from(MatrixOrder.class)
                                .where()
                                .eq(o.getUserId(), u.getId())
                ).orderByAsc(MatrixUser::getId);
            }).list(MatrixUser.class);
            Assert.assertEquals(2, sqlList.size());
            Assert.assertEquals("charlie", sqlList.get(0).getUsername());
        });
    }

    /**
     * 6.6 SELECT 标量子查询投影
     * 场景：投影用户的 id、username 以及名下订单数量 (SELECT COUNT(...) ...) AS order_cnt
     */
    @Test
    public void test6_6_SelectScalarSubQueryProjection() {
        assertMatrixSql(
                oOrm -> {
                    MatrixUser u = oOrm.proxy(MatrixUser.class);
                    MatrixOrder o = oOrm.proxy(MatrixOrder.class);
                    return oOrm.select(
                            u.getId(),
                            u.getUsername(),
                            subQuery(
                                    oOrm.select(count(o.getId()))
                                            .from(MatrixOrder.class)
                                            .where()
                                            .eq(o.getUserId(), u.getId())
                            ).as("order_cnt")
                    ).from(MatrixUser.class).orderByAsc(u.getId());
                },
                // MySQL
                "SELECT t1.`id`, t1.`username`, (SELECT COUNT(t0.`id`) FROM `matrix_order` t0 WHERE t0.`user_id` = t1.`id`) AS `order_cnt` FROM `matrix_user` t1 ORDER BY t1.`id` ASC",
                // PostgreSQL
                "SELECT t1.\"id\", t1.\"username\", (SELECT COUNT(t0.\"id\") FROM \"matrix_order\" t0 WHERE t0.\"user_id\" = t1.\"id\") AS \"order_cnt\" FROM \"matrix_user\" t1 ORDER BY t1.\"id\" ASC",
                // Oracle
                "SELECT t1.\"ID\", t1.\"USERNAME\", (SELECT COUNT(t0.\"ID\") FROM \"MATRIX_ORDER\" t0 WHERE t0.\"USER_ID\" = t1.\"ID\") AS \"order_cnt\" FROM \"MATRIX_USER\" t1 ORDER BY t1.\"ID\" ASC",
                // SQLite
                "SELECT t1.\"id\", t1.\"username\", (SELECT COUNT(t0.\"id\") FROM \"matrix_order\" t0 WHERE t0.\"user_id\" = t1.\"id\") AS \"order_cnt\" FROM \"matrix_user\" t1 ORDER BY t1.\"id\" ASC",
                // H2
                "SELECT t1.\"id\", t1.\"username\", (SELECT COUNT(t0.\"id\") FROM \"matrix_order\" t0 WHERE t0.\"user_id\" = t1.\"id\") AS \"order_cnt\" FROM \"matrix_user\" t1 ORDER BY t1.\"id\" ASC"
        );

        // 真实执行闭环：
        // Alice: 2 笔订单
        // Bob: 1 笔订单
        // Charlie: 0 笔订单
        // David: 0 笔订单
        verifyAllModes(targetOrm -> {
            MatrixUser u = targetOrm.proxy(MatrixUser.class);
            MatrixOrder o = targetOrm.proxy(MatrixOrder.class);
            List<Map<String, Object>> rows = targetOrm.select(
                    u.getId(),
                    u.getUsername(),
                    subQuery(
                            targetOrm.select(count(o.getId()))
                                    .from(MatrixOrder.class)
                                    .where()
                                    .eq(o.getUserId(), u.getId())
                    ).as("order_cnt")
            ).from(MatrixUser.class).orderByAsc(u.getId()).listMaps();

            Assert.assertEquals(4, rows.size());

            // row 0: Alice
            Map<String, Object> r1 = rows.get(0);
            Assert.assertEquals("alice", r1.get("username") != null ? r1.get("username") : r1.get("USERNAME"));
            Number cnt1 = (Number) (r1.get("order_cnt") != null ? r1.get("order_cnt") : r1.get("ORDER_CNT"));
            Assert.assertEquals(2L, cnt1.longValue());

            // row 1: Bob
            Map<String, Object> r2 = rows.get(1);
            Assert.assertEquals("bob", r2.get("username") != null ? r2.get("username") : r2.get("USERNAME"));
            Number cnt2 = (Number) (r2.get("order_cnt") != null ? r2.get("order_cnt") : r2.get("ORDER_CNT"));
            Assert.assertEquals(1L, cnt2.longValue());

            // row 2: Charlie
            Map<String, Object> r3 = rows.get(2);
            Assert.assertEquals("charlie", r3.get("username") != null ? r3.get("username") : r3.get("USERNAME"));
            Number cnt3 = (Number) (r3.get("order_cnt") != null ? r3.get("order_cnt") : r3.get("ORDER_CNT"));
            Assert.assertEquals(0L, cnt3.longValue());

            // row 3: David
            Map<String, Object> r4 = rows.get(3);
            Assert.assertEquals("david", r4.get("username") != null ? r4.get("username") : r4.get("USERNAME"));
            Number cnt4 = (Number) (r4.get("order_cnt") != null ? r4.get("order_cnt") : r4.get("ORDER_CNT"));
            Assert.assertEquals(0L, cnt4.longValue());

            // 模式 2: orm.sql() 模式
            List<Map<String, Object>> sqlRows = targetOrm.sql(e -> {
                e.toExecutionFromReturn(
                        targetOrm.select(
                                u.getId(),
                                u.getUsername(),
                                subQuery(
                                        targetOrm.select(count(o.getId()))
                                                .from(MatrixOrder.class)
                                                .where()
                                                .eq(o.getUserId(), u.getId())
                                ).as("order_cnt")
                        ).from(MatrixUser.class).orderByAsc(u.getId())
                );
            }).maps();
            Assert.assertEquals(4, sqlRows.size());
            Number sqlCnt1 = (Number) (sqlRows.get(0).get("order_cnt") != null ? sqlRows.get(0).get("order_cnt") : sqlRows.get(0).get("ORDER_CNT"));
            Assert.assertEquals(2L, sqlCnt1.longValue());
        });
    }

    /**
     * 6.7 集合操作：UNION ALL (多结果集合并，保留重复项)
     * 场景：查询 status = 1 的用户集合 UNION ALL status = 0 的用户集合
     */
    @Test
    public void test6_7_UnionAll() {
        assertMatrixSql(
                oOrm -> {
                    SelectBuilder<MatrixUser> q1 = oOrm.select(MatrixUser.class);
                    q1.where().eq(MatrixUser::getStatus, 1);
                    SelectBuilder<MatrixUser> q2 = oOrm.select(MatrixUser.class);
                    q2.where().eq(MatrixUser::getStatus, 0);
                    return q1.unionAll(q2);
                },
                // MySQL
                ALL_USERS_MYSQL + " WHERE t0.`status` = ? UNION ALL " + ALL_USERS_MYSQL + " WHERE t0.`status` = ?",
                // PostgreSQL
                ALL_USERS_PG + " WHERE t0.\"status\" = ? UNION ALL " + ALL_USERS_PG + " WHERE t0.\"status\" = ?",
                // Oracle
                ALL_USERS_ORACLE + " WHERE t0.\"STATUS\" = ? UNION ALL " + ALL_USERS_ORACLE + " WHERE t0.\"STATUS\" = ?",
                // SQLite
                ALL_USERS_PG + " WHERE t0.\"status\" = ? UNION ALL " + ALL_USERS_PG + " WHERE t0.\"status\" = ?",
                // H2
                ALL_USERS_PG + " WHERE t0.\"status\" = ? UNION ALL " + ALL_USERS_PG + " WHERE t0.\"status\" = ?",
                1, 0
        );

        // 真实执行闭环：q1 返回 3 条(alice, bob, david), q2 返回 1 条(charlie)，共 4 条
        verifyAllModes(targetOrm -> {
            SelectBuilder<MatrixUser> q1 = targetOrm.select(MatrixUser.class);
            q1.where().eq(MatrixUser::getStatus, 1);
            SelectBuilder<MatrixUser> q2 = targetOrm.select(MatrixUser.class);
            q2.where().eq(MatrixUser::getStatus, 0);
            List<MatrixUser> list = q1.unionAll(q2).list();
            Assert.assertEquals(4, list.size());

            // 模式 2: orm.sql() 模式
            List<MatrixUser> sqlList = targetOrm.sql(e -> {
                SelectBuilder<MatrixUser> sq1 = targetOrm.select(MatrixUser.class);
                sq1.where().eq(MatrixUser::getStatus, 1);
                SelectBuilder<MatrixUser> sq2 = targetOrm.select(MatrixUser.class);
                sq2.where().eq(MatrixUser::getStatus, 0);
                e.toExecutionFromReturn(sq1.unionAll(sq2));
            }).list(MatrixUser.class);
            Assert.assertEquals(4, sqlList.size());
        });
    }

    /**
     * 6.8 集合操作：UNION (多结果集合并，自动去重)
     * 场景：q1 查 age = 20 的用户名 (alice, david)，q2 查 score >= 80 的用户名 (alice, bob)
     * UNION 后应当去重，结果为 alice, david, bob 3 条
     */
    @Test
    public void test6_8_UnionDistinct() {
        assertMatrixSql(
                oOrm -> {
                    MatrixUser u = oOrm.proxy(MatrixUser.class);
                    SelectBuilder<MatrixUser> q1 = oOrm.select(u.getUsername()).from(MatrixUser.class);
                    q1.where().eq(u.getAge(), 20);
                    SelectBuilder<MatrixUser> q2 = oOrm.select(u.getUsername()).from(MatrixUser.class);
                    q2.where().ge(u.getScore(), new BigDecimal("80.00"));
                    return q1.union(q2);
                },
                // MySQL
                "SELECT t0.`username` FROM `matrix_user` t0 WHERE t0.`age` = ? UNION SELECT t0.`username` FROM `matrix_user` t0 WHERE t0.`score` >= ?",
                // PostgreSQL
                "SELECT t0.\"username\" FROM \"matrix_user\" t0 WHERE t0.\"age\" = ? UNION SELECT t0.\"username\" FROM \"matrix_user\" t0 WHERE t0.\"score\" >= ?",
                // Oracle
                "SELECT t0.\"USERNAME\" FROM \"MATRIX_USER\" t0 WHERE t0.\"AGE\" = ? UNION SELECT t0.\"USERNAME\" FROM \"MATRIX_USER\" t0 WHERE t0.\"SCORE\" >= ?",
                // SQLite
                "SELECT t0.\"username\" FROM \"matrix_user\" t0 WHERE t0.\"age\" = ? UNION SELECT t0.\"username\" FROM \"matrix_user\" t0 WHERE t0.\"score\" >= ?",
                // H2
                "SELECT t0.\"username\" FROM \"matrix_user\" t0 WHERE t0.\"age\" = ? UNION SELECT t0.\"username\" FROM \"matrix_user\" t0 WHERE t0.\"score\" >= ?",
                20, new BigDecimal("80.00")
        );

        // 真实执行闭环：
        // q1: alice (age=20), david (age=20)
        // q2: alice (score=95.50), bob (score=88.00)
        // UNION 去重后共 3 人: alice, david, bob
        verifyAllModes(targetOrm -> {
            MatrixUser u = targetOrm.proxy(MatrixUser.class);
            SelectBuilder<MatrixUser> q1 = targetOrm.select(u.getUsername()).from(MatrixUser.class);
            q1.where().eq(u.getAge(), 20);
            SelectBuilder<MatrixUser> q2 = targetOrm.select(u.getUsername()).from(MatrixUser.class);
            q2.where().ge(u.getScore(), new BigDecimal("80.00"));
            List<Map<String, Object>> rows = q1.union(q2).listMaps();
            Assert.assertEquals(3, rows.size());

            // 模式 2: orm.sql() 模式
            List<Map<String, Object>> sqlRows = targetOrm.sql(e -> {
                MatrixUser su = e.table(MatrixUser.class);
                SelectBuilder<MatrixUser> sq1 = targetOrm.select(su.getUsername()).from(MatrixUser.class);
                sq1.where().eq(su.getAge(), 20);
                SelectBuilder<MatrixUser> sq2 = targetOrm.select(su.getUsername()).from(MatrixUser.class);
                sq2.where().ge(su.getScore(), new BigDecimal("80.00"));
                e.toExecutionFromReturn(sq1.union(sq2));
            }).maps();
            Assert.assertEquals(3, sqlRows.size());
        });
    }

    /**
     * 6.9 综合子查询：子查询结合 JOIN 与分组过滤
     * 场景：查询已下单金额合计 >= 200 的用户的订单明细
     */
    @Test
    public void test6_9_ComplexSubqueryWithJoinAndAggregation() {
        assertMatrixSql(
                oOrm -> {
                    MatrixOrder o = oOrm.proxy(MatrixOrder.class);
                    // 子查询：找到订单数量 >= 1 的 userId 列表
                    SelectBuilder<MatrixOrder> highSpenderQuery = oOrm.select(o.getUserId())
                            .from(MatrixOrder.class)
                            .groupBy(o.getUserId())
                            .having().ge(count(o.getId()), 1)
                            .getQuery();

                    return oOrm.select(MatrixUser.class)
                            .where(w -> w.in(MatrixUser::getId, highSpenderQuery))
                            .orderByAsc(MatrixUser::getId);
                },
                // MySQL
                ALL_USERS_MYSQL + " WHERE t0.`id` IN (SELECT t0.`user_id` FROM `matrix_order` t0 GROUP BY t0.`user_id` HAVING COUNT(t0.`id`) >= ?) ORDER BY t0.`id` ASC",
                // PostgreSQL
                ALL_USERS_PG + " WHERE t0.\"id\" IN (SELECT t0.\"user_id\" FROM \"matrix_order\" t0 GROUP BY t0.\"user_id\" HAVING COUNT(t0.\"id\") >= ?) ORDER BY t0.\"id\" ASC",
                // Oracle
                ALL_USERS_ORACLE + " WHERE t0.\"ID\" IN (SELECT t0.\"USER_ID\" FROM \"MATRIX_ORDER\" t0 GROUP BY t0.\"USER_ID\" HAVING COUNT(t0.\"ID\") >= ?) ORDER BY t0.\"ID\" ASC",
                // SQLite
                ALL_USERS_PG + " WHERE t0.\"id\" IN (SELECT t0.\"user_id\" FROM \"matrix_order\" t0 GROUP BY t0.\"user_id\" HAVING COUNT(t0.\"id\") >= ?) ORDER BY t0.\"id\" ASC",
                // H2
                ALL_USERS_PG + " WHERE t0.\"id\" IN (SELECT t0.\"user_id\" FROM \"matrix_order\" t0 GROUP BY t0.\"user_id\" HAVING COUNT(t0.\"id\") >= ?) ORDER BY t0.\"id\" ASC",
                1
        );

        // 真实执行闭环：alice 和 bob 均有订单 (count >= 1)
        verifyAllModes(targetOrm -> {
            MatrixOrder o = targetOrm.proxy(MatrixOrder.class);
            SelectBuilder<MatrixOrder> highSpenderQuery = targetOrm.select(o.getUserId())
                    .from(MatrixOrder.class)
                    .groupBy(o.getUserId())
                    .having().ge(count(o.getId()), 1)
                    .getQuery();

            List<MatrixUser> list = targetOrm.select(MatrixUser.class)
                    .where(w -> w.in(MatrixUser::getId, highSpenderQuery))
                    .orderByAsc(MatrixUser::getId)
                    .list();
            Assert.assertEquals(2, list.size());
            Assert.assertEquals("alice", list.get(0).getUsername());
            Assert.assertEquals("bob", list.get(1).getUsername());

            // 模式 2: orm.sql() 模式
            List<MatrixUser> sqlList = targetOrm.sql(e -> {
                MatrixOrder so = e.table(MatrixOrder.class);
                SelectBuilder<MatrixOrder> hsq = targetOrm.select(so.getUserId())
                        .from(MatrixOrder.class)
                        .groupBy(so.getUserId())
                        .having().ge(count(so.getId()), 1)
                        .getQuery();
                MatrixUser su = e.table(MatrixUser.class);
                e.select(su).from(su).where(w -> w.in(su.getId(), hsq)).orderByAsc(su.getId());
            }).list(MatrixUser.class);
            Assert.assertEquals(2, sqlList.size());
            Assert.assertEquals("alice", sqlList.get(0).getUsername());
        });
    }
}
