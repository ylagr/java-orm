package com.javaorm.core.sqlmatrix;

import org.junit.Assert;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.javaorm.core.SQL.*;

/**
 * Phase 4: GROUP BY, HAVING 与聚合分析全方言矩阵单测。
 *
 * 覆盖矩阵：
 * 4.1 单列与多列 GROUP BY
 * 4.2 单条件与复合多条件 HAVING 过滤
 * 4.3 WHERE -> GROUP BY -> HAVING 全流程链路组合查询
 * 4.4 分组聚合后对聚合函数表达式 ORDER BY 排序
 * 4.5 多表关联 JOIN + GROUP BY + HAVING 综合分析
 *
 * 严格遵守：
 * 1. 100% CodeFirst 建表（基类中已同步完成，严禁手写 DDL）；
 * 2. 100% 全文本精确断言 5 大方言（MySQL, PostgreSQL, Oracle, SQLite, H2）；
 * 3. 双重校验闭环：不仅断言 SQL 语法及参数，同时执行真实 JDBC 查询验证实体数据与条数。
 */
public class Phase4GroupByHavingMatrixTest extends AbstractSqlMatrixTest {

    public static class StatusSummaryDto {
        private Integer status;
        private Long userCount;

        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
        public Long getUserCount() { return userCount; }
        public void setUserCount(Long userCount) { this.userCount = userCount; }
    }

    /**
     * 4.1.1 单列 GROUP BY 分组聚合（计算每组用户数与平均年龄）
     */
    @Test
    public void test4_1_1_SingleColumnGroupBy() {
        MatrixUser u = $(MatrixUser.class);

        assertMatrixSql(
                oOrm -> oOrm.select(u.getStatus(), count(u.getId()).as("user_count"), avg(u.getAge()).as("avg_age"))
                        .from(MatrixUser.class)
                        .groupBy(u.getStatus())
                        .orderByAsc(u.getStatus()),
                // MySQL
                "SELECT t0.`status`, COUNT(t0.`id`) AS `user_count`, AVG(t0.`age`) AS `avg_age` " +
                        "FROM `matrix_user` t0 " +
                        "GROUP BY t0.`status` " +
                        "ORDER BY t0.`status` ASC",
                // PostgreSQL
                "SELECT t0.\"status\", COUNT(t0.\"id\") AS \"user_count\", AVG(t0.\"age\") AS \"avg_age\" " +
                        "FROM \"matrix_user\" t0 " +
                        "GROUP BY t0.\"status\" " +
                        "ORDER BY t0.\"status\" ASC",
                // Oracle
                "SELECT t0.\"STATUS\", COUNT(t0.\"ID\") AS \"user_count\", AVG(t0.\"AGE\") AS \"avg_age\" " +
                        "FROM \"MATRIX_USER\" t0 " +
                        "GROUP BY t0.\"STATUS\" " +
                        "ORDER BY t0.\"STATUS\" ASC",
                // SQLite
                "SELECT t0.\"status\", COUNT(t0.\"id\") AS \"user_count\", AVG(t0.\"age\") AS \"avg_age\" " +
                        "FROM \"matrix_user\" t0 " +
                        "GROUP BY t0.\"status\" " +
                        "ORDER BY t0.\"status\" ASC",
                // H2
                "SELECT t0.\"status\", COUNT(t0.\"id\") AS \"user_count\", AVG(t0.\"age\") AS \"avg_age\" " +
                        "FROM \"matrix_user\" t0 " +
                        "GROUP BY t0.\"status\" " +
                        "ORDER BY t0.\"status\" ASC"
        );

        // 数据闭环校验：status=0 (charlie, 30岁, 1人); status=1 (alice 20, bob 25, david 20, 共3人, 平均21.6667)
        verifyAllModes(oOrm -> {
            List<Map<String, Object>> rows = oOrm.select(u.getStatus(), count(u.getId()).as("user_count"), avg(u.getAge()).as("avg_age"))
                    .from(MatrixUser.class)
                    .groupBy(u.getStatus())
                    .orderByAsc(u.getStatus())
                    .maps();

            Assert.assertEquals(2, rows.size());
            // 组 1: status = 0
            Assert.assertEquals(0, ((Number) rows.get(0).get("status")).intValue());
            Assert.assertEquals(1L, ((Number) rows.get(0).get("user_count")).longValue());
            Assert.assertEquals(30.0, ((Number) rows.get(0).get("avg_age")).doubleValue(), 0.01);

            // 组 2: status = 1
            Assert.assertEquals(1, ((Number) rows.get(1).get("status")).intValue());
            Assert.assertEquals(3L, ((Number) rows.get(1).get("user_count")).longValue());
            Assert.assertEquals(21.67, ((Number) rows.get(1).get("avg_age")).doubleValue(), 0.05);

            // 模式 2: orm.sql() 模式
            List<Map<String, Object>> sqlRows = oOrm.sql(e -> {
                MatrixUser su = e.table(MatrixUser.class);
                e.select(su.getStatus(), count(su.getId()).as("user_count"), avg(su.getAge()).as("avg_age"))
                        .from(su)
                        .groupBy(su.getStatus())
                        .orderByAsc(su.getStatus());
            }).maps();
            Assert.assertEquals(2, sqlRows.size());
            Assert.assertEquals(0, ((Number) sqlRows.get(0).get("status")).intValue());
            Assert.assertEquals(1L, ((Number) sqlRows.get(0).get("user_count")).longValue());
        });
    }

