package com.javaorm.core.ast;

public class UnionClause {
    private final boolean unionAll;
    private final Object query; // Query<?>

    public UnionClause(boolean unionAll, Object query) {
        this.unionAll = unionAll;
        this.query = query;
    }

    public UnionClause(Object query, boolean unionAll) {
        this.unionAll = unionAll;
        this.query = query;
    }

    public boolean isUnionAll() {
        return unionAll;
    }

    public Object getQuery() {
        return query;
    }
}
