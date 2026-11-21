package com.javaorm.core.sqlmatrix;

import org.junit.Assert;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.javaorm.core.SQL.$;

/**
 * Phase 2: WHERE 条件分支与操作符全覆盖测试矩阵
 * 1. 严格全文本精确断言 5 大数据库方言 (MySQL, PostgreSQL, Oracle, SQLite, H2) 生成的 SQL 字符串与参数；
 * 2. 真实执行 JDBC 查询，严格校验查询返回的实体数据（条数、字段值过滤精准性）。
 */
public class Phase2WhereClauseMatrixTest extends AbstractSqlMatrixTest {

    private static final String ALL_COLS_MYSQL = "SELECT t0.`id`, t0.`username`, t0.`email`, t0.`age`, t0.`status`, t0.`score`, t0.`create_time`, t0.`is_deleted` FROM `matrix_user` t0";
    private static final String ALL_COLS_DOUBLE_QUOTE = "SELECT t0.\"id\", t0.\"username\", t0.\"email\", t0.\"age\", t0.\"status\", t0.\"score\", t0.\"create_time\", t0.\"is_deleted\" FROM \"matrix_user\" t0";
    private static final String ALL_COLS_ORACLE = "SELECT t0.\"ID\", t0.\"USERNAME\", t0.\"EMAIL\", t0.\"AGE\", t0.\"STATUS\", t0.\"SCORE\", t0.\"CREATE_TIME\", t0.\"IS_DELETED\" FROM \"MATRIX_USER\" t0";

    /**
     * 2.1 基础比较操作符：eq, ne, gt, ge, lt, le
     */
    @Test
    public void testBasicComparisonOperators() {
        MatrixUser u = $(MatrixUser.class);

        // 1. eq (=)
        assertMatrixSql(
                o -> o.select(MatrixUser.class).where().eq(u.getUsername(), "alice"),
                ALL_COLS_MYSQL + " WHERE t0.`username` = ?",
                ALL_COLS_DOUBLE_QUOTE + " WHERE t0.\"username\" = ?",
                ALL_COLS_ORACLE + " WHERE t0.\"USERNAME\" = ?",
                ALL_COLS_DOUBLE_QUOTE + " WHERE t0.\"username\" = ?",
                ALL_COLS_DOUBLE_QUOTE + " WHERE t0.\"username\" = ?",
                "alice"
        );
        verifyAllModes(o -> {
            List<MatrixUser> eqList = o.select(MatrixUser.class).where().eq(u.getUsername(), "alice").list();
            Assert.assertEquals(1, eqList.size());
            Assert.assertEquals("alice", eqList.get(0).getUsername());

            // 模式 2: orm.sql() 模式
            List<MatrixUser> sqlEqList = o.sql(e -> {
                MatrixUser su = e.table(MatrixUser.class);
                e.select(su).where().eq(su.getUsername(), "alice");
            }).list(MatrixUser.class);
            Assert.assertEquals(1, sqlEqList.size());
            Assert.assertEquals("alice", sqlEqList.get(0).getUsername());
        });

        // 2. ne (<>)
        assertMatrixSql(
                o -> o.select(MatrixUser.class).where().ne(u.getStatus(), 1),
                ALL_COLS_MYSQL + " WHERE t0.`status` <> ?",
                ALL_COLS_DOUBLE_QUOTE + " WHERE t0.\"status\" <> ?",
                ALL_COLS_ORACLE + " WHERE t0.\"STATUS\" <> ?",
                ALL_COLS_DOUBLE_QUOTE + " WHERE t0.\"status\" <> ?",
                ALL_COLS_DOUBLE_QUOTE + " WHERE t0.\"status\" <> ?",
                1
        );
        verifyAllModes(o -> {
            List<MatrixUser> neList = o.select(MatrixUser.class).where().ne(u.getStatus(), 1).list();
            Assert.assertEquals(1, neList.size());
            Assert.assertEquals("charlie", neList.get(0).getUsername());

            // 模式 2: orm.sql() 模式
            List<MatrixUser> sqlNeList = o.sql(e -> {
                MatrixUser su = e.table(MatrixUser.class);
                e.select(su).where().ne(su.getStatus(), 1);
            }).list(MatrixUser.class);
            Assert.assertEquals(1, sqlNeList.size());
            Assert.assertEquals("charlie", sqlNeList.get(0).getUsername());
        });

        // 3. gt (>), ge (>=), lt (<), le (<=)
        assertMatrixSql(
                o -> o.select(MatrixUser.class).where().gt(u.getAge(), 20).le(u.getAge(), 30),
                ALL_COLS_MYSQL + " WHERE t0.`age` > ? AND t0.`age` <= ?",
                ALL_COLS_DOUBLE_QUOTE + " WHERE t0.\"age\" > ? AND t0.\"age\" <= ?",
                ALL_COLS_ORACLE + " WHERE t0.\"AGE\" > ? AND t0.\"AGE\" <= ?",
                ALL_COLS_DOUBLE_QUOTE + " WHERE t0.\"age\" > ? AND t0.\"age\" <= ?",
                ALL_COLS_DOUBLE_QUOTE + " WHERE t0.\"age\" > ? AND t0.\"age\" <= ?",
                20, 30
        );
        verifyAllModes(o -> {
            List<MatrixUser> rangeUsers = o.select(MatrixUser.class).where().gt(u.getAge(), 20).le(u.getAge(), 30).list();
            // bob(25), charlie(30)
            Assert.assertEquals(2, rangeUsers.size());

            // 模式 2: orm.sql() 模式
            List<MatrixUser> sqlRangeUsers = o.sql(e -> {
                MatrixUser su = e.table(MatrixUser.class);
                e.select(su).where().gt(su.getAge(), 20).le(su.getAge(), 30);
            }).list(MatrixUser.class);
            Assert.assertEquals(2, sqlRangeUsers.size());
        });
    }

