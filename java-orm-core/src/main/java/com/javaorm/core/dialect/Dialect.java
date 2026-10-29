package com.javaorm.core.dialect;

import com.javaorm.core.ast.DeleteStatement;
import com.javaorm.core.ast.InsertStatement;
import com.javaorm.core.ast.SelectStatement;
import com.javaorm.core.ast.UpdateStatement;
import com.javaorm.core.function.FunctionRenderer;
import com.javaorm.core.function.SqlFunction;
import com.javaorm.core.operator.OperatorRenderer;
import com.javaorm.core.operator.SqlOperator;

import java.util.List;

/**
 * 数据库方言接口（抽象不同数据库的标识符引用、分页语法、函数转译与特性）。
 */
public interface Dialect {
    String quote(String identifier);
    SqlAndParams buildSelect(SelectStatement stmt);
    SqlAndParams buildUpdate(UpdateStatement stmt);
    SqlAndParams buildDelete(DeleteStatement stmt);
    SqlAndParams buildInsert(InsertStatement stmt);

    /**
     * 注册或替换特定 SQL 函数的方言渲染器。
     */
    default void registerFunction(SqlFunction function, FunctionRenderer renderer) {}

    /**
     * 根据名称注册或替换 SQL 函数的方言渲染器。
     */
    default void registerFunction(String functionName, FunctionRenderer renderer) {
        registerFunction(SqlFunction.of(functionName), renderer);
    }

    /**
     * 获取指定 SQL 函数的方言渲染器。
     */
    default FunctionRenderer getFunctionRenderer(SqlFunction function) {
        return null;
    }

    /**
     * 根据名称获取 SQL 函数的方言渲染器。
     */
    default FunctionRenderer getFunctionRenderer(String functionName) {
        return getFunctionRenderer(SqlFunction.of(functionName));
    }

    /**
     * 注册或替换特定 SQL 操作符的方言渲染器。
     */
    default void registerOperator(SqlOperator operator, OperatorRenderer renderer) {}

    /**
     * 根据名称注册或替换 SQL 操作符的方言渲染器。
     */
    default void registerOperator(String operatorName, OperatorRenderer renderer) {
        registerOperator(SqlOperator.of(operatorName), renderer);
    }

    /**
     * 获取指定 SQL 操作符的方言渲染器。
     */
    default OperatorRenderer getOperatorRenderer(SqlOperator operator) {
        return null;
    }

    /**
     * 根据名称获取 SQL 操作符的方言渲染器。
     */
    default OperatorRenderer getOperatorRenderer(String operatorName) {
        return getOperatorRenderer(SqlOperator.of(operatorName));
    }

    /**
     * 渲染操作符符号或表达式。
     */
    default String renderOperator(SqlOperator operator) {
        return operator != null ? operator.getSql() : "";
    }

    /**
     * 渲染表达式或参数到 SQL 缓冲区（供自定义 FunctionRenderer 使用）。
     */
    default void renderArg(Object arg, StringBuilder sql, List<Object> params) {}
}
