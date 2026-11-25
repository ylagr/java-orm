package com.javaorm.sample.service;

import com.javaorm.core.JavaOrm;
import com.javaorm.core.pagination.Page;
import com.javaorm.sample.dto.MallOrderDto;
import com.javaorm.sample.entity.MallOrder;
import com.javaorm.sample.entity.MallOrderItem;
import com.javaorm.sample.entity.MallProduct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Service
public class OrderService {

    @Autowired
    private JavaOrm orm;

    /**
     * 分页查询订单列表（核心亮点：使用 selectAutoInclude 自动装配关联订单明细与下单用户 DTO）
     */
    public Page<MallOrderDto> pageOrders(String orderNo, Integer status, int page, int size) {
        boolean hasOrderNo = orderNo != null && !orderNo.trim().isEmpty();
        return orm.select(MallOrder.class)
                .where()
                .like(hasOrderNo, MallOrder::getOrderNo, hasOrderNo ? "%" + orderNo.trim() + "%" : "")
                .eq(status != null, MallOrder::getStatus, status)
                .orderBy(MallOrder::getId, false)
                .selectAutoInclude(MallOrderDto.class)
                .toPage(page, size);
    }

    /**
     * 获取单个订单完整详情（通过 selectAutoInclude 零反射级联提取）
     */
    public MallOrderDto getOrderDetail(Long id) {
        MallOrder table = orm.proxy(MallOrder.class);
        return orm.select(MallOrder.class)
                .where()
                .eq(table.getId(), id)
                .selectAutoInclude(MallOrderDto.class)
                .firstOrNull();
    }

    /**
     * 订单发货
     */
    @Transactional
    public boolean shipOrder(Long id) {
        return orm.update(MallOrder.class)
                .set(MallOrder::getStatus, 30) // 30-已发货
                .where()
                .eq(MallOrder::getId, id)
                .eq(MallOrder::getStatus, 20) // 只能发货“待发货”状态的订单
                .execute() > 0;
    }

    /**
     * 取消订单
     */
    @Transactional
    public boolean cancelOrder(Long id) {
        return orm.update(MallOrder.class)
                .set(MallOrder::getStatus, 50) // 50-已取消
                .where()
                .eq(MallOrder::getId, id)
                .execute() > 0;
    }

    /**
     * 创建订单测试数据（带明细）
     */
    @Transactional
    public Long createOrder(MallOrder order, List<MallOrderItem> items) {
        order.setCreateTime(new Date());
        order.setVersion(1);
        order.setDeleted(0);
        orm.insert(order).exec();

        if (items != null) {
            for (MallOrderItem item : items) {
                item.setOrderId(order.getId());
                item.setCreateTime(new Date());
                orm.insert(item).exec();
            }
        }
        return order.getId();
    }
}
