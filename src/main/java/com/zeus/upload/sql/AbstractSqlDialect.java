package com.zeus.upload.sql;

/**
 * Shared quoting helpers for dialects that use double-quoted identifiers.
 */
public abstract class AbstractSqlDialect implements SqlDialect {

    @Override
    public String quoteIdentifier(String identifier) {
        String normalized = normalizeIdentifier(identifier);
        return "\"" + normalized.replace("\"", "\"\"") + "\"";
    }

    @Override
    public String qualifyTable(String libraryOrSchema, String table) {
        return quoteIdentifier(libraryOrSchema) + "." + quoteIdentifier(table);
    }
}
