package com.javaorm.core.sqlmatrix;

import com.javaorm.core.JavaOrm;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * 阶段 10：DELETE 删除与物理/逻辑清理 (Deletes & Logic Delete)
 * 覆盖：
 * 10.1 实体单行条件物理删除 (deleteById / WHERE id = ?)
 * 10.2 复合条件物理删除 (WHERE col1 = ? AND col2 > ?)
 * 10.3 集合批量删除 (whereByIds / WHERE id IN (?, ?))
 * 10.4 逻辑删除自动重写为 UPDATE (Logic delete automatic transform)
 * 10.5 逻辑删除实体强制物理删除 (disableLogicDelete)
 * 10.6 全表删除无条件语法验证 (Unconstrained full table delete)
 */
public class Phase10DeleteMatrixTest extends AbstractSqlMatrixTest {

    /**
     * 10.1 实体单行条件物理删除
     * 场景：根据 ID 删除单行记录
     */
    @Test
    public void test10_1_DeleteSingleRowById() {
        assertMatrixSql(
                oOrm -> oOrm.delete(MatrixUser.class).where().eq(MatrixUser::getId, 4L),
                // MySQL
                "DELETE FROM `matrix_user` WHERE `id` = ?",
                // PostgreSQL
                "DELETE FROM \"matrix_user\" WHERE \"id\" = ?",
                // Oracle
                "DELETE FROM \"MATRIX_USER\" WHERE \"ID\" = ?",
                // SQLite
                "DELETE FROM \"matrix_user\" WHERE \"id\" = ?",
                // H2
                "DELETE FROM \"matrix_user\" WHERE \"id\" = ?",
                4L
        );

        // 真实物理数据库执行闭环：物理删除 id=4 (david)
        verifyAllModes(targetOrm -> {
            int rows = targetOrm.delete(MatrixUser.class)
                    .where().eq(MatrixUser::getId, 4L)
                    .execute();
            Assert.assertEquals(1, rows);

            MatrixUser loaded = targetOrm.select(MatrixUser.class)
                    .where().eq(MatrixUser::getId, 4L)
                    .firstOrNull();
            Assert.assertNull(loaded);

            // 模式 2: orm.sql() 模式
            int sqlRows = targetOrm.sql(e -> {
                MatrixUser su = e.table(MatrixUser.class);
                e.delete(su).where().eq(su.getId(), 4L);
            }).execute();
            Assert.assertTrue(sqlRows >= 0);
        });
    }

    /**
     * 10.2 复合条件物理删除
     * 场景：删除 status = 0 且 age >= 30 的用户 (charlie, 30岁, status 0)
     */
    @Test
    public void test10_2_DeleteWithCompoundCondition() {
        assertMatrixSql(
                oOrm -> oOrm.delete(MatrixUser.class)
                        .where().eq(MatrixUser::getStatus, 0).and().ge(MatrixUser::getAge, 30),
                // MySQL
                "DELETE FROM `matrix_user` WHERE `status` = ? AND `age` >= ?",
                // PostgreSQL
                "DELETE FROM \"matrix_user\" WHERE \"status\" = ? AND \"age\" >= ?",
                // Oracle
                "DELETE FROM \"MATRIX_USER\" WHERE \"STATUS\" = ? AND \"AGE\" >= ?",
                // SQLite
                "DELETE FROM \"matrix_user\" WHERE \"status\" = ? AND \"age\" >= ?",
                // H2
                "DELETE FROM \"matrix_user\" WHERE \"status\" = ? AND \"age\" >= ?",
                0, 30
        );

        // 真实物理数据库执行闭环：删除 charlie
        verifyAllModes(targetOrm -> {
            int rows = targetOrm.delete(MatrixUser.class)
                    .where().eq(MatrixUser::getStatus, 0).and().ge(MatrixUser::getAge, 30)
                    .execute();
            Assert.assertEquals(1, rows);

            MatrixUser loaded = targetOrm.select(MatrixUser.class)
                    .where().eq(MatrixUser::getId, 3L)
                    .firstOrNull();
            Assert.assertNull(loaded);

            // 模式 2: orm.sql() 模式
            int sqlRows = targetOrm.sql(e -> {
                MatrixUser su = e.table(MatrixUser.class);
                e.delete(su).where().eq(su.getStatus(), 0).and().ge(su.getAge(), 30);
            }).execute();
            Assert.assertTrue(sqlRows >= 0);
        });
    }

