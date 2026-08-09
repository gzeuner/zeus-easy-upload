package com.zeus.upload.util;

import com.zeus.upload.sql.IdentifierPolicy;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ColumnNameSanitizer {

    /**
     * Default max length for IBM i system-style names when no dialect policy is supplied.
     * Prefer {@link IdentifierPolicy#maxLength()} from the active {@code SqlDialect}.
     */
    public static final int MAX_COLUMN_LENGTH = 10;

    public String sanitizeBase(String input) {
        return sanitizeBase(input, IdentifierPolicy.ibmISystemNames());
    }

    public String sanitizeBase(String input, IdentifierPolicy policy) {
        IdentifierPolicy effective = policy == null ? IdentifierPolicy.ibmISystemNames() : policy;
        String value = input == null ? "" : input.trim();
        if (effective.forceUpperCase()) {
            value = value.toUpperCase(Locale.ROOT);
        }
        value = value.replaceAll("[^A-Za-z0-9_]", "_");
        value = value.replaceAll("_+", "_");
        value = value.replaceAll("^_+", "");
        value = value.replaceAll("_+$", "");

        if (value.isBlank()) {
            value = effective.emptyFallback();
        }
        if (effective.forceUpperCase()) {
            value = value.toUpperCase(Locale.ROOT);
        }
        if (!Character.isLetter(value.charAt(0))) {
            value = effective.leadingDigitPrefix() + value;
            if (effective.forceUpperCase()) {
                value = value.toUpperCase(Locale.ROOT);
            }
        }
        return value;
    }

    public String sanitizeColumnName(String input) {
        return enforceLengthWithHash(sanitizeBase(input), MAX_COLUMN_LENGTH);
    }

    public String sanitizeColumnName(String input, IdentifierPolicy policy) {
        IdentifierPolicy effective = policy == null ? IdentifierPolicy.ibmISystemNames() : policy;
        return enforceLengthWithHash(sanitizeBase(input, effective), effective.maxLength());
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
        String hash = Integer.toHexString(value.hashCode()).toUpperCase(Locale.ROOT).replace("-", "A");
        if (hash.length() > suffixLength) {
            hash = hash.substring(0, suffixLength);
        } else if (hash.length() < suffixLength) {
            hash = String.format("%1$" + suffixLength + "s", hash).replace(' ', '0');
        }
        int prefixLen = maxLength - suffixLength;
        return value.substring(0, prefixLen) + hash;
    }
}