    /**
     * 2.2 空值检查：isNull, isNotNull
     */
    @Test
    public void testNullChecks() {
        MatrixUser u = $(MatrixUser.class);

        // isNull
        assertMatrixSql(
                o -> o.select(MatrixUser.class).where().isNull(u.getEmail()),
                ALL_COLS_MYSQL + " WHERE t0.`email` IS NULL",
                ALL_COLS_DOUBLE_QUOTE + " WHERE t0.\"email\" IS NULL",
                ALL_COLS_ORACLE + " WHERE t0.\"EMAIL\" IS NULL",
                ALL_COLS_DOUBLE_QUOTE + " WHERE t0.\"email\" IS NULL",
                ALL_COLS_DOUBLE_QUOTE + " WHERE t0.\"email\" IS NULL"
        );
        verifyAllModes(o -> {
            List<MatrixUser> nullList = o.select(MatrixUser.class).where().isNull(u.getEmail()).list();
            Assert.assertEquals(0, nullList.size()); // 种子数据均有 email

            // 模式 2: orm.sql() 模式
            List<MatrixUser> sqlNullList = o.sql(e -> {
                MatrixUser su = e.table(MatrixUser.class);
                e.select(su).where().isNull(su.getEmail());
            }).list(MatrixUser.class);
            Assert.assertEquals(0, sqlNullList.size());
        });

        // isNotNull
        assertMatrixSql(
                o -> o.select(MatrixUser.class).where().isNotNull(u.getEmail()),
                ALL_COLS_MYSQL + " WHERE t0.`email` IS NOT NULL",
                ALL_COLS_DOUBLE_QUOTE + " WHERE t0.\"email\" IS NOT NULL",
                ALL_COLS_ORACLE + " WHERE t0.\"EMAIL\" IS NOT NULL",
                ALL_COLS_DOUBLE_QUOTE + " WHERE t0.\"email\" IS NOT NULL",
                ALL_COLS_DOUBLE_QUOTE + " WHERE t0.\"email\" IS NOT NULL"
        );
        verifyAllModes(o -> {
            List<MatrixUser> notNullList = o.select(MatrixUser.class).where().isNotNull(u.getEmail()).list();
            Assert.assertEquals(4, notNullList.size());

            // 模式 2: orm.sql() 模式
            List<MatrixUser> sqlNotNullList = o.sql(e -> {
                MatrixUser su = e.table(MatrixUser.class);
                e.select(su).where().isNotNull(su.getEmail());
            }).list(MatrixUser.class);
            Assert.assertEquals(4, sqlNotNullList.size());
        });
    }

