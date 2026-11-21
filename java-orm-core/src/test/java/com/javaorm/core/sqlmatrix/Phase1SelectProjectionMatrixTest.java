package com.javaorm.core.sqlmatrix;

import org.junit.Assert;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.javaorm.core.SQL.*;

/**
 * Phase 1: SELECT 投影与基础查询全方言测试矩阵
 * 1. 严格全文本精确断言 5 大数据库方言 (MySQL, PostgreSQL, Oracle, SQLite, H2) 生成的 SQL 字符串与参数；
 * 2. 真实执行 JDBC 查询，严格校验查询返回的实体数据（字段值、条数、类型、聚合结果）。
 */
public class Phase1SelectProjectionMatrixTest extends AbstractSqlMatrixTest {

    /**
     * 1.1 全列实体投影：orm.select(MatrixUser.class)
     * 校验点：
     * 1. SQL 100% 展开所有实体字段（绝非通配符 *）；
     * 2. 真实返回实体列表，校验数量为 4，且每个实体的各物理字段（id, username, email, age, score 等）全部正确装配。
     */
    @Test
    public void testSelectAllEntityColumns() {
        assertMatrixSql(
                o -> o.select(MatrixUser.class),
                "SELECT t0.`id`, t0.`username`, t0.`email`, t0.`age`, t0.`status`, t0.`score`, t0.`create_time`, t0.`is_deleted` FROM `matrix_user` t0",
                "SELECT t0.\"id\", t0.\"username\", t0.\"email\", t0.\"age\", t0.\"status\", t0.\"score\", t0.\"create_time\", t0.\"is_deleted\" FROM \"matrix_user\" t0",
                "SELECT t0.\"ID\", t0.\"USERNAME\", t0.\"EMAIL\", t0.\"AGE\", t0.\"STATUS\", t0.\"SCORE\", t0.\"CREATE_TIME\", t0.\"IS_DELETED\" FROM \"MATRIX_USER\" t0",
                "SELECT t0.\"id\", t0.\"username\", t0.\"email\", t0.\"age\", t0.\"status\", t0.\"score\", t0.\"create_time\", t0.\"is_deleted\" FROM \"matrix_user\" t0",
                "SELECT t0.\"id\", t0.\"username\", t0.\"email\", t0.\"age\", t0.\"status\", t0.\"score\", t0.\"create_time\", t0.\"is_deleted\" FROM \"matrix_user\" t0"
        );

        // 真实在三大隔离环境 (MySQL, PostgreSQL, Oracle 严格模式) 执行并校验实体数据
        verifyAllModes(o -> {
            // 模式 1: 直接流式 select
            List<MatrixUser> users = o.select(MatrixUser.class).list();
            Assert.assertNotNull(users);
            Assert.assertEquals(4, users.size());

            MatrixUser alice = users.stream().filter(u -> "alice".equals(u.getUsername())).findFirst().orElse(null);
            Assert.assertNotNull(alice);
            Assert.assertNotNull(alice.getId());
            Assert.assertEquals("alice@example.com", alice.getEmail());
            Assert.assertEquals(Integer.valueOf(20), alice.getAge());
            Assert.assertEquals(Integer.valueOf(1), alice.getStatus());
            Assert.assertEquals(new BigDecimal("95.50"), alice.getScore());
            Assert.assertEquals(Integer.valueOf(0), alice.getIsDeleted());
            Assert.assertNotNull(alice.getCreateTime());

            MatrixUser bob = users.stream().filter(u -> "bob".equals(u.getUsername())).findFirst().orElse(null);
            Assert.assertNotNull(bob);
            Assert.assertEquals(Integer.valueOf(25), bob.getAge());
            Assert.assertEquals(new BigDecimal("88.00"), bob.getScore());

            // 模式 2: orm.sql() 模式
            List<MatrixUser> sqlUsers = o.sql(e -> {
                e.select(MatrixUser.class);
            }).toList();
            Assert.assertNotNull(sqlUsers);
            Assert.assertEquals(4, sqlUsers.size());
            MatrixUser sqlAlice = sqlUsers.stream().filter(u -> "alice".equals(u.getUsername())).findFirst().orElse(null);
            Assert.assertNotNull(sqlAlice);
            Assert.assertEquals("alice@example.com", sqlAlice.getEmail());
            Assert.assertEquals(new BigDecimal("95.50"), sqlAlice.getScore());
        });
    }

