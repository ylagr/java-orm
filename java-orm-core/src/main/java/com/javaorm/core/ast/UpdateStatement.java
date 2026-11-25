package com.javaorm.core.ast;

import java.util.LinkedHashMap;
import java.util.Map;

public class UpdateStatement extends AbstractConditionalStatement {

    private final Map<String, Object> setValues = new LinkedHashMap<>();

    public UpdateStatement(Class<?> entityClass, String tableName) {
        super(entityClass, tableName);
    }

    public void addSet(String column, Object value) {
        setValues.put(column, value);
    }

    public Map<String, Object> getSetValues() {
        return setValues;
    }

    public UpdateStatement copyWithTableName(String newTableName) {
        UpdateStatement copy = new UpdateStatement(this.entityClass, newTableName);
        copy.setValues.putAll(this.setValues);
        copyConditionalBaseTo(copy);
        return copy;
    }
}
