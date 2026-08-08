package com.zeus.upload.connector.rest;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;

public final class RestEndpointRule {

    private final String scheme;
    private final String hostname;
    private final int port;
    private final String pathPrefix;

    public RestEndpointRule(String scheme, String hostname, int port, String pathPrefix) {
        this.scheme = requireText(scheme, "scheme").toLowerCase(Locale.ROOT);
        this.hostname = requireText(hostname, "hostname").toLowerCase(Locale.ROOT);
        if (port < 1 || port > 65535) throw new IllegalArgumentException("port must be between 1 and 65535");
        this.port = port;
        this.pathPrefix = normalizePathPrefix(pathPrefix);
    }

    public boolean matches(URI endpoint) {
        int effectivePort = endpoint.getPort() > 0 ? endpoint.getPort() : defaultPort(endpoint.getScheme());
        String endpointPath = endpoint.getPath() == null || endpoint.getPath().isBlank() ? "/" : endpoint.getPath();
        return scheme.equalsIgnoreCase(endpoint.getScheme())
                && hostname.equalsIgnoreCase(endpoint.getHost())
                && port == effectivePort
                && (endpointPath.equals(pathPrefix) || endpointPath.startsWith(pathPrefix.endsWith("/")
                ? pathPrefix : pathPrefix + "/"));
    }

    private int defaultPort(String endpointScheme) {
        return "https".equalsIgnoreCase(endpointScheme) ? 443 : 80;
    }

    private String normalizePathPrefix(String value) {
        String prefix = value == null || value.isBlank() ? "/" : value.trim();
        if (!prefix.startsWith("/")) prefix = "/" + prefix;
        return prefix.length() > 1 && prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    public String getScheme() { return scheme; }
    public String getHostname() { return hostname; }
    public int getPort() { return port; }
    public String getPathPrefix() { return pathPrefix; }
}
