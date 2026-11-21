package com.javaorm.core.sqlmatrix;

import com.javaorm.core.dialect.SqlAndParams;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 阶段八：INSERT 插入与冲突更新 (Upsert / Merge) 矩阵单测 (Phase 8)。
 *
 * 核心验证矩阵：
 * 1. 实体单行插入 (Single Entity Insert)；
 * 2. 批量实体插入 (Batch Insert) 与主键/多行入库；
 * 3. 冲突更新：默认主键冲突更新 (Upsert Default - ON DUPLICATE KEY UPDATE / ON CONFLICT DO UPDATE / MERGE INTO)；
 * 4. 冲突更新：冲突时忽略 (Upsert DO NOTHING)；
 * 5. 冲突更新：指定唯一键与按需更新列 (Upsert onConflict & updateColumns)。
 *
 * 校验闭环：
 * 1. 5 大数据库方言 (MySQL, PostgreSQL, Oracle 12c+, SQLite, H2) 100% 全文本精确断言；
 * 2. 真实 JDBC 数据库执行闭环校验（MySQL, PostgreSQL, Oracle 严格模式）。
 */
public class Phase8InsertAndUpsertMatrixTest extends AbstractSqlMatrixTest {

    /**
     * 8.1 实体单行插入 (Single Entity Insert)
     * 场景：插入一位新用户，非自增字段完整赋值
     */
    @Test
    public void test8_1_SingleEntityInsert() {
        LocalDateTime testTime = LocalDateTime.of(2026, 11, 20, 12, 0, 0);

        assertMatrixSql(
                oOrm -> {
                    MatrixUser user = new MatrixUser();
                    user.setUsername("eve");
                    user.setEmail("eve@example.com");
                    user.setAge(22);
                    user.setStatus(1);
                    user.setScore(new BigDecimal("91.00"));
                    user.setCreateTime(testTime);
                    user.setIsDeleted(0);
                    return oOrm.insertable(user);
                },
                // MySQL
                "INSERT INTO `matrix_user` (`username`, `email`, `age`, `status`, `score`, `create_time`, `is_deleted`) VALUES (?, ?, ?, ?, ?, ?, ?)",
                // PostgreSQL
                "INSERT INTO \"matrix_user\" (\"username\", \"email\", \"age\", \"status\", \"score\", \"create_time\", \"is_deleted\") VALUES (?, ?, ?, ?, ?, ?, ?)",
                // Oracle
                "INSERT INTO \"MATRIX_USER\" (\"USERNAME\", \"EMAIL\", \"AGE\", \"STATUS\", \"SCORE\", \"CREATE_TIME\", \"IS_DELETED\") VALUES (?, ?, ?, ?, ?, ?, ?)",
                // SQLite
                "INSERT INTO \"matrix_user\" (\"username\", \"email\", \"age\", \"status\", \"score\", \"create_time\", \"is_deleted\") VALUES (?, ?, ?, ?, ?, ?, ?)",
                // H2
                "INSERT INTO \"matrix_user\" (\"username\", \"email\", \"age\", \"status\", \"score\", \"create_time\", \"is_deleted\") VALUES (?, ?, ?, ?, ?, ?, ?)",
                "eve", "eve@example.com", 22, 1, new BigDecimal("91.00"), testTime, 0
        );

        // 真实物理数据库执行闭环：在三大独立模式下执行单行插入并查询核对
        verifyAllModes(targetOrm -> {
            MatrixUser user = new MatrixUser();
            user.setUsername("eve_mode_test");
            user.setEmail("eve_mode@example.com");
            user.setAge(22);
            user.setStatus(1);
            user.setScore(new BigDecimal("91.00"));
            user.setCreateTime(testTime);
            user.setIsDeleted(0);

            int rows = targetOrm.insert(user);
            Assert.assertEquals(1, rows);
            Assert.assertNotNull(user.getId());

            // 查出刚插入的用户
            MatrixUser loaded = targetOrm.select(MatrixUser.class)
                    .where().eq(MatrixUser::getId, user.getId())
                    .firstOrNull();
            Assert.assertNotNull(loaded);
            Assert.assertEquals("eve_mode_test", loaded.getUsername());
            Assert.assertEquals(Integer.valueOf(22), loaded.getAge());
            Assert.assertEquals(new BigDecimal("91.00"), loaded.getScore());

            // 模式 2: orm.sql() 模式
            MatrixUser sqlUser = new MatrixUser();
            sqlUser.setUsername("eve_sql_test");
            sqlUser.setEmail("eve_sql@example.com");
            sqlUser.setAge(23);
            sqlUser.setStatus(1);
            sqlUser.setScore(new BigDecimal("92.00"));
            sqlUser.setCreateTime(testTime);
            sqlUser.setIsDeleted(0);
            int sqlRows = targetOrm.sql(e -> {
                e.createInsert(sqlUser);
            }).execute();
            Assert.assertEquals(1, sqlRows);
            Assert.assertNotNull(sqlUser.getId());
        });
    }

