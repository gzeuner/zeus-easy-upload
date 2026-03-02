package com.zeus.upload.util;

import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ColumnNameSanitizer {

    public static final int MAX_COLUMN_LENGTH = 10;

    public String sanitizeBase(String input) {
        String value = input == null ? "" : input.trim().toUpperCase();
        value = value.replaceAll("[^A-Z0-9_]", "_");
        value = value.replaceAll("_+", "_");
        value = value.replaceAll("^_+", "");
        value = value.replaceAll("_+$", "");

        if (value.isBlank()) {
            value = "COL";
        }
        if (!Character.isLetter(value.charAt(0))) {
            value = "C_" + value;
        }
        return value;
    }

    public String sanitizeColumnName(String input) {
        return enforceLengthWithHash(sanitizeBase(input), MAX_COLUMN_LENGTH);
    }

    public String uniquify(String sanitizedBase, Set<String> used, int maxLength) {
        String candidate = enforceLengthWithHash(sanitizedBase, maxLength);
        int counter = 1;
        while (used.contains(candidate)) {
            String withCounter = sanitizedBase + "_" + counter++;
            candidate = enforceLengthWithHash(withCounter, maxLength);
        }
        used.add(candidate);
        return candidate;
    }

    private String enforceLengthWithHash(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        int suffixLength = Math.min(3, maxLength - 1);
        String hash = Integer.toHexString(value.hashCode()).toUpperCase().replace("-", "A");
        if (hash.length() > suffixLength) {
            hash = hash.substring(0, suffixLength);
        } else if (hash.length() < suffixLength) {
            hash = String.format("%1$" + suffixLength + "s", hash).replace(' ', '0');
        }
        int prefixLen = maxLength - suffixLength;
        return value.substring(0, prefixLen) + hash;
    }
}
