package com.javaorm.core.sharding;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 常用时间按月分表路由。
 * 例如 logicTableName = "t_order", createTime = 2026-11-18 -> 生成 t_order_202611
 */
public class MonthTableRouter implements TableRouter<Object> {

    private static final DateTimeFormatter YYYYMM = DateTimeFormatter.ofPattern("yyyyMM");
    private final List<String> knownTables;

    public MonthTableRouter() {
        this(Collections.emptyList());
    }

    public MonthTableRouter(List<String> knownTables) {
        this.knownTables = knownTables != null ? knownTables : Collections.emptyList();
    }

    @Override
    public String route(String logicTableName, Object shardingValue) {
        if (shardingValue == null) {
            throw new IllegalArgumentException("Sharding value must not be null for MonthTableRouter");
        }
        String yyyymm;
        if (shardingValue instanceof LocalDateTime) {
            yyyymm = ((LocalDateTime) shardingValue).format(YYYYMM);
        } else if (shardingValue instanceof LocalDate) {
            yyyymm = ((LocalDate) shardingValue).format(YYYYMM);
        } else if (shardingValue instanceof YearMonth) {
            yyyymm = ((YearMonth) shardingValue).format(YYYYMM);
        } else if (shardingValue instanceof Date) {
            LocalDateTime ldt = ((Date) shardingValue).toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
            yyyymm = ldt.format(YYYYMM);
        } else {
            String str = shardingValue.toString().replaceAll("[-/ :]", "");
            if (str.length() >= 6) {
                yyyymm = str.substring(0, 6);
            } else {
                throw new IllegalArgumentException("Cannot extract year-month from sharding value: " + shardingValue);
            }
        }
        return logicTableName + "_" + yyyymm;
    }

    @Override
    public List<String> routeRange(String logicTableName, Object startValue, Object endValue) {
        if (startValue == null || endValue == null) {
            return allTables(logicTableName);
        }
        String startTable = route(logicTableName, startValue);
        String endTable = route(logicTableName, endValue);
        if (startTable.equals(endTable)) {
            return Collections.singletonList(startTable);
        }
        YearMonth ymStart = extractYearMonth(startValue);
        YearMonth ymEnd = extractYearMonth(endValue);
        if (ymStart != null && ymEnd != null && !ymStart.isAfter(ymEnd)) {
            List<String> tables = new ArrayList<>();
            YearMonth cur = ymStart;
            while (!cur.isAfter(ymEnd)) {
                tables.add(logicTableName + "_" + cur.format(YYYYMM));
                cur = cur.plusMonths(1);
            }
            return tables;
        }
        return allTables(logicTableName);
    }

    private YearMonth extractYearMonth(Object val) {
        if (val instanceof LocalDateTime) return YearMonth.from((LocalDateTime) val);
        if (val instanceof LocalDate) return YearMonth.from((LocalDate) val);
        if (val instanceof YearMonth) return (YearMonth) val;
        if (val instanceof Date) {
            LocalDateTime ldt = ((Date) val).toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
            return YearMonth.from(ldt);
        }
        if (val != null) {
            try {
                String str = val.toString().replaceAll("[-/ :]", "");
                if (str.length() >= 6) {
                    int year = Integer.parseInt(str.substring(0, 4));
                    int month = Integer.parseInt(str.substring(4, 6));
                    return YearMonth.of(year, month);
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    @Override
    public List<String> allTables(String logicTableName) {
        if (!knownTables.isEmpty()) {
            return knownTables;
        }
        return Collections.singletonList(logicTableName);
    }
}