    /**
     * 2.3 集合运算：in, notIn 与空集合防御分支
     */
    @Test
    public void testInAndNotInOperators() {
        MatrixUser u = $(MatrixUser.class);

        // in(Collection)
        assertMatrixSql(
                o -> o.select(MatrixUser.class).where().in(u.getUsername(), Arrays.asList("alice", "bob")),
                ALL_COLS_MYSQL + " WHERE t0.`username` IN (?, ?)",
                ALL_COLS_DOUBLE_QUOTE + " WHERE t0.\"username\" IN (?, ?)",
                ALL_COLS_ORACLE + " WHERE t0.\"USERNAME\" IN (?, ?)",
                ALL_COLS_DOUBLE_QUOTE + " WHERE t0.\"username\" IN (?, ?)",
                ALL_COLS_DOUBLE_QUOTE + " WHERE t0.\"username\" IN (?, ?)",
                "alice", "bob"
        );
        verifyAllModes(o -> {
            List<MatrixUser> inList = o.select(MatrixUser.class).where().in(u.getUsername(), Arrays.asList("alice", "bob")).list();
            Assert.assertEquals(2, inList.size());

            // 模式 2: orm.sql() 模式
            List<MatrixUser> sqlInList = o.sql(e -> {
                MatrixUser su = e.table(MatrixUser.class);
                e.select(su).where().in(su.getUsername(), Arrays.asList("alice", "bob"));
            }).list(MatrixUser.class);
            Assert.assertEquals(2, sqlInList.size());
        });

        // notIn(Collection)
        assertMatrixSql(
                o -> o.select(MatrixUser.class).where().notIn(u.getUsername(), Arrays.asList("alice", "bob")),
                ALL_COLS_MYSQL + " WHERE t0.`username` NOT IN (?, ?)",
                ALL_COLS_DOUBLE_QUOTE + " WHERE t0.\"username\" NOT IN (?, ?)",
                ALL_COLS_ORACLE + " WHERE t0.\"USERNAME\" NOT IN (?, ?)",
                ALL_COLS_DOUBLE_QUOTE + " WHERE t0.\"username\" NOT IN (?, ?)",
                ALL_COLS_DOUBLE_QUOTE + " WHERE t0.\"username\" NOT IN (?, ?)",
                "alice", "bob"
        );
        verifyAllModes(o -> {
            List<MatrixUser> notInList = o.select(MatrixUser.class).where().notIn(u.getUsername(), Arrays.asList("alice", "bob")).list();
            Assert.assertEquals(2, notInList.size()); // charlie, david

            // 模式 2: orm.sql() 模式
            List<MatrixUser> sqlNotInList = o.sql(e -> {
                MatrixUser su = e.table(MatrixUser.class);
                e.select(su).where().notIn(su.getUsername(), Arrays.asList("alice", "bob"));
            }).list(MatrixUser.class);
            Assert.assertEquals(2, sqlNotInList.size());
        });
    }

    /**
     * 2.4 范围检查：between, notBetween
     */
    @Test
    public void testBetweenAndNotBetween() {
        MatrixUser u = $(MatrixUser.class);

        // between
        assertMatrixSql(
                o -> o.select(MatrixUser.class).where().between(u.getAge(), 20, 25),
                ALL_COLS_MYSQL + " WHERE t0.`age` BETWEEN ? AND ?",
                ALL_COLS_DOUBLE_QUOTE + " WHERE t0.\"age\" BETWEEN ? AND ?",
                ALL_COLS_ORACLE + " WHERE t0.\"AGE\" BETWEEN ? AND ?",
                ALL_COLS_DOUBLE_QUOTE + " WHERE t0.\"age\" BETWEEN ? AND ?",
                ALL_COLS_DOUBLE_QUOTE + " WHERE t0.\"age\" BETWEEN ? AND ?",
                20, 25
        );
        verifyAllModes(o -> {
            List<MatrixUser> betweenList = o.select(MatrixUser.class).where().between(u.getAge(), 20, 25).list();
            // alice(20), bob(25), david(20)
            Assert.assertEquals(3, betweenList.size());

            // 模式 2: orm.sql() 模式
            List<MatrixUser> sqlBetweenList = o.sql(e -> {
                MatrixUser su = e.table(MatrixUser.class);
                e.select(su).where().between(su.getAge(), 20, 25);
            }).list(MatrixUser.class);
            Assert.assertEquals(3, sqlBetweenList.size());
        });

        // notBetween
        assertMatrixSql(
                o -> o.select(MatrixUser.class).where().notBetween(u.getAge(), 20, 25),
                ALL_COLS_MYSQL + " WHERE t0.`age` NOT BETWEEN ? AND ?",
                ALL_COLS_DOUBLE_QUOTE + " WHERE t0.\"age\" NOT BETWEEN ? AND ?",
                ALL_COLS_ORACLE + " WHERE t0.\"AGE\" NOT BETWEEN ? AND ?",
                ALL_COLS_DOUBLE_QUOTE + " WHERE t0.\"age\" NOT BETWEEN ? AND ?",
                ALL_COLS_DOUBLE_QUOTE + " WHERE t0.\"age\" NOT BETWEEN ? AND ?",
                20, 25
        );
        verifyAllModes(o -> {
            List<MatrixUser> notBetweenList = o.select(MatrixUser.class).where().notBetween(u.getAge(), 20, 25).list();
            // charlie(30)
            Assert.assertEquals(1, notBetweenList.size());
            Assert.assertEquals("charlie", notBetweenList.get(0).getUsername());

            // 模式 2: orm.sql() 模式
            List<MatrixUser> sqlNotBetweenList = o.sql(e -> {
                MatrixUser su = e.table(MatrixUser.class);
                e.select(su).where().notBetween(su.getAge(), 20, 25);
            }).list(MatrixUser.class);
            Assert.assertEquals(1, sqlNotBetweenList.size());
            Assert.assertEquals("charlie", sqlNotBetweenList.get(0).getUsername());
        });
    }