    /**
     * 1.2 显式指定单列/多列投影：select(u.getId(), u.getUsername(), u.getAge())
     * 校验点：
     * 1. SQL 仅投影显式指定的列；
     * 2. 真实返回实体，投影的字段有值，未投影的字段（email, score 等）必须为 null。
     */
    @Test
    public void testSelectSpecificColumns() {
        MatrixUser u = $(MatrixUser.class);
        assertMatrixSql(
                o -> o.select(MatrixUser.class).select(u.getId(), u.getUsername(), u.getAge()),
                "SELECT t0.`id`, t0.`username`, t0.`age` FROM `matrix_user` t0",
                "SELECT t0.\"id\", t0.\"username\", t0.\"age\" FROM \"matrix_user\" t0",
                "SELECT t0.\"ID\", t0.\"USERNAME\", t0.\"AGE\" FROM \"MATRIX_USER\" t0",
                "SELECT t0.\"id\", t0.\"username\", t0.\"age\" FROM \"matrix_user\" t0",
                "SELECT t0.\"id\", t0.\"username\", t0.\"age\" FROM \"matrix_user\" t0"
        );

        // 真实在三大隔离环境下执行并校验数据
        verifyAllModes(o -> {
            List<MatrixUser> users = o.select(MatrixUser.class).select(u.getId(), u.getUsername(), u.getAge()).list();
            Assert.assertEquals(4, users.size());

            for (MatrixUser user : users) {
                Assert.assertNotNull(user.getId());
                Assert.assertNotNull(user.getUsername());
                Assert.assertNotNull(user.getAge());
                // 未选取的字段应该未被赋值（保持默认 null）
                Assert.assertNull(user.getEmail());
                Assert.assertNull(user.getScore());
            }

            // 模式 2: orm.sql() 模式
            List<MatrixUser> sqlUsers = o.sql(e -> {
                MatrixUser su = e.table(MatrixUser.class);
                e.select(su.getId(), su.getUsername(), su.getAge()).from(su);
            }).list(MatrixUser.class);
            Assert.assertEquals(4, sqlUsers.size());
            for (MatrixUser user : sqlUsers) {
                Assert.assertNotNull(user.getId());
                Assert.assertNotNull(user.getUsername());
                Assert.assertNotNull(user.getAge());
                Assert.assertNull(user.getEmail());
                Assert.assertNull(user.getScore());
            }
        });
    }

