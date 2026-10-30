package com.javaorm.core.operator;

/**
 * SQL 比较与逻辑断言操作符规范接口。
 * 解耦物理数据库操作符与 AST 逻辑表达，支持内置标准枚举（BuiltinSqlOperator）及自定义操作符，
 * 支持不同方言自由扩展与重写渲染逻辑。
 */
public interface SqlOperator {

    /**
     * 获取操作符在 SQL 中的默认表达（如 =, <>, LIKE, BETWEEN 等）。
     */
    String getSql();

    /**
     * 获取逻辑标识符名称（默认与 getSql() 相同）。
     */
    default String getName() {
        return getSql();
    }

    SqlOperator EQ = BuiltinSqlOperator.EQ;
    SqlOperator NE = BuiltinSqlOperator.NE;
    SqlOperator GT = BuiltinSqlOperator.GT;
    SqlOperator GE = BuiltinSqlOperator.GE;
    SqlOperator LT = BuiltinSqlOperator.LT;
    SqlOperator LE = BuiltinSqlOperator.LE;
    SqlOperator LIKE = BuiltinSqlOperator.LIKE;
    SqlOperator NOT_LIKE = BuiltinSqlOperator.NOT_LIKE;
    SqlOperator IN = BuiltinSqlOperator.IN;
    SqlOperator NOT_IN = BuiltinSqlOperator.NOT_IN;
    SqlOperator IS_NULL = BuiltinSqlOperator.IS_NULL;
    SqlOperator IS_NOT_NULL = BuiltinSqlOperator.IS_NOT_NULL;
    SqlOperator BETWEEN = BuiltinSqlOperator.BETWEEN;
    SqlOperator IS_EMPTY = BuiltinSqlOperator.IS_EMPTY;
    SqlOperator IS_NOT_EMPTY = BuiltinSqlOperator.IS_NOT_EMPTY;
    SqlOperator IS_BLANK = BuiltinSqlOperator.IS_BLANK;
    SqlOperator IS_NOT_BLANK = BuiltinSqlOperator.IS_NOT_BLANK;
    SqlOperator RANGE_CLOSED = BuiltinSqlOperator.RANGE_CLOSED;
    SqlOperator RANGE_OPEN = BuiltinSqlOperator.RANGE_OPEN;
    SqlOperator RANGE_CLOSED_OPEN = BuiltinSqlOperator.RANGE_CLOSED_OPEN;
    SqlOperator RANGE_OPEN_CLOSED = BuiltinSqlOperator.RANGE_OPEN_CLOSED;

    /**
     * 根据 SQL 符号或名称动态获取或创建 SqlOperator。
     */
    static SqlOperator of(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            throw new IllegalArgumentException("Operator SQL cannot be null or empty");
        }
        String trimmed = sql.trim();
        for (BuiltinSqlOperator op : BuiltinSqlOperator.values()) {
            if (op.getSql().equalsIgnoreCase(trimmed) || op.name().equalsIgnoreCase(trimmed)) {
                return op;
            }
        }
        return new SimpleSqlOperator(trimmed);
    }
}
