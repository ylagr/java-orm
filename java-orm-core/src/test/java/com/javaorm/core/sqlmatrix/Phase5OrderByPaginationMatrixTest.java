package com.javaorm.core.sqlmatrix;

import com.javaorm.core.dialect.SqlAndParams;
import com.javaorm.core.pagination.PageResult;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.javaorm.core.SQL.count;

/**
 * 阶段五：ORDER BY, LIMIT / OFFSET 与各方言物理分页矩阵单测 (Phase 5)。
 *
 * 核心验证矩阵：
 * 1. 单列基础排序 (Single-Column ORDER BY - ASC / DESC)；
 * 2. 多列复合排序 (Multi-Column ORDER BY - 多字段组合)；
 * 3. 聚合列 / 表达式排序 (Aggregate Expression ORDER BY)；
 * 4. 边界分页：仅 Top N (Limit Only)；
 * 5. 偏移分页：LIMIT + OFFSET；
 * 6. 快捷分页方法：page(pageNum, pageSize)；
 * 7. 实体分页结果封装：toPage(pageNum, pageSize) 与 PageResult<T> 元数据校验；
 * 8. 复合复杂查询：JOIN + WHERE + ORDER BY + 分页综合测试；
 *
 * 校验闭环：
 * 1. 5 大数据库方言 (MySQL, PostgreSQL, Oracle 12c+, SQLite, H2) 100% 全文本精确断言；
 * 2. 真实 JDBC 数据库执行闭环校验（实体顺序、条数、字段值强类型断言）。
 */
public class Phase5OrderByPaginationMatrixTest extends AbstractSqlMatrixTest {