    /**
     * 8.2 批量实体插入 (Batch Insert)
     * 场景：批量插入两条订单明细记录并校验真实入库条数
     */
    @Test
    public void test8_2_BatchInsert() {
        verifyAllModes(targetOrm -> {
            LocalDateTime now = LocalDateTime.now();
            MatrixOrderItem itemA = new MatrixOrderItem();
            itemA.setOrderId(1L);
            itemA.setProdName("BatchItemA");
            itemA.setPrice(new BigDecimal("15.50"));
            itemA.setQuantity(2);

            MatrixOrderItem itemB = new MatrixOrderItem();
            itemB.setOrderId(1L);
            itemB.setProdName("BatchItemB");
            itemB.setPrice(new BigDecimal("25.00"));
            itemB.setQuantity(1);

            int total = targetOrm.insertBatch(Arrays.asList(itemA, itemB)).batchSize(10).execute();
            Assert.assertEquals(2, total);

            List<MatrixOrderItem> list = targetOrm.select(MatrixOrderItem.class)
                    .where().like(MatrixOrderItem::getProdName, "BatchItem%")
                    .orderByAsc(MatrixOrderItem::getId)
                    .list();
            Assert.assertEquals(2, list.size());
            Assert.assertEquals("BatchItemA", list.get(0).getProdName());
            Assert.assertEquals("BatchItemB", list.get(1).getProdName());

            // 模式 2: orm.sql() 模式
            MatrixOrderItem sqlItem = new MatrixOrderItem();
            sqlItem.setOrderId(1L);
            sqlItem.setProdName("BatchItemSql");
            sqlItem.setPrice(new BigDecimal("30.00"));
            sqlItem.setQuantity(3);
            int sqlRows = targetOrm.sql(e -> {
                e.createInsert(sqlItem);
            }).execute();
            Assert.assertEquals(1, sqlRows);
        });
    }

