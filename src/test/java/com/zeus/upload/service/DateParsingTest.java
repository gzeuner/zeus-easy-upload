package com.zeus.upload.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class DateParsingTest {

    private final TypeInferenceService service = new TypeInferenceService();

    @Test
    void shouldParseSupportedDateFormats() {
        assertThat(service.parseDate("2025-01-31")).isEqualTo(LocalDate.of(2025, 1, 31));
        assertThat(service.parseDate("31.01.2025")).isEqualTo(LocalDate.of(2025, 1, 31));
        assertThat(service.parseDate("31/01/2025")).isEqualTo(LocalDate.of(2025, 1, 31));
    }

    @Test
    void shouldParseSupportedTimestampFormats() {
        assertThat(service.parseTimestamp("2025-01-31 12:30:40"))
                .isEqualTo(LocalDateTime.of(2025, 1, 31, 12, 30, 40));
        assertThat(service.parseTimestamp("2025-01-31T12:30:40"))
                .isEqualTo(LocalDateTime.of(2025, 1, 31, 12, 30, 40));
    }

    @Test
    void shouldReturnNullForInvalidDateOrTimestamp() {
        assertThat(service.parseDate("2025/31/01")).isNull();
        assertThat(service.parseTimestamp("2025-01-31")).isNull();
    }
}