    /**
     * 4.1.2 多列组合 GROUP BY 分组聚合 (age, status)
     */
    @Test
    public void test4_1_2_MultiColumnGroupBy() {
        MatrixUser u = $(MatrixUser.class);

        assertMatrixSql(
                oOrm -> oOrm.select(u.getAge(), u.getStatus(), count(u.getId()).as("cnt"))
                        .from(MatrixUser.class)
                        .groupBy(u.getAge(), u.getStatus())
                        .orderByAsc(u.getAge())
                        .orderByAsc(u.getStatus()),
                // MySQL
                "SELECT t0.`age`, t0.`status`, COUNT(t0.`id`) AS `cnt` " +
                        "FROM `matrix_user` t0 " +
                        "GROUP BY t0.`age`, t0.`status` " +
                        "ORDER BY t0.`age` ASC, t0.`status` ASC",
                // PostgreSQL
                "SELECT t0.\"age\", t0.\"status\", COUNT(t0.\"id\") AS \"cnt\" " +
                        "FROM \"matrix_user\" t0 " +
                        "GROUP BY t0.\"age\", t0.\"status\" " +
                        "ORDER BY t0.\"age\" ASC, t0.\"status\" ASC",
                // Oracle
                "SELECT t0.\"AGE\", t0.\"STATUS\", COUNT(t0.\"ID\") AS \"cnt\" " +
                        "FROM \"MATRIX_USER\" t0 " +
                        "GROUP BY t0.\"AGE\", t0.\"STATUS\" " +
                        "ORDER BY t0.\"AGE\" ASC, t0.\"STATUS\" ASC",
                // SQLite
                "SELECT t0.\"age\", t0.\"status\", COUNT(t0.\"id\") AS \"cnt\" " +
                        "FROM \"matrix_user\" t0 " +
                        "GROUP BY t0.\"age\", t0.\"status\" " +
                        "ORDER BY t0.\"age\" ASC, t0.\"status\" ASC",
                // H2
                "SELECT t0.\"age\", t0.\"status\", COUNT(t0.\"id\") AS \"cnt\" " +
                        "FROM \"matrix_user\" t0 " +
                        "GROUP BY t0.\"age\", t0.\"status\" " +
                        "ORDER BY t0.\"age\" ASC, t0.\"status\" ASC"
        );

        // 数据闭环校验
        verifyAllModes(oOrm -> {
            List<Map<String, Object>> rows = oOrm.select(u.getAge(), u.getStatus(), count(u.getId()).as("cnt"))
                    .from(MatrixUser.class)
                    .groupBy(u.getAge(), u.getStatus())
                    .orderByAsc(u.getAge())
                    .orderByAsc(u.getStatus())
                    .maps();

            Assert.assertEquals(3, rows.size());
            Assert.assertEquals(20, ((Number) rows.get(0).get("age")).intValue());
            Assert.assertEquals(1, ((Number) rows.get(0).get("status")).intValue());
            Assert.assertEquals(2L, ((Number) rows.get(0).get("cnt")).longValue());

            Assert.assertEquals(25, ((Number) rows.get(1).get("age")).intValue());
            Assert.assertEquals(1, ((Number) rows.get(1).get("status")).intValue());
            Assert.assertEquals(1L, ((Number) rows.get(1).get("cnt")).longValue());

            Assert.assertEquals(30, ((Number) rows.get(2).get("age")).intValue());
            Assert.assertEquals(0, ((Number) rows.get(2).get("status")).intValue());
            Assert.assertEquals(1L, ((Number) rows.get(2).get("cnt")).longValue());

            // 模式 2: orm.sql() 模式
            List<Map<String, Object>> sqlRows = oOrm.sql(e -> {
                MatrixUser su = e.table(MatrixUser.class);
                e.select(su.getAge(), su.getStatus(), count(su.getId()).as("cnt"))
                        .from(su)
                        .groupBy(su.getAge(), su.getStatus())
                        .orderByAsc(su.getAge())
                        .orderByAsc(su.getStatus());
            }).maps();
            Assert.assertEquals(3, sqlRows.size());
        });
    }

