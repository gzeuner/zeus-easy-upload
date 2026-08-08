package com.zeus.upload.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeus.upload.domain.ConnectionProfileRequest;
import com.zeus.upload.domain.ConnectionType;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConnectionProfileServiceTest {

    private static final String MASTER_KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void storesSecretsEncryptedAndNeverReturnsThemFromProfileMetadata() throws Exception {
        var directory = Files.createTempDirectory("zeus-connections");
        var crypto = new ConnectionCryptoService(new ObjectMapper(), MASTER_KEY);
        var service = new ConnectionProfileService(new ObjectMapper(), directory, crypto);
        var request = request("rest-prod", ConnectionType.REST, "https://api.example.com/v1", Map.of(
                "username", "service-user", "secret", "top-secret-token"));

        var saved = service.save(request);

        assertThat(saved.isCredentialsConfigured()).isTrue();
        assertThat(service.load("rest-prod").getEndpoint()).isEqualTo("https://api.example.com/v1");
        assertThat(service.loadCredentials("rest-prod"))
                .containsEntry("username", "service-user")
                .containsEntry("secret", "top-secret-token");
        String stored = Files.readString(directory.resolve("rest-prod.json"));
        assertThat(stored).doesNotContain("top-secret-token", "service-user");
    }

    @Test
    void preservesExistingSecretWhenGuiLeavesCredentialFieldsEmpty() throws Exception {
        var directory = Files.createTempDirectory("zeus-connections");
        var crypto = new ConnectionCryptoService(new ObjectMapper(), MASTER_KEY);
        var service = new ConnectionProfileService(new ObjectMapper(), directory, crypto);
        service.save(request("db2", ConnectionType.DB2_400, "jdbc:as400://system/bib", Map.of("secret", "pw")));

        service.save(request("db2", ConnectionType.DB2_400, "jdbc:as400://system/bib", Map.of()));

        assertThat(service.loadCredentials("db2")).containsEntry("secret", "pw");
    }

    @Test
    void acceptsJt400TranslateBinaryJdbcAttribute() throws Exception {
        var directory = Files.createTempDirectory("zeus-connections");
        var crypto = new ConnectionCryptoService(new ObjectMapper(), MASTER_KEY);
        var service = new ConnectionProfileService(new ObjectMapper(), directory, crypto);
        var endpoint = "jdbc:as400://system/bib;translate binary=true";

        var saved = service.save(request("db2-translate", ConnectionType.DB2_400, endpoint, Map.of()));

        assertThat(saved.getEndpoint()).isEqualTo(endpoint);
    }

    @Test
    void refusesToSaveWhenMasterKeyIsMissing() throws Exception {
        var directory = Files.createTempDirectory("zeus-connections");
        var crypto = new ConnectionCryptoService(new ObjectMapper(), "");
        var service = new ConnectionProfileService(new ObjectMapper(), directory, crypto);

        assertThatThrownBy(() -> service.save(request("db2", ConnectionType.DB2_400,
                "jdbc:as400://system/bib", Map.of("secret", "pw"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("master key");
    }

    @Test
    void rejectsCredentialsEmbeddedInEndpoint() {
        var request = request("unsafe", ConnectionType.REST, "https://user:pw@example.com/api", Map.of());
        var crypto = new ConnectionCryptoService(new ObjectMapper(), MASTER_KEY);
        var service = new ConnectionProfileService(new ObjectMapper(),
                java.nio.file.Path.of("target/test-connections"), crypto);

        assertThatThrownBy(() -> service.save(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endpoint");
    }

    private ConnectionProfileRequest request(String name, ConnectionType type, String endpoint,
                                             Map<String, String> credentials) {
        var request = new ConnectionProfileRequest();
        request.setName(name);
        request.setType(type);
        request.setEndpoint(endpoint);
        request.setCredentials(credentials);
        return request;
    }
}
