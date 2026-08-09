package com.zeus.upload.service;

import com.zeus.upload.domain.ConnectionProfile;
import com.zeus.upload.domain.ConnectionType;
import com.zeus.upload.sql.DatabaseProduct;
import com.zeus.upload.sql.SqlDialectRegistry;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Builds JDBC {@link DataSource} instances from a connection profile endpoint and
 * decrypted credentials. Never logs secrets.
 */
@Component
public class ConnectionDataSourceFactory {

    /** Small pools per profile — enough for concurrent jobs without holding many idle connections. */
    static final int MAX_POOL_SIZE = 5;
    static final int MIN_IDLE = 0;
    static final long IDLE_TIMEOUT_MS = 60_000L;
    static final long MAX_LIFETIME_MS = 300_000L;
    static final long CONNECTION_TIMEOUT_MS = 15_000L;

    /**
     * Unpooled DataSource (tests / one-shot use). Prefer {@link #createPooled} for runtime.
     */
    public DataSource create(ConnectionProfile profile, Map<String, String> credentials) {
        ResolvedJdbc target = resolve(profile, credentials);
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(target.url());
        if (target.username() != null) {
            dataSource.setUsername(target.username());
        }
        if (target.password() != null) {
            dataSource.setPassword(target.password());
        }
        if (target.driverClassName() != null) {
            dataSource.setDriverClassName(target.driverClassName());
        }
        return dataSource;
    }

    /**
     * Hikari pool for named profiles. Caller owns lifecycle (via {@link ConnectionPoolCache}).
     */
    public HikariDataSource createPooled(ConnectionProfile profile, Map<String, String> credentials) {
        ResolvedJdbc target = resolve(profile, credentials);
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(target.url());
        if (target.username() != null) {
            config.setUsername(target.username());
        }
        if (target.password() != null) {
            config.setPassword(target.password());
        }
        if (target.driverClassName() != null) {
            config.setDriverClassName(target.driverClassName());
        }
        config.setPoolName("zeus-profile-" + sanitizePoolName(profile.getName()));
        config.setMaximumPoolSize(MAX_POOL_SIZE);
        config.setMinimumIdle(MIN_IDLE);
        config.setIdleTimeout(IDLE_TIMEOUT_MS);
        config.setMaxLifetime(MAX_LIFETIME_MS);
        config.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
        config.setInitializationFailTimeout(-1);
        return new HikariDataSource(config);
    }

    private ResolvedJdbc resolve(ConnectionProfile profile, Map<String, String> credentials) {
        Objects.requireNonNull(profile, "profile");
        if (profile.getType() == null || !profile.getType().isJdbc()) {
            throw new IllegalArgumentException(
                    "Connection profile '" + profile.getName() + "' is not a JDBC profile and cannot be used as a DataSource.");
        }
        String url = profile.getEndpoint() == null ? "" : profile.getEndpoint().trim();
        if (!StringUtils.hasText(url) || !url.toLowerCase(Locale.ROOT).startsWith("jdbc:")) {
            throw new IllegalArgumentException(
                    "Connection profile '" + profile.getName() + "' requires a jdbc: endpoint URL.");
        }

        Map<String, String> creds = credentials == null ? Map.of() : credentials;
        String username = firstNonBlank(creds, "username", "user");
        String password = firstNonBlank(creds, "password", "secret");
        return new ResolvedJdbc(url, username, password, resolveDriverClass(url));
    }

    static String resolveDriverClass(String jdbcUrl) {
        DatabaseProduct product = SqlDialectRegistry.detectProduct(jdbcUrl);
        return switch (product) {
            case DB2_I -> "com.ibm.as400.access.AS400JDBCDriver";
            case H2 -> "org.h2.Driver";
            case POSTGRES -> "org.postgresql.Driver";
            case GENERIC_JDBC -> null;
        };
    }

    private static String sanitizePoolName(String name) {
        if (!StringUtils.hasText(name)) {
            return "unknown";
        }
        return name.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private static String firstNonBlank(Map<String, String> map, String... keys) {
        for (String key : keys) {
            String value = map.get(key);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private record ResolvedJdbc(String url, String username, String password, String driverClassName) {
    }
}
