package com.zeus.upload.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DecimalDetectionTest {

    private final TypeInferenceService service = new TypeInferenceService();

    @Test
    void shouldDetectValidDecimals() {
        assertThat(service.isDecimal("12.34")).isTrue();
        assertThat(service.isDecimal("-12,34")).isTrue();
        assertThat(service.isDecimal("10")).isTrue();
    }

    @Test
    void shouldRejectInvalidDecimals() {
        assertThat(service.isDecimal("12..34")).isFalse();
        assertThat(service.isDecimal("12a")).isFalse();
        assertThat(service.isDecimal("")).isFalse();
    }
}
