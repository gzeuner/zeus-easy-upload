package com.zeus.upload.service;

import com.zeus.upload.domain.ConnectionProfile;
import com.zeus.upload.domain.ConnectionTestResult;
import com.zeus.upload.domain.ConnectionType;
import com.zeus.upload.sql.DatabaseProduct;
import com.zeus.upload.sql.SqlDialectRegistry;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Tests named connection profiles without returning secrets.
 * Supports JDBC/DB2/PostgreSQL sessions and lightweight REST HTTP checks.
 */
@Service
public class ConnectionTestService {

    private static final Logger log = LoggerFactory.getLogger(ConnectionTestService.class);
    private static final Duration REST_TIMEOUT = Duration.ofSeconds(10);

    private final ConnectionProfileService profileService;
    private final DbSessionFactory dbSessionFactory;
    private final HttpClient httpClient;

    @Autowired
    public ConnectionTestService(ConnectionProfileService profileService, DbSessionFactory dbSessionFactory) {
        this(profileService, dbSessionFactory, HttpClient.newBuilder()
                .connectTimeout(REST_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    /** Package-visible for tests that inject a custom {@link HttpClient}. */
    ConnectionTestService(
            ConnectionProfileService profileService,
            DbSessionFactory dbSessionFactory,
            HttpClient httpClient
    ) {
        this.profileService = profileService;
        this.dbSessionFactory = dbSessionFactory;
        this.httpClient = httpClient;
    }

    public ConnectionTestResult test(String connectionName) {
        long started = System.currentTimeMillis();
        try {
            ConnectionProfile profile = profileService.load(connectionName);
            if (profile.getType() == ConnectionType.REST) {
                return testRest(profile, started);
            }
            return testJdbc(profile, started);
        } catch (NoSuchFileException | java.io.FileNotFoundException ex) {
            return ConnectionTestResult.failure(
                    connectionName, null, null, "Connection profile not found.", System.currentTimeMillis() - started);
        } catch (IOException ex) {
            // Defensive: some IO paths still surface missing files as generic IOException.
            String detail = sanitize(ex.getMessage());
            if (detail.toLowerCase(Locale.ROOT).contains("no such file")
                    || detail.toLowerCase(Locale.ROOT).contains("cannot find")
                    || detail.toLowerCase(Locale.ROOT).contains("not found")) {
                return ConnectionTestResult.failure(
                        connectionName, null, null, "Connection profile not found.",
                        System.currentTimeMillis() - started);
            }
            return ConnectionTestResult.failure(
                    connectionName, null, null,
                    "Could not load connection profile: " + detail,
                    System.currentTimeMillis() - started);
        } catch (Exception ex) {
            log.warn("Connection test failed for profile '{}': {}", connectionName, sanitize(ex.getMessage()));
            return ConnectionTestResult.failure(
                    connectionName, null, null,
                    "Connection failed: " + sanitize(ex.getMessage()),
                    System.currentTimeMillis() - started);
        }
    }

    private ConnectionTestResult testJdbc(ConnectionProfile profile, long started) {
        try (DbSession session = dbSessionFactory.open(profile.getName());
             Connection connection = session.dataSource().getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            String productName = (safe(meta.getDatabaseProductName()) + " " + safe(meta.getDatabaseProductVersion())).trim();
            boolean valid = connection.isValid(5);
            long duration = System.currentTimeMillis() - started;
            if (!valid) {
                return ConnectionTestResult.failure(
                        profile.getName(), profile.getType(), session.product(),
                        "Driver reported connection as not valid.", duration);
            }
            log.info("JDBC connection test OK for profile '{}' product={}", profile.getName(), session.product());
            return ConnectionTestResult.ok(
                    profile.getName(), profile.getType(), session.product(), productName, duration);
        } catch (Exception ex) {
            log.warn("JDBC connection test failed for profile '{}': {}", profile.getName(), sanitize(ex.getMessage()));
            DatabaseProduct product = SqlDialectRegistry.detectProduct(profile.getEndpoint());
            return ConnectionTestResult.failure(
                    profile.getName(), profile.getType(), product,
                    "Connection failed: " + sanitize(ex.getMessage()),
                    System.currentTimeMillis() - started);
        }
    }

    private ConnectionTestResult testRest(ConnectionProfile profile, long started) {
        try {
            Map<String, String> credentials = profileService.loadCredentials(profile.getName());
            URI endpoint = URI.create(profile.getEndpoint().trim());
            validateRestEndpoint(endpoint);

            HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                    .timeout(REST_TIMEOUT)
                    .GET()
                    .header("Accept", "*/*")
                    .header("User-Agent", "zeus-easy-upload-connection-test");

            String authorization = buildAuthorizationHeader(credentials);
            if (authorization != null) {
                builder.header("Authorization", authorization);
            }

            HttpResponse<Void> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            long duration = System.currentTimeMillis() - started;
            String statusLabel = "HTTP " + status;

            if (status >= 200 && status <= 399) {
                log.info("REST connection test OK for profile '{}' status={}", profile.getName(), status);
                return ConnectionTestResult.ok(
                        profile.getName(), ConnectionType.REST, null, statusLabel, duration);
            }
            if (status == 401 || status == 403) {
                return ConnectionTestResult.failure(
                        profile.getName(), ConnectionType.REST, null,
                        "Endpoint reachable but authentication failed (" + statusLabel + ").",
                        duration);
            }
            return ConnectionTestResult.failure(
                    profile.getName(), ConnectionType.REST, null,
                    "Unexpected response status " + statusLabel + ".",
                    duration);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return ConnectionTestResult.failure(
                    profile.getName(), ConnectionType.REST, null,
                    "REST connection test was interrupted.",
                    System.currentTimeMillis() - started);
        } catch (Exception ex) {
            log.warn("REST connection test failed for profile '{}': {}", profile.getName(), sanitize(ex.getMessage()));
            return ConnectionTestResult.failure(
                    profile.getName(), ConnectionType.REST, null,
                    "REST connection failed: " + sanitize(ex.getMessage()),
                    System.currentTimeMillis() - started);
        }
    }

    static void validateRestEndpoint(URI endpoint) {
        if (endpoint == null || endpoint.getScheme() == null) {
            throw new IllegalArgumentException("REST endpoint is invalid.");
        }
        String scheme = endpoint.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("REST connections require an HTTP(S) endpoint.");
        }
        if (endpoint.getUserInfo() != null || endpoint.getFragment() != null) {
            throw new IllegalArgumentException("REST endpoint must not contain credentials or fragments.");
        }
        if (!StringUtils.hasText(endpoint.getHost())) {
            throw new IllegalArgumentException("REST endpoint host is required.");
        }
    }

    /**
     * Basic when username+secret present; Bearer when only secret is present.
     */
    static String buildAuthorizationHeader(Map<String, String> credentials) {
        if (credentials == null || credentials.isEmpty()) {
            return null;
        }
        String username = firstNonBlank(credentials, "username", "user");
        String secret = firstNonBlank(credentials, "secret", "password", "token");
        if (!StringUtils.hasText(secret) && !StringUtils.hasText(username)) {
            return null;
        }
        if (StringUtils.hasText(username) && StringUtils.hasText(secret)) {
            String basic = username + ":" + secret;
            return "Basic " + Base64.getEncoder().encodeToString(basic.getBytes(StandardCharsets.UTF_8));
        }
        if (StringUtils.hasText(secret)) {
            return "Bearer " + secret;
        }
        return null;
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

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    /** Strip likely credential fragments from error messages. */
    static String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "unknown error";
        }
        String cleaned = message
                .replaceAll("(?i)password\\s*=\\s*[^;\\s]+", "password=***")
                .replaceAll("(?i)pwd\\s*=\\s*[^;\\s]+", "pwd=***")
                .replaceAll("(?i)user(?:name)?\\s*=\\s*[^;\\s]+", "user=***")
                .replaceAll("(?i)Bearer\\s+[A-Za-z0-9._\\-]+", "Bearer ***")
                .replaceAll("(?i)Basic\\s+[A-Za-z0-9+/=]+", "Basic ***");
        if (cleaned.length() > 400) {
            cleaned = cleaned.substring(0, 400) + "…";
        }
        return cleaned;
    }
}
