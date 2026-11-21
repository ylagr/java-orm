package com.javaorm.sample.service;

import com.javaorm.core.JavaOrm;
import com.javaorm.sample.dto.DashboardStatsDto;
import com.javaorm.sample.entity.MallCategory;
import com.javaorm.sample.entity.MallOrder;
import com.javaorm.sample.entity.MallProduct;
import com.javaorm.sample.entity.MallUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

@Service
public class DashboardService {

    @Autowired
    private JavaOrm orm;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public DashboardStatsDto getDashboardStats() {
        DashboardStatsDto stats = new DashboardStatsDto();

        // 1. 商品总数、会员总数、订单总数（通过 JavaORM 快捷 count）
        long totalProducts = orm.select(MallProduct.class).count();
        long totalUsers = orm.select(MallUser.class).count();
        long totalOrders = orm.select(MallOrder.class).count();

        // 2. 统计已付款订单的总销售额（通过 JdbcTemplate 原生聚合，展示与 Spring 生态完美契合）
        BigDecimal totalSales = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(pay_amount), 0) FROM mall_order WHERE status >= 20 AND status <= 40 AND deleted = 0",
                BigDecimal.class
        );
        if (totalSales == null) {
            totalSales = BigDecimal.ZERO;
        }

        // 3. 分类商品分布统计（分组统计，注意 H2 中 value 为关键字，使用 cnt 作为别名并映射为 value）
        List<Map<String, Object>> categoryRaw = jdbcTemplate.queryForList(
                "SELECT c.name AS name, COUNT(p.id) AS cnt " +
                        "FROM mall_category c " +
                        "LEFT JOIN mall_product p ON c.id = p.category_id AND p.deleted = 0 " +
                        "WHERE c.deleted = 0 " +
                        "GROUP BY c.id, c.name"
        );
        List<Map<String, Object>> categoryDist = new ArrayList<>();
        for (Map<String, Object> row : categoryRaw) {
            Map<String, Object> map = new HashMap<>();
            map.put("name", row.get("name"));
            map.put("value", row.get("cnt"));
            categoryDist.add(map);
        }

        // 4. 热销 Top 5 商品（通过 JavaORM 强类型流式排序 + limit）
        List<MallProduct> topList = orm.select(MallProduct.class)
                .where()
                .eq(MallProduct::getStatus, 1)
                .orderBy(MallProduct::getSales, false)
                .limit(5)
                .list();

        List<Map<String, Object>> topProducts = new ArrayList<>();
        for (MallProduct p : topList) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", p.getId());
            map.put("name", p.getName());
            map.put("sales", p.getSales());
            map.put("price", p.getPrice());
            topProducts.add(map);
        }

        stats.setTotalProducts(totalProducts);
        stats.setTotalUsers(totalUsers);
        stats.setTotalOrders(totalOrders);
        stats.setTotalSales(totalSales);
        stats.setCategoryDist(categoryDist);
        stats.setTopProducts(topProducts);
        return stats;
    }
}
