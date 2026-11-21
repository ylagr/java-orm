package com.javaorm.sample.controller;

import com.javaorm.sample.common.Result;
import com.javaorm.sample.dto.DashboardStatsDto;
import com.javaorm.sample.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    @GetMapping("/stats")
    public Result<DashboardStatsDto> getStats() {
        return Result.success(dashboardService.getDashboardStats());
    }
}