    public static class StatusCountDto {
        private Integer status;
        private Long cnt;

        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
        public Long getCnt() { return cnt; }
        public void setCnt(Long cnt) { this.cnt = cnt; }
    }

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
     * 5.1 单列基础排序 (ASC / DESC)
     */
    @Test
    public void test5_1_SingleColumnOrderBy() {
        // 5.1.1 升序 (ASC)
        assertMatrixSql(
                () -> orm.select(MatrixUser.class).orderByAsc(MatrixUser::getAge),
                // MySQL
                "SELECT t0.`id`, t0.`username`, t0.`email`, t0.`age`, t0.`status`, t0.`score`, t0.`create_time`, t0.`is_deleted` " +
                        "FROM `matrix_user` t0 " +
                        "ORDER BY t0.`age` ASC",
                // PostgreSQL
                "SELECT t0.\"id\", t0.\"username\", t0.\"email\", t0.\"age\", t0.\"status\", t0.\"score\", t0.\"create_time\", t0.\"is_deleted\" " +
                        "FROM \"matrix_user\" t0 " +
                        "ORDER BY t0.\"age\" ASC",
                // Oracle
                "SELECT t0.\"id\", t0.\"username\", t0.\"email\", t0.\"age\", t0.\"status\", t0.\"score\", t0.\"create_time\", t0.\"is_deleted\" " +
                        "FROM \"matrix_user\" t0 " +
                        "ORDER BY t0.\"age\" ASC",
                // SQLite
                "SELECT t0.\"id\", t0.\"username\", t0.\"email\", t0.\"age\", t0.\"status\", t0.\"score\", t0.\"create_time\", t0.\"is_deleted\" " +
                        "FROM \"matrix_user\" t0 " +
                        "ORDER BY t0.\"age\" ASC",
                // H2
                "SELECT t0.\"id\", t0.\"username\", t0.\"email\", t0.\"age\", t0.\"status\", t0.\"score\", t0.\"create_time\", t0.\"is_deleted\" " +
                        "FROM \"matrix_user\" t0 " +
                        "ORDER BY t0.\"age\" ASC"
        );

        List<MatrixUser> ascList = orm.select(MatrixUser.class).orderByAsc(MatrixUser::getAge).list();
        Assert.assertEquals(4, ascList.size());
        Assert.assertEquals(Integer.valueOf(20), ascList.get(0).getAge());
        Assert.assertEquals(Integer.valueOf(20), ascList.get(1).getAge());
        Assert.assertEquals(Integer.valueOf(25), ascList.get(2).getAge());
        Assert.assertEquals(Integer.valueOf(30), ascList.get(3).getAge());

        // 5.1.2 降序 (DESC)
        assertMatrixSql(
                () -> orm.select(MatrixUser.class).orderByDesc(MatrixUser::getAge),
                // MySQL
                "SELECT t0.`id`, t0.`username`, t0.`email`, t0.`age`, t0.`status`, t0.`score`, t0.`create_time`, t0.`is_deleted` " +
                        "FROM `matrix_user` t0 " +
                        "ORDER BY t0.`age` DESC",
                // PostgreSQL
                "SELECT t0.\"id\", t0.\"username\", t0.\"email\", t0.\"age\", t0.\"status\", t0.\"score\", t0.\"create_time\", t0.\"is_deleted\" " +
                        "FROM \"matrix_user\" t0 " +
                        "ORDER BY t0.\"age\" DESC",
                // Oracle
                "SELECT t0.\"id\", t0.\"username\", t0.\"email\", t0.\"age\", t0.\"status\", t0.\"score\", t0.\"create_time\", t0.\"is_deleted\" " +
                        "FROM \"matrix_user\" t0 " +
                        "ORDER BY t0.\"age\" DESC",
                // SQLite
                "SELECT t0.\"id\", t0.\"username\", t0.\"email\", t0.\"age\", t0.\"status\", t0.\"score\", t0.\"create_time\", t0.\"is_deleted\" " +
                        "FROM \"matrix_user\" t0 " +
                        "ORDER BY t0.\"age\" DESC",
                // H2
                "SELECT t0.\"id\", t0.\"username\", t0.\"email\", t0.\"age\", t0.\"status\", t0.\"score\", t0.\"create_time\", t0.\"is_deleted\" " +
                        "FROM \"matrix_user\" t0 " +
                        "ORDER BY t0.\"age\" DESC"
        );

        List<MatrixUser> descList = orm.select(MatrixUser.class).orderByDesc(MatrixUser::getAge).list();
        Assert.assertEquals(4, descList.size());
        Assert.assertEquals(Integer.valueOf(30), descList.get(0).getAge());
        Assert.assertEquals(Integer.valueOf(25), descList.get(1).getAge());
        Assert.assertEquals(Integer.valueOf(20), descList.get(2).getAge());
        Assert.assertEquals(Integer.valueOf(20), descList.get(3).getAge());
    }

