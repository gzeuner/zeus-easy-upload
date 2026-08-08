package com.zeus.upload.flow;

import com.zeus.upload.connector.rest.RestConnectorSettings;
import java.net.URI;
import java.util.Objects;

public final class RestTargetConfiguration implements TargetConfiguration {
    public enum Method { POST, PUT }

    private final URI endpoint;
    private final Method method;
    private final int batchSize;
    private final String idempotencyHeader;
    private final RestConnectorSettings settings;

    public RestTargetConfiguration(URI endpoint, Method method, int batchSize,
                                   String idempotencyHeader, RestConnectorSettings settings) {
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint must not be null");
        this.method = Objects.requireNonNull(method, "method must not be null");
        if (batchSize < 1 || batchSize > 10_000) throw new IllegalArgumentException("batchSize is outside the allowed range");
        this.batchSize = batchSize;
        this.idempotencyHeader = idempotencyHeader == null || idempotencyHeader.isBlank()
                ? null : idempotencyHeader;
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
        if (method == Method.POST && settings.getMaxRetries() > 0 && this.idempotencyHeader == null) {
            throw new IllegalArgumentException("POST targets with retries require an idempotency header");
        }
    }

    public URI getEndpoint() { return endpoint; }
    public Method getMethod() { return method; }
    public int getBatchSize() { return batchSize; }
    public String getIdempotencyHeader() { return idempotencyHeader; }
    public RestConnectorSettings getSettings() { return settings; }
}
