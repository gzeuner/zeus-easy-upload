package com.zeus.upload.connector.rest;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

public final class RestConnectorSettings {

    private final RestDeploymentProfile profile;
    private final List<RestEndpointRule> allowlist;
    private final SecretProvider secretProvider;
    private final RestAuthentication authentication;
    private final Duration connectTimeout;
    private final Duration requestTimeout;
    private final int maxResponseBytes;
    private final int maxRequestBytes;
    private final int maxRetries;
    private final int maxRecordsPerPage;
    private final RestEndpointValidator endpointValidator;

    private RestConnectorSettings(Builder builder) {
        profile = builder.profile;
        allowlist = List.copyOf(builder.allowlist);
        secretProvider = builder.secretProvider;
        authentication = builder.authentication;
        connectTimeout = builder.connectTimeout;
        requestTimeout = builder.requestTimeout;
        maxResponseBytes = builder.maxResponseBytes;
        maxRequestBytes = builder.maxRequestBytes;
        maxRetries = builder.maxRetries;
        maxRecordsPerPage = builder.maxRecordsPerPage;
        endpointValidator = new RestEndpointValidator(profile, allowlist);
        if (allowlist.isEmpty()) throw new IllegalArgumentException("REST endpoint allowlist must not be empty");
        if (authentication.getType() != RestAuthentication.Type.NONE && secretProvider == null) {
            throw new IllegalArgumentException("A SecretProvider is required for authenticated REST access");
        }
    }

    public static Builder builder() { return new Builder(); }

    public void validateEndpoint(URI endpoint) {
        endpointValidator.validate(endpoint);
    }

    public HttpClient createHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public String authorizationHeader() {
        if (authentication.getType() == RestAuthentication.Type.NONE) return null;
        String secret = secretProvider.resolve(authentication.getSecretKey());
        if (secret == null || secret.isBlank()) throw new RestConnectorException(
                RestConnectorException.Category.AUTHENTICATION, "Configured REST secret is unavailable");
        if (authentication.getType() == RestAuthentication.Type.BEARER) return "Bearer " + secret;
        String basic = authentication.getUsername() + ":" + secret;
        return "Basic " + Base64.getEncoder().encodeToString(basic.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public RestDeploymentProfile getProfile() { return profile; }
    public Duration getRequestTimeout() { return requestTimeout; }
    public int getMaxResponseBytes() { return maxResponseBytes; }
    public int getMaxRequestBytes() { return maxRequestBytes; }
    public int getMaxRetries() { return maxRetries; }
    public int getMaxRecordsPerPage() { return maxRecordsPerPage; }

    public static final class Builder {
        private RestDeploymentProfile profile = RestDeploymentProfile.PRODUCTION;
        private List<RestEndpointRule> allowlist = List.of();
        private SecretProvider secretProvider;
        private RestAuthentication authentication = RestAuthentication.none();
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration requestTimeout = Duration.ofSeconds(30);
        private int maxResponseBytes = 10 * 1024 * 1024;
        private int maxRequestBytes = 10 * 1024 * 1024;
        private int maxRetries = 2;
        private int maxRecordsPerPage = 1_000;

        public Builder profile(RestDeploymentProfile value) { profile = Objects.requireNonNull(value); return this; }
        public Builder allowlist(List<RestEndpointRule> value) { allowlist = Objects.requireNonNull(value); return this; }
        public Builder secretProvider(SecretProvider value) { secretProvider = value; return this; }
        public Builder authentication(RestAuthentication value) { authentication = Objects.requireNonNull(value); return this; }
        public Builder connectTimeout(Duration value) { connectTimeout = bounded(value, Duration.ofMillis(1), Duration.ofSeconds(30), "connectTimeout"); return this; }
        public Builder requestTimeout(Duration value) { requestTimeout = bounded(value, Duration.ofMillis(1), Duration.ofMinutes(5), "requestTimeout"); return this; }
        public Builder maxResponseBytes(int value) { maxResponseBytes = bounded(value, 1, 100 * 1024 * 1024, "maxResponseBytes"); return this; }
        public Builder maxRequestBytes(int value) { maxRequestBytes = bounded(value, 1, 100 * 1024 * 1024, "maxRequestBytes"); return this; }
        public Builder maxRetries(int value) { maxRetries = bounded(value, 0, 5, "maxRetries"); return this; }
        public Builder maxRecordsPerPage(int value) { maxRecordsPerPage = bounded(value, 1, 10_000, "maxRecordsPerPage"); return this; }

        public RestConnectorSettings build() { return new RestConnectorSettings(this); }

        private Duration bounded(Duration value, Duration min, Duration max, String name) {
            Objects.requireNonNull(value, name + " must not be null");
            if (value.compareTo(min) < 0 || value.compareTo(max) > 0) throw new IllegalArgumentException(name + " is outside the allowed range");
            return value;
        }

        private int bounded(int value, int min, int max, String name) {
            if (value < min || value > max) throw new IllegalArgumentException(name + " is outside the allowed range");
            return value;
        }
    }

    private static final class RestEndpointValidator {
        private final RestDeploymentProfile profile;
        private final List<RestEndpointRule> allowlist;

        private RestEndpointValidator(RestDeploymentProfile profile, List<RestEndpointRule> allowlist) {
            this.profile = profile;
            this.allowlist = allowlist;
        }

        private void validate(URI endpoint) {
            if (endpoint == null || endpoint.getHost() == null || endpoint.getUserInfo() != null
                    || endpoint.getFragment() != null || endpoint.getQuery() != null && endpoint.getQuery().contains("#")) {
                throw new RestConnectorException(RestConnectorException.Category.SECURITY_POLICY, "REST endpoint URI is not allowed");
            }
            if (!"https".equalsIgnoreCase(endpoint.getScheme())
                    && !(profile == RestDeploymentProfile.TEST && "http".equalsIgnoreCase(endpoint.getScheme()))) {
                throw new RestConnectorException(RestConnectorException.Category.SECURITY_POLICY, "REST endpoint scheme is not allowed");
            }
            if (allowlist.stream().noneMatch(rule -> rule.matches(endpoint))) {
                throw new RestConnectorException(RestConnectorException.Category.SECURITY_POLICY, "REST endpoint is not allowlisted");
            }
            if (profile == RestDeploymentProfile.PRODUCTION) {
                try {
                    for (InetAddress address : InetAddress.getAllByName(endpoint.getHost())) {
                        if (isPrivateOrReserved(address)) throw new RestConnectorException(
                                RestConnectorException.Category.SECURITY_POLICY, "REST endpoint resolves to a private or reserved address");
                    }
                } catch (UnknownHostException ex) {
                    throw new RestConnectorException(RestConnectorException.Category.SECURITY_POLICY, "REST endpoint host cannot be resolved", ex);
                }
            }
        }

        private boolean isPrivateOrReserved(InetAddress address) {
            byte[] bytes = address.getAddress();
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isMulticastAddress()) return true;
            if (bytes.length == 4) {
                int first = bytes[0] & 0xff;
                int second = bytes[1] & 0xff;
                return first == 0 || first == 10 || first == 127 || first >= 224
                        || first == 169 && second == 254
                        || first == 172 && second >= 16 && second <= 31
                        || first == 192 && second == 168
                        || first == 100 && second >= 64 && second <= 127
                        || first == 198 && (second == 18 || second == 19);
            }
            return (bytes[0] & 0xff) == 0xfc || (bytes[0] & 0xfe) == 0xfc;
        }
    }
}