    /**
     * 5.2 多列复合排序 (status DESC, age ASC, id ASC)
     */
    @Test
    public void test5_2_MultiColumnOrderBy() {
        assertMatrixSql(
                () -> orm.select(MatrixUser.class)
                        .orderByDesc(MatrixUser::getStatus)
                        .orderByAsc(MatrixUser::getAge)
                        .orderByAsc(MatrixUser::getId),
                // MySQL
                "SELECT t0.`id`, t0.`username`, t0.`email`, t0.`age`, t0.`status`, t0.`score`, t0.`create_time`, t0.`is_deleted` " +
                        "FROM `matrix_user` t0 " +
                        "ORDER BY t0.`status` DESC, t0.`age` ASC, t0.`id` ASC",
                // PostgreSQL
                "SELECT t0.\"id\", t0.\"username\", t0.\"email\", t0.\"age\", t0.\"status\", t0.\"score\", t0.\"create_time\", t0.\"is_deleted\" " +
                        "FROM \"matrix_user\" t0 " +
                        "ORDER BY t0.\"status\" DESC, t0.\"age\" ASC, t0.\"id\" ASC",
                // Oracle
                "SELECT t0.\"id\", t0.\"username\", t0.\"email\", t0.\"age\", t0.\"status\", t0.\"score\", t0.\"create_time\", t0.\"is_deleted\" " +
                        "FROM \"matrix_user\" t0 " +
                        "ORDER BY t0.\"status\" DESC, t0.\"age\" ASC, t0.\"id\" ASC",
                // SQLite
                "SELECT t0.\"id\", t0.\"username\", t0.\"email\", t0.\"age\", t0.\"status\", t0.\"score\", t0.\"create_time\", t0.\"is_deleted\" " +
                        "FROM \"matrix_user\" t0 " +
                        "ORDER BY t0.\"status\" DESC, t0.\"age\" ASC, t0.\"id\" ASC",
                // H2
                "SELECT t0.\"id\", t0.\"username\", t0.\"email\", t0.\"age\", t0.\"status\", t0.\"score\", t0.\"create_time\", t0.\"is_deleted\" " +
                        "FROM \"matrix_user\" t0 " +
                        "ORDER BY t0.\"status\" DESC, t0.\"age\" ASC, t0.\"id\" ASC"
        );

        List<MatrixUser> list = orm.select(MatrixUser.class)
                .orderByDesc(MatrixUser::getStatus)
                .orderByAsc(MatrixUser::getAge)
                .orderByAsc(MatrixUser::getId)
                .list();

        Assert.assertEquals(4, list.size());
        // status = 1 (alice: 20, david: 20, bob: 25)
        Assert.assertEquals("alice", list.get(0).getUsername());
        Assert.assertEquals("david", list.get(1).getUsername());
        Assert.assertEquals("bob", list.get(2).getUsername());
        // status = 0 (charlie: 30)
        Assert.assertEquals("charlie", list.get(3).getUsername());
    }

    /**
     * 5.3 聚合列 / 表达式排序 (ORDER BY COUNT(...) DESC, status ASC)
     */
    @Test
    public void test5_3_AggregateOrderBy() {
        assertMatrixSql(
                () -> orm.sql(e -> {
                    MatrixUser u = e.table(MatrixUser.class, "u");
                    e.select(u.getStatus(), count(u.getId()).as("cnt"))
                            .from(u)
                            .groupBy(u.getStatus())
                            .orderByDesc(count(u.getId()))
                            .orderByAsc(u.getStatus());
                }),
                // MySQL
                "SELECT `u`.`status`, COUNT(`u`.`id`) AS `cnt` " +
                        "FROM `matrix_user` `u` " +
                        "GROUP BY `u`.`status` " +
                        "ORDER BY COUNT(`u`.`id`) DESC, `u`.`status` ASC",
                // PostgreSQL
                "SELECT \"u\".\"status\", COUNT(\"u\".\"id\") AS \"cnt\" " +
                        "FROM \"matrix_user\" \"u\" " +
                        "GROUP BY \"u\".\"status\" " +
                        "ORDER BY COUNT(\"u\".\"id\") DESC, \"u\".\"status\" ASC",
                // Oracle
                "SELECT \"u\".\"status\", COUNT(\"u\".\"id\") AS \"cnt\" " +
                        "FROM \"matrix_user\" \"u\" " +
                        "GROUP BY \"u\".\"status\" " +
                        "ORDER BY COUNT(\"u\".\"id\") DESC, \"u\".\"status\" ASC",
                // SQLite
                "SELECT \"u\".\"status\", COUNT(\"u\".\"id\") AS \"cnt\" " +
                        "FROM \"matrix_user\" \"u\" " +
                        "GROUP BY \"u\".\"status\" " +
                        "ORDER BY COUNT(\"u\".\"id\") DESC, \"u\".\"status\" ASC",
                // H2
                "SELECT \"u\".\"status\", COUNT(\"u\".\"id\") AS \"cnt\" " +
                        "FROM \"matrix_user\" \"u\" " +
                        "GROUP BY \"u\".\"status\" " +
                        "ORDER BY COUNT(\"u\".\"id\") DESC, \"u\".\"status\" ASC"
        );

        List<StatusCountDto> list = orm.sql(e -> {
            MatrixUser u = e.table(MatrixUser.class, "u");
            e.select(u.getStatus(), count(u.getId()).as("cnt"))
                    .from(u)
                    .groupBy(u.getStatus())
                    .orderByDesc(count(u.getId()))
                    .orderByAsc(u.getStatus());
        }).toList(StatusCountDto.class);

        Assert.assertEquals(2, list.size());
        Assert.assertEquals(Integer.valueOf(1), list.get(0).getStatus());
        Assert.assertEquals(Long.valueOf(3), list.get(0).getCnt());
        Assert.assertEquals(Integer.valueOf(0), list.get(1).getStatus());
        Assert.assertEquals(Long.valueOf(1), list.get(1).getCnt());
    }

