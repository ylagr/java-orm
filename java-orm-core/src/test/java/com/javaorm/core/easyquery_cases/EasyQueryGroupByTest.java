package com.javaorm.core.easyquery_cases;

import com.javaorm.core.easyquery_cases.entity.SysUser;
import com.javaorm.core.easyquery_cases.entity.Topic;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static com.javaorm.core.SQL.*;
import static org.junit.Assert.*;

/**
 * 1:1 复刻 EasyQuery 官方分组与聚合查询测试 (GroupByTest & AggregateTest)
 */
public class EasyQueryGroupByTest extends EasyQueryBaseTest {

    public static class CompanyUserStat {
        private String companyId;
        private Long userCount;
        private Double avgAge;

        public String getCompanyId() { return companyId; }
        public void setCompanyId(String companyId) { this.companyId = companyId; }
        public Long getUserCount() { return userCount; }
        public void setUserCount(Long userCount) { this.userCount = userCount; }
        public Double getAvgAge() { return avgAge; }
        public void setAvgAge(Double avgAge) { this.avgAge = avgAge; }
    }

    @Test
    public void testGroupByAndAggregates() {
        SysUser u = orm.proxy(SysUser.class);

        // 1. 单列 GroupBy 并计算 Count 与 Avg
        List<CompanyUserStat> stats = orm.select(CompanyUserStat.class,
                        u.getCompanyId(),
                        count(u.getId()).as("userCount"),
                        avg(u.getAge()).as("avgAge"))
                .from(SysUser.class)
                .groupBy(u.getCompanyId())
                .orderByAsc(u.getCompanyId())
                .toList();

        assertEquals(2, stats.size());

        // c1: Alice(25), Bob(30) -> count=2, avg=27.5
        assertEquals("c1", stats.get(0).getCompanyId());
        assertEquals(Long.valueOf(2), stats.get(0).getUserCount());
        assertEquals(27.5, stats.get(0).getAvgAge(), 0.01);

        // c2: Charlie(28), David(35) -> count=2, avg=31.5
        assertEquals("c2", stats.get(1).getCompanyId());
        assertEquals(Long.valueOf(2), stats.get(1).getUserCount());
        assertEquals(31.5, stats.get(1).getAvgAge(), 0.01);
    }

    @Test
    public void testHavingClause() {
        SysUser u = orm.proxy(SysUser.class);

        // 使用 Having 过滤平均年龄 > 30 的公司
        List<CompanyUserStat> stats = orm.select(CompanyUserStat.class,
                        u.getCompanyId(),
                        count(u.getId()).as("userCount"),
                        avg(u.getAge()).as("avgAge"))
                .from(SysUser.class)
                .groupBy(u.getCompanyId())
                .having(h -> h.gt(avg(u.getAge()), 30.0))
                .toList();

        assertEquals(1, stats.size());
        assertEquals("c2", stats.get(0).getCompanyId());
    }

    @Test
    public void testMaxMinSumCountAggregates() {
        Topic t = orm.proxy(Topic.class);

        // 统计 Topic 表中的 stars 的最大值、最小值、总和与行数
        Map<String, Object> map = orm.select(
                        max(t.getStars()).as("max_star"),
                        min(t.getStars()).as("min_star"),
                        sum(t.getStars()).as("sum_star"),
                        count(t.getId()).as("total_count"))
                .from(Topic.class)
                .toMap();

        assertNotNull(map);
        assertEquals(60, ((Number) map.get("max_star")).intValue());
        assertEquals(10, ((Number) map.get("min_star")).intValue());
        assertEquals(210, ((Number) map.get("sum_star")).intValue()); // 10+20+30+40+50+60 = 210
        assertEquals(6, ((Number) map.get("total_count")).intValue());
    }

    @Test
    public void testSqlModeGroupBy() {
        SysUser u = orm.proxy(SysUser.class);

        List<CompanyUserStat> stats = orm.sql(e -> {
            e.select(CompanyUserStat.class,
                    u.getCompanyId(),
                    count(u.getId()).as("userCount"),
                    avg(u.getAge()).as("avgAge"))
            .from(SysUser.class)
            .groupBy(u.getCompanyId())
            .orderByDesc(u.getCompanyId());
        }).list(CompanyUserStat.class);

        assertEquals(2, stats.size());
        assertEquals("c2", stats.get(0).getCompanyId());
    }
}
