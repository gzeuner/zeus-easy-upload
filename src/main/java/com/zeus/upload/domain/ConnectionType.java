package com.zeus.upload.domain;

public enum ConnectionType {
    /** IBM i DB2/400 (jt400 JDBC). */
    DB2_400,
    /** PostgreSQL JDBC. */
    POSTGRES,
    /** HTTP(S) REST API. */
    REST;

    public boolean isJdbc() {
        return this == DB2_400 || this == POSTGRES;
    }
}
