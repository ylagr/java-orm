package com.javaorm.sample.controller;

import com.javaorm.core.pagination.Page;
import com.javaorm.sample.common.Result;
import com.javaorm.sample.dto.MallProductDto;
import com.javaorm.sample.entity.MallProduct;
import com.javaorm.sample.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    @Autowired
    private ProductService productService;

    @GetMapping
    public Result<Page<MallProductDto>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<MallProductDto> pageResult = productService.pageProducts(keyword, categoryId, status, page, size);
        return Result.success(pageResult, pageResult.getTotal());
    }

    @GetMapping("/{id}")
    public Result<MallProduct> getById(@PathVariable Long id) {
        return Result.success(productService.getById(id));
    }

    @PostMapping
    public Result<Long> create(@RequestBody MallProduct product) {
        return Result.success(productService.create(product));
    }

    @PutMapping("/{id}")
    public Result<Boolean> update(@PathVariable Long id, @RequestBody MallProduct product) {
        product.setId(id);
        return Result.success(productService.update(product));
    }

    @PutMapping("/{id}/status")
    public Result<Boolean> toggleStatus(@PathVariable Long id, @RequestParam Integer status) {
        return Result.success(productService.toggleStatus(id, status));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.success(productService.delete(id));
    }
}
