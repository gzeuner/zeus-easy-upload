package com.zeus.upload.domain;

import com.zeus.upload.sql.DatabaseProduct;

/**
 * Result of a connection profile connectivity test. Never contains secrets.
 */
public class ConnectionTestResult {

    private final boolean success;
    private final String connectionName;
    private final ConnectionType type;
    private final DatabaseProduct product;
    private final String message;
    private final String databaseProductName;
    private final long durationMs;

    public ConnectionTestResult(
            boolean success,
            String connectionName,
            ConnectionType type,
            DatabaseProduct product,
            String message,
            String databaseProductName,
            long durationMs
    ) {
        this.success = success;
        this.connectionName = connectionName;
        this.type = type;
        this.product = product;
        this.message = message;
        this.databaseProductName = databaseProductName;
        this.durationMs = durationMs;
    }

    public static ConnectionTestResult ok(
            String name,
            ConnectionType type,
            DatabaseProduct product,
            String databaseProductName,
            long durationMs
    ) {
        return new ConnectionTestResult(
                true, name, type, product,
                "Connection successful.",
                databaseProductName, durationMs);
    }

    public static ConnectionTestResult failure(
            String name,
            ConnectionType type,
            DatabaseProduct product,
            String message,
            long durationMs
    ) {
        return new ConnectionTestResult(false, name, type, product, message, null, durationMs);
    }

    public boolean isSuccess() { return success; }
    public String getConnectionName() { return connectionName; }
    public ConnectionType getType() { return type; }
    public DatabaseProduct getProduct() { return product; }
    public String getMessage() { return message; }
    public String getDatabaseProductName() { return databaseProductName; }
    public long getDurationMs() { return durationMs; }
}
