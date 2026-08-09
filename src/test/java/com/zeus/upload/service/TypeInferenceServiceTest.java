package com.zeus.upload.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeus.upload.domain.ColumnProposal;
import java.util.List;
import org.junit.jupiter.api.Test;

class TypeInferenceServiceTest {

    private final TypeInferenceService service = new TypeInferenceService();

    @Test
    void shouldInferInteger() {
        ColumnProposal proposal = service.inferColumn(0, "id", "ID", List.of("1", "2", "3"));
        assertThat(proposal.getSqlType()).isEqualTo("INTEGER");
    }

    @Test
    void shouldInferBigInt() {
        ColumnProposal proposal = service.inferColumn(0, "id", "ID", List.of("1", "9223372036854770000"));
        assertThat(proposal.getSqlType()).isEqualTo("BIGINT");
    }

    @Test
    void shouldInferDecimalWithPrecisionScale() {
        ColumnProposal proposal = service.inferColumn(0, "amount", "AMOUNT", List.of("1.20", "12345.678"));
        assertThat(proposal.getSqlType()).isEqualTo("DECIMAL");
        assertThat(proposal.getPrecision()).isGreaterThanOrEqualTo(8);
        assertThat(proposal.getScale()).isEqualTo(3);
    }

    @Test
    void shouldSizeDecimalToFitLargestIntegerPartIncludingTrailingZeros() {
        // Regression: stripTrailingZeros used to turn 1500.00 into 1.5E+3 and yield DECIMAL(5,2).
        ColumnProposal proposal = service.inferColumn(
                0, "amount", "AMOUNT", List.of("123.45", "99,90", "1500.00", "-12.50"));
        assertThat(proposal.getSqlType()).isEqualTo("DECIMAL");
        assertThat(proposal.getScale()).isEqualTo(2);
        // 1500.00 needs 4 integer digits + scale 2 => at least DECIMAL(6,2); headroom may add 1
        assertThat(proposal.getPrecision()).isGreaterThanOrEqualTo(6);
        assertThat(proposal.getPrecision() - proposal.getScale()).isGreaterThanOrEqualTo(4);
    }

    @Test
    void shouldInferDate() {
        ColumnProposal proposal = service.inferColumn(0, "d", "D", List.of("2025-01-01", "02.01.2025", "03/01/2025"));
        assertThat(proposal.getSqlType()).isEqualTo("DATE");
    }

    @Test
    void shouldInferTimestamp() {
        ColumnProposal proposal = service.inferColumn(0, "ts", "TS", List.of("2025-01-01 10:11:12", "2025-01-01T10:11:12"));
        assertThat(proposal.getSqlType()).isEqualTo("TIMESTAMP");
    }

    @Test
    void shouldInferVarcharLength() {
        ColumnProposal proposal = service.inferColumn(0, "txt", "TXT", List.of("abc", "a very long text"));
        assertThat(proposal.getSqlType()).isEqualTo("VARCHAR");
        assertThat(proposal.getLength()).isBetween(32, 4000);
    }
}
