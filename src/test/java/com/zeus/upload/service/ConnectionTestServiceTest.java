package com.zeus.upload.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeus.upload.domain.ConnectionProfileRequest;
import com.zeus.upload.domain.ConnectionTestResult;
import com.zeus.upload.domain.ConnectionType;
import com.zeus.upload.sql.SqlDialectRegistry;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ConnectionTestServiceTest {

    private static final String MASTER_KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void testsNamedH2JdbcProfileSuccessfully() throws Exception {
        var directory = Files.createTempDirectory("zeus-conn-test");
        var crypto = new ConnectionCryptoService(new ObjectMapper(), MASTER_KEY);
        var profiles = new ConnectionProfileService(new ObjectMapper(), directory, crypto);
        String url = "jdbc:h2:mem:conn_test_svc;MODE=DB2;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=true";
        var request = new ConnectionProfileRequest();
        request.setName("h2-test");
        request.setType(ConnectionType.DB2_400);
        request.setEndpoint(url);
        request.setCredentials(Map.of("username", "sa", "secret", ""));
        profiles.save(request);

        DataSource bootstrap = new DriverManagerDataSource(url, "sa", "");
        var dialect = SqlDialectRegistry.createDefault().resolveFromJdbcUrl(url);
        var sessions = new DbSessionFactory(
                bootstrap, dialect, profiles, new ConnectionDataSourceFactory(), SqlDialectRegistry.createDefault());
        var service = new ConnectionTestService(profiles, sessions);

        ConnectionTestResult result = service.test("h2-test");
        assertThat(result.isSuccess()).as(result.getMessage()).isTrue();
        assertThat(result.getDurationMs()).isGreaterThanOrEqualTo(0);
        assertThat(result.getDatabaseProductName()).containsIgnoringCase("H2");
    }

    @Test
    void sanitizeMasksPasswordFragments() {
        assertThat(ConnectionTestService.sanitize("login failed password=supersecret user=admin"))
                .doesNotContain("supersecret")
                .contains("password=***");
    }
}