    /**
     * 8.3 冲突更新 (Upsert Default - 默认主键冲突)
     * 场景：已有主键记录冲突时自动更新非主键且非审计时间列
     */
    @Test
    public void test8_3_UpsertDefault() {
        LocalDateTime testTime = LocalDateTime.of(2026, 11, 20, 12, 0, 0);

        MatrixUser user = new MatrixUser();
        user.setId(1L);
        user.setUsername("alice_upsert");
        user.setEmail("alice_upsert@example.com");
        user.setAge(21);
        user.setStatus(1);
        user.setScore(new BigDecimal("99.99"));
        user.setCreateTime(testTime);
        user.setIsDeleted(0);

        assertMatrixSql(
                oOrm -> oOrm.insertOrUpdateable(user),
                // MySQL
                "INSERT INTO `matrix_user` (`id`, `username`, `email`, `age`, `status`, `score`, `create_time`, `is_deleted`) VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE `username` = VALUES(`username`), `email` = VALUES(`email`), `age` = VALUES(`age`), `status` = VALUES(`status`), `score` = VALUES(`score`), `is_deleted` = VALUES(`is_deleted`)",
                // PostgreSQL
                "INSERT INTO \"matrix_user\" (\"id\", \"username\", \"email\", \"age\", \"status\", \"score\", \"create_time\", \"is_deleted\") VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (\"id\") DO UPDATE SET \"username\" = EXCLUDED.\"username\", \"email\" = EXCLUDED.\"email\", \"age\" = EXCLUDED.\"age\", \"status\" = EXCLUDED.\"status\", \"score\" = EXCLUDED.\"score\", \"is_deleted\" = EXCLUDED.\"is_deleted\"",
                // Oracle
                "MERGE INTO \"MATRIX_USER\" t USING (SELECT ? AS c_0, ? AS c_1, ? AS c_2, ? AS c_3, ? AS c_4, ? AS c_5, ? AS c_6, ? AS c_7 FROM DUAL) s ON (t.\"ID\" = s.c_0) WHEN MATCHED THEN UPDATE SET t.\"USERNAME\" = s.c_1, t.\"EMAIL\" = s.c_2, t.\"AGE\" = s.c_3, t.\"STATUS\" = s.c_4, t.\"SCORE\" = s.c_5, t.\"IS_DELETED\" = s.c_7 WHEN NOT MATCHED THEN INSERT (\"ID\", \"USERNAME\", \"EMAIL\", \"AGE\", \"STATUS\", \"SCORE\", \"CREATE_TIME\", \"IS_DELETED\") VALUES (s.c_0, s.c_1, s.c_2, s.c_3, s.c_4, s.c_5, s.c_6, s.c_7)",
                // SQLite
                "INSERT INTO \"matrix_user\" (\"id\", \"username\", \"email\", \"age\", \"status\", \"score\", \"create_time\", \"is_deleted\") VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (\"id\") DO UPDATE SET \"username\" = EXCLUDED.\"username\", \"email\" = EXCLUDED.\"email\", \"age\" = EXCLUDED.\"age\", \"status\" = EXCLUDED.\"status\", \"score\" = EXCLUDED.\"score\", \"is_deleted\" = EXCLUDED.\"is_deleted\"",
                // H2
                "MERGE INTO \"matrix_user\" (\"id\", \"username\", \"email\", \"age\", \"status\", \"score\", \"create_time\", \"is_deleted\") KEY (\"id\") VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                1L, "alice_upsert", "alice_upsert@example.com", 21, 1, new BigDecimal("99.99"), testTime, 0
        );

        // 真实物理数据库执行闭环：对已有 id=1 (alice) 执行 upsert 更新其 username 和 score
        verifyAllModes(targetOrm -> {
            MatrixUser upsertUser = new MatrixUser();
            upsertUser.setId(1L);
            upsertUser.setUsername("alice_upserted");
            upsertUser.setEmail("alice_new@example.com");
            upsertUser.setAge(21);
            upsertUser.setStatus(1);
            upsertUser.setScore(new BigDecimal("99.99"));
            upsertUser.setCreateTime(testTime);
            upsertUser.setIsDeleted(0);

            int affected = targetOrm.insertOrUpdate(upsertUser);
            Assert.assertTrue(affected >= 1);

            MatrixUser loaded = targetOrm.select(MatrixUser.class)
                    .where().eq(MatrixUser::getId, 1L)
                    .firstOrNull();
            Assert.assertNotNull(loaded);
            Assert.assertEquals("alice_upserted", loaded.getUsername());
            Assert.assertEquals(new BigDecimal("99.99"), loaded.getScore());

            // 模式 2: orm.sql() 模式
            upsertUser.setScore(new BigDecimal("100.00"));
            int sqlAff = targetOrm.sql(e -> {
                e.createUpsert(upsertUser);
            }).execute();
            Assert.assertTrue(sqlAff >= 1);
            MatrixUser sqlLoaded = targetOrm.select(MatrixUser.class)
                    .where().eq(MatrixUser::getId, 1L)
                    .firstOrNull();
            Assert.assertNotNull(sqlLoaded);
            Assert.assertEquals(new BigDecimal("100.00"), sqlLoaded.getScore());
        });
    }