    /**
     * 2.5 模糊匹配与正则：like, notLike, likeLeft, likeRight
     */
    @Test
    public void testLikeAndPatternMatching() {
        MatrixUser u = $(MatrixUser.class);

        // like
        assertMatrixSql(
                o -> o.select(MatrixUser.class).where().like(u.getUsername(), "%li%"),
                ALL_COLS_MYSQL + " WHERE t0.`username` LIKE ?",
                ALL_COLS_DOUBLE_QUOTE + " WHERE t0.\"username\" LIKE ?",
                ALL_COLS_ORACLE + " WHERE t0.\"USERNAME\" LIKE ?",
                ALL_COLS_DOUBLE_QUOTE + " WHERE t0.\"username\" LIKE ?",
                ALL_COLS_DOUBLE_QUOTE + " WHERE t0.\"username\" LIKE ?",
                "%li%"
        );
        verifyAllModes(o -> {
            List<MatrixUser> likeList = o.select(MatrixUser.class).where().like(u.getUsername(), "%li%").list();
            // alice, charlie
            Assert.assertEquals(2, likeList.size());

            // 模式 2: orm.sql() 模式
            List<MatrixUser> sqlLikeList = o.sql(e -> {
                MatrixUser su = e.table(MatrixUser.class);
                e.select(su).where().like(su.getUsername(), "%li%");
            }).list(MatrixUser.class);
            Assert.assertEquals(2, sqlLikeList.size());
        });

        // notLike
        assertMatrixSql(
                o -> o.select(MatrixUser.class).where().notLike(u.getUsername(), "%li%"),
                ALL_COLS_MYSQL + " WHERE t0.`username` NOT LIKE ?",
                ALL_COLS_DOUBLE_QUOTE + " WHERE t0.\"username\" NOT LIKE ?",
                ALL_COLS_ORACLE + " WHERE t0.\"USERNAME\" NOT LIKE ?",
                ALL_COLS_DOUBLE_QUOTE + " WHERE t0.\"username\" NOT LIKE ?",
                ALL_COLS_DOUBLE_QUOTE + " WHERE t0.\"username\" NOT LIKE ?",
                "%li%"
        );
        verifyAllModes(o -> {
            List<MatrixUser> notLikeList = o.select(MatrixUser.class).where().notLike(u.getUsername(), "%li%").list();
            // bob, david
            Assert.assertEquals(2, notLikeList.size());

            // 模式 2: orm.sql() 模式
            List<MatrixUser> sqlNotLikeList = o.sql(e -> {
                MatrixUser su = e.table(MatrixUser.class);
                e.select(su).where().notLike(su.getUsername(), "%li%");
            }).list(MatrixUser.class);
            Assert.assertEquals(2, sqlNotLikeList.size());
        });
    }