    /**
     * 4.2.1 单条件 HAVING 过滤（过滤出组内人数 > 1 的 status 分组）
     */
    @Test
    public void test4_2_1_SingleHavingCondition() {
        MatrixUser u = $(MatrixUser.class);

        assertMatrixSql(
                oOrm -> oOrm.select(u.getStatus(), count(u.getId()).as("user_count"))
                        .from(MatrixUser.class)
                        .groupBy(u.getStatus())
                        .having(h -> h.gt(count(u.getId()), 1))
                        .orderByAsc(u.getStatus()),
                // MySQL
                "SELECT t0.`status`, COUNT(t0.`id`) AS `user_count` " +
                        "FROM `matrix_user` t0 " +
                        "GROUP BY t0.`status` " +
                        "HAVING COUNT(t0.`id`) > ? " +
                        "ORDER BY t0.`status` ASC",
                // PostgreSQL
                "SELECT t0.\"status\", COUNT(t0.\"id\") AS \"user_count\" " +
                        "FROM \"matrix_user\" t0 " +
                        "GROUP BY t0.\"status\" " +
                        "HAVING COUNT(t0.\"id\") > ? " +
                        "ORDER BY t0.\"status\" ASC",
                // Oracle
                "SELECT t0.\"STATUS\", COUNT(t0.\"ID\") AS \"user_count\" " +
                        "FROM \"MATRIX_USER\" t0 " +
                        "GROUP BY t0.\"STATUS\" " +
                        "HAVING COUNT(t0.\"ID\") > ? " +
                        "ORDER BY t0.\"STATUS\" ASC",
                // SQLite
                "SELECT t0.\"status\", COUNT(t0.\"id\") AS \"user_count\" " +
                        "FROM \"matrix_user\" t0 " +
                        "GROUP BY t0.\"status\" " +
                        "HAVING COUNT(t0.\"id\") > ? " +
                        "ORDER BY t0.\"status\" ASC",
                // H2
                "SELECT t0.\"status\", COUNT(t0.\"id\") AS \"user_count\" " +
                        "FROM \"matrix_user\" t0 " +
                        "GROUP BY t0.\"status\" " +
                        "HAVING COUNT(t0.\"id\") > ? " +
                        "ORDER BY t0.\"status\" ASC",
                1
        );

        // 数据闭环校验：仅 status=1 保留 (count=3 > 1)
        verifyAllModes(oOrm -> {
            List<Map<String, Object>> rows = oOrm.select(u.getStatus(), count(u.getId()).as("user_count"))
                    .from(MatrixUser.class)
                    .groupBy(u.getStatus())
                    .having(h -> h.gt(count(u.getId()), 1))
                    .orderByAsc(u.getStatus())
                    .maps();

            Assert.assertEquals(1, rows.size());
            Assert.assertEquals(1, ((Number) rows.get(0).get("status")).intValue());
            Assert.assertEquals(3L, ((Number) rows.get(0).get("user_count")).longValue());

            // 模式 2: orm.sql() 模式
            List<Map<String, Object>> sqlRows = oOrm.sql(e -> {
                MatrixUser su = e.table(MatrixUser.class);
                e.select(su.getStatus(), count(su.getId()).as("user_count"))
                        .from(su)
                        .groupBy(su.getStatus())
                        .having(h -> h.gt(count(su.getId()), 1))
                        .orderByAsc(su.getStatus());
            }).maps();
            Assert.assertEquals(1, sqlRows.size());
            Assert.assertEquals(1, ((Number) sqlRows.get(0).get("status")).intValue());
            Assert.assertEquals(3L, ((Number) sqlRows.get(0).get("user_count")).longValue());
        });
    }

