package com.javaorm.sample.controller;

import com.javaorm.core.pagination.Page;
import com.javaorm.sample.common.Result;
import com.javaorm.sample.dto.MallUserDto;
import com.javaorm.sample.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping
    public Result<Page<MallUserDto>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<MallUserDto> pageResult = userService.pageUsers(keyword, status, page, size);
        return Result.success(pageResult, pageResult.getTotal());
    }

    @PutMapping("/{id}/status")
    public Result<Boolean> toggleStatus(@PathVariable Long id, @RequestParam Integer status) {
        return Result.success(userService.toggleStatus(id, status));
    }
}
