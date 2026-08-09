package com.zeus.upload.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.zeus.upload.domain.ConnectionProfileRequest;
import com.zeus.upload.domain.ConnectionTestResult;
import com.zeus.upload.domain.ConnectionType;
import com.zeus.upload.sql.SqlDialectRegistry;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.Executors;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ConnectionTestServiceRestTest {

    private static final String MASTER_KEY = Base64.getEncoder().encodeToString(new byte[32]);

    private HttpServer server;
    private int port;
    private ConnectionProfileService profiles;
    private ConnectionTestService service;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok", exchange -> {
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/secure", exchange -> {
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            String expected = "Basic " + Base64.getEncoder()
                    .encodeToString("svc:s3cret".getBytes(StandardCharsets.UTF_8));
            if (expected.equals(auth)) {
                exchange.sendResponseHeaders(204, -1);
            } else {
                exchange.sendResponseHeaders(401, -1);
            }
            exchange.close();
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        port = server.getAddress().getPort();

        var directory = Files.createTempDirectory("zeus-rest-test");
        var crypto = new ConnectionCryptoService(new ObjectMapper(), MASTER_KEY);
        profiles = new ConnectionProfileService(new ObjectMapper(), directory, crypto);
        DataSource bootstrap = new DriverManagerDataSource("jdbc:h2:mem:rest_test_bootstrap;DB_CLOSE_DELAY=-1", "sa", "");
        var dialect = SqlDialectRegistry.createDefault().get(com.zeus.upload.sql.DatabaseProduct.H2);
        var sessions = new DbSessionFactory(
                bootstrap, dialect, profiles, new ConnectionPoolCache(new ConnectionDataSourceFactory()),
                SqlDialectRegistry.createDefault());
        service = new ConnectionTestService(profiles, sessions);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void restUnauthenticatedGetSucceeds() throws Exception {
        saveRest("rest-ok", "http://127.0.0.1:" + port + "/ok", Map.of());
        ConnectionTestResult result = service.test("rest-ok");
        assertThat(result.isSuccess()).as(result.getMessage()).isTrue();
        assertThat(result.getDatabaseProductName()).isEqualTo("HTTP 200");
        assertThat(result.getType()).isEqualTo(ConnectionType.REST);
    }

    @Test
    void restBasicAuthSucceeds() throws Exception {
        saveRest("rest-basic", "http://127.0.0.1:" + port + "/secure",
                Map.of("username", "svc", "secret", "s3cret"));
        ConnectionTestResult result = service.test("rest-basic");
        assertThat(result.isSuccess()).as(result.getMessage()).isTrue();
        assertThat(result.getDatabaseProductName()).isEqualTo("HTTP 204");
    }

    @Test
    void restAuthFailureIsReportedWithoutSecrets() throws Exception {
        saveRest("rest-bad-auth", "http://127.0.0.1:" + port + "/secure",
                Map.of("username", "svc", "secret", "wrong"));
        ConnectionTestResult result = service.test("rest-bad-auth");
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("authentication failed");
        assertThat(result.getMessage()).doesNotContain("wrong");
    }

    @Test
    void buildAuthorizationPrefersBasicThenBearer() {
        assertThat(ConnectionTestService.buildAuthorizationHeader(Map.of("username", "u", "secret", "p")))
                .startsWith("Basic ");
        assertThat(ConnectionTestService.buildAuthorizationHeader(Map.of("secret", "tok")))
                .isEqualTo("Bearer tok");
        assertThat(ConnectionTestService.buildAuthorizationHeader(Map.of())).isNull();
    }

    private void saveRest(String name, String endpoint, Map<String, String> credentials) throws Exception {
        ConnectionProfileRequest request = new ConnectionProfileRequest();
        request.setName(name);
        request.setType(ConnectionType.REST);
        request.setEndpoint(endpoint);
        request.setCredentials(credentials);
        profiles.save(request);
    }
}
