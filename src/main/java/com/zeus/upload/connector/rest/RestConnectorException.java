package com.zeus.upload.connector.rest;

public class RestConnectorException extends RuntimeException {

    public enum Category {
        CONFIGURATION, SECURITY_POLICY, AUTHENTICATION, TIMEOUT, RATE_LIMIT,
        REMOTE_FAILURE, PAYLOAD_LIMIT, PARSE_FAILURE, CANCELLED
    }

    private final Category category;

    public RestConnectorException(Category category, String message) {
        super(message);
        this.category = category;
    }

    public RestConnectorException(Category category, String message, Throwable cause) {
        super(message, cause);
        this.category = category;
    }

    public Category getCategory() { return category; }
}
