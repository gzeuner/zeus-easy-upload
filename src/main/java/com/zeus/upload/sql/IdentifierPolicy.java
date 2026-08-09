package com.zeus.upload.sql;

/**
 * Rules for sanitizing identifiers when creating or quoting table/column names.
 */
public final class IdentifierPolicy {

    private final int maxLength;
    private final boolean forceUpperCase;
    private final String emptyFallback;
    private final String leadingDigitPrefix;

    public IdentifierPolicy(int maxLength, boolean forceUpperCase, String emptyFallback, String leadingDigitPrefix) {
        if (maxLength < 1) {
            throw new IllegalArgumentException("maxLength must be >= 1");
        }
        this.maxLength = maxLength;
        this.forceUpperCase = forceUpperCase;
        this.emptyFallback = emptyFallback == null || emptyFallback.isBlank() ? "COL" : emptyFallback;
        this.leadingDigitPrefix = leadingDigitPrefix == null || leadingDigitPrefix.isBlank() ? "C_" : leadingDigitPrefix;
    }

    /**
     * IBM i system-name style: upper case, max 10 for created columns.
     * Leading non-letter prefix {@code T_} matches historic {@code Db2IdentifierUtil} quoting.
     */
    public static IdentifierPolicy ibmISystemNames() {
        return new IdentifierPolicy(10, true, "COL", "T_");
    }

    /** H2 in DB2-compat mode: keep IBM i-like names for predictable local tests. */
    public static IdentifierPolicy h2Db2Compat() {
        return ibmISystemNames();
    }

    /** PostgreSQL: longer identifiers, still uppercased for app consistency. */
    public static IdentifierPolicy postgres() {
        return new IdentifierPolicy(63, true, "COL", "C_");
    }

    /** Conservative generic JDBC defaults. */
    public static IdentifierPolicy genericJdbc() {
        return new IdentifierPolicy(30, true, "COL", "C_");
    }

    public int maxLength() {
        return maxLength;
    }

    public boolean forceUpperCase() {
        return forceUpperCase;
    }

    public String emptyFallback() {
        return emptyFallback;
    }

    public String leadingDigitPrefix() {
        return leadingDigitPrefix;
    }
}
