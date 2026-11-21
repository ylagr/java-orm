package com.javaorm.sample.controller;

import com.javaorm.sample.common.Result;
import com.javaorm.sample.entity.MallCategory;
import com.javaorm.sample.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    @Autowired
    private CategoryService categoryService;

    @GetMapping
    public Result<List<MallCategory>> list() {
        return Result.success(categoryService.listAll());
    }

    @PostMapping
    public Result<Long> create(@RequestBody MallCategory category) {
        return Result.success(categoryService.create(category));
    }

    @PutMapping("/{id}")
    public Result<Boolean> update(@PathVariable Long id, @RequestBody MallCategory category) {
        category.setId(id);
        return Result.success(categoryService.update(category));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.success(categoryService.delete(id));
    }
}