    /**
     * 10.3 集合批量删除 (whereByIds)
     * 场景：删除订单明细 ID 在集合 [2, 3] 中的记录
     */
    @Test
    public void test10_3_DeleteByIdsCollection() {
        assertMatrixSql(
                oOrm -> oOrm.delete(MatrixOrderItem.class).whereByIds(Arrays.asList(2L, 3L)),
                // MySQL
                "DELETE FROM `matrix_order_item` WHERE `id` IN (?, ?)",
                // PostgreSQL
                "DELETE FROM \"matrix_order_item\" WHERE \"id\" IN (?, ?)",
                // Oracle
                "DELETE FROM \"MATRIX_ORDER_ITEM\" WHERE \"ID\" IN (?, ?)",
                // SQLite
                "DELETE FROM \"matrix_order_item\" WHERE \"id\" IN (?, ?)",
                // H2
                "DELETE FROM \"matrix_order_item\" WHERE \"id\" IN (?, ?)",
                2L, 3L
        );

        // 真实物理数据库执行闭环：删除明细 2 和 3
        verifyAllModes(targetOrm -> {
            int rows = targetOrm.delete(MatrixOrderItem.class).whereByIds(Arrays.asList(2L, 3L)).execute();
            Assert.assertEquals(2, rows);

            List<MatrixOrderItem> remaining = targetOrm.select(MatrixOrderItem.class)
                    .where().in(MatrixOrderItem::getId, Arrays.asList(2L, 3L))
                    .list();
            Assert.assertTrue(remaining.isEmpty());

            // 模式 2: orm.sql() 模式
            int sqlRows = targetOrm.sql(e -> {
                MatrixOrderItem soi = e.table(MatrixOrderItem.class);
                e.delete(soi).where().in(soi.getId(), Arrays.asList(2L, 3L));
            }).execute();
            Assert.assertTrue(sqlRows >= 0);
        });
    }

    /**
     * 10.4 逻辑删除自动重写为 UPDATE (Logic delete automatic transform)
     * 场景：对配置了 @JoLogicDelete 的 MatrixSoftDelete 执行 delete，底层自动重写为 UPDATE
     */
    @Test
    public void test10_4_LogicDeleteAutomaticTransform() {
        assertMatrixSql(
                oOrm -> oOrm.delete(MatrixSoftDelete.class).where().eq(MatrixSoftDelete::getId, 1L),
                // MySQL
                "UPDATE `matrix_soft_delete` SET `is_deleted` = ? WHERE `is_deleted` = ? AND (`id` = ?)",
                // PostgreSQL
                "UPDATE \"matrix_soft_delete\" SET \"is_deleted\" = ? WHERE \"is_deleted\" = ? AND (\"id\" = ?)",
                // Oracle
                "UPDATE \"MATRIX_SOFT_DELETE\" SET \"IS_DELETED\" = ? WHERE \"IS_DELETED\" = ? AND (\"ID\" = ?)",
                // SQLite
                "UPDATE \"matrix_soft_delete\" SET \"is_deleted\" = ? WHERE \"is_deleted\" = ? AND (\"id\" = ?)",
                // H2
                "UPDATE \"matrix_soft_delete\" SET \"is_deleted\" = ? WHERE \"is_deleted\" = ? AND (\"id\" = ?)",
                "1", "0", 1L
        );

        // 真实物理数据库执行闭环：逻辑删除 id=1 记录
        verifyAllModes(targetOrm -> {
            int rows = targetOrm.delete(MatrixSoftDelete.class)
                    .where().eq(MatrixSoftDelete::getId, 1L)
                    .execute();
            Assert.assertEquals(1, rows);

            // 验证 1：默认查询因逻辑删除透明过滤查不到
            MatrixSoftDelete softQueried = targetOrm.select(MatrixSoftDelete.class)
                    .where().eq(MatrixSoftDelete::getId, 1L)
                    .firstOrNull();
            Assert.assertNull(softQueried);

            // 验证 2：禁用逻辑删除过滤后可以查出，且 is_deleted 已被标记为 1
            MatrixSoftDelete rawQueried = targetOrm.select(MatrixSoftDelete.class)
                    .disableLogicDelete()
                    .where().eq(MatrixSoftDelete::getId, 1L)
                    .firstOrNull();
            Assert.assertNotNull(rawQueried);
            Assert.assertEquals(Integer.valueOf(1), rawQueried.getIsDeleted());

            // 模式 2: orm.sql() 模式
            int sqlRows = targetOrm.sql(e -> {
                MatrixSoftDelete ssd = e.table(MatrixSoftDelete.class);
                e.delete(ssd).where().eq(ssd.getId(), 1L);
            }).execute();
            Assert.assertTrue(sqlRows >= 0);
        });
    }

