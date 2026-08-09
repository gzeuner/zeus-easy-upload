package com.zeus.upload.service;

import com.zeus.upload.sql.DatabaseProduct;
import com.zeus.upload.sql.SqlDialect;
import java.util.Objects;
import javax.sql.DataSource;

/**
 * Resolved JDBC execution context: DataSource + dialect for one import/metadata operation.
 * Does not expose credentials. Close when the operation finishes.
 * Named profile sessions use a shared pool from {@link ConnectionPoolCache}; session close
 * does not shut that pool down (closer is null). Bootstrap sessions are also a no-op on close.
 */
public final class DbSession implements AutoCloseable {

    private final DataSource dataSource;
    private final SqlDialect dialect;
    private final String connectionProfileName;
    private final DatabaseProduct product;
    private final AutoCloseable closer;
    private boolean closed;

    public DbSession(
            DataSource dataSource,
            SqlDialect dialect,
            String connectionProfileName,
            DatabaseProduct product,
            AutoCloseable closer
    ) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        this.connectionProfileName = connectionProfileName;
        this.product = Objects.requireNonNull(product, "product");
        this.closer = closer;
    }

    public DataSource dataSource() {
        return dataSource;
    }

    public SqlDialect dialect() {
        return dialect;
    }

    /** {@code null} when using the application bootstrap DataSource. */
    public String connectionProfileName() {
        return connectionProfileName;
    }

    public boolean isBootstrap() {
        return connectionProfileName == null || connectionProfileName.isBlank();
    }

    public DatabaseProduct product() {
        return product;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (closer != null) {
            try {
                closer.close();
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to close database session resources: " + ex.getMessage(), ex);
            }
        }
    }
}