    /**
     * 5.4 边界分页：仅 LIMIT (Top N)
     */
    @Test
    public void test5_4_LimitOnly_TopN() {
        assertMatrixSqlWithDialectParams(
                () -> orm.select(MatrixUser.class)
                        .orderByAsc(MatrixUser::getId)
                        .limit(2),
                // MySQL
                "SELECT t0.`id`, t0.`username`, t0.`email`, t0.`age`, t0.`status`, t0.`score`, t0.`create_time`, t0.`is_deleted` " +
                        "FROM `matrix_user` t0 " +
                        "ORDER BY t0.`id` ASC " +
                        "LIMIT ?",
                Arrays.asList(2),
                // PostgreSQL
                "SELECT t0.\"id\", t0.\"username\", t0.\"email\", t0.\"age\", t0.\"status\", t0.\"score\", t0.\"create_time\", t0.\"is_deleted\" " +
                        "FROM \"matrix_user\" t0 " +
                        "ORDER BY t0.\"id\" ASC " +
                        "LIMIT ?",
                Arrays.asList(2),
                // Oracle (12c+: OFFSET ? ROWS FETCH NEXT ? ROWS ONLY，offset 默认为 0，参数为 [0, 2])
                "SELECT t0.\"id\", t0.\"username\", t0.\"email\", t0.\"age\", t0.\"status\", t0.\"score\", t0.\"create_time\", t0.\"is_deleted\" " +
                        "FROM \"matrix_user\" t0 " +
                        "ORDER BY t0.\"id\" ASC " +
                        "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                Arrays.asList(0, 2),
                // SQLite
                "SELECT t0.\"id\", t0.\"username\", t0.\"email\", t0.\"age\", t0.\"status\", t0.\"score\", t0.\"create_time\", t0.\"is_deleted\" " +
                        "FROM \"matrix_user\" t0 " +
                        "ORDER BY t0.\"id\" ASC " +
                        "LIMIT ?",
                Arrays.asList(2),
                // H2
                "SELECT t0.\"id\", t0.\"username\", t0.\"email\", t0.\"age\", t0.\"status\", t0.\"score\", t0.\"create_time\", t0.\"is_deleted\" " +
                        "FROM \"matrix_user\" t0 " +
                        "ORDER BY t0.\"id\" ASC " +
                        "LIMIT ?",
                Arrays.asList(2)
        );

        List<MatrixUser> list = orm.select(MatrixUser.class)
                .orderByAsc(MatrixUser::getId)
                .limit(2)
                .list();

        Assert.assertEquals(2, list.size());
        Assert.assertEquals("alice", list.get(0).getUsername());
        Assert.assertEquals("bob", list.get(1).getUsername());
    }