    /**
     * 1.3 显式表别名与列投影：as("u") 与 col(u.getUsername()).as("user_name")
     * 校验点：
     * 1. SQL 表别名与列别名精确带 AS 渲染；
     * 2. 真实执行并通过 maps() 返回，校验 Map 中 Key 与重命名别名一致。
     */
    @Test
    public void testSelectWithTableAndColumnAlias() {
        MatrixUser u = $(MatrixUser.class);
        assertMatrixSql(
                o -> o.select(MatrixUser.class).as("u").select(u.getId(), col(u.getUsername()).as("user_name")),
                "SELECT `u`.`id`, `u`.`username` AS `user_name` FROM `matrix_user` `u`",
                "SELECT \"u\".\"id\", \"u\".\"username\" AS \"user_name\" FROM \"matrix_user\" \"u\"",
                "SELECT \"u\".\"ID\", \"u\".\"USERNAME\" AS \"user_name\" FROM \"MATRIX_USER\" \"u\"",
                "SELECT \"u\".\"id\", \"u\".\"username\" AS \"user_name\" FROM \"matrix_user\" \"u\"",
                "SELECT \"u\".\"id\", \"u\".\"username\" AS \"user_name\" FROM \"matrix_user\" \"u\""
        );

        // 真实执行并校验 Map 数据
        verifyAllModes(o -> {
            List<Map<String, Object>> maps = o.select(MatrixUser.class).as("u").select(u.getId(), col(u.getUsername()).as("user_name")).maps();
            Assert.assertEquals(4, maps.size());
            Map<String, Object> first = maps.get(0);
            Assert.assertTrue("结果集必须包含别名列 user_name", first.containsKey("user_name") || first.containsKey("USER_NAME"));
            Object nameVal = first.get("user_name") != null ? first.get("user_name") : first.get("USER_NAME");
            Assert.assertNotNull(nameVal);

            // 模式 2: orm.sql() 模式
            List<Map<String, Object>> sqlMaps = o.sql(e -> {
                MatrixUser su = e.table(MatrixUser.class, "u");
                e.select(su.getId(), col(su.getUsername()).as("user_name")).from(su);
            }).maps();
            Assert.assertEquals(4, sqlMaps.size());
            Map<String, Object> sqlFirst = sqlMaps.get(0);
            Assert.assertTrue("结果集必须包含别名列 user_name", sqlFirst.containsKey("user_name") || sqlFirst.containsKey("USER_NAME"));
        });
    }

    /**
     * 1.4 DISTINCT 去重投影
     * 校验点：
     * 1. SQL 包含 DISTINCT 关键字；
     * 2. 实体数据中 (status=1, age=20) 存在两条（alice 与 david），去重后从 4 条精确缩减至 3 条。
     */
    @Test
    public void testSelectDistinct() {
        MatrixUser u = $(MatrixUser.class);
        assertMatrixSql(
                o -> o.select(MatrixUser.class).distinct().select(u.getStatus(), u.getAge()),
                "SELECT DISTINCT t0.`status`, t0.`age` FROM `matrix_user` t0",
                "SELECT DISTINCT t0.\"status\", t0.\"age\" FROM \"matrix_user\" t0",
                "SELECT DISTINCT t0.\"STATUS\", t0.\"AGE\" FROM \"MATRIX_USER\" t0",
                "SELECT DISTINCT t0.\"status\", t0.\"age\" FROM \"matrix_user\" t0",
                "SELECT DISTINCT t0.\"status\", t0.\"age\" FROM \"matrix_user\" t0"
        );

        // 真实执行并校验去重结果
        verifyAllModes(o -> {
            List<MatrixUser> list = o.select(MatrixUser.class).distinct().select(u.getStatus(), u.getAge()).list();
            Assert.assertEquals("DISTINCT 去重后应为 3 条", 3, list.size());

            // 模式 2: orm.sql() 模式
            List<MatrixUser> sqlList = o.sql(e -> {
                MatrixUser su = e.table(MatrixUser.class);
                e.select(su.getStatus(), su.getAge()).from(su).distinct();
            }).list(MatrixUser.class);
            Assert.assertEquals("DISTINCT 去重后应为 3 条", 3, sqlList.size());
        });
    }

