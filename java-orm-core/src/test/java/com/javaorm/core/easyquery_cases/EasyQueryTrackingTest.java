package com.javaorm.core.easyquery_cases;

import com.javaorm.core.easyquery_cases.entity.Topic;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * 1:1 复刻 EasyQuery 官方变脏跟踪与增量更新测试 (TrackingTest & DirtyCheckingTest)
 */
public class EasyQueryTrackingTest extends EasyQueryBaseTest {

    @Test
    public void testAttachAndDirtyCheckingUpdate() {
        // 1. 查询实体
        Topic topic = orm.queryable(Topic.class)
                .whereById("1")
                .firstOrNull();
        assertNotNull(topic);
        assertEquals("Java ORM Guide", topic.getTitle());
        assertEquals(Integer.valueOf(10), topic.getStars());

        // 2. 附加上快照跟踪 (Attach Tracking)
        orm.attach(topic);

        // 3. 仅修改 stars 属性，不修改 title 和其他字段
        topic.setStars(99);

        // 4. 执行更新，脏检查机制自动计算 Diff，仅生成包含变更列的 UPDATE SET 语句
        int rows = orm.update(topic);
        assertEquals(1, rows);

        // 5. 重新读取验证
        Topic reloaded = orm.queryable(Topic.class).whereById("1").firstOrNull();
        assertNotNull(reloaded);
        assertEquals(Integer.valueOf(99), reloaded.getStars());
        assertEquals("Java ORM Guide", reloaded.getTitle()); // 未被污染
    }

    @Test
    public void testAsTrackingQueryAndDirtyUpdate() {
        // 开启 asTracking() 流式查询并自动挂载跟踪
        Topic topic = orm.queryable(Topic.class)
                .whereById("2")
                .asTracking()
                .firstOrNull();

        assertNotNull(topic);
        assertEquals("EasyQuery Parity", topic.getTitle());

        // 修改 title
        topic.setTitle("EasyQuery Tracking Success");
        int rows = orm.update(topic);
        assertEquals(1, rows);

        Topic reloaded = orm.queryable(Topic.class).whereById("2").firstOrNull();
        assertNotNull(reloaded);
        assertEquals("EasyQuery Tracking Success", reloaded.getTitle());
        assertEquals(Integer.valueOf(20), reloaded.getStars()); // 未修改字段不变
    }
}