    /**
     * 8.4 冲突更新：冲突时忽略 (Upsert DO NOTHING)
     * 场景：主键冲突时不执行任何更新操作
     */
    @Test
    public void test8_4_UpsertDoNothing() {
        LocalDateTime testTime = LocalDateTime.of(2026, 11, 20, 12, 0, 0);

        MatrixUser user = new MatrixUser();
        user.setId(2L);
        user.setUsername("bob_conflict_ignore");
        user.setEmail("bob_conflict@example.com");
        user.setAge(99);
        user.setStatus(1);
        user.setScore(new BigDecimal("10.00"));
        user.setCreateTime(testTime);
        user.setIsDeleted(0);

        List<Object> standardParams = Arrays.asList(2L, "bob_conflict_ignore", "bob_conflict@example.com", 99, 1, new BigDecimal("10.00"), testTime, 0);

        assertMatrixSqlWithDialectParams(
                oOrm -> oOrm.insertOrUpdateable(user).doNothing(),
                // MySQL (MySQL 通过 id = id 模拟 do nothing)
                "INSERT INTO `matrix_user` (`id`, `username`, `email`, `age`, `status`, `score`, `create_time`, `is_deleted`) VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE `id` = `id`",
                standardParams,
                // PostgreSQL
                "INSERT INTO \"matrix_user\" (\"id\", \"username\", \"email\", \"age\", \"status\", \"score\", \"create_time\", \"is_deleted\") VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (\"id\") DO NOTHING",
                standardParams,
                // Oracle
                "MERGE INTO \"MATRIX_USER\" t USING (SELECT ? AS c_0, ? AS c_1, ? AS c_2, ? AS c_3, ? AS c_4, ? AS c_5, ? AS c_6, ? AS c_7 FROM DUAL) s ON (t.\"ID\" = s.c_0) WHEN NOT MATCHED THEN INSERT (\"ID\", \"USERNAME\", \"EMAIL\", \"AGE\", \"STATUS\", \"SCORE\", \"CREATE_TIME\", \"IS_DELETED\") VALUES (s.c_0, s.c_1, s.c_2, s.c_3, s.c_4, s.c_5, s.c_6, s.c_7)",
                standardParams,
                // SQLite
                "INSERT INTO \"matrix_user\" (\"id\", \"username\", \"email\", \"age\", \"status\", \"score\", \"create_time\", \"is_deleted\") VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (\"id\") DO NOTHING",
                standardParams,
                // H2 (H2 doNothing 仅 MERGE 主键列，避免更新其它列)
                "MERGE INTO \"matrix_user\" (\"id\") KEY (\"id\") VALUES (?)",
                Collections.singletonList(2L)
        );

        // 真实物理数据库执行闭环：id=2(bob, 25岁) 存在，执行 doNothing 后 Bob 数据不变
        verifyAllModes(targetOrm -> {
            MatrixUser ignoreUser = new MatrixUser();
            ignoreUser.setId(2L);
            ignoreUser.setUsername("bob_conflict_ignore");
            ignoreUser.setEmail("bob_conflict@example.com");
            ignoreUser.setAge(99);
            ignoreUser.setStatus(1);
            ignoreUser.setScore(new BigDecimal("10.00"));
            ignoreUser.setCreateTime(testTime);
            ignoreUser.setIsDeleted(0);

            targetOrm.insertOrUpdateable(ignoreUser).doNothing().execute();

            MatrixUser loaded = targetOrm.select(MatrixUser.class)
                    .where().eq(MatrixUser::getId, 2L)
                    .firstOrNull();
            Assert.assertNotNull(loaded);
            Assert.assertEquals("bob", loaded.getUsername());
            Assert.assertEquals(Integer.valueOf(25), loaded.getAge());

            // 模式 2: orm.sql() 模式
            int sqlDoNothingAff = targetOrm.sql(e -> {
                e.createUpsert(ignoreUser).doNothing();
            }).execute();
            Assert.assertTrue(sqlDoNothingAff >= 0);
        });
    }

