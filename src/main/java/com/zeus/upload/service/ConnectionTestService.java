package com.zeus.upload.service;

import com.zeus.upload.domain.ConnectionProfile;
import com.zeus.upload.domain.ConnectionTestResult;
import com.zeus.upload.domain.ConnectionType;
import com.zeus.upload.sql.DatabaseProduct;
import com.zeus.upload.sql.SqlDialectRegistry;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Tests named connection profiles without returning secrets.
 */
@Service
public class ConnectionTestService {

    private static final Logger log = LoggerFactory.getLogger(ConnectionTestService.class);

    private final ConnectionProfileService profileService;
    private final DbSessionFactory dbSessionFactory;

    public ConnectionTestService(ConnectionProfileService profileService, DbSessionFactory dbSessionFactory) {
        this.profileService = profileService;
        this.dbSessionFactory = dbSessionFactory;
    }

    public ConnectionTestResult test(String connectionName) {
        long started = System.currentTimeMillis();
        try {
            ConnectionProfile profile = profileService.load(connectionName);
            if (profile.getType() == ConnectionType.REST) {
                return ConnectionTestResult.failure(
                        profile.getName(),
                        ConnectionType.REST,
                        DatabaseProduct.GENERIC_JDBC,
                        "REST connection tests are not implemented yet. Use a JDBC/DB2 profile.",
                        System.currentTimeMillis() - started
                );
            }

            DatabaseProduct product = SqlDialectRegistry.detectProduct(profile.getEndpoint());
            if (product == DatabaseProduct.GENERIC_JDBC && profile.getType() == ConnectionType.DB2_400) {
                product = DatabaseProduct.DB2_I;
            }

            try (DbSession session = dbSessionFactory.open(profile.getName());
                 Connection connection = session.dataSource().getConnection()) {
                DatabaseMetaData meta = connection.getMetaData();
                String productName = safe(meta.getDatabaseProductName()) + " " + safe(meta.getDatabaseProductVersion());
                boolean valid = connection.isValid(5);
                long duration = System.currentTimeMillis() - started;
                if (!valid) {
                    return ConnectionTestResult.failure(
                            profile.getName(), profile.getType(), session.product(),
                            "Driver reported connection as not valid.", duration);
                }
                log.info("Connection test OK for profile '{}' product={}", profile.getName(), session.product());
                return ConnectionTestResult.ok(
                        profile.getName(), profile.getType(), session.product(), productName.trim(), duration);
            }
        } catch (NoSuchFileException ex) {
            return ConnectionTestResult.failure(
                    connectionName, null, null, "Connection profile not found.", System.currentTimeMillis() - started);
        } catch (IOException ex) {
            return ConnectionTestResult.failure(
                    connectionName, null, null,
                    "Could not load connection profile: " + sanitize(ex.getMessage()),
                    System.currentTimeMillis() - started);
        } catch (Exception ex) {
            log.warn("Connection test failed for profile '{}': {}", connectionName, sanitize(ex.getMessage()));
            return ConnectionTestResult.failure(
                    connectionName, null, null,
                    "Connection failed: " + sanitize(ex.getMessage()),
                    System.currentTimeMillis() - started);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    /** Strip likely credential fragments from driver error messages. */
    static String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "unknown error";
        }
        String cleaned = message
                .replaceAll("(?i)password\\s*=\\s*[^;\\s]+", "password=***")
                .replaceAll("(?i)pwd\\s*=\\s*[^;\\s]+", "pwd=***")
                .replaceAll("(?i)user(?:name)?\\s*=\\s*[^;\\s]+", "user=***");
        if (cleaned.length() > 400) {
            cleaned = cleaned.substring(0, 400) + "…";
        }
        return cleaned;
    }
}
