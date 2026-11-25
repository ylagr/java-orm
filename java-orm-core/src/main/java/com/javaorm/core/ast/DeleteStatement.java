package com.javaorm.core.ast;

public class DeleteStatement extends AbstractConditionalStatement {

    public DeleteStatement(Class<?> entityClass, String tableName) {
        super(entityClass, tableName);
    }

    public DeleteStatement copyWithTableName(String newTableName) {
        DeleteStatement copy = new DeleteStatement(this.entityClass, newTableName);
        copyConditionalBaseTo(copy);
        return copy;
    }
}
