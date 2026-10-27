package com.javaorm.core.dialect;

public class PostgreSQLDialect extends AbstractDialect {
    @Override
    public String quote(String identifier) {
        if (identifier == null) return null;
        if (identifier.startsWith("\"") && identifier.endsWith("\"")) return identifier;
        return "\"" + identifier + "\"";
    }
}
