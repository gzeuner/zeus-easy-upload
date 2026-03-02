package com.zeus.upload.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ColumnNameSanitizerTest {

    private final ColumnNameSanitizer sanitizer = new ColumnNameSanitizer();

    @Test
    void shouldSanitizeToUppercaseAndUnderscore() {
        String sanitized = sanitizer.sanitizeColumnName("Order Number#");
        assertThat(sanitized).matches("[A-Z0-9_]+");
        assertThat(sanitized.length()).isLessThanOrEqualTo(10);
    }

    @Test
    void shouldGenerateDifferentNamesForDuplicates() {
        Set<String> used = new HashSet<>();
        String first = sanitizer.uniquify(sanitizer.sanitizeBase("customer name"), used, 10);
        String second = sanitizer.uniquify(sanitizer.sanitizeBase("customer-name"), used, 10);
        assertThat(first).isNotEqualTo(second);
        assertThat(first.length()).isLessThanOrEqualTo(10);
        assertThat(second.length()).isLessThanOrEqualTo(10);
    }
}
