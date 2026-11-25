package com.javaorm.core.easyquery_cases;

import com.javaorm.core.easyquery_cases.entity.Topic;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * 1:1 复刻 EasyQuery 官方 DML 增删改测试 (InsertTest, UpdateTest, DeleteTest)
 */
public class EasyQueryDmlTest extends EasyQueryBaseTest {

    @Test
    public void testInsertSingleAndBatch() {
        // 1. 单条实体插入
        Topic t100 = new Topic("100", 88, "Inserted Title", LocalDateTime.now());
        int rows = orm.insert(t100).executeRows();
        assertEquals(1, rows);

        Topic q100 = orm.select(Topic.class).whereById("100").firstOrNull();
        assertNotNull(q100);
        assertEquals("Inserted Title", q100.getTitle());
        assertEquals(Integer.valueOf(88), q100.getStars());

        // 2. 快捷 insert(entity)
        Topic t101 = new Topic("101", 99, "Insert Shortcut", LocalDateTime.now());
        int row101 = orm.insert(t101).exec();
        assertEquals(1, row101);

        // 3. 批量插入 insert(Collection)
        Topic t102 = new Topic("102", 102, "Batch 102", LocalDateTime.now());
        Topic t103 = new Topic("103", 103, "Batch 103", LocalDateTime.now());
        int batchRows = orm.insert(Arrays.asList(t102, t103)).exec();
        assertEquals(2, batchRows);

        List<Topic> batchList = orm.select(Topic.class).whereByIds("102", "103").toList();
        assertEquals(2, batchList.size());
    }

    @Test
    public void testUpdateEntityAndExpression() {
        Topic proxy = orm.proxy(Topic.class);

        // 1. 实体根据主键更新
        Topic t1 = orm.select(Topic.class).whereById("1").firstOrNull();
        assertNotNull(t1);
        t1.setTitle("Modified Topic 1");
        t1.setStars(999);
        int rows = orm.update(t1).exec();
        assertEquals(1, rows);

        Topic q1 = orm.select(Topic.class).whereById("1").firstOrNull();
        assertNotNull(q1);
        assertEquals("Modified Topic 1", q1.getTitle());
        assertEquals(Integer.valueOf(999), q1.getStars());

        // 2. 表达式条件更新 (updatable / updateable)
        int exprRows = orm.update(Topic.class)
                .set(proxy.getTitle(), "Expression Updated")
                .set(proxy.getStars(), 666)
                .whereById("2")
                .executeRows();
        assertEquals(1, exprRows);

        Topic q2 = orm.select(Topic.class).whereById("2").firstOrNull();
        assertNotNull(q2);
        assertEquals("Expression Updated", q2.getTitle());
        assertEquals(Integer.valueOf(666), q2.getStars());

        // 3. 批量条件表达式更新
        int batchExprRows = orm.update(Topic.class)
                .set(proxy.getStars(), 888)
                .where(w -> w.in(proxy.getId(), Arrays.asList("3", "4")))
                .executeRows();
        assertEquals(2, batchExprRows);

        Topic q3 = orm.select(Topic.class).whereById("3").firstOrNull();
        assertEquals(Integer.valueOf(888), q3.getStars());
    }

    @Test
    public void testDeleteAndLogicDelete() {
        Topic proxy = orm.proxy(Topic.class);

        // 1. 按主键删除 (t_topic 配置了 @JoLogicDelete，物理执行 UPDATE is_deleted = 1)
        int delRows = orm.deleteById(Topic.class, "1").exec();
        assertEquals(1, delRows);

        // 正常查询自动过滤已删除记录
        Topic q1 = orm.select(Topic.class).whereById("1").firstOrNull();
        assertNull("已逻辑删除的实体正常查询应为 null", q1);

        // 2. 批量条件逻辑删除
        int condDelRows = orm.delete(Topic.class)
                .where(w -> w.eq(proxy.getId(), "2"))
                .executeRows();
        assertEquals(1, condDelRows);

        Topic q2 = orm.select(Topic.class).whereById("2").firstOrNull();
        assertNull(q2);

        // 剩余有效条数应从初始的 6 条减少为 4 条
        long remainingCount = orm.select(Topic.class).count();
        assertEquals(4, remainingCount);
    }
}
