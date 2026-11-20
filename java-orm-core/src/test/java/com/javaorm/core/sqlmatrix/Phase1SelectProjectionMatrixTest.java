package com.javaorm.core.sqlmatrix;

import org.junit.Test;

import static com.javaorm.core.SQL.*;

/**
 * Phase 1: SELECT 投影与基础查询全方言测试矩阵
 * 1.1 全列实体投影
 * 1.2 显式指定单列/多列投影
 * 1.3 显式列别名与表别名
 * 1.4 DISTINCT 去重投影
 * 1.5 聚合函数投影 (COUNT, SUM, AVG, MIN, MAX)
 * 1.6 算术表达式与列别名投影
 */
public class Phase1SelectProjectionMatrixTest extends AbstractSqlMatrixTest {

    /**
     * 1.1 全列实体投影：orm.select(MatrixUser.class)
     */
    @Test
    public void testSelectAllEntityColumns() {
        assertMatrixSql(
                () -> orm.select(MatrixUser.class),
                "SELECT t0.* FROM `matrix_user` t0",
                "SELECT t0.* FROM \"matrix_user\" t0",
                "SELECT t0.* FROM \"matrix_user\" t0",
                "SELECT t0.* FROM \"matrix_user\" t0",
                "SELECT t0.* FROM \"matrix_user\" t0"
        );
    }

    /**
     * 1.2 显式指定单列/多列投影：select(u.getId(), u.getUsername(), u.getAge())
     */
    @Test
    public void testSelectSpecificColumns() {
        MatrixUser u = $(MatrixUser.class);
        assertMatrixSql(
                () -> orm.select(MatrixUser.class).select(u.getId(), u.getUsername(), u.getAge()),
                "SELECT t0.`id`, t0.`username`, t0.`age` FROM `matrix_user` t0",
                "SELECT t0.\"id\", t0.\"username\", t0.\"age\" FROM \"matrix_user\" t0",
                "SELECT t0.\"id\", t0.\"username\", t0.\"age\" FROM \"matrix_user\" t0",
                "SELECT t0.\"id\", t0.\"username\", t0.\"age\" FROM \"matrix_user\" t0",
                "SELECT t0.\"id\", t0.\"username\", t0.\"age\" FROM \"matrix_user\" t0"
        );
    }

    /**
     * 1.3 显式表别名与列投影：as("u") 与 col(u.getUsername()).as("user_name")
     */
    @Test
    public void testSelectWithTableAndColumnAlias() {
        MatrixUser u = $(MatrixUser.class);
        assertMatrixSql(
                () -> orm.select(MatrixUser.class).as("u").select(u.getId(), col(u.getUsername()).as("user_name")),
                "SELECT `u`.`id`, `u`.`username` AS `user_name` FROM `matrix_user` `u`",
                "SELECT \"u\".\"id\", \"u\".\"username\" AS \"user_name\" FROM \"matrix_user\" \"u\"",
                "SELECT \"u\".\"id\", \"u\".\"username\" AS \"user_name\" FROM \"matrix_user\" \"u\"",
                "SELECT \"u\".\"id\", \"u\".\"username\" AS \"user_name\" FROM \"matrix_user\" \"u\"",
                "SELECT \"u\".\"id\", \"u\".\"username\" AS \"user_name\" FROM \"matrix_user\" \"u\""
        );
    }

    /**
     * 1.4 DISTINCT 去重投影
     */
    @Test
    public void testSelectDistinct() {
        MatrixUser u = $(MatrixUser.class);
        assertMatrixSql(
                () -> orm.select(MatrixUser.class).distinct().select(u.getStatus(), u.getAge()),
                "SELECT DISTINCT t0.`status`, t0.`age` FROM `matrix_user` t0",
                "SELECT DISTINCT t0.\"status\", t0.\"age\" FROM \"matrix_user\" t0",
                "SELECT DISTINCT t0.\"status\", t0.\"age\" FROM \"matrix_user\" t0",
                "SELECT DISTINCT t0.\"status\", t0.\"age\" FROM \"matrix_user\" t0",
                "SELECT DISTINCT t0.\"status\", t0.\"age\" FROM \"matrix_user\" t0"
        );
    }