    /**
     * 5.5 偏移分页：LIMIT + OFFSET
     */
    @Test
    public void test5_5_LimitAndOffset() {
        assertMatrixSqlWithDialectParams(
                () -> orm.select(MatrixUser.class)
                        .orderByAsc(MatrixUser::getId)
                        .limit(2)
                        .offset(1),
                // MySQL
                "SELECT t0.`id`, t0.`username`, t0.`email`, t0.`age`, t0.`status`, t0.`score`, t0.`create_time`, t0.`is_deleted` " +
                        "FROM `matrix_user` t0 " +
                        "ORDER BY t0.`id` ASC " +
                        "LIMIT ? OFFSET ?",
                Arrays.asList(2, 1),
                // PostgreSQL
                "SELECT t0.\"id\", t0.\"username\", t0.\"email\", t0.\"age\", t0.\"status\", t0.\"score\", t0.\"create_time\", t0.\"is_deleted\" " +
                        "FROM \"matrix_user\" t0 " +
                        "ORDER BY t0.\"id\" ASC " +
                        "LIMIT ? OFFSET ?",
                Arrays.asList(2, 1),
                // Oracle (参数为 [offset, limit] -> [1, 2])
                "SELECT t0.\"id\", t0.\"username\", t0.\"email\", t0.\"age\", t0.\"status\", t0.\"score\", t0.\"create_time\", t0.\"is_deleted\" " +
                        "FROM \"matrix_user\" t0 " +
                        "ORDER BY t0.\"id\" ASC " +
                        "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                Arrays.asList(1, 2),
                // SQLite
                "SELECT t0.\"id\", t0.\"username\", t0.\"email\", t0.\"age\", t0.\"status\", t0.\"score\", t0.\"create_time\", t0.\"is_deleted\" " +
                        "FROM \"matrix_user\" t0 " +
                        "ORDER BY t0.\"id\" ASC " +
                        "LIMIT ? OFFSET ?",
                Arrays.asList(2, 1),
                // H2
                "SELECT t0.\"id\", t0.\"username\", t0.\"email\", t0.\"age\", t0.\"status\", t0.\"score\", t0.\"create_time\", t0.\"is_deleted\" " +
                        "FROM \"matrix_user\" t0 " +
                        "ORDER BY t0.\"id\" ASC " +
                        "LIMIT ? OFFSET ?",
                Arrays.asList(2, 1)
        );

        List<MatrixUser> list = orm.select(MatrixUser.class)
                .orderByAsc(MatrixUser::getId)
                .limit(2)
                .offset(1)
                .list();

        Assert.assertEquals(2, list.size());
        Assert.assertEquals("bob", list.get(0).getUsername());
        Assert.assertEquals("charlie", list.get(1).getUsername());
    }

