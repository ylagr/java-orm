package com.javaorm.sample.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 仪表盘核心统计数据 DTO
 */
public class DashboardStatsDto implements Serializable {

    private BigDecimal totalSales;       // 总销售额
    private Long totalOrders;            // 订单总数
    private Long totalProducts;          // 商品总数
    private Long totalUsers;             // 会员总数
    private List<Map<String, Object>> categoryDist; // 分类分布统计
    private List<Map<String, Object>> topProducts;  // 热销 Top 5 商品

    public BigDecimal getTotalSales() { return totalSales; }
    public void setTotalSales(BigDecimal totalSales) { this.totalSales = totalSales; }

    public Long getTotalOrders() { return totalOrders; }
    public void setTotalOrders(Long totalOrders) { this.totalOrders = totalOrders; }

    public Long getTotalProducts() { return totalProducts; }
    public void setTotalProducts(Long totalProducts) { this.totalProducts = totalProducts; }

    public Long getTotalUsers() { return totalUsers; }
    public void setTotalUsers(Long totalUsers) { this.totalUsers = totalUsers; }

    public List<Map<String, Object>> getCategoryDist() { return categoryDist; }
    public void setCategoryDist(List<Map<String, Object>> categoryDist) { this.categoryDist = categoryDist; }

    public List<Map<String, Object>> getTopProducts() { return topProducts; }
    public void setTopProducts(List<Map<String, Object>> topProducts) { this.topProducts = topProducts; }
}