    /**
     * 4.2.2 复合多条件 HAVING 过滤 (count(*) >= 2 AND sum(amount) > 200.00)
     */
    @Test
    public void test4_2_2_MultiHavingConditions() {
        MatrixOrder o = $(MatrixOrder.class);

        assertMatrixSql(
                oOrm -> oOrm.select(o.getUserId(), count(o.getId()).as("order_count"), sum(o.getAmount()).as("total_amount"))
                        .from(MatrixOrder.class)
                        .groupBy(o.getUserId())
                        .having(h -> {
                            h.ge(count(o.getId()), 2);
                            h.gt(sum(o.getAmount()), new BigDecimal("200.00"));
                        })
                        .orderByAsc(o.getUserId()),
                // MySQL
                "SELECT t0.`user_id`, COUNT(t0.`id`) AS `order_count`, SUM(t0.`amount`) AS `total_amount` " +
                        "FROM `matrix_order` t0 " +
                        "GROUP BY t0.`user_id` " +
                        "HAVING COUNT(t0.`id`) >= ? AND SUM(t0.`amount`) > ? " +
                        "ORDER BY t0.`user_id` ASC",
                // PostgreSQL
                "SELECT t0.\"user_id\", COUNT(t0.\"id\") AS \"order_count\", SUM(t0.\"amount\") AS \"total_amount\" " +
                        "FROM \"matrix_order\" t0 " +
                        "GROUP BY t0.\"user_id\" " +
                        "HAVING COUNT(t0.\"id\") >= ? AND SUM(t0.\"amount\") > ? " +
                        "ORDER BY t0.\"user_id\" ASC",
                // Oracle
                "SELECT t0.\"USER_ID\", COUNT(t0.\"ID\") AS \"order_count\", SUM(t0.\"AMOUNT\") AS \"total_amount\" " +
                        "FROM \"MATRIX_ORDER\" t0 " +
                        "GROUP BY t0.\"USER_ID\" " +
                        "HAVING COUNT(t0.\"ID\") >= ? AND SUM(t0.\"AMOUNT\") > ? " +
                        "ORDER BY t0.\"USER_ID\" ASC",
                // SQLite
                "SELECT t0.\"user_id\", COUNT(t0.\"id\") AS \"order_count\", SUM(t0.\"amount\") AS \"total_amount\" " +
                        "FROM \"matrix_order\" t0 " +
                        "GROUP BY t0.\"user_id\" " +
                        "HAVING COUNT(t0.\"id\") >= ? AND SUM(t0.\"amount\") > ? " +
                        "ORDER BY t0.\"user_id\" ASC",
                // H2
                "SELECT t0.\"user_id\", COUNT(t0.\"id\") AS \"order_count\", SUM(t0.\"amount\") AS \"total_amount\" " +
                        "FROM \"matrix_order\" t0 " +
                        "GROUP BY t0.\"user_id\" " +
                        "HAVING COUNT(t0.\"id\") >= ? AND SUM(t0.\"amount\") > ? " +
                        "ORDER BY t0.\"user_id\" ASC",
                2, new BigDecimal("200.00")
        );

        // 数据闭环校验
        verifyAllModes(oOrm -> {
            List<Map<String, Object>> rows = oOrm.select(o.getUserId(), count(o.getId()).as("order_count"), sum(o.getAmount()).as("total_amount"))
                    .from(MatrixOrder.class)
                    .groupBy(o.getUserId())
                    .having(h -> {
                        h.ge(count(o.getId()), 2);
                        h.gt(sum(o.getAmount()), new BigDecimal("200.00"));
                    })
                    .orderByAsc(o.getUserId())
                    .maps();

            Assert.assertEquals(1, rows.size());
            Assert.assertEquals(1L, ((Number) rows.get(0).get("user_id")).longValue());
            Assert.assertEquals(2L, ((Number) rows.get(0).get("order_count")).longValue());
            Assert.assertEquals(new BigDecimal("498.90"), new BigDecimal(rows.get(0).get("total_amount").toString()));

            // 模式 2: orm.sql() 模式
            List<Map<String, Object>> sqlRows = oOrm.sql(e -> {
                MatrixOrder so = e.table(MatrixOrder.class);
                e.select(so.getUserId(), count(so.getId()).as("order_count"), sum(so.getAmount()).as("total_amount"))
                        .from(so)
                        .groupBy(so.getUserId())
                        .having(h -> {
                            h.ge(count(so.getId()), 2);
                            h.gt(sum(so.getAmount()), new BigDecimal("200.00"));
                        })
                        .orderByAsc(so.getUserId());
            }).maps();
            Assert.assertEquals(1, sqlRows.size());
            Assert.assertEquals(1L, ((Number) sqlRows.get(0).get("user_id")).longValue());
        });
    }

