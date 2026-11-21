package com.javaorm.sample.test;

import com.javaorm.core.JavaOrm;
import com.javaorm.core.pagination.Page;
import com.javaorm.sample.SampleMallApplication;
import com.javaorm.sample.dto.DashboardStatsDto;
import com.javaorm.sample.dto.MallOrderDto;
import com.javaorm.sample.dto.MallProductDto;
import com.javaorm.sample.entity.MallCategory;
import com.javaorm.sample.entity.MallProduct;
import com.javaorm.sample.service.CategoryService;
import com.javaorm.sample.service.DashboardService;
import com.javaorm.sample.service.OrderService;
import com.javaorm.sample.service.ProductService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

@SpringBootTest(classes = SampleMallApplication.class)
public class SampleMallApplicationTest {

    @Autowired
    private JavaOrm orm;

    @Autowired
    private ProductService productService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private DashboardService dashboardService;

    @Test
    public void testJavaOrmBeanInjection() {
        Assertions.assertNotNull(orm, "JavaOrm Spring Bean 应该成功注入");
    }

    @Test
    public void testCategoryList() {
        List<MallCategory> list = categoryService.listAll();
        Assertions.assertFalse(list.isEmpty(), "分类应该已成功初始化");
    }

    @Test
    public void testProductServiceAndOptimisticLock() {
        Page<MallProductDto> page = productService.pageProducts(null, null, null, 1, 10);
        Assertions.assertTrue(page.getTotal() > 0, "商品列表总数应该大于0");
        Assertions.assertFalse(page.getRecords().isEmpty(), "当前页商品记录不应为空");

        MallProductDto first = page.getRecords().get(0);
        Assertions.assertNotNull(first.getCategoryName());

        // 验证乐观锁更新
        MallProduct p = productService.getById(first.getId());
        Assertions.assertNotNull(p);
        int oldVer = p.getVersion();
        p.setTitle("单元测试乐观锁更新测试");
        boolean ok = productService.update(p);
        Assertions.assertTrue(ok, "更新应该成功");

        MallProduct updated = productService.getById(first.getId());
        Assertions.assertEquals(Integer.valueOf(oldVer + 1), updated.getVersion(), "版本号应该自动自增");
    }

    @Test
    public void testSelectAutoIncludeOrders() {
        // 核心亮点测试：selectAutoInclude 自动装配订单、明细列表与下单用户
        Page<MallOrderDto> orderPage = orderService.pageOrders(null, null, 1, 10);
        Assertions.assertTrue(orderPage.getTotal() > 0, "订单总数应该大于0");

        List<MallOrderDto> orders = orderPage.getRecords();
        Assertions.assertFalse(orders.isEmpty());

        MallOrderDto order = orders.get(0);
        Assertions.assertNotNull(order.getOrderNo(), "订单编号不应为空");
        Assertions.assertNotNull(order.getUser(), "AutoInclude 应该自动装配下单会员 DTO");
        Assertions.assertNotNull(order.getUser().getUsername(), "会员账号不应为空");
        Assertions.assertNotNull(order.getItems(), "AutoInclude 应该自动装配明细列表 DTO");
        Assertions.assertFalse(order.getItems().isEmpty(), "订单明细列表不应为空");
    }

    @Test
    public void testDashboardStats() {
        DashboardStatsDto stats = dashboardService.getDashboardStats();
        Assertions.assertNotNull(stats);
        Assertions.assertTrue(stats.getTotalSales().compareTo(BigDecimal.ZERO) >= 0, "销售额应该大于0");
        Assertions.assertTrue(stats.getTotalOrders() > 0, "订单数应该大于0");
        Assertions.assertTrue(stats.getTotalProducts() > 0, "商品数应该大于0");
        Assertions.assertTrue(stats.getTotalUsers() > 0, "会员数应该大于0");
        Assertions.assertFalse(stats.getCategoryDist().isEmpty(), "分类统计不应为空");
    }
}