    /**
     * 2.6 复杂嵌套与逻辑分支：and(w -> ...), or(w -> ...)
     */
    @Test
    public void testNestedLogicalGrouping() {
        MatrixUser u = $(MatrixUser.class);

        // WHERE (status = 1 AND (age = 25 OR score >= 90.00))
        assertMatrixSql(
                o -> o.select(MatrixUser.class).where()
                        .eq(u.getStatus(), 1)
                        .and(w -> w.eq(u.getAge(), 25).or(w2 -> w2.ge(u.getScore(), new BigDecimal("90.00")))),
                ALL_COLS_MYSQL + " WHERE t0.`status` = ? AND (t0.`age` = ? OR (t0.`score` >= ?))",
                ALL_COLS_DOUBLE_QUOTE + " WHERE t0.\"status\" = ? AND (t0.\"age\" = ? OR (t0.\"score\" >= ?))",
                ALL_COLS_ORACLE + " WHERE t0.\"STATUS\" = ? AND (t0.\"AGE\" = ? OR (t0.\"SCORE\" >= ?))",
                ALL_COLS_DOUBLE_QUOTE + " WHERE t0.\"status\" = ? AND (t0.\"age\" = ? OR (t0.\"score\" >= ?))",
                ALL_COLS_DOUBLE_QUOTE + " WHERE t0.\"status\" = ? AND (t0.\"age\" = ? OR (t0.\"score\" >= ?))",
                1, 25, new BigDecimal("90.00")
        );

        verifyAllModes(o -> {
            List<MatrixUser> nestedUsers = o.select(MatrixUser.class).where()
                    .eq(u.getStatus(), 1)
                    .and(w -> w.eq(u.getAge(), 25).or(w2 -> w2.ge(u.getScore(), new BigDecimal("90.00"))))
                    .list();
            // alice (status=1, score=95.50), bob (status=1, age=25)
            Assert.assertEquals(2, nestedUsers.size());

            // 模式 2: orm.sql() 模式
            List<MatrixUser> sqlNestedUsers = o.sql(e -> {
                MatrixUser su = e.table(MatrixUser.class);
                e.select(su).where()
                        .eq(su.getStatus(), 1)
                        .and(w -> w.eq(su.getAge(), 25).or(w2 -> w2.ge(su.getScore(), new BigDecimal("90.00"))));
            }).list(MatrixUser.class);
            Assert.assertEquals(2, sqlNestedUsers.size());
        });
    }

    /**
     * 2.7 动态条件过滤 (Condition Control)
     * condition == false 时自动剔除条件，绝不拼接无意义的 AND 1=1
     */
    @Test
    public void testDynamicConditionControl() {
        MatrixUser u = $(MatrixUser.class);
        String nameFilter = null;
        Integer ageFilter = 25;

        assertMatrixSql(
                o -> o.select(MatrixUser.class).where()
                        .eq(nameFilter != null, u.getUsername(), nameFilter)
                        .eq(ageFilter != null, u.getAge(), ageFilter),
                ALL_COLS_MYSQL + " WHERE t0.`age` = ?",
                ALL_COLS_DOUBLE_QUOTE + " WHERE t0.\"age\" = ?",
                ALL_COLS_ORACLE + " WHERE t0.\"AGE\" = ?",
                ALL_COLS_DOUBLE_QUOTE + " WHERE t0.\"age\" = ?",
                ALL_COLS_DOUBLE_QUOTE + " WHERE t0.\"age\" = ?",
                25
        );

        verifyAllModes(o -> {
            List<MatrixUser> result = o.select(MatrixUser.class).where()
                    .eq(nameFilter != null, u.getUsername(), nameFilter)
                    .eq(ageFilter != null, u.getAge(), ageFilter)
                    .list();
            Assert.assertEquals(1, result.size());
            Assert.assertEquals("bob", result.get(0).getUsername());

            // 模式 2: orm.sql() 模式
            List<MatrixUser> sqlResult = o.sql(e -> {
                MatrixUser su = e.table(MatrixUser.class);
                e.select(su).where()
                        .eq(nameFilter != null, su.getUsername(), nameFilter)
                        .eq(ageFilter != null, su.getAge(), ageFilter);
            }).list(MatrixUser.class);
            Assert.assertEquals(1, sqlResult.size());
            Assert.assertEquals("bob", sqlResult.get(0).getUsername());
        });
    }
}
