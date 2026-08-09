package com.zeus.upload.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeus.upload.service.ValueConversionService.SqlTypeFamily;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ValueConversionServiceTest {

    private final ValueConversionService service = new ValueConversionService(new TypeInferenceService());

    @Test
    void convertsCoreTypes() {
        assertThat(service.convert("42", SqlTypeFamily.INTEGER)).isEqualTo(42);
        assertThat((BigDecimal) service.convert("99,5", SqlTypeFamily.DECIMAL)).isEqualByComparingTo("99.5");
        assertThat(service.convert("21.01.2025", SqlTypeFamily.DATE)).isEqualTo(LocalDate.of(2025, 1, 21));
        assertThat(service.convert("  hello ", SqlTypeFamily.VARCHAR)).isEqualTo("hello");
        assertThat(service.convert("", SqlTypeFamily.INTEGER)).isNull();
    }

    @Test
    void rejectsInvalidInteger() {
        assertThatThrownBy(() -> service.convert("abc", SqlTypeFamily.INTEGER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INTEGER");
    }

    @Test
    void familyFromDbTypeNames() {
        assertThat(service.familyFrom("INTEGER", null)).isEqualTo(SqlTypeFamily.INTEGER);
        assertThat(service.familyFrom("DECIMAL", null)).isEqualTo(SqlTypeFamily.DECIMAL);
        assertThat(service.familyFrom("VARCHAR", null)).isEqualTo(SqlTypeFamily.VARCHAR);
        assertThat(service.familyFrom("TIMESTAMP", null)).isEqualTo(SqlTypeFamily.TIMESTAMP);
    }

    @Test
    void conversionErrorHelper() {
        assertThat(service.conversionError("1", SqlTypeFamily.INTEGER)).isNull();
        assertThat(service.conversionError("x", SqlTypeFamily.INTEGER)).contains("INTEGER");
    }
}
