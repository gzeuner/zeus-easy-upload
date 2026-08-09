package com.zeus.upload.service;

import com.zeus.upload.domain.ConnectionProfile;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Caches small Hikari connection pools per JDBC connection profile.
 * Pools are reused across import/metadata operations and recreated when the
 * profile fingerprint (endpoint/type/updatedAt/credentials) changes.
 * Never logs credentials.
 */
@Service
public class ConnectionPoolCache {

    private static final Logger log = LoggerFactory.getLogger(ConnectionPoolCache.class);

    private final ConnectionDataSourceFactory dataSourceFactory;
    private final ConcurrentHashMap<String, CachedPool> pools = new ConcurrentHashMap<>();

    public ConnectionPoolCache(ConnectionDataSourceFactory dataSourceFactory) {
        this.dataSourceFactory = Objects.requireNonNull(dataSourceFactory);
    }

    /**
     * Return a pooled DataSource for the profile. Thread-safe; may create a new pool
     * when the fingerprint differs from the cached entry.
     */
    public DataSource getOrCreate(ConnectionProfile profile, Map<String, String> credentials) {
        Objects.requireNonNull(profile, "profile");
        String name = profile.getName();
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("Connection profile name must not be blank");
        }
        String fingerprint = fingerprint(profile, credentials);

        CachedPool cached = pools.compute(name, (key, existing) -> {
            if (existing != null && existing.fingerprint().equals(fingerprint) && isRunning(existing.dataSource())) {
                return existing;
            }
            if (existing != null) {
                log.info("Recreating connection pool for profile '{}' (fingerprint changed or pool closed)", key);
                closeQuietly(existing.dataSource());
            } else {
                log.info("Creating connection pool for profile '{}'", key);
            }
            HikariDataSource dataSource = dataSourceFactory.createPooled(profile, credentials);
            return new CachedPool(fingerprint, dataSource);
        });
        return cached.dataSource();
    }

    public void invalidate(String connectionProfileName) {
        if (!StringUtils.hasText(connectionProfileName)) {
            return;
        }
        String name = connectionProfileName.trim();
        CachedPool removed = pools.remove(name);
        if (removed != null) {
            log.info("Invalidating connection pool for profile '{}'", name);
            closeQuietly(removed.dataSource());
        }
    }

    public void invalidateAll() {
        for (String name : pools.keySet()) {
            invalidate(name);
        }
    }

    /** Visible for tests. */
    int size() {
        return pools.size();
    }

    /** Visible for tests. */
    boolean contains(String connectionProfileName) {
        return pools.containsKey(connectionProfileName);
    }

    @PreDestroy
    void shutdown() {
        invalidateAll();
    }

    /**
     * Stable identity for cache reuse. Includes a digest of credentials so password
     * rotations force a new pool without storing secrets in the cache key text.
     */
    static String fingerprint(ConnectionProfile profile, Map<String, String> credentials) {
        StringBuilder material = new StringBuilder();
        material.append(nullToEmpty(profile.getName())).append('\n');
        material.append(profile.getType() == null ? "" : profile.getType().name()).append('\n');
        material.append(nullToEmpty(profile.getEndpoint())).append('\n');
        material.append(nullToEmpty(profile.getUpdatedAt())).append('\n');
        if (credentials != null && !credentials.isEmpty()) {
            credentials.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> material
                            .append(nullToEmpty(entry.getKey()))
                            .append('=')
                            .append(nullToEmpty(entry.getValue()))
                            .append('\n'));
        }
        return sha256Hex(material.toString());
    }

    private static boolean isRunning(HikariDataSource dataSource) {
        return dataSource != null && !dataSource.isClosed();
    }

    private static void closeQuietly(HikariDataSource dataSource) {
        if (dataSource == null || dataSource.isClosed()) {
            return;
        }
        try {
            dataSource.close();
        } catch (Exception ex) {
            log.warn("Failed to close connection pool {}: {}", dataSource.getPoolName(), ex.getMessage());
        }
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            // Unreachable on standard JVMs; fall back to identity hash without secrets length only.
            return Integer.toHexString(value.hashCode());
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record CachedPool(String fingerprint, HikariDataSource dataSource) {
    }
}
