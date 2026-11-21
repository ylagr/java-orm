package com.javaorm.core.sqlmatrix;

import com.javaorm.core.JavaOrm;
import com.javaorm.core.SQL;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;

/**
 * 阶段 9：UPDATE 增量更新与脏检查 (Updates & Dirty Checking)
 * 覆盖：
 * 9.1 按需指定单列更新 (set column with WHERE)
 * 9.2 按需指定多列更新 (set multiple columns with compound WHERE)
 * 9.3 表达式原子自增更新 (setIncrement / SQL.increment)
 * 9.4 表达式原子自减更新 (setDecrement / SQL.decrement)
 * 9.5 实体选择性更新 (setColumns selective)
 * 9.6 实体快照脏检查更新 (Dirty checking with Snapshot tracking)
 */
public class Phase9UpdateMatrixTest extends AbstractSqlMatrixTest {

    /**
     * 9.1 按需指定单列更新
     * 场景：根据 ID 更新单列 status
     */
    @Test
    public void test9_1_UpdateSingleColumnWithWhere() {
        assertMatrixSql(
                oOrm -> oOrm.update(MatrixUser.class)
                        .set(MatrixUser::getStatus, 2)
                        .where().eq(MatrixUser::getId, 1L),
                // MySQL
                "UPDATE `matrix_user` SET `status` = ? WHERE `id` = ?",
                // PostgreSQL
                "UPDATE \"matrix_user\" SET \"status\" = ? WHERE \"id\" = ?",
                // Oracle
                "UPDATE \"MATRIX_USER\" SET \"STATUS\" = ? WHERE \"ID\" = ?",
                // SQLite
                "UPDATE \"matrix_user\" SET \"status\" = ? WHERE \"id\" = ?",
                // H2
                "UPDATE \"matrix_user\" SET \"status\" = ? WHERE \"id\" = ?",
                2, 1L
        );

        // 真实物理数据库执行闭环：更新 id=1 用户的 status 为 2
        verifyAllModes(targetOrm -> {
            int rows = targetOrm.update(MatrixUser.class)
                    .set(MatrixUser::getStatus, 2)
                    .where().eq(MatrixUser::getId, 1L)
                    .execute();
            Assert.assertEquals(1, rows);

            MatrixUser loaded = targetOrm.select(MatrixUser.class)
                    .where().eq(MatrixUser::getId, 1L)
                    .firstOrNull();
            Assert.assertNotNull(loaded);
            Assert.assertEquals(Integer.valueOf(2), loaded.getStatus());

            // 模式 2: orm.sql() 模式
            int sqlRows = targetOrm.sql(e -> {
                MatrixUser su = e.table(MatrixUser.class);
                e.update(su).set(su.getStatus(), 2).where().eq(su.getId(), 1L);
            }).execute();
            Assert.assertEquals(1, sqlRows);
        });
    }

    /**
     * 9.2 按需指定多列更新
     * 场景：同时更新 age 和 score，带复合 WHERE 条件 (id = 2 AND status = 1)
     */
    @Test
    public void test9_2_UpdateMultipleColumnsWithWhere() {
        assertMatrixSql(
                oOrm -> oOrm.update(MatrixUser.class)
                        .set(MatrixUser::getAge, 28)
                        .set(MatrixUser::getScore, new BigDecimal("88.88"))
                        .where().eq(MatrixUser::getId, 2L).and().eq(MatrixUser::getStatus, 1),
                // MySQL
                "UPDATE `matrix_user` SET `age` = ?, `score` = ? WHERE `id` = ? AND `status` = ?",
                // PostgreSQL
                "UPDATE \"matrix_user\" SET \"age\" = ?, \"score\" = ? WHERE \"id\" = ? AND \"status\" = ?",
                // Oracle
                "UPDATE \"MATRIX_USER\" SET \"AGE\" = ?, \"SCORE\" = ? WHERE \"ID\" = ? AND \"STATUS\" = ?",
                // SQLite
                "UPDATE \"matrix_user\" SET \"age\" = ?, \"score\" = ? WHERE \"id\" = ? AND \"status\" = ?",
                // H2
                "UPDATE \"matrix_user\" SET \"age\" = ?, \"score\" = ? WHERE \"id\" = ? AND \"status\" = ?",
                28, new BigDecimal("88.88"), 2L, 1
        );

        // 真实物理数据库执行闭环：更新 id=2 (bob, 25岁) 为 28 岁与 88.88 分
        verifyAllModes(targetOrm -> {
            int rows = targetOrm.update(MatrixUser.class)
                    .set(MatrixUser::getAge, 28)
                    .set(MatrixUser::getScore, new BigDecimal("88.88"))
                    .where().eq(MatrixUser::getId, 2L).and().eq(MatrixUser::getStatus, 1)
                    .execute();
            Assert.assertEquals(1, rows);

            MatrixUser loaded = targetOrm.select(MatrixUser.class)
                    .where().eq(MatrixUser::getId, 2L)
                    .firstOrNull();
            Assert.assertNotNull(loaded);
            Assert.assertEquals(Integer.valueOf(28), loaded.getAge());
            Assert.assertEquals(new BigDecimal("88.88"), loaded.getScore());

            // 模式 2: orm.sql() 模式
            int sqlRows = targetOrm.sql(e -> {
                MatrixUser su = e.table(MatrixUser.class);
                e.update(su).set(su.getAge(), 28).set(su.getScore(), new BigDecimal("88.88"))
                        .where().eq(su.getId(), 2L).and().eq(su.getStatus(), 1);
            }).execute();
            Assert.assertEquals(1, sqlRows);
        });
    }

