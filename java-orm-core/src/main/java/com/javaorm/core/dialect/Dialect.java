package com.javaorm.core.dialect;

import com.javaorm.core.ast.DeleteStatement;
import com.javaorm.core.ast.InsertStatement;
import com.javaorm.core.ast.SelectStatement;
import com.javaorm.core.ast.UpdateStatement;

/**
 * 数据库方言接口（抽象不同数据库的标识符引用、分页语法与特性）。
 */
public interface Dialect {
    String quote(String identifier);
    SqlAndParams buildSelect(SelectStatement stmt);
    SqlAndParams buildUpdate(UpdateStatement stmt);
    SqlAndParams buildDelete(DeleteStatement stmt);
    SqlAndParams buildInsert(InsertStatement stmt);
}