    /**
     * 1.5 聚合函数投影 (COUNT, SUM, AVG, MIN, MAX)
     * 校验点：
     * 1. SQL 渲染各个标准聚合函数；
     * 2. 真实计算数值：COUNT=4, SUM(score)=316.00, MIN(age)=20, MAX(age)=30。
     */
    @Test
    public void testAggregateProjections() {
        MatrixUser u = $(MatrixUser.class);

        // 1. COUNT(*) 校验
        assertMatrixSql(
                o -> o.select(MatrixUser.class).select(count()),
                "SELECT COUNT(1) FROM `matrix_user` t0",
                "SELECT COUNT(1) FROM \"matrix_user\" t0",
                "SELECT COUNT(1) FROM \"MATRIX_USER\" t0",
                "SELECT COUNT(1) FROM \"matrix_user\" t0",
                "SELECT COUNT(1) FROM \"matrix_user\" t0"
        );
        verifyAllModes(o -> {
            long totalCount = o.select(MatrixUser.class).count();
            Assert.assertEquals(4L, totalCount);

            // 模式 2: orm.sql() 模式
            long sqlTotalCount = o.sql(e -> {
                MatrixUser su = e.table(MatrixUser.class);
                e.select(count()).from(su);
            }).count();
            Assert.assertEquals(4L, sqlTotalCount);
        });

        // 2. COUNT(col) 校验
        assertMatrixSql(
                o -> o.select(MatrixUser.class).select(count(u.getId()).as("user_count")),
                "SELECT COUNT(t0.`id`) AS `user_count` FROM `matrix_user` t0",
                "SELECT COUNT(t0.\"id\") AS \"user_count\" FROM \"matrix_user\" t0",
                "SELECT COUNT(t0.\"ID\") AS \"user_count\" FROM \"MATRIX_USER\" t0",
                "SELECT COUNT(t0.\"id\") AS \"user_count\" FROM \"matrix_user\" t0",
                "SELECT COUNT(t0.\"id\") AS \"user_count\" FROM \"matrix_user\" t0"
        );

        // 3. SUM, AVG, MIN, MAX 组合投影与计算值校验
        assertMatrixSql(
                o -> o.select(MatrixUser.class).select(
                        sum(u.getScore()).as("total_score"),
                        avg(u.getAge()).as("avg_age"),
                        min(u.getAge()).as("min_age"),
                        max(u.getAge()).as("max_age")
                ),
                "SELECT SUM(t0.`score`) AS `total_score`, AVG(t0.`age`) AS `avg_age`, MIN(t0.`age`) AS `min_age`, MAX(t0.`age`) AS `max_age` FROM `matrix_user` t0",
                "SELECT SUM(t0.\"score\") AS \"total_score\", AVG(t0.\"age\") AS \"avg_age\", MIN(t0.\"age\") AS \"min_age\", MAX(t0.\"age\") AS \"max_age\" FROM \"matrix_user\" t0",
                "SELECT SUM(t0.\"SCORE\") AS \"total_score\", AVG(t0.\"AGE\") AS \"avg_age\", MIN(t0.\"AGE\") AS \"min_age\", MAX(t0.\"AGE\") AS \"max_age\" FROM \"MATRIX_USER\" t0",
                "SELECT SUM(t0.\"score\") AS \"total_score\", AVG(t0.\"age\") AS \"avg_age\", MIN(t0.\"age\") AS \"min_age\", MAX(t0.\"age\") AS \"max_age\" FROM \"matrix_user\" t0",
                "SELECT SUM(t0.\"score\") AS \"total_score\", AVG(t0.\"age\") AS \"avg_age\", MIN(t0.\"age\") AS \"min_age\", MAX(t0.\"age\") AS \"max_age\" FROM \"matrix_user\" t0"
        );

        verifyAllModes(o -> {
            List<Map<String, Object>> aggList = o.select(MatrixUser.class).select(
                    sum(u.getScore()).as("total_score"),
                    avg(u.getAge()).as("avg_age"),
                    min(u.getAge()).as("min_age"),
                    max(u.getAge()).as("max_age")
            ).maps();
            Assert.assertFalse(aggList.isEmpty());
            Map<String, Object> aggMap = aggList.get(0);

            // 兼容大写/小写 Key
            Number totalScore = (Number) (aggMap.get("total_score") != null ? aggMap.get("total_score") : aggMap.get("TOTAL_SCORE"));
            Number minAge = (Number) (aggMap.get("min_age") != null ? aggMap.get("min_age") : aggMap.get("MIN_AGE"));
            Number maxAge = (Number) (aggMap.get("max_age") != null ? aggMap.get("max_age") : aggMap.get("MAX_AGE"));

            Assert.assertEquals(316.0, totalScore.doubleValue(), 0.01);
            Assert.assertEquals(20, minAge.intValue());
            Assert.assertEquals(30, maxAge.intValue());

            // 模式 2: orm.sql() 模式
            List<Map<String, Object>> sqlAggList = o.sql(e -> {
                MatrixUser su = e.table(MatrixUser.class);
                e.select(
                        sum(su.getScore()).as("total_score"),
                        avg(su.getAge()).as("avg_age"),
                        min(su.getAge()).as("min_age"),
                        max(su.getAge()).as("max_age")
                ).from(su);
            }).maps();
            Assert.assertFalse(sqlAggList.isEmpty());
            Map<String, Object> sqlAggMap = sqlAggList.get(0);
            Number sqlTotalScore = (Number) (sqlAggMap.get("total_score") != null ? sqlAggMap.get("total_score") : sqlAggMap.get("TOTAL_SCORE"));
            Assert.assertEquals(316.0, sqlTotalScore.doubleValue(), 0.01);
        });
    }

