package com.zeus.upload.sql;

import java.util.Objects;

/**
 * Generated upsert statement plus strategy metadata (dry-run / UI).
 */
public final class UpsertSql {

    private final String sql;
    private final UpsertStrategy strategy;

    public UpsertSql(String sql, UpsertStrategy strategy) {
        this.sql = Objects.requireNonNull(sql, "sql");
        this.strategy = Objects.requireNonNull(strategy, "strategy");
    }

    public String sql() {
        return sql;
    }

    public UpsertStrategy strategy() {
        return strategy;
    }
}
