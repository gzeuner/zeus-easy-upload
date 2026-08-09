package com.zeus.upload.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeus.upload.domain.ConnectionProfile;
import com.zeus.upload.domain.ConnectionType;
import com.zaxxer.hikari.HikariDataSource;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ConnectionPoolCacheTest {

    private final ConnectionPoolCache cache = new ConnectionPoolCache(new ConnectionDataSourceFactory());

    @AfterEach
    void tearDown() {
        cache.invalidateAll();
    }

    @Test
    void reusesSamePoolForIdenticalProfileFingerprint() {
        ConnectionProfile profile = profile("pool-a", "jdbc:h2:mem:pool_cache_a;DB_CLOSE_DELAY=-1", "t1");
        Map<String, String> credentials = Map.of("username", "sa", "secret", "");

        DataSource first = cache.getOrCreate(profile, credentials);
        DataSource second = cache.getOrCreate(profile, credentials);

        assertThat(second).isSameAs(first);
        assertThat(first).isInstanceOf(HikariDataSource.class);
        assertThat(cache.size()).isEqualTo(1);
        assertThat(((HikariDataSource) first).getPoolName()).isEqualTo("zeus-profile-pool-a");
    }

    @Test
    void recreatesPoolWhenFingerprintChanges() {
        ConnectionProfile v1 = profile("pool-b", "jdbc:h2:mem:pool_cache_b1;DB_CLOSE_DELAY=-1", "t1");
        Map<String, String> credentials = Map.of("username", "sa", "secret", "");
        DataSource first = cache.getOrCreate(v1, credentials);

        ConnectionProfile v2 = profile("pool-b", "jdbc:h2:mem:pool_cache_b2;DB_CLOSE_DELAY=-1", "t2");
        DataSource second = cache.getOrCreate(v2, credentials);

        assertThat(second).isNotSameAs(first);
        assertThat(((HikariDataSource) first).isClosed()).isTrue();
        assertThat(((HikariDataSource) second).isClosed()).isFalse();
        assertThat(cache.size()).isEqualTo(1);
    }

    @Test
    void invalidateClosesAndRemovesPool() {
        ConnectionProfile profile = profile("pool-c", "jdbc:h2:mem:pool_cache_c;DB_CLOSE_DELAY=-1", "t1");
        HikariDataSource ds = (HikariDataSource) cache.getOrCreate(profile, Map.of("username", "sa"));
        assertThat(cache.contains("pool-c")).isTrue();

        cache.invalidate("pool-c");

        assertThat(cache.contains("pool-c")).isFalse();
        assertThat(ds.isClosed()).isTrue();
        assertThat(cache.size()).isZero();
    }

    @Test
    void fingerprintDiffersWhenCredentialsChange() {
        ConnectionProfile profile = profile("pool-d", "jdbc:h2:mem:x", "t1");
        String withSecretA = ConnectionPoolCache.fingerprint(profile, Map.of("secret", "super-secret-alpha"));
        String withSecretB = ConnectionPoolCache.fingerprint(profile, Map.of("secret", "super-secret-beta"));
        assertThat(withSecretA).isNotEqualTo(withSecretB);
        assertThat(withSecretA).doesNotContain("super-secret");
        assertThat(withSecretB).doesNotContain("super-secret");
    }

    private static ConnectionProfile profile(String name, String endpoint, String updatedAt) {
        return new ConnectionProfile(name, ConnectionType.DB2_400, endpoint, null, true, updatedAt);
    }
}