    /**
     * 4.3 综合查询全链路 (WHERE 过滤 -> GROUP BY 分组 -> HAVING 筛选)
     */
    @Test
    public void test4_3_WhereGroupByHavingPipeline() {
        MatrixUser u = $(MatrixUser.class);

        assertMatrixSql(
                oOrm -> oOrm.select(u.getAge(), count(u.getId()).as("cnt"), avg(u.getScore()).as("avg_score"))
                        .from(MatrixUser.class)
                        .where().ge(u.getScore(), new BigDecimal("70.00"))
                        .groupBy(u.getAge())
                        .having(h -> h.ge(count(u.getId()), 1))
                        .orderByAsc(u.getAge()),
                // MySQL
                "SELECT t0.`age`, COUNT(t0.`id`) AS `cnt`, AVG(t0.`score`) AS `avg_score` " +
                        "FROM `matrix_user` t0 " +
                        "WHERE t0.`score` >= ? " +
                        "GROUP BY t0.`age` " +
                        "HAVING COUNT(t0.`id`) >= ? " +
                        "ORDER BY t0.`age` ASC",
                // PostgreSQL
                "SELECT t0.\"age\", COUNT(t0.\"id\") AS \"cnt\", AVG(t0.\"score\") AS \"avg_score\" " +
                        "FROM \"matrix_user\" t0 " +
                        "WHERE t0.\"score\" >= ? " +
                        "GROUP BY t0.\"age\" " +
                        "HAVING COUNT(t0.\"id\") >= ? " +
                        "ORDER BY t0.\"age\" ASC",
                // Oracle
                "SELECT t0.\"AGE\", COUNT(t0.\"ID\") AS \"cnt\", AVG(t0.\"SCORE\") AS \"avg_score\" " +
                        "FROM \"MATRIX_USER\" t0 " +
                        "WHERE t0.\"SCORE\" >= ? " +
                        "GROUP BY t0.\"AGE\" " +
                        "HAVING COUNT(t0.\"ID\") >= ? " +
                        "ORDER BY t0.\"AGE\" ASC",
                // SQLite
                "SELECT t0.\"age\", COUNT(t0.\"id\") AS \"cnt\", AVG(t0.\"score\") AS \"avg_score\" " +
                        "FROM \"matrix_user\" t0 " +
                        "WHERE t0.\"score\" >= ? " +
                        "GROUP BY t0.\"age\" " +
                        "HAVING COUNT(t0.\"id\") >= ? " +
                        "ORDER BY t0.\"age\" ASC",
                // H2
                "SELECT t0.\"age\", COUNT(t0.\"id\") AS \"cnt\", AVG(t0.\"score\") AS \"avg_score\" " +
                        "FROM \"matrix_user\" t0 " +
                        "WHERE t0.\"score\" >= ? " +
                        "GROUP BY t0.\"age\" " +
                        "HAVING COUNT(t0.\"id\") >= ? " +
                        "ORDER BY t0.\"age\" ASC",
                new BigDecimal("70.00"), 1
        );

        // 数据闭环校验
        verifyAllModes(oOrm -> {
            List<Map<String, Object>> rows = oOrm.select(u.getAge(), count(u.getId()).as("cnt"), avg(u.getScore()).as("avg_score"))
                    .from(MatrixUser.class)
                    .where().ge(u.getScore(), new BigDecimal("70.00"))
                    .groupBy(u.getAge())
                    .having(h -> h.ge(count(u.getId()), 1))
                    .orderByAsc(u.getAge())
                    .maps();

            Assert.assertEquals(3, rows.size());
            Assert.assertEquals(20, ((Number) rows.get(0).get("age")).intValue());
            Assert.assertEquals(1L, ((Number) rows.get(0).get("cnt")).longValue());
            Assert.assertEquals(95.50, ((Number) rows.get(0).get("avg_score")).doubleValue(), 0.01);

            Assert.assertEquals(25, ((Number) rows.get(1).get("age")).intValue());
            Assert.assertEquals(1L, ((Number) rows.get(1).get("cnt")).longValue());
            Assert.assertEquals(88.00, ((Number) rows.get(1).get("avg_score")).doubleValue(), 0.01);

            Assert.assertEquals(30, ((Number) rows.get(2).get("age")).intValue());
            Assert.assertEquals(1L, ((Number) rows.get(2).get("cnt")).longValue());
            Assert.assertEquals(72.50, ((Number) rows.get(2).get("avg_score")).doubleValue(), 0.01);

            // 模式 2: orm.sql() 模式
            List<Map<String, Object>> sqlRows = oOrm.sql(e -> {
                MatrixUser su = e.table(MatrixUser.class);
                e.select(su.getAge(), count(su.getId()).as("cnt"), avg(su.getScore()).as("avg_score"))
                        .from(su)
                        .where().ge(su.getScore(), new BigDecimal("70.00"))
                        .groupBy(su.getAge())
                        .having(h -> h.ge(count(su.getId()), 1))
                        .orderByAsc(su.getAge());
            }).maps();
            Assert.assertEquals(3, sqlRows.size());
        });
    }