    /**
     * 5.6 快捷分页方法：page(pageNum, pageSize)
     */
    @Test
    public void test5_6_PageMethod() {
        // 5.6.1 第 1 页: page(1, 2) -> offset = 0, limit = 2
        assertMatrixSqlWithDialectParams(
                () -> orm.select(MatrixUser.class)
                        .orderByAsc(MatrixUser::getId)
                        .page(1, 2),
                // MySQL (offset = 0 时不生成 OFFSET 子句)
                "SELECT t0.`id`, t0.`username`, t0.`email`, t0.`age`, t0.`status`, t0.`score`, t0.`create_time`, t0.`is_deleted` " +
                        "FROM `matrix_user` t0 " +
                        "ORDER BY t0.`id` ASC " +
                        "LIMIT ?",
                Arrays.asList(2),
                // PostgreSQL
                "SELECT t0.\"id\", t0.\"username\", t0.\"email\", t0.\"age\", t0.\"status\", t0.\"score\", t0.\"create_time\", t0.\"is_deleted\" " +
                        "FROM \"matrix_user\" t0 " +
                        "ORDER BY t0.\"id\" ASC " +
                        "LIMIT ?",
                Arrays.asList(2),
                // Oracle
                "SELECT t0.\"id\", t0.\"username\", t0.\"email\", t0.\"age\", t0.\"status\", t0.\"score\", t0.\"create_time\", t0.\"is_deleted\" " +
                        "FROM \"matrix_user\" t0 " +
                        "ORDER BY t0.\"id\" ASC " +
                        "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                Arrays.asList(0, 2),
                // SQLite
                "SELECT t0.\"id\", t0.\"username\", t0.\"email\", t0.\"age\", t0.\"status\", t0.\"score\", t0.\"create_time\", t0.\"is_deleted\" " +
                        "FROM \"matrix_user\" t0 " +
                        "ORDER BY t0.\"id\" ASC " +
                        "LIMIT ?",
                Arrays.asList(2),
                // H2
                "SELECT t0.\"id\", t0.\"username\", t0.\"email\", t0.\"age\", t0.\"status\", t0.\"score\", t0.\"create_time\", t0.\"is_deleted\" " +
                        "FROM \"matrix_user\" t0 " +
                        "ORDER BY t0.\"id\" ASC " +
                        "LIMIT ?",
                Arrays.asList(2)
        );

        List<MatrixUser> page1 = orm.select(MatrixUser.class)
                .orderByAsc(MatrixUser::getId)
                .page(1, 2)
                .list();
        Assert.assertEquals(2, page1.size());
        Assert.assertEquals("alice", page1.get(0).getUsername());
        Assert.assertEquals("bob", page1.get(1).getUsername());

        // 5.6.2 第 2 页: page(2, 2) -> offset = 2, limit = 2
        assertMatrixSqlWithDialectParams(
                () -> orm.select(MatrixUser.class)
                        .orderByAsc(MatrixUser::getId)
                        .page(2, 2),
                // MySQL
                "SELECT t0.`id`, t0.`username`, t0.`email`, t0.`age`, t0.`status`, t0.`score`, t0.`create_time`, t0.`is_deleted` " +
                        "FROM `matrix_user` t0 " +
                        "ORDER BY t0.`id` ASC " +
                        "LIMIT ? OFFSET ?",
                Arrays.asList(2, 2),
                // PostgreSQL
                "SELECT t0.\"id\", t0.\"username\", t0.\"email\", t0.\"age\", t0.\"status\", t0.\"score\", t0.\"create_time\", t0.\"is_deleted\" " +
                        "FROM \"matrix_user\" t0 " +
                        "ORDER BY t0.\"id\" ASC " +
                        "LIMIT ? OFFSET ?",
                Arrays.asList(2, 2),
                // Oracle
                "SELECT t0.\"id\", t0.\"username\", t0.\"email\", t0.\"age\", t0.\"status\", t0.\"score\", t0.\"create_time\", t0.\"is_deleted\" " +
                        "FROM \"matrix_user\" t0 " +
                        "ORDER BY t0.\"id\" ASC " +
                        "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                Arrays.asList(2, 2),
                // SQLite
                "SELECT t0.\"id\", t0.\"username\", t0.\"email\", t0.\"age\", t0.\"status\", t0.\"score\", t0.\"create_time\", t0.\"is_deleted\" " +
                        "FROM \"matrix_user\" t0 " +
                        "ORDER BY t0.\"id\" ASC " +
                        "LIMIT ? OFFSET ?",
                Arrays.asList(2, 2),
                // H2
                "SELECT t0.\"id\", t0.\"username\", t0.\"email\", t0.\"age\", t0.\"status\", t0.\"score\", t0.\"create_time\", t0.\"is_deleted\" " +
                        "FROM \"matrix_user\" t0 " +
                        "ORDER BY t0.\"id\" ASC " +
                        "LIMIT ? OFFSET ?",
                Arrays.asList(2, 2)
        );

        List<MatrixUser> page2 = orm.select(MatrixUser.class)
                .orderByAsc(MatrixUser::getId)
                .page(2, 2)
                .list();
        Assert.assertEquals(2, page2.size());
        Assert.assertEquals("charlie", page2.get(0).getUsername());
        Assert.assertEquals("david", page2.get(1).getUsername());
    }