    /**
     * 1.5 聚合函数投影 (COUNT, SUM, AVG, MIN, MAX)
     */
    @Test
    public void testAggregateProjections() {
        MatrixUser u = $(MatrixUser.class);

        // COUNT(*) -> 渲染为 COUNT(1)
        assertMatrixSql(
                () -> orm.select(MatrixUser.class).select(count()),
                "SELECT COUNT(1) FROM `matrix_user` t0",
                "SELECT COUNT(1) FROM \"matrix_user\" t0",
                "SELECT COUNT(1) FROM \"matrix_user\" t0",
                "SELECT COUNT(1) FROM \"matrix_user\" t0",
                "SELECT COUNT(1) FROM \"matrix_user\" t0"
        );

        // COUNT(col)
        assertMatrixSql(
                () -> orm.select(MatrixUser.class).select(count(u.getId()).as("user_count")),
                "SELECT COUNT(t0.`id`) AS `user_count` FROM `matrix_user` t0",
                "SELECT COUNT(t0.\"id\") AS \"user_count\" FROM \"matrix_user\" t0",
                "SELECT COUNT(t0.\"id\") AS \"user_count\" FROM \"matrix_user\" t0",
                "SELECT COUNT(t0.\"id\") AS \"user_count\" FROM \"matrix_user\" t0",
                "SELECT COUNT(t0.\"id\") AS \"user_count\" FROM \"matrix_user\" t0"
        );

        // SUM, AVG, MIN, MAX 组合投影
        assertMatrixSql(
                () -> orm.select(MatrixUser.class).select(
                        sum(u.getScore()).as("total_score"),
                        avg(u.getAge()).as("avg_age"),
                        min(u.getAge()).as("min_age"),
                        max(u.getAge()).as("max_age")
                ),
                "SELECT SUM(t0.`score`) AS `total_score`, AVG(t0.`age`) AS `avg_age`, MIN(t0.`age`) AS `min_age`, MAX(t0.`age`) AS `max_age` FROM `matrix_user` t0",
                "SELECT SUM(t0.\"score\") AS \"total_score\", AVG(t0.\"age\") AS \"avg_age\", MIN(t0.\"age\") AS \"min_age\", MAX(t0.\"age\") AS \"max_age\" FROM \"matrix_user\" t0",
                "SELECT SUM(t0.\"score\") AS \"total_score\", AVG(t0.\"age\") AS \"avg_age\", MIN(t0.\"age\") AS \"min_age\", MAX(t0.\"age\") AS \"max_age\" FROM \"matrix_user\" t0",
                "SELECT SUM(t0.\"score\") AS \"total_score\", AVG(t0.\"age\") AS \"avg_age\", MIN(t0.\"age\") AS \"min_age\", MAX(t0.\"age\") AS \"max_age\" FROM \"matrix_user\" t0",
                "SELECT SUM(t0.\"score\") AS \"total_score\", AVG(t0.\"age\") AS \"avg_age\", MIN(t0.\"age\") AS \"min_age\", MAX(t0.\"age\") AS \"max_age\" FROM \"matrix_user\" t0"
        );
    }

    /**
     * 1.6 算术表达式与原生表达式投影
     */
    @Test
    public void testArithmeticExpressionProjection() {
        assertMatrixSql(
                () -> orm.select(MatrixUser.class).select(
                        raw("age + 1").as("next_age"),
                        raw("score * 0.8").as("discount_score")
                ),
                "SELECT age + 1 AS `next_age`, score * 0.8 AS `discount_score` FROM `matrix_user` t0",
                "SELECT age + 1 AS \"next_age\", score * 0.8 AS \"discount_score\" FROM \"matrix_user\" t0",
                "SELECT age + 1 AS \"next_age\", score * 0.8 AS \"discount_score\" FROM \"matrix_user\" t0",
                "SELECT age + 1 AS \"next_age\", score * 0.8 AS \"discount_score\" FROM \"matrix_user\" t0",
                "SELECT age + 1 AS \"next_age\", score * 0.8 AS \"discount_score\" FROM \"matrix_user\" t0"
        );
    }
}
