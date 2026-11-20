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
public interface Query<T> {

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
     * 执行查询并返回第一条记录（firstOrNull 别名）
     */
    default <R> R first() {
        return firstOrNull();
    }

    /**
     * 执行查询并映射为指定目标类型的第一条记录
     */
    <R> R firstOrNull(Class<R> targetClass);

    /**
     * 执行查询并映射为指定目标类型的第一条记录（firstOrNull 别名）
     */
    default <R> R first(Class<R> targetClass) {
        return firstOrNull(targetClass);
    }

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

    /**
     * 添加悲观排他锁 (FOR UPDATE)
     */
    default Query<T> forUpdate() {
        return forUpdate(com.javaorm.core.ast.LockMode.FOR_UPDATE);
    }

    /**
     * 指定锁定模式（FOR UPDATE / NOWAIT / SKIP LOCKED）
     */
    Query<T> forUpdate(com.javaorm.core.ast.LockMode lockMode);

    /**
     * 添加非阻塞悲观排他锁 (FOR UPDATE NOWAIT)
     */
    default Query<T> forUpdateNowait() {
        return forUpdate(com.javaorm.core.ast.LockMode.FOR_UPDATE_NOWAIT);
    }

    /**
     * 跳过锁定行的高并发悲观锁 (FOR UPDATE SKIP LOCKED)
     */
    default Query<T> forUpdateSkipLocked() {
        return forUpdate(com.javaorm.core.ast.LockMode.FOR_UPDATE_SKIP_LOCKED);
    }

    /**
     * 开启实体变脏追踪（在实体装配时自动拍摄快照，用于差异更新与脏检查）
     */
    default Query<T> tracking() {
        return tracking(true);
    }

    /**
     * 设置是否开启实体变脏追踪
     */
    Query<T> tracking(boolean enable);

    /**
     * 读写分离：显式强制路由到 Master 主库
     */
    default Query<T> useMaster() {
        if (getStatement() != null) {
            getStatement().setRouteRole(com.javaorm.core.datasource.RouteRole.MASTER);
        }
        return this;
    }

    /**
     * 读写分离：显式强制路由到 Slave 从库（按权重/轮询负载均衡）
     */
    default Query<T> useSlave() {
        if (getStatement() != null) {
            getStatement().setRouteRole(com.javaorm.core.datasource.RouteRole.SLAVE);
        }
        return this;
    }

    /**
     * 读写分离：显式强制路由到指定命名从库
     */
    default Query<T> useSlave(String slaveName) {
        if (getStatement() != null) {
            getStatement().setRouteRole(com.javaorm.core.datasource.RouteRole.SLAVE);
            getStatement().setTargetDataSource(slaveName);
        }
        return this;
    }

    /**
     * 显式指定查询的目标物理数据源名称（支持分库与动态多数据源）
     */
    default Query<T> useDataSource(String dataSourceName) {
        if (getStatement() != null) {
            getStatement().setTargetDataSource(dataSourceName);
        }
        return this;
    }
}
