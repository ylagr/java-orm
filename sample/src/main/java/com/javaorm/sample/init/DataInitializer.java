package com.javaorm.sample.init;

import com.javaorm.core.JavaOrm;
import com.javaorm.sample.entity.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Autowired
    private JavaOrm orm;

    @Override
    public void run(String... args) {
        log.info("==> [SampleMall] 正在自动初始化商城演示数据...");

        // 1. 初始化分类
        if (orm.select(MallCategory.class).count() == 0) {
            String[] categoryNames = {"数码数码", "电脑办公", "服饰鞋靴", "食品生鲜", "家居生活", "运动户外"};
            for (int i = 0; i < categoryNames.length; i++) {
                MallCategory c = new MallCategory();
                c.setName(categoryNames[i]);
                c.setSort(i + 1);
                c.setIcon("folder");
                c.setDeleted(0);
                c.setCreateTime(new Date());
                orm.insert(c);
            }
        }

        List<MallCategory> categories = orm.select(MallCategory.class).list();

        // 2. 初始化商品
        if (orm.select(MallProduct.class).count() == 0 && !categories.isEmpty()) {
            Long c1 = categories.get(0).getId();
            Long c2 = categories.size() > 1 ? categories.get(1).getId() : c1;
            Long c3 = categories.size() > 2 ? categories.get(2).getId() : c1;

            insertProduct(c1, "旗舰智能手机 X1", "骁龙旗舰芯片 / 120Hz 高刷屏 / 5000mAh", new BigDecimal("4999.00"), 120, 350, "https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?w=300");
            insertProduct(c1, "无线降噪耳机 Pro", "主动降噪 / 30小时续航 / 空间音频", new BigDecimal("899.00"), 300, 780, "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=300");
            insertProduct(c2, "超薄笔记本电脑 Air", "14英寸 2.8K OLED / 16G+1T / 轻薄便携", new BigDecimal("5999.00"), 50, 160, "https://images.unsplash.com/photo-1496181133206-80ce9b88a853?w=300");
            insertProduct(c2, "客制化机械键盘", "Gasket结构 / 热插拔轴座 / RGB炫彩", new BigDecimal("399.00"), 200, 420, "https://images.unsplash.com/photo-1587829741301-dc798b83add3?w=300");
            insertProduct(c2, "4K超高清显示器 27寸", "IPS广视角 / HDR400 / Type-C 90W反向供电", new BigDecimal("1699.00"), 80, 230, "https://images.unsplash.com/photo-1527443224154-c4a3942d3acf?w=300");
            insertProduct(c3, "三合一户外冲锋衣", "Gore-Tex防水防风 / 抓绒可拆卸内胆", new BigDecimal("699.00"), 150, 560, "https://images.unsplash.com/photo-1544441893-675973e31985?w=300");
            insertProduct(c3, "经典复古慢跑鞋", "轻量减震大底 / 透气网面", new BigDecimal("359.00"), 260, 890, "https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=300");
        }

        // 3. 初始化会员
        if (orm.select(MallUser.class).count() == 0) {
            insertUser("alice", "爱丽丝", "13800000001", 1200, new BigDecimal("888.50"));
            insertUser("bob", "鲍伯", "13800000002", 500, new BigDecimal("256.00"));
            insertUser("charlie", "查理", "13800000003", 2600, new BigDecimal("1920.00"));
            insertUser("david", "大卫", "13800000004", 80, new BigDecimal("50.00"));
            insertUser("eva", "伊娃", "13800000005", 3400, new BigDecimal("3600.00"));
        }

        // 4. 初始化订单与订单明细
        if (orm.select(MallOrder.class).count() == 0) {
            List<MallUser> users = orm.select(MallUser.class).list();
            List<MallProduct> products = orm.select(MallProduct.class).list();
            if (!users.isEmpty() && !products.isEmpty()) {
                MallUser u1 = users.get(0);
                MallUser u2 = users.size() > 1 ? users.get(1) : u1;
                MallUser u3 = users.size() > 2 ? users.get(2) : u1;

                MallProduct p1 = products.get(0);
                MallProduct p2 = products.size() > 1 ? products.get(1) : p1;
                MallProduct p3 = products.size() > 2 ? products.get(2) : p1;

                createSampleOrder("ORD202611210001", u1.getId(), 20, "张女士", "13800000001", "上海市浦东新区张江高科园区88号",
                        new MallOrderItem[]{ createItem(p1, 1), createItem(p2, 1) });

                createSampleOrder("ORD202611210002", u2.getId(), 30, "李先生", "13800000002", "北京市朝阳区望京SOHO T3",
                        new MallOrderItem[]{ createItem(p3, 1) });

                createSampleOrder("ORD202611210003", u3.getId(), 40, "王总", "13800000003", "深圳市南山区科技园高新南道",
                        new MallOrderItem[]{ createItem(p1, 2), createItem(p3, 1) });

                createSampleOrder("ORD202611210004", u1.getId(), 10, "张女士", "13800000001", "上海市浦东新区张江高科园区88号",
                        new MallOrderItem[]{ createItem(p2, 2) });
            }
        }

        log.info("==> [SampleMall] 演示数据初始化完毕！");
    }

    private void insertProduct(Long categoryId, String name, String title, BigDecimal price, int stock, int sales, String img) {
        MallProduct p = new MallProduct();
        p.setCategoryId(categoryId);
        p.setName(name);
        p.setTitle(title);
        p.setPrice(price);
        p.setStock(stock);
        p.setSales(sales);
        p.setImage(img);
        p.setStatus(1);
        p.setVersion(1);
        p.setDeleted(0);
        p.setCreateTime(new Date());
        p.setUpdateTime(new Date());
        orm.insert(p);
    }

    private void insertUser(String username, String nickname, String phone, int points, BigDecimal balance) {
        MallUser u = new MallUser();
        u.setUsername(username);
        u.setNickname(nickname);
        u.setPhone(phone);
        u.setStatus(1);
        u.setPoints(points);
        u.setBalance(balance);
        u.setDeleted(0);
        u.setCreateTime(new Date());
        orm.insert(u);
    }

    private MallOrderItem createItem(MallProduct p, int qty) {
        MallOrderItem item = new MallOrderItem();
        item.setProductId(p.getId());
        item.setProductName(p.getName());
        item.setProductImage(p.getImage());
        item.setPrice(p.getPrice());
        item.setQuantity(qty);
        item.setTotalAmount(p.getPrice().multiply(BigDecimal.valueOf(qty)));
        item.setCreateTime(new Date());
        return item;
    }

    private void createSampleOrder(String orderNo, Long userId, int status, String rName, String rPhone, String rAddr, MallOrderItem[] items) {
        MallOrder o = new MallOrder();
        o.setOrderNo(orderNo);
        o.setUserId(userId);
        o.setStatus(status);
        o.setReceiverName(rName);
        o.setReceiverPhone(rPhone);
        o.setReceiverAddress(rAddr);
        o.setVersion(1);
        o.setDeleted(0);
        o.setCreateTime(new Date());
        if (status >= 20) {
            o.setPayTime(new Date());
        }

        BigDecimal total = BigDecimal.ZERO;
        for (MallOrderItem item : items) {
            total = total.add(item.getTotalAmount());
        }
        o.setTotalAmount(total);
        o.setPayAmount(total);

        orm.insert(o);
        for (MallOrderItem item : items) {
            item.setOrderId(o.getId());
            orm.insert(item);
        }
    }
}