    /**
     * 1.6 算术表达式与原生表达式投影
     * 校验点：
     * 1. SQL 渲染原生算术表达式与别名；
     * 2. 真实计算表达式值：age=20 时 age + 1 = 21，score=95.5 时 score * 0.8 = 76.4。
     */
    @Test
    public void testArithmeticExpressionProjection() {
        assertMatrixSql(
                o -> o.select(MatrixUser.class).select(
                        raw("age + 1").as("next_age"),
                        raw("score * 0.8").as("discount_score")
                ),
                "SELECT age + 1 AS `next_age`, score * 0.8 AS `discount_score` FROM `matrix_user` t0",
                "SELECT age + 1 AS \"next_age\", score * 0.8 AS \"discount_score\" FROM \"matrix_user\" t0",
                "SELECT age + 1 AS \"next_age\", score * 0.8 AS \"discount_score\" FROM \"MATRIX_USER\" t0",
                "SELECT age + 1 AS \"next_age\", score * 0.8 AS \"discount_score\" FROM \"matrix_user\" t0",
                "SELECT age + 1 AS \"next_age\", score * 0.8 AS \"discount_score\" FROM \"matrix_user\" t0"
        );

        verifyAllModes(o -> {
            // 在严格 Oracle 模式下，未被 quote 的列名在建表时是大写 "AGE", "SCORE"，在 H2 Oracle 模式下 raw("age + 1") / raw("\"AGE\" + 1")
            // 注意：H2 MODE=Oracle 下不带引号的标识符不区分大小写，或者大写匹配
            List<Map<String, Object>> exprList = o.select(MatrixUser.class)
                    .select(
                            raw("age + 1").as("next_age"),
                            raw("score * 0.8").as("discount_score")
                    )
                    .where().eq($(MatrixUser.class).getUsername(), "alice")
                    .maps();

            Assert.assertEquals(1, exprList.size());
            Map<String, Object> aliceExpr = exprList.get(0);
            Number nextAge = (Number) (aliceExpr.get("next_age") != null ? aliceExpr.get("next_age") : aliceExpr.get("NEXT_AGE"));
            Number discountScore = (Number) (aliceExpr.get("discount_score") != null ? aliceExpr.get("discount_score") : aliceExpr.get("DISCOUNT_SCORE"));

            Assert.assertEquals(21, nextAge.intValue());
            Assert.assertEquals(76.4, discountScore.doubleValue(), 0.01);

            // 模式 2: orm.sql() 模式
            List<Map<String, Object>> sqlExprList = o.sql(e -> {
                MatrixUser su = e.table(MatrixUser.class);
                e.select(
                        raw("age + 1").as("next_age"),
                        raw("score * 0.8").as("discount_score")
                ).from(su).where().eq(su.getUsername(), "alice");
            }).maps();
            Assert.assertEquals(1, sqlExprList.size());
            Map<String, Object> sqlAliceExpr = sqlExprList.get(0);
            Number sqlNextAge = (Number) (sqlAliceExpr.get("next_age") != null ? sqlAliceExpr.get("next_age") : sqlAliceExpr.get("NEXT_AGE"));
            Assert.assertEquals(21, sqlNextAge.intValue());
        });
    }
}
