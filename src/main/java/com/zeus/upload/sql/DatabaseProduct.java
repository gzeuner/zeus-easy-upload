package com.zeus.upload.sql;

/**
 * Supported database products for dialect selection.
 * Independent of GUI {@code ConnectionType}; map connection endpoints to a product via
 * {@link SqlDialectRegistry}.
 */
public enum DatabaseProduct {
    /** IBM i DB2/400 (jt400). First-class production target. */
    DB2_I,
    /** Embedded H2 (local/dev/test, often MODE=DB2). */
    H2,
    /** PostgreSQL. */
    POSTGRES,
    /**
     * Unknown or not yet specialized JDBC product.
     * Portable DML only; upsert is fail-closed.
     */
    GENERIC_JDBC
}
