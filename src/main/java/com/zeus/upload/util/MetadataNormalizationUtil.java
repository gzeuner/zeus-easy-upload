package com.zeus.upload.util;

import java.util.Locale;

public final class MetadataNormalizationUtil {

    private MetadataNormalizationUtil() {
    }

    public static String normalizeLibrary(String value) {
        return normalizeToken(value);
    }

    public static String normalizeTable(String value) {
        return normalizeToken(value);
    }

    private static String normalizeToken(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }
}
