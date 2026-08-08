package com.zeus.upload.connector.rest;

import java.util.Objects;

public final class RestAuthentication {

    public enum Type { NONE, BEARER, BASIC }

    private final Type type;
    private final String username;
    private final String secretKey;

    private RestAuthentication(Type type, String username, String secretKey) {
        this.type = type;
        this.username = username;
        this.secretKey = secretKey;
    }

    public static RestAuthentication none() {
        return new RestAuthentication(Type.NONE, null, null);
    }

    public static RestAuthentication bearer(String tokenSecretKey) {
        return new RestAuthentication(Type.BEARER, null,
                requireSecretKey(tokenSecretKey));
    }

    public static RestAuthentication basic(String username, String passwordSecretKey) {
        return new RestAuthentication(Type.BASIC, Objects.requireNonNull(username, "username must not be null"),
                requireSecretKey(passwordSecretKey));
    }

    private static String requireSecretKey(String secretKey) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalArgumentException("secretKey must not be blank");
        }
        return secretKey;
    }

    public Type getType() { return type; }
    public String getUsername() { return username; }
    public String getSecretKey() { return secretKey; }
}
