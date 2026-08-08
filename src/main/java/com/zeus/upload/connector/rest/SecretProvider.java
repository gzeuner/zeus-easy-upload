package com.zeus.upload.connector.rest;

@FunctionalInterface
public interface SecretProvider {
    String resolve(String secretKey);
}
