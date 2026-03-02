package com.zeus.upload.util;

public final class Db2IdentifierUtil {

    private Db2IdentifierUtil() {
    }

    public static String sanitizeIdentifier(String value) {
        String sanitized = value == null ? "" : value.trim().toUpperCase();
        sanitized = sanitized.replaceAll("[^A-Z0-9_]", "_");
        sanitized = sanitized.replaceAll("_+", "_");
        sanitized = sanitized.replaceAll("^_+", "");
        sanitized = sanitized.replaceAll("_+$", "");
        if (sanitized.isBlank()) {
            throw new IllegalArgumentException("Identifier must not be empty");
        }
        if (!Character.isLetter(sanitized.charAt(0))) {
            sanitized = "T_" + sanitized;
        }
        return sanitized;
    }
}
