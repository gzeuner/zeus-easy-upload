package com.zeus.upload.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeus.upload.config.AppProperties;
import com.zeus.upload.domain.ConnectionProfile;
import com.zeus.upload.domain.ConnectionProfileRequest;
import com.zeus.upload.domain.EncryptedConnectionSecrets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.net.URI;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ConnectionProfileService {

    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,63}");
    private final ObjectMapper objectMapper;
    private final ConnectionCryptoService cryptoService;
    private final Path connectionDirectory;
    private final ConnectionPoolCache connectionPoolCache;

    @Autowired
    public ConnectionProfileService(
            ObjectMapper objectMapper,
            AppProperties appProperties,
            ConnectionCryptoService cryptoService,
            ConnectionPoolCache connectionPoolCache
    ) {
        this(objectMapper, Path.of(appProperties.getConnectionProfileDirectory()), cryptoService, connectionPoolCache);
    }

    ConnectionProfileService(ObjectMapper objectMapper, Path connectionDirectory,
                             ConnectionCryptoService cryptoService) {
        this(objectMapper, connectionDirectory, cryptoService, null);
    }

    ConnectionProfileService(
            ObjectMapper objectMapper,
            Path connectionDirectory,
            ConnectionCryptoService cryptoService,
            ConnectionPoolCache connectionPoolCache
    ) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.connectionDirectory = Objects.requireNonNull(connectionDirectory, "connectionDirectory must not be null")
                .toAbsolutePath().normalize();
        this.cryptoService = Objects.requireNonNull(cryptoService, "cryptoService must not be null");
        this.connectionPoolCache = connectionPoolCache;
    }

    public ConnectionProfile save(ConnectionProfileRequest request) throws IOException {
        validateRequest(request);
        String safeName = validateName(request.getName());
        Path target = profilePath(safeName);
        StoredConnectionProfile previous = Files.exists(target) ? readStored(target) : null;
        Map<String, String> credentials = sanitizeCredentials(request.getCredentials());
        EncryptedConnectionSecrets encrypted = previous == null ? null : previous.getSecrets();
        String newAssociatedData = associatedData(safeName, request.getType(), request.getEndpoint());
        if (!credentials.isEmpty()) {
            encrypted = cryptoService.encrypt(credentials, newAssociatedData);
        } else if (encrypted != null && previous != null
                && !associatedData(safeName, previous.getProfile().getType(), previous.getProfile().getEndpoint()).equals(newAssociatedData)) {
            throw new IllegalArgumentException("Changing a connection endpoint or type requires replacing its credentials");
        }
        ConnectionProfile profile = new ConnectionProfile(
                safeName, request.getType(), request.getEndpoint().trim(), trimToNull(request.getDescription()),
                encrypted != null, Instant.now().toString());
        StoredConnectionProfile stored = new StoredConnectionProfile(profile, encrypted);
        Files.createDirectories(connectionDirectory);
        Path temporary = Files.createTempFile(connectionDirectory, safeName + "-", ".tmp");
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), stored);
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        invalidatePool(safeName);
        return profile;
    }

    public ConnectionProfile load(String name) throws IOException {
        return readStored(profilePath(validateName(name))).getProfile();
    }

    public Map<String, String> loadCredentials(String name) throws IOException {
        StoredConnectionProfile stored = readStored(profilePath(validateName(name)));
        if (stored.getSecrets() == null) return Map.of();
        ConnectionProfile profile = stored.getProfile();
        return cryptoService.decrypt(stored.getSecrets(), associatedData(
                profile.getName(), profile.getType(), profile.getEndpoint()));
    }

    public List<ConnectionProfile> list() throws IOException {
        if (!Files.isDirectory(connectionDirectory)) return List.of();
        try (var paths = Files.list(connectionDirectory)) {
            return paths.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(path -> {
                        try { return readStored(path).getProfile(); }
                        catch (IOException ex) { throw new IllegalStateException("Could not read connection profile", ex); }
                    })
                    .toList();
        }
    }

    public void delete(String name) throws IOException {
        String safeName = validateName(name);
        Files.deleteIfExists(profilePath(safeName));
        invalidatePool(safeName);
    }

    private void invalidatePool(String profileName) {
        if (connectionPoolCache != null) {
            connectionPoolCache.invalidate(profileName);
        }
    }

    private StoredConnectionProfile readStored(Path path) throws IOException {
        return objectMapper.readValue(path.toFile(), StoredConnectionProfile.class);
    }

    private Path profilePath(String name) {
        return connectionDirectory.resolve(name + ".json").normalize();
    }

    private void validateRequest(ConnectionProfileRequest request) {
        if (request == null || !StringUtils.hasText(request.getName())) throw new IllegalArgumentException("Connection name must not be blank");
        if (request.getType() == null) throw new IllegalArgumentException("Connection type must be selected");
        if (!StringUtils.hasText(request.getEndpoint())) throw new IllegalArgumentException("Connection endpoint must not be blank");
        if (request.getEndpoint().length() > 2_000) throw new IllegalArgumentException("Connection endpoint is too long");
        try {
            String endpointValue = request.getEndpoint().trim();
            if (endpointValue.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("Connection endpoint must not contain control characters");
            }
            // JT400 uses JDBC attributes such as "translate binary=true" with a space.
            // Encode spaces for URI validation while preserving the original JDBC URL.
            URI endpoint = URI.create(endpointValue.replace(" ", "%20"));
            if (endpoint.getUserInfo() != null || endpoint.getFragment() != null) {
                throw new IllegalArgumentException("Connection endpoint must not contain credentials or fragments");
            }
            if (request.getType() == com.zeus.upload.domain.ConnectionType.REST
                    && !("http".equalsIgnoreCase(endpoint.getScheme()) || "https".equalsIgnoreCase(endpoint.getScheme()))) {
                throw new IllegalArgumentException("REST connections require an HTTP(S) endpoint");
            }
            if (request.getType() != null && request.getType().isJdbc()
                    && !endpointValue.toLowerCase().startsWith("jdbc:")) {
                throw new IllegalArgumentException("JDBC connections require a jdbc: endpoint URL");
            }
            if (request.getType() == com.zeus.upload.domain.ConnectionType.POSTGRES
                    && !(endpointValue.toLowerCase().startsWith("jdbc:postgresql:")
                    || endpointValue.toLowerCase().startsWith("jdbc:pgsql:"))) {
                throw new IllegalArgumentException("POSTGRES connections require a jdbc:postgresql: endpoint URL");
            }
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Connection endpoint is invalid", ex);
        }
    }

    private Map<String, String> sanitizeCredentials(Map<String, String> input) {
        Map<String, String> result = new LinkedHashMap<>();
        if (input == null) return result;
        if (input.size() > 20) throw new IllegalArgumentException("Too many connection credentials");
        input.forEach((key, value) -> {
            if (StringUtils.hasText(key) && value != null && !value.isBlank()) {
                if (key.length() > 64 || value.length() > 10_000) throw new IllegalArgumentException("Connection credential is too long");
                result.put(key.trim(), value);
            }
        });
        return result;
    }

    private String associatedData(String name, com.zeus.upload.domain.ConnectionType type, String endpoint) {
        return name + "|" + type.name() + "|" + endpoint.trim();
    }

    private String validateName(String name) {
        if (!StringUtils.hasText(name) || !SAFE_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Connection name must match [A-Za-z0-9][A-Za-z0-9_-]{0,63}.");
        }
        return name;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public static class StoredConnectionProfile {
        private ConnectionProfile profile;
        private EncryptedConnectionSecrets secrets;

        public StoredConnectionProfile() { }
        StoredConnectionProfile(ConnectionProfile profile, EncryptedConnectionSecrets secrets) {
            this.profile = profile;
            this.secrets = secrets;
        }
        public ConnectionProfile getProfile() { return profile; }
        public void setProfile(ConnectionProfile profile) { this.profile = profile; }
        public EncryptedConnectionSecrets getSecrets() { return secrets; }
        public void setSecrets(EncryptedConnectionSecrets secrets) { this.secrets = secrets; }
    }
}