    /**
     * 4.4 分组后按聚合表达式 ORDER BY 降序排序
     */
    @Test
    public void test4_4_GroupByOrderByAggregateExpression() {
        MatrixUser u = $(MatrixUser.class);

        assertMatrixSql(
                oOrm -> oOrm.select(u.getStatus(), count(u.getId()).as("cnt"))
                        .from(MatrixUser.class)
                        .groupBy(u.getStatus())
                        .orderByDesc(count(u.getId())),
                // MySQL
                "SELECT t0.`status`, COUNT(t0.`id`) AS `cnt` " +
                        "FROM `matrix_user` t0 " +
                        "GROUP BY t0.`status` " +
                        "ORDER BY COUNT(t0.`id`) DESC",
                // PostgreSQL
                "SELECT t0.\"status\", COUNT(t0.\"id\") AS \"cnt\" " +
                        "FROM \"matrix_user\" t0 " +
                        "GROUP BY t0.\"status\" " +
                        "ORDER BY COUNT(t0.\"id\") DESC",
                // Oracle
                "SELECT t0.\"STATUS\", COUNT(t0.\"ID\") AS \"cnt\" " +
                        "FROM \"MATRIX_USER\" t0 " +
                        "GROUP BY t0.\"STATUS\" " +
                        "ORDER BY COUNT(t0.\"ID\") DESC",
                // SQLite
                "SELECT t0.\"status\", COUNT(t0.\"id\") AS \"cnt\" " +
                        "FROM \"matrix_user\" t0 " +
                        "GROUP BY t0.\"status\" " +
                        "ORDER BY COUNT(t0.\"id\") DESC",
                // H2
                "SELECT t0.\"status\", COUNT(t0.\"id\") AS \"cnt\" " +
                        "FROM \"matrix_user\" t0 " +
                        "GROUP BY t0.\"status\" " +
                        "ORDER BY COUNT(t0.\"id\") DESC"
        );

        // 数据闭环校验
        verifyAllModes(oOrm -> {
            List<Map<String, Object>> rows = oOrm.select(u.getStatus(), count(u.getId()).as("cnt"))
                    .from(MatrixUser.class)
                    .groupBy(u.getStatus())
                    .orderByDesc(count(u.getId()))
                    .maps();

            Assert.assertEquals(2, rows.size());
            Assert.assertEquals(1, ((Number) rows.get(0).get("status")).intValue());
            Assert.assertEquals(3L, ((Number) rows.get(0).get("cnt")).longValue());

            Assert.assertEquals(0, ((Number) rows.get(1).get("status")).intValue());
            Assert.assertEquals(1L, ((Number) rows.get(1).get("cnt")).longValue());

            // 模式 2: orm.sql() 模式
            List<Map<String, Object>> sqlRows = oOrm.sql(e -> {
                MatrixUser su = e.table(MatrixUser.class);
                e.select(su.getStatus(), count(su.getId()).as("cnt"))
                        .from(su)
                        .groupBy(su.getStatus())
                        .orderByDesc(count(su.getId()));
            }).maps();
            Assert.assertEquals(2, sqlRows.size());
            Assert.assertEquals(1, ((Number) sqlRows.get(0).get("status")).intValue());
        });
    }

