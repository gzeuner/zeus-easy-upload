package com.zeus.upload.connector.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.zeus.upload.flow.DataRecord;
import com.zeus.upload.flow.RestSourceConfiguration;
import com.zeus.upload.flow.RestTargetConfiguration;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RestConnectorTest {

    private HttpServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void sourceStreamsPageNumberPagination() {
        List<String> queries = new CopyOnWriteArrayList<>();
        server.createContext("/api/items", exchange -> {
            queries.add(exchange.getRequestURI().getRawQuery());
            String body = exchange.getRequestURI().getQuery().contains("page=1")
                    ? "{\"items\":[{\"id\":1}]}"
                    : "{\"items\":[]}";
            respond(exchange, 200, body);
        });
        RestConnectorSettings settings = settings("/api");
        RestPaginationConfiguration pagination = RestPaginationConfiguration.builder()
                .mode(RestPaginationMode.PAGE_NUMBER).recordsField("items")
                .pageSize(1).maxPages(3).build();
        RestSourceConfiguration configuration = new RestSourceConfiguration(
                endpoint("/api/items"), "items", pagination, settings);

        List<DataRecord> records;
        try (Stream<DataRecord> stream = new RestJsonSourceConnector(configuration).read()) {
            records = stream.toList();
        }

        assertThat(records).hasSize(1);
        assertThat(records.get(0).get("id")).isEqualTo(1);
        assertThat(queries).containsExactly("page=1&pageSize=1", "page=2&pageSize=1");
    }

    @Test
    void sourceFollowsCursorPagination() {
        server.createContext("/api/cursor", exchange -> {
            String body = exchange.getRequestURI().getQuery() == null
                    ? "{\"items\":[{\"id\":1}],\"next\":\"abc\"}"
                    : "{\"items\":[{\"id\":2}],\"next\":null}";
            respond(exchange, 200, body);
        });
        RestPaginationConfiguration pagination = RestPaginationConfiguration.builder()
                .mode(RestPaginationMode.CURSOR).recordsField("items").nextCursorField("next")
                .maxPages(3).build();
        RestSourceConfiguration configuration = new RestSourceConfiguration(
                endpoint("/api/cursor"), "items", pagination, settings("/api"));

        try (Stream<DataRecord> stream = new RestJsonSourceConnector(configuration).read()) {
            assertThat(stream.map(record -> record.get("id")).toList()).containsExactly(1, 2);
        }
    }

    @Test
    void targetBatchesRecordsAndSendsIdempotencyHeaders() {
        List<byte[]> bodies = new CopyOnWriteArrayList<>();
        List<String> idempotencyKeys = new CopyOnWriteArrayList<>();
        server.createContext("/api/import", exchange -> {
            bodies.add(exchange.getRequestBody().readAllBytes());
            idempotencyKeys.add(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            respond(exchange, 201, "{}");
        });
        RestConnectorSettings settings = RestConnectorSettings.builder()
                .profile(RestDeploymentProfile.TEST)
                .allowlist(List.of(new RestEndpointRule("http", "localhost", server.getAddress().getPort(), "/api")))
                .maxRetries(1).build();
        RestTargetConfiguration configuration = new RestTargetConfiguration(
                endpoint("/api/import"), RestTargetConfiguration.Method.POST, 2,
                "Idempotency-Key", settings);
        RestJsonTargetConnector target = new RestJsonTargetConnector(configuration);

        DataRecord first = record(1);
        DataRecord second = record(2);
        DataRecord third = record(3);
        target.write(Stream.of(first, second, third));

        assertThat(target.getAcceptedRecordCount()).isEqualTo(3);
        assertThat(bodies).hasSize(2);
        assertThat(new String(bodies.get(0), StandardCharsets.UTF_8)).contains("\"id\":1", "\"id\":2");
        assertThat(new String(bodies.get(1), StandardCharsets.UTF_8)).contains("\"id\":3");
        assertThat(idempotencyKeys).allMatch(key -> key != null && !key.isBlank());
        assertThat(idempotencyKeys.get(0)).isNotEqualTo(idempotencyKeys.get(1));
    }

    @Test
    void bearerSecretIsAddedAtRequestTime() {
        List<String> authorization = new CopyOnWriteArrayList<>();
        server.createContext("/api/auth", exchange -> {
            authorization.add(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, "[]");
        });
        RestConnectorSettings settings = RestConnectorSettings.builder()
                .profile(RestDeploymentProfile.TEST)
                .allowlist(List.of(new RestEndpointRule("http", "localhost", server.getAddress().getPort(), "/api")))
                .authentication(RestAuthentication.bearer("service-token"))
                .secretProvider(key -> "secret-value")
                .build();
        RestSourceConfiguration configuration = new RestSourceConfiguration(
                endpoint("/api/auth"), null, RestPaginationConfiguration.none(), settings);

        try (Stream<DataRecord> ignored = new RestJsonSourceConnector(configuration).read()) {
            ignored.toList();
        }

        assertThat(authorization).containsExactly("Bearer secret-value");
    }

    @Test
    void productionRejectsHttpBeforeNetworkAccess() {
        RestConnectorSettings settings = RestConnectorSettings.builder()
                .profile(RestDeploymentProfile.PRODUCTION)
                .allowlist(List.of(new RestEndpointRule("http", "example.com", 80, "/")))
                .build();

        assertThatThrownBy(() -> settings.validateEndpoint(URI.create("http://example.com/records")))
                .isInstanceOf(RestConnectorException.class)
                .extracting(error -> ((RestConnectorException) error).getCategory())
                .isEqualTo(RestConnectorException.Category.SECURITY_POLICY);
    }

    private RestConnectorSettings settings(String path) {
        return RestConnectorSettings.builder()
                .profile(RestDeploymentProfile.TEST)
                .allowlist(List.of(new RestEndpointRule("http", "localhost", server.getAddress().getPort(), path)))
                .build();
    }

    private URI endpoint(String path) {
        return URI.create("http://localhost:" + server.getAddress().getPort() + path);
    }

    private DataRecord record(int id) {
        DataRecord record = new DataRecord();
        record.set("id", id);
        record.set("name", "row-" + id);
        return record;
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, payload.length);
        try (var output = exchange.getResponseBody()) {
            output.write(payload);
        }
    }
}