    /**
     * 9.3 表达式原子自增更新 (SQL.increment)
     * 场景：UPDATE SET age = age + ? WHERE id = ?
     */
    @Test
    public void test9_3_UpdateIncrementExpression() {
        assertMatrixSql(
                oOrm -> oOrm.update(MatrixUser.class)
                        .set(MatrixUser::getAge, SQL.increment(1))
                        .where().eq(MatrixUser::getId, 2L),
                // MySQL
                "UPDATE `matrix_user` SET `age` = `age` + ? WHERE `id` = ?",
                // PostgreSQL
                "UPDATE \"matrix_user\" SET \"age\" = \"age\" + ? WHERE \"id\" = ?",
                // Oracle
                "UPDATE \"MATRIX_USER\" SET \"AGE\" = \"AGE\" + ? WHERE \"ID\" = ?",
                // SQLite
                "UPDATE \"matrix_user\" SET \"age\" = \"age\" + ? WHERE \"id\" = ?",
                // H2
                "UPDATE \"matrix_user\" SET \"age\" = \"age\" + ? WHERE \"id\" = ?",
                1, 2L
        );

        // 真实物理数据库执行闭环：对 bob(此前为28岁) 递增 1 岁
        verifyAllModes(targetOrm -> {
            MatrixUser before = targetOrm.select(MatrixUser.class)
                    .where().eq(MatrixUser::getId, 2L)
                    .firstOrNull();
            Assert.assertNotNull(before);
            int originAge = before.getAge();

            int rows = targetOrm.update(MatrixUser.class)
                    .set(MatrixUser::getAge, SQL.increment(1))
                    .where().eq(MatrixUser::getId, 2L)
                    .execute();
            Assert.assertEquals(1, rows);

            MatrixUser after = targetOrm.select(MatrixUser.class)
                    .where().eq(MatrixUser::getId, 2L)
                    .firstOrNull();
            Assert.assertNotNull(after);
            Assert.assertEquals(Integer.valueOf(originAge + 1), after.getAge());

            // 模式 2: orm.sql() 模式
            int sqlRows = targetOrm.sql(e -> {
                MatrixUser su = e.table(MatrixUser.class);
                e.update(su).set(su.getAge(), SQL.increment(1)).where().eq(su.getId(), 2L);
            }).execute();
            Assert.assertEquals(1, sqlRows);
        });
    }

    /**
     * 9.4 表达式原子自减更新 (SQL.decrement)
     * 场景：UPDATE SET score = score - ? WHERE id = ?
     */
    @Test
    public void test9_4_UpdateDecrementExpression() {
        assertMatrixSql(
                oOrm -> oOrm.update(MatrixUser.class)
                        .set(MatrixUser::getScore, SQL.decrement(new BigDecimal("5.00")))
                        .where().eq(MatrixUser::getId, 2L),
                // MySQL
                "UPDATE `matrix_user` SET `score` = `score` - ? WHERE `id` = ?",
                // PostgreSQL
                "UPDATE \"matrix_user\" SET \"score\" = \"score\" - ? WHERE \"id\" = ?",
                // Oracle
                "UPDATE \"MATRIX_USER\" SET \"SCORE\" = \"SCORE\" - ? WHERE \"ID\" = ?",
                // SQLite
                "UPDATE \"matrix_user\" SET \"score\" = \"score\" - ? WHERE \"id\" = ?",
                // H2
                "UPDATE \"matrix_user\" SET \"score\" = \"score\" - ? WHERE \"id\" = ?",
                new BigDecimal("5.00"), 2L
        );

        // 真实物理数据库执行闭环：对 bob (score 88.88) 扣减 5.00 分
        verifyAllModes(targetOrm -> {
            MatrixUser before = targetOrm.select(MatrixUser.class)
                    .where().eq(MatrixUser::getId, 2L)
                    .firstOrNull();
            Assert.assertNotNull(before);
            BigDecimal originScore = before.getScore();

            int rows = targetOrm.update(MatrixUser.class)
                    .set(MatrixUser::getScore, SQL.decrement(new BigDecimal("5.00")))
                    .where().eq(MatrixUser::getId, 2L)
                    .execute();
            Assert.assertEquals(1, rows);

            MatrixUser after = targetOrm.select(MatrixUser.class)
                    .where().eq(MatrixUser::getId, 2L)
                    .firstOrNull();
            Assert.assertNotNull(after);
            Assert.assertEquals(originScore.subtract(new BigDecimal("5.00")), after.getScore());

            // 模式 2: orm.sql() 模式
            int sqlRows = targetOrm.sql(e -> {
                MatrixUser su = e.table(MatrixUser.class);
                e.update(su).set(su.getScore(), SQL.decrement(new BigDecimal("5.00"))).where().eq(su.getId(), 2L);
            }).execute();
            Assert.assertEquals(1, sqlRows);
        });
    }