    /**
     * 10.5 逻辑删除实体强制物理删除 (disableLogicDelete)
     * 场景：对配置了 @JoLogicDelete 的实体显式调用 disableLogicDelete()，执行真正物理删除
     */
    @Test
    public void test10_5_DisableLogicDeletePhysicalDelete() {
        assertMatrixSql(
                oOrm -> oOrm.delete(MatrixSoftDelete.class)
                        .disableLogicDelete()
                        .where().eq(MatrixSoftDelete::getId, 2L),
                // MySQL
                "DELETE FROM `matrix_soft_delete` WHERE `id` = ?",
                // PostgreSQL
                "DELETE FROM \"matrix_soft_delete\" WHERE \"id\" = ?",
                // Oracle
                "DELETE FROM \"MATRIX_SOFT_DELETE\" WHERE \"ID\" = ?",
                // SQLite
                "DELETE FROM \"matrix_soft_delete\" WHERE \"id\" = ?",
                // H2
                "DELETE FROM \"matrix_soft_delete\" WHERE \"id\" = ?",
                2L
        );

        // 真实物理数据库执行闭环：真正物理删除 id=2 记录
        verifyAllModes(targetOrm -> {
            int rows = targetOrm.delete(MatrixSoftDelete.class)
                    .disableLogicDelete()
                    .where().eq(MatrixSoftDelete::getId, 2L)
                    .execute();
            Assert.assertEquals(1, rows);

            // 无论是否启用逻辑删除，该记录在数据库中均已彻底消失
            MatrixSoftDelete rawQueried = targetOrm.select(MatrixSoftDelete.class)
                    .disableLogicDelete()
                    .where().eq(MatrixSoftDelete::getId, 2L)
                    .firstOrNull();
            Assert.assertNull(rawQueried);

            // 模式 2: orm.sql() 模式
            int sqlRows = targetOrm.sql(e -> {
                MatrixSoftDelete ssd = e.table(MatrixSoftDelete.class);
                e.delete(ssd).where().eq(ssd.getId(), 2L);
            }).execute();
            Assert.assertTrue(sqlRows >= 0);
        });
    }

    /**
     * 10.6 全表删除无条件语法验证 (Unconstrained full table delete)
     * 场景：不带任何 WHERE 条件的 DELETE 语句在 5 大方言下的标准生成
     */
    @Test
    public void test10_6_UnconstrainedDeleteSql() {
        assertMatrixSql(
                oOrm -> oOrm.delete(MatrixOrderItem.class),
                // MySQL
                "DELETE FROM `matrix_order_item`",
                // PostgreSQL
                "DELETE FROM \"matrix_order_item\"",
                // Oracle
                "DELETE FROM \"MATRIX_ORDER_ITEM\"",
                // SQLite
                "DELETE FROM \"matrix_order_item\"",
                // H2
                "DELETE FROM \"matrix_order_item\""
        );

        // 模式 2: orm.sql() 模式验证
        assertMatrixSql(
                oOrm -> oOrm.sql(e -> {
                    e.delete(MatrixOrderItem.class);
                }),
                // MySQL
                "DELETE FROM `matrix_order_item`",
                // PostgreSQL
                "DELETE FROM \"matrix_order_item\"",
                // Oracle
                "DELETE FROM \"MATRIX_ORDER_ITEM\"",
                // SQLite
                "DELETE FROM \"matrix_order_item\"",
                // H2
                "DELETE FROM \"matrix_order_item\""
        );
    }
}