    /**
     * 4.5 多表关联分组聚合 (JOIN + GROUP BY + HAVING)
     */
    @Test
    public void test4_5_JoinWithGroupByAndHaving() {
        assertMatrixSql(
                oOrm -> oOrm.sql(e -> {
                    MatrixUser u = e.table(MatrixUser.class, "u");
                    MatrixOrder o = e.table(MatrixOrder.class, "o");
                    e.select(u.getUsername(), count(o.getId()).as("order_cnt"), sum(o.getAmount()).as("total_spend"))
                            .from(u)
                            .innerJoin(o).on(o.getUserId(), u.getId())
                            .groupBy(u.getUsername())
                            .having(h -> h.ge(count(o.getId()), 2))
                            .orderByAsc(u.getUsername());
                }),
                // MySQL
                "SELECT `u`.`username`, COUNT(`o`.`id`) AS `order_cnt`, SUM(`o`.`amount`) AS `total_spend` " +
                        "FROM `matrix_user` `u` " +
                        "INNER JOIN `matrix_order` `o` ON `o`.`user_id` = `u`.`id` " +
                        "GROUP BY `u`.`username` " +
                        "HAVING COUNT(`o`.`id`) >= ? " +
                        "ORDER BY `u`.`username` ASC",
                // PostgreSQL
                "SELECT \"u\".\"username\", COUNT(\"o\".\"id\") AS \"order_cnt\", SUM(\"o\".\"amount\") AS \"total_spend\" " +
                        "FROM \"matrix_user\" \"u\" " +
                        "INNER JOIN \"matrix_order\" \"o\" ON \"o\".\"user_id\" = \"u\".\"id\" " +
                        "GROUP BY \"u\".\"username\" " +
                        "HAVING COUNT(\"o\".\"id\") >= ? " +
                        "ORDER BY \"u\".\"username\" ASC",
                // Oracle
                "SELECT \"u\".\"USERNAME\", COUNT(\"o\".\"ID\") AS \"order_cnt\", SUM(\"o\".\"AMOUNT\") AS \"total_spend\" " +
                        "FROM \"MATRIX_USER\" \"u\" " +
                        "INNER JOIN \"MATRIX_ORDER\" \"o\" ON \"o\".\"USER_ID\" = \"u\".\"ID\" " +
                        "GROUP BY \"u\".\"USERNAME\" " +
                        "HAVING COUNT(\"o\".\"ID\") >= ? " +
                        "ORDER BY \"u\".\"USERNAME\" ASC",
                // SQLite
                "SELECT \"u\".\"username\", COUNT(\"o\".\"id\") AS \"order_cnt\", SUM(\"o\".\"amount\") AS \"total_spend\" " +
                        "FROM \"matrix_user\" \"u\" " +
                        "INNER JOIN \"matrix_order\" \"o\" ON \"o\".\"user_id\" = \"u\".\"id\" " +
                        "GROUP BY \"u\".\"username\" " +
                        "HAVING COUNT(\"o\".\"id\") >= ? " +
                        "ORDER BY \"u\".\"username\" ASC",
                // H2
                "SELECT \"u\".\"username\", COUNT(\"o\".\"id\") AS \"order_cnt\", SUM(\"o\".\"amount\") AS \"total_spend\" " +
                        "FROM \"matrix_user\" \"u\" " +
                        "INNER JOIN \"matrix_order\" \"o\" ON \"o\".\"user_id\" = \"u\".\"id\" " +
                        "GROUP BY \"u\".\"username\" " +
                        "HAVING COUNT(\"o\".\"id\") >= ? " +
                        "ORDER BY \"u\".\"username\" ASC",
                2
        );

        // 数据闭环校验：alice (2单，总金额 498.90) >= 2 满足条件；bob (1单) 被 HAVING 剔除
        verifyAllModes(oOrm -> {
            List<Map<String, Object>> rows = oOrm.sql(e -> {
                MatrixUser u = e.table(MatrixUser.class, "u");
                MatrixOrder o = e.table(MatrixOrder.class, "o");
                e.select(u.getUsername(), count(o.getId()).as("order_cnt"), sum(o.getAmount()).as("total_spend"))
                        .from(u)
                        .innerJoin(o).on(o.getUserId(), u.getId())
                        .groupBy(u.getUsername())
                        .having(h -> h.ge(count(o.getId()), 2))
                        .orderByAsc(u.getUsername());
            }).listMaps();

            Assert.assertEquals(1, rows.size());
            Assert.assertEquals("alice", rows.get(0).get("username"));
            Assert.assertEquals(2L, ((Number) rows.get(0).get("order_cnt")).longValue());
            Assert.assertEquals(new BigDecimal("498.90"), new BigDecimal(rows.get(0).get("total_spend").toString()));
        });
    }
}