    /**
     * 8.5 冲突更新：指定更新列 (updateColumns)
     * 场景：冲突时只更新 score 字段，其他字段不更新
     */
    @Test
    public void test8_5_UpsertSpecifiedUpdateColumns() {
        LocalDateTime testTime = LocalDateTime.of(2026, 11, 20, 12, 0, 0);

        MatrixUser user = new MatrixUser();
        user.setId(3L);
        user.setUsername("charlie_should_not_change");
        user.setEmail("charlie_new@example.com");
        user.setAge(31);
        user.setStatus(1);
        user.setScore(new BigDecimal("100.00"));
        user.setCreateTime(testTime);
        user.setIsDeleted(0);

        assertMatrixSql(
                oOrm -> oOrm.insertOrUpdateable(user).updateColumns(MatrixUser::getScore),
                // MySQL
                "INSERT INTO `matrix_user` (`id`, `username`, `email`, `age`, `status`, `score`, `create_time`, `is_deleted`) VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE `score` = VALUES(`score`)",
                // PostgreSQL
                "INSERT INTO \"matrix_user\" (\"id\", \"username\", \"email\", \"age\", \"status\", \"score\", \"create_time\", \"is_deleted\") VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (\"id\") DO UPDATE SET \"score\" = EXCLUDED.\"score\"",
                // Oracle
                "MERGE INTO \"MATRIX_USER\" t USING (SELECT ? AS c_0, ? AS c_1, ? AS c_2, ? AS c_3, ? AS c_4, ? AS c_5, ? AS c_6, ? AS c_7 FROM DUAL) s ON (t.\"ID\" = s.c_0) WHEN MATCHED THEN UPDATE SET t.\"SCORE\" = s.c_5 WHEN NOT MATCHED THEN INSERT (\"ID\", \"USERNAME\", \"EMAIL\", \"AGE\", \"STATUS\", \"SCORE\", \"CREATE_TIME\", \"IS_DELETED\") VALUES (s.c_0, s.c_1, s.c_2, s.c_3, s.c_4, s.c_5, s.c_6, s.c_7)",
                // SQLite
                "INSERT INTO \"matrix_user\" (\"id\", \"username\", \"email\", \"age\", \"status\", \"score\", \"create_time\", \"is_deleted\") VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (\"id\") DO UPDATE SET \"score\" = EXCLUDED.\"score\"",
                // H2
                "MERGE INTO \"matrix_user\" (\"id\", \"username\", \"email\", \"age\", \"status\", \"score\", \"create_time\", \"is_deleted\") KEY (\"id\") VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                3L, "charlie_should_not_change", "charlie_new@example.com", 31, 1, new BigDecimal("100.00"), testTime, 0
        );

        // 真实物理数据库执行闭环：id=3(charlie) 冲突，只更新 score 为 100.00，username 仍为 charlie
        verifyAllModes(targetOrm -> {
            MatrixUser partialUser = new MatrixUser();
            partialUser.setId(3L);
            partialUser.setUsername("charlie_should_not_change");
            partialUser.setEmail("charlie_new@example.com");
            partialUser.setAge(31);
            partialUser.setStatus(1);
            partialUser.setScore(new BigDecimal("100.00"));
            partialUser.setCreateTime(testTime);
            partialUser.setIsDeleted(0);

            targetOrm.insertOrUpdateable(partialUser).updateColumns(MatrixUser::getScore).execute();

            MatrixUser loaded = targetOrm.select(MatrixUser.class)
                    .where().eq(MatrixUser::getId, 3L)
                    .firstOrNull();
            Assert.assertNotNull(loaded);
            Assert.assertEquals("charlie", loaded.getUsername());
            Assert.assertEquals(new BigDecimal("100.00"), loaded.getScore());

            // 模式 2: orm.sql() 模式
            int sqlPartAff = targetOrm.sql(e -> {
                e.createUpsert(partialUser).updateColumns(MatrixUser::getScore);
            }).execute();
            Assert.assertTrue(sqlPartAff >= 0);
        });
    }
}
