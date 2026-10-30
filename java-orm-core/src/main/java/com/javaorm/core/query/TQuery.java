package com.javaorm.core.query;

import com.javaorm.core.ast.SelectStatement;
import com.javaorm.core.dialect.SqlAndParams;
import com.javaorm.core.sql.SqlEnv;

import java.util.List;
import java.util.Map;

/**
 * 带有结果实体/DTO泛型约束的查询类型通用标记与能力接口。
 * 允许外部直接感知并推导查询结果类型 T。
 *
 * @param <T> 查询结果类型
 */
public interface TQuery<T> {

    /**
     * 获取查询结果实体或 DTO 的 Class 对象
     */
    Class<T> getResultClass();

    /**
     * 获取底层的 SelectStatement AST 树
     */
    SelectStatement getStatement();

    /**
     * 获取关联的 SqlEnv 环境
     */
    SqlEnv getEnv();

    /**
     * 执行查询并返回强类型列表
     */
    <R> List<R> toList();

    /**
     * 执行查询并映射为指定目标类型列表
     */
    <R> List<R> toList(Class<R> targetClass);

    /**
     * 执行查询并返回第一条记录，无记录返回 null
     */
    <R> R firstOrNull();

    /**
     * 执行查询并映射为指定目标类型的第一条记录
     */
    <R> R firstOrNull(Class<R> targetClass);

    /**
     * 执行查询并返回字典列表
     */
    List<Map<String, Object>> listMaps();

    /**
     * 查询记录总数
     */
    long count();

    /**
     * 获取生成的 SQL 字符串
     */
    String toSql();

    /**
     * 获取生成的 SQL 字符串与参数列表
     */
    SqlAndParams toSqlAndParams();
}