    /**
     * 9.5 实体选择性更新 (setColumns selective)
     * 场景：仅更新非空字段，null 字段自动跳过
     */
    @Test
    public void test9_5_UpdateSetColumnsSelective() {
        MatrixUser partialUser = new MatrixUser();
        partialUser.setEmail("charlie_selective@example.com");
        partialUser.setAge(35);
        // username, status, score 等为 null

        assertMatrixSql(
                oOrm -> oOrm.update(MatrixUser.class)
                        .setColumns(partialUser, true)
                        .where().eq(MatrixUser::getId, 3L),
                // MySQL
                "UPDATE `matrix_user` SET `email` = ?, `age` = ? WHERE `id` = ?",
                // PostgreSQL
                "UPDATE \"matrix_user\" SET \"email\" = ?, \"age\" = ? WHERE \"id\" = ?",
                // Oracle
                "UPDATE \"MATRIX_USER\" SET \"EMAIL\" = ?, \"AGE\" = ? WHERE \"ID\" = ?",
                // SQLite
                "UPDATE \"matrix_user\" SET \"email\" = ?, \"age\" = ? WHERE \"id\" = ?",
                // H2
                "UPDATE \"matrix_user\" SET \"email\" = ?, \"age\" = ? WHERE \"id\" = ?",
                "charlie_selective@example.com", 35, 3L
        );

        // 真实物理数据库执行闭环：仅更新 charlie (id=3) 的 email 和 age，username "charlie" 不被覆盖
        verifyAllModes(targetOrm -> {
            MatrixUser updateBean = new MatrixUser();
            updateBean.setEmail("charlie_selective@example.com");
            updateBean.setAge(35);

            int rows = targetOrm.update(MatrixUser.class)
                    .setColumns(updateBean, true)
                    .where().eq(MatrixUser::getId, 3L)
                    .execute();
            Assert.assertEquals(1, rows);

            MatrixUser loaded = targetOrm.select(MatrixUser.class)
                    .where().eq(MatrixUser::getId, 3L)
                    .firstOrNull();
            Assert.assertNotNull(loaded);
            Assert.assertEquals("charlie", loaded.getUsername());
            Assert.assertEquals("charlie_selective@example.com", loaded.getEmail());
            Assert.assertEquals(Integer.valueOf(35), loaded.getAge());

            // 模式 2: orm.sql() 模式
            int sqlRows = targetOrm.sql(e -> {
                MatrixUser su = e.table(MatrixUser.class);
                e.update(su).set(su.getEmail(), "charlie_selective@example.com").set(su.getAge(), 35).where().eq(su.getId(), 3L);
            }).execute();
            Assert.assertEquals(1, sqlRows);
        });
    }

    /**
     * 9.6 实体快照脏检查更新 (Dirty checking with Snapshot tracking)
     * 场景：attach 实体后仅修改 username，ORM 自动计算 Diff 只生成修改列的 UPDATE 语句
     */
    @Test
    public void test9_6_UpdateDirtyChecking() {
        verifyAllModes(targetOrm -> {
            MatrixUser user = targetOrm.select(MatrixUser.class)
                    .where().eq(MatrixUser::getId, 4L)
                    .firstOrNull();
            Assert.assertNotNull(user);
            Assert.assertEquals("david", user.getUsername());

            // 附加上快照跟踪
            targetOrm.attach(user);

            // 仅修改 username
            user.setUsername("david_dirty_updated");

            // 执行实体更新：底层脏检查机制应只更新 username
            int affected = targetOrm.update(user);
            Assert.assertEquals(1, affected);

            MatrixUser reloaded = targetOrm.select(MatrixUser.class)
                    .where().eq(MatrixUser::getId, 4L)
                    .firstOrNull();
            Assert.assertNotNull(reloaded);
            Assert.assertEquals("david_dirty_updated", reloaded.getUsername());
            // 其他未修改列保持不变
            Assert.assertEquals("david@example.com", reloaded.getEmail());
            Assert.assertEquals(Integer.valueOf(20), reloaded.getAge());

            // 模式 2: orm.sql() 模式
            int sqlRows = targetOrm.sql(e -> {
                MatrixUser su = e.table(MatrixUser.class);
                e.update(su).set(su.getUsername(), "david_dirty_updated").where().eq(su.getId(), 4L);
            }).execute();
            Assert.assertEquals(1, sqlRows);
        });
    }
}
