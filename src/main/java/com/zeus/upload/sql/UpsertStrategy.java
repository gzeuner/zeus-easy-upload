package com.zeus.upload.sql;

/**
 * How a dialect implements upsert. Services must not hardcode product SQL.
 */
public enum UpsertStrategy {
    /** IBM i style: MERGE INTO … USING (VALUES (…)) AS S(…). */
    DB2_MERGE_VALUES,
    /** PostgreSQL style: INSERT … ON CONFLICT (…) DO UPDATE. */
    POSTGRES_ON_CONFLICT,
    /** Dialect does not support upsert; callers must fail closed. */
    UNSUPPORTED
}
