package com.zeus.upload.sql;

/**
 * H2 dialect for local development and automated tests (typically MODE=DB2).
 * Identifier policy mirrors IBM i create-table rules for predictable offline work.
 * Upsert is intentionally unsupported until a proven H2-compatible strategy is added.
 */
public class H2Dialect extends AbstractSqlDialect {

    @Override
    public DatabaseProduct product() {
        return DatabaseProduct.H2;
    }

    @Override
    public IdentifierPolicy identifierPolicy() {
        return IdentifierPolicy.h2Db2Compat();
    }

    @Override
    public String createSchemaSql(String libraryOrSchema) {
        return "CREATE SCHEMA IF NOT EXISTS " + quoteIdentifier(libraryOrSchema);
    }

    @Override
    public UpsertStrategy upsertStrategy() {
        return UpsertStrategy.UNSUPPORTED;
    }

    @Override
    public UpsertSql buildUpsertSql(
            String libraryOrSchema,
            String table,
            java.util.List<String> insertColumns,
            java.util.List<String> updateColumns,
            java.util.List<String> keyColumns
    ) {
        throw new UnsupportedOperationException(
                "Upsert is not supported for H2 in this version. "
                        + "Use INSERT/UPDATE/DELETE, or run against IBM i (DB2_I) for MERGE upsert."
        );
    }
}