    /**
     * 5.7 实体分页结果封装：toPage(pageNum, pageSize) 与 PageResult<T> 元数据校验
     */
    @Test
    public void test5_7_ToPageResult_EntityPagination() {
        // 第 1 页分页
        PageResult<MatrixUser> pageResult1 = orm.select(MatrixUser.class)
                .orderByAsc(MatrixUser::getId)
                .toPage(1, 2);

        Assert.assertNotNull(pageResult1);
        Assert.assertEquals(4L, pageResult1.getTotal());
        Assert.assertEquals(1L, pageResult1.getPageNum());
        Assert.assertEquals(2L, pageResult1.getPageSize());
        Assert.assertEquals(2L, pageResult1.getTotalPages());
        Assert.assertTrue(pageResult1.hasNext());
        Assert.assertFalse(pageResult1.hasPrevious());
        Assert.assertEquals(2, pageResult1.getData().size());
        Assert.assertEquals("alice", pageResult1.getData().get(0).getUsername());
        Assert.assertEquals("bob", pageResult1.getData().get(1).getUsername());

        // 第 2 页分页
        PageResult<MatrixUser> pageResult2 = orm.select(MatrixUser.class)
                .orderByAsc(MatrixUser::getId)
                .toPage(2, 2);

        Assert.assertNotNull(pageResult2);
        Assert.assertEquals(4L, pageResult2.getTotal());
        Assert.assertEquals(2L, pageResult2.getPageNum());
        Assert.assertEquals(2L, pageResult2.getPageSize());
        Assert.assertEquals(2L, pageResult2.getTotalPages());
        Assert.assertFalse(pageResult2.hasNext());
        Assert.assertTrue(pageResult2.hasPrevious());
        Assert.assertEquals(2, pageResult2.getData().size());
        Assert.assertEquals("charlie", pageResult2.getData().get(0).getUsername());
        Assert.assertEquals("david", pageResult2.getData().get(1).getUsername());

        // 带条件的精确分页校验 (status = 1 共有 3 人：alice, bob, david)
        PageResult<MatrixUser> filteredPage = orm.select(MatrixUser.class)
                .where(w -> w.eq(MatrixUser::getStatus, 1))
                .orderByAsc(MatrixUser::getId)
                .toPage(2, 2);

        Assert.assertEquals(3L, filteredPage.getTotal());
        Assert.assertEquals(2L, filteredPage.getPageNum());
        Assert.assertEquals(2L, filteredPage.getPageSize());
        Assert.assertEquals(2L, filteredPage.getTotalPages());
        Assert.assertFalse(filteredPage.hasNext());
        Assert.assertTrue(filteredPage.hasPrevious());
        Assert.assertEquals(1, filteredPage.getData().size());
        Assert.assertEquals("david", filteredPage.getData().get(0).getUsername());
    }

