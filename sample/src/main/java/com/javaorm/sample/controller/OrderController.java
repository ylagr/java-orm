package com.javaorm.sample.controller;

import com.javaorm.core.pagination.Page;
import com.javaorm.sample.common.Result;
import com.javaorm.sample.dto.MallOrderDto;
import com.javaorm.sample.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @GetMapping
    public Result<Page<MallOrderDto>> list(
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<MallOrderDto> pageResult = orderService.pageOrders(orderNo, status, page, size);
        return Result.success(pageResult, pageResult.getTotal());
    }

    @GetMapping("/{id}")
    public Result<MallOrderDto> getById(@PathVariable Long id) {
        return Result.success(orderService.getOrderDetail(id));
    }

    @PutMapping("/{id}/ship")
    public Result<Boolean> ship(@PathVariable Long id) {
        return Result.success(orderService.shipOrder(id));
    }

    @PutMapping("/{id}/cancel")
    public Result<Boolean> cancel(@PathVariable Long id) {
        return Result.success(orderService.cancelOrder(id));
    }
}
