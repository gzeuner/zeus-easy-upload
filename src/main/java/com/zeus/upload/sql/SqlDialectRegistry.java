package com.zeus.upload.sql;

import com.zeus.upload.domain.ConnectionType;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.util.StringUtils;

/**
 * Resolves {@link SqlDialect} instances by product, connection type, or JDBC URL.
 * Does not open connections or store credentials.
 */
public class SqlDialectRegistry {

    private final Map<DatabaseProduct, SqlDialect> dialects;

    public SqlDialectRegistry(Map<DatabaseProduct, SqlDialect> dialects) {
        this.dialects = new EnumMap<>(DatabaseProduct.class);
        Objects.requireNonNull(dialects, "dialects").forEach((product, dialect) -> {
            if (dialect != null) {
                this.dialects.put(product, dialect);
            }
        });
        if (!this.dialects.containsKey(DatabaseProduct.DB2_I)) {
            this.dialects.put(DatabaseProduct.DB2_I, new Db2iDialect());
        }
        if (!this.dialects.containsKey(DatabaseProduct.H2)) {
            this.dialects.put(DatabaseProduct.H2, new H2Dialect());
        }
        if (!this.dialects.containsKey(DatabaseProduct.POSTGRES)) {
            this.dialects.put(DatabaseProduct.POSTGRES, new PostgresDialect());
        }
        if (!this.dialects.containsKey(DatabaseProduct.GENERIC_JDBC)) {
            this.dialects.put(DatabaseProduct.GENERIC_JDBC, new GenericJdbcDialect());
        }
    }

    public static SqlDialectRegistry createDefault() {
        Map<DatabaseProduct, SqlDialect> map = new EnumMap<>(DatabaseProduct.class);
        map.put(DatabaseProduct.DB2_I, new Db2iDialect());
        map.put(DatabaseProduct.H2, new H2Dialect());
        map.put(DatabaseProduct.POSTGRES, new PostgresDialect());
        map.put(DatabaseProduct.GENERIC_JDBC, new GenericJdbcDialect());
        return new SqlDialectRegistry(map);
    }

    public SqlDialect get(DatabaseProduct product) {
        DatabaseProduct key = product == null ? DatabaseProduct.GENERIC_JDBC : product;
        SqlDialect dialect = dialects.get(key);
        if (dialect == null) {
            return dialects.get(DatabaseProduct.GENERIC_JDBC);
        }
        return dialect;
    }

    public SqlDialect resolveFromJdbcUrl(String jdbcUrl) {
        return get(detectProduct(jdbcUrl));
    }

    public SqlDialect resolveFromConnectionType(ConnectionType type) {
        if (type == ConnectionType.DB2_400) {
            return get(DatabaseProduct.DB2_I);
        }
        return get(DatabaseProduct.GENERIC_JDBC);
    }

    /**
     * Heuristic product detection from JDBC URL. Does not validate reachability.
     */
    public static DatabaseProduct detectProduct(String jdbcUrl) {
        if (!StringUtils.hasText(jdbcUrl)) {
            return DatabaseProduct.GENERIC_JDBC;
        }
        String url = jdbcUrl.trim().toLowerCase(Locale.ROOT);
        if (url.startsWith("jdbc:as400:") || url.startsWith("jdbc:db2:")) {
            // jdbc:db2: often LUW; treat classic as400 as DB2_I. For pure LUW URLs
            // without as400 we still map to DB2_I dialect (MERGE-compatible enough).
            if (url.startsWith("jdbc:as400:")) {
                return DatabaseProduct.DB2_I;
            }
            return DatabaseProduct.DB2_I;
        }
        if (url.startsWith("jdbc:h2:")) {
            return DatabaseProduct.H2;
        }
        if (url.startsWith("jdbc:postgresql:") || url.startsWith("jdbc:pgsql:")) {
            return DatabaseProduct.POSTGRES;
        }
        return DatabaseProduct.GENERIC_JDBC;
    }
}
