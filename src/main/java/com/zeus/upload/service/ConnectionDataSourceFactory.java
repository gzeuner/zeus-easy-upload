package com.zeus.upload.service;

import com.zeus.upload.domain.ConnectionProfile;
import com.zeus.upload.domain.ConnectionType;
import com.zeus.upload.sql.DatabaseProduct;
import com.zeus.upload.sql.SqlDialectRegistry;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Builds a non-pooled {@link DataSource} from a connection profile endpoint and
 * decrypted credentials. Never logs secrets.
 */
@Component
public class ConnectionDataSourceFactory {

    public DataSource create(ConnectionProfile profile, Map<String, String> credentials) {
        Objects.requireNonNull(profile, "profile");
        if (profile.getType() == ConnectionType.REST) {
            throw new IllegalArgumentException(
                    "Connection profile '" + profile.getName() + "' is REST and cannot be used as a JDBC DataSource.");
        }
        String url = profile.getEndpoint() == null ? "" : profile.getEndpoint().trim();
        if (!StringUtils.hasText(url) || !url.toLowerCase(Locale.ROOT).startsWith("jdbc:")) {
            throw new IllegalArgumentException(
                    "Connection profile '" + profile.getName() + "' requires a jdbc: endpoint URL.");
        }

        Map<String, String> creds = credentials == null ? Map.of() : credentials;
        String username = firstNonBlank(creds, "username", "user");
        String password = firstNonBlank(creds, "password", "secret");

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(url);
        if (username != null) {
            dataSource.setUsername(username);
        }
        if (password != null) {
            dataSource.setPassword(password);
        }

        String driver = resolveDriverClass(url);
        if (driver != null) {
            dataSource.setDriverClassName(driver);
        }
        return dataSource;
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

    private static String firstNonBlank(Map<String, String> map, String... keys) {
        for (String key : keys) {
            String value = map.get(key);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
