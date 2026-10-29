package com.javaorm.core.dialect;

public class MySQLDialect extends AbstractDialect {
    @Override
    public String quote(String identifier) {
        if (identifier == null) return null;
        String clean = identifier;
        if (clean.length() >= 2 && clean.startsWith("`") && clean.endsWith("`")) {
            clean = clean.substring(1, clean.length() - 1);
        }
        return "`" + clean.replace("`", "``") + "`";
    }
}
