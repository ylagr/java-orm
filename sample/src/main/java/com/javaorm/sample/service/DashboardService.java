package com.javaorm.sample.service;

import com.javaorm.core.JavaOrm;
import com.javaorm.sample.dto.DashboardStatsDto;
import com.javaorm.sample.entity.MallCategory;
import com.javaorm.sample.entity.MallOrder;
import com.javaorm.sample.entity.MallProduct;
import com.javaorm.sample.entity.MallUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

import static com.javaorm.core.SQL.coalesce;
import static com.javaorm.core.SQL.count;
import static com.javaorm.core.SQL.sum;

@Service
public class DashboardService {

    @Autowired
    private JavaOrm orm;

    public DashboardStatsDto getDashboardStats() {
        DashboardStatsDto stats = new DashboardStatsDto();

        // 1. 商品总数、会员总数、订单总数（通过 JavaORM 快捷 count）
        long totalProducts = orm.select(MallProduct.class).count();
        long totalUsers = orm.select(MallUser.class).count();
        long totalOrders = orm.select(MallOrder.class).count();

        // 2. 统计已付款订单的总销售额（通过 JavaORM 流式 coalesce + sum 聚合）
        List<Map<String, Object>> salesRows = orm.sql(e -> {
            MallOrder o = e.table(MallOrder.class, "o");
            e.select(coalesce(sum(o.getPayAmount()), BigDecimal.ZERO).as("total_sales"))
                    .from(o)
                    .where()
                    .ge(o.getStatus(), 20)
                    .le(o.getStatus(), 40)
                    .eq(o.getDeleted(), 0);
        }).listMaps();
        BigDecimal totalSales = BigDecimal.ZERO;
        if (!salesRows.isEmpty()) {
            Map<String, Object> row = salesRows.get(0);
            Object val = row.get("total_sales") != null ? row.get("total_sales") : row.get("TOTAL_SALES");
            if (val != null) {
                totalSales = new BigDecimal(val.toString());
            }
        }

        // 3. 分类商品分布统计（通过 JavaORM 流式连表、聚合与分组统计）
        List<Map<String, Object>> categoryRaw = orm.sql(e -> {
            MallCategory c = e.table(MallCategory.class, "c");
            MallProduct p = e.table(MallProduct.class, "p");

            e.select(c.getName(), count(p.getId()).as("cnt"))
                    .from(c)
                    .leftJoin(p).on(on -> on.eq(c.getId(), p.getCategoryId()).eq(p.getDeleted(), 0))
                    .where().eq(c.getDeleted(), 0)
                    .groupBy(c.getId(), c.getName());
        }).listMaps();

        List<Map<String, Object>> categoryDist = new ArrayList<>();
        for (Map<String, Object> row : categoryRaw) {
            Map<String, Object> map = new HashMap<>();
            Object nameVal = row.get("name") != null ? row.get("name") : row.get("NAME");
            Object cntVal = row.get("cnt") != null ? row.get("cnt") : row.get("CNT");
            map.put("name", nameVal);
            map.put("value", cntVal);
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
