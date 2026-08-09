package com.zeus.upload.sql;

/**
 * Fail-closed dialect for unrecognized JDBC products.
 * Supports portable INSERT/UPDATE/DELETE; upsert is unsupported.
 */
public class GenericJdbcDialect extends AbstractSqlDialect {

    @Override
    public DatabaseProduct product() {
        return DatabaseProduct.GENERIC_JDBC;
    }

    @Override
    public IdentifierPolicy identifierPolicy() {
        return IdentifierPolicy.genericJdbc();
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
                "Upsert is not supported for GENERIC_JDBC. Add a product-specific SqlDialect."
        );
    }
}
