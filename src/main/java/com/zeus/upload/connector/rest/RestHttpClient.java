package com.zeus.upload.connector.rest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

final class RestHttpClient {

    private final RestConnectorSettings settings;
    private final HttpClient client;

    RestHttpClient(RestConnectorSettings settings) {
        this.settings = settings;
        this.client = settings.createHttpClient();
    }

    byte[] execute(URI endpoint, String method, byte[] requestBody, Map<String, String> headers, boolean retryable) {
        settings.validateEndpoint(endpoint);
        if (requestBody != null && requestBody.length > settings.getMaxRequestBytes()) {
            throw new RestConnectorException(RestConnectorException.Category.PAYLOAD_LIMIT, "REST request exceeds the configured size limit");
        }
        int attempts = retryable ? settings.getMaxRetries() : 0;
        for (int attempt = 0; ; attempt++) {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                        .timeout(settings.getRequestTimeout())
                        .header("Accept", "application/json");
                if (headers != null) headers.forEach((name, value) -> builder.header(name, value));
                String authorization = settings.authorizationHeader();
                if (authorization != null) builder.header("Authorization", authorization);
                HttpRequest.BodyPublisher body = requestBody == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofByteArray(requestBody);
                HttpResponse<InputStream> response = client.send(
                        builder.method(method, body).build(), HttpResponse.BodyHandlers.ofInputStream());
                int status = response.statusCode();
                byte[] payload;
                try (InputStream stream = response.body()) {
                    payload = readLimited(stream, settings.getMaxResponseBytes());
                }
                if (status >= 200 && status <= 299) return payload;
                if (attempt < attempts && isRetryableStatus(status)) {
                    pause(retryDelay(attempt, response.headers().firstValue("Retry-After").orElse(null)));
                    continue;
                }
                throw remoteFailure(status);
            } catch (RestConnectorException ex) {
                throw ex;
            } catch (java.net.http.HttpTimeoutException ex) {
                if (attempt < attempts) {
                    pause(backoff(attempt));
                    continue;
                }
                throw new RestConnectorException(RestConnectorException.Category.TIMEOUT, "REST request timed out", ex);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new RestConnectorException(RestConnectorException.Category.CANCELLED, "REST request was interrupted", ex);
            } catch (IOException ex) {
                if (attempt < attempts) {
                    pause(backoff(attempt));
                    continue;
                }
                throw new RestConnectorException(RestConnectorException.Category.REMOTE_FAILURE, "REST request failed", ex);
            }
        }
    }

    private byte[] readLimited(InputStream stream, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(limit, 8192));
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = stream.read(buffer)) >= 0) {
            total += read;
            if (total > limit) throw new RestConnectorException(
                    RestConnectorException.Category.PAYLOAD_LIMIT, "REST response exceeds the configured size limit");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private boolean isRetryableStatus(int status) {
        return status == 408 || status == 429 || status >= 500;
    }

    private RestConnectorException remoteFailure(int status) {
        RestConnectorException.Category category = status == 429
                ? RestConnectorException.Category.RATE_LIMIT
                : RestConnectorException.Category.REMOTE_FAILURE;
        return new RestConnectorException(category, "REST endpoint returned HTTP status " + status);
    }

    private Duration retryDelay(int attempt, String retryAfter) {
        if (retryAfter != null) {
            try {
                return Duration.ofSeconds(Math.min(Long.parseLong(retryAfter), 5));
            } catch (NumberFormatException ignored) {
                // Fall back to bounded exponential backoff.
            }
        }
        return backoff(attempt);
    }

    private Duration backoff(int attempt) {
        return Duration.ofMillis(Math.min(1_000, 50L * (1L << Math.min(attempt, 4))));
    }

    private void pause(Duration delay) {
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RestConnectorException(RestConnectorException.Category.CANCELLED, "REST retry was interrupted", ex);
        }
    }
}