    /**
     * 5.8 复合复杂查询：JOIN + WHERE + ORDER BY + 分页综合测试
     */
    @Test
    public void test5_8_ComplexJoinWithOrderByAndPagination() {
        assertMatrixSqlWithDialectParams(
                () -> orm.sql(e -> {
                    MatrixUser u = e.table(MatrixUser.class, "u");
                    MatrixOrder o = e.table(MatrixOrder.class, "o");
                    e.select(u.getUsername(), o.getOrderNo(), o.getAmount())
                            .from(u)
                            .innerJoin(o).on(o.getUserId(), u.getId())
                            .where().eq(u.getIsDeleted(), 0)
                            .orderByDesc(o.getAmount())
                            .limit(2)
                            .offset(1);
                }),
                // MySQL
                "SELECT `u`.`username`, `o`.`order_no`, `o`.`amount` " +
                        "FROM `matrix_user` `u` " +
                        "INNER JOIN `matrix_order` `o` ON `o`.`user_id` = `u`.`id` " +
                        "WHERE `u`.`is_deleted` = ? " +
                        "ORDER BY `o`.`amount` DESC " +
                        "LIMIT ? OFFSET ?",
                Arrays.asList(0, 2, 1),
                // PostgreSQL
                "SELECT \"u\".\"username\", \"o\".\"order_no\", \"o\".\"amount\" " +
                        "FROM \"matrix_user\" \"u\" " +
                        "INNER JOIN \"matrix_order\" \"o\" ON \"o\".\"user_id\" = \"u\".\"id\" " +
                        "WHERE \"u\".\"is_deleted\" = ? " +
                        "ORDER BY \"o\".\"amount\" DESC " +
                        "LIMIT ? OFFSET ?",
                Arrays.asList(0, 2, 1),
                // Oracle (参数顺序为 WHERE 参数优先，然后是分页 offset, limit -> [0, 1, 2])
                "SELECT \"u\".\"username\", \"o\".\"order_no\", \"o\".\"amount\" " +
                        "FROM \"matrix_user\" \"u\" " +
                        "INNER JOIN \"matrix_order\" \"o\" ON \"o\".\"user_id\" = \"u\".\"id\" " +
                        "WHERE \"u\".\"is_deleted\" = ? " +
                        "ORDER BY \"o\".\"amount\" DESC " +
                        "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                Arrays.asList(0, 1, 2),
                // SQLite
                "SELECT \"u\".\"username\", \"o\".\"order_no\", \"o\".\"amount\" " +
                        "FROM \"matrix_user\" \"u\" " +
                        "INNER JOIN \"matrix_order\" \"o\" ON \"o\".\"user_id\" = \"u\".\"id\" " +
                        "WHERE \"u\".\"is_deleted\" = ? " +
                        "ORDER BY \"o\".\"amount\" DESC " +
                        "LIMIT ? OFFSET ?",
                Arrays.asList(0, 2, 1),
                // H2
                "SELECT \"u\".\"username\", \"o\".\"order_no\", \"o\".\"amount\" " +
                        "FROM \"matrix_user\" \"u\" " +
                        "INNER JOIN \"matrix_order\" \"o\" ON \"o\".\"user_id\" = \"u\".\"id\" " +
                        "WHERE \"u\".\"is_deleted\" = ? " +
                        "ORDER BY \"o\".\"amount\" DESC " +
                        "LIMIT ? OFFSET ?",
                Arrays.asList(0, 2, 1)
        );

        // 物理执行校验：
        // 订单金额降序匹配用户：
        // 1. ORD-002 (299.00, alice)
        // 2. ORD-001 (199.90, alice)
        // 3. ORD-003 (99.00, bob)
        // limit 2 offset 1 将跳过第 1 条 ORD-002，返回 ORD-001 与 ORD-003
        List<UserOrderDto> list = orm.sql(e -> {
            MatrixUser u = e.table(MatrixUser.class, "u");
            MatrixOrder o = e.table(MatrixOrder.class, "o");
            e.select(u.getUsername(), o.getOrderNo(), o.getAmount())
                    .from(u)
                    .innerJoin(o).on(o.getUserId(), u.getId())
                    .where().eq(u.getIsDeleted(), 0)
                    .orderByDesc(o.getAmount())
                    .limit(2)
                    .offset(1);
        }).toList(UserOrderDto.class);

        Assert.assertEquals(2, list.size());
        Assert.assertEquals("ORD-001", list.get(0).getOrderNo());
        Assert.assertEquals(new BigDecimal("199.90"), list.get(0).getAmount());
        Assert.assertEquals("alice", list.get(0).getUsername());

        Assert.assertEquals("ORD-003", list.get(1).getOrderNo());
        Assert.assertEquals(new BigDecimal("99.00"), list.get(1).getAmount());
        Assert.assertEquals("bob", list.get(1).getUsername());
    }
}
