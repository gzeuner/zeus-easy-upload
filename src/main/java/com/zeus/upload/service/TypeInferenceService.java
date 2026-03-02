package com.zeus.upload.service;

import com.zeus.upload.domain.ColumnProposal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TypeInferenceService {

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy")
    );

    private static final List<DateTimeFormatter> TIMESTAMP_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    );

    public ColumnProposal inferColumn(int index, String originalName, String sanitizedName, List<String> rawValues) {
        List<String> values = new ArrayList<>();
        int maxLength = 0;
        for (String value : rawValues) {
            if (value == null) {
                continue;
            }
            String trimmed = value.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            values.add(trimmed);
            maxLength = Math.max(maxLength, trimmed.length());
        }

        ColumnProposal proposal = new ColumnProposal();
        proposal.setIndex(index);
        proposal.setOriginalName(originalName);
        proposal.setSanitizedName(sanitizedName);
        proposal.setFinalName(sanitizedName);

        if (values.isEmpty()) {
            proposal.setDetectedType("VARCHAR");
            proposal.setSqlType("VARCHAR");
            proposal.setLength(32);
            return proposal;
        }

        if (allInteger(values)) {
            if (fitsInt(values)) {
                proposal.setDetectedType("INTEGER");
                proposal.setSqlType("INTEGER");
            } else {
                proposal.setDetectedType("BIGINT");
                proposal.setSqlType("BIGINT");
            }
            return proposal;
        }

        if (allDecimal(values)) {
            int[] precisionScale = calculatePrecisionScale(values);
            proposal.setDetectedType("DECIMAL");
            proposal.setSqlType("DECIMAL");
            proposal.setPrecision(precisionScale[0]);
            proposal.setScale(precisionScale[1]);
            return proposal;
        }

        if (allTimestamp(values)) {
            proposal.setDetectedType("TIMESTAMP");
            proposal.setSqlType("TIMESTAMP");
            return proposal;
        }

        if (allDate(values)) {
            proposal.setDetectedType("DATE");
            proposal.setSqlType("DATE");
            return proposal;
        }

        proposal.setDetectedType("VARCHAR");
        proposal.setSqlType("VARCHAR");
        proposal.setLength(calculateVarcharLength(maxLength));
        return proposal;
    }

    public boolean isDecimal(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim().replace(',', '.');
        if (!normalized.matches("[-+]?\\d+(\\.\\d+)?")) {
            return false;
        }
        try {
            new BigDecimal(normalized);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    public LocalDate parseDate(String value) {
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        return null;
    }

    public LocalDateTime parseTimestamp(String value) {
        for (DateTimeFormatter formatter : TIMESTAMP_FORMATTERS) {
            try {
                return LocalDateTime.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        return null;
    }

    private boolean allInteger(List<String> values) {
        return values.stream().allMatch(v -> v.matches("[-+]?\\d+"));
    }

    private boolean fitsInt(List<String> values) {
        try {
            for (String value : values) {
                long parsed = Long.parseLong(value);
                if (parsed > Integer.MAX_VALUE || parsed < Integer.MIN_VALUE) {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private boolean allDecimal(List<String> values) {
        return values.stream().allMatch(this::isDecimal);
    }

    private boolean allDate(List<String> values) {
        return values.stream().allMatch(value -> parseDate(value) != null);
    }

    private boolean allTimestamp(List<String> values) {
        return values.stream().allMatch(value -> parseTimestamp(value) != null);
    }

    private int[] calculatePrecisionScale(List<String> values) {
        int precision = 1;
        int scale = 0;
        for (String value : values) {
            BigDecimal decimal = new BigDecimal(value.replace(',', '.').trim());
            decimal = decimal.stripTrailingZeros();
            int valuePrecision = decimal.precision();
            int valueScale = Math.max(decimal.scale(), 0);
            precision = Math.max(precision, valuePrecision);
            scale = Math.max(scale, valueScale);
        }
        precision = Math.max(precision, scale + 1);
        if (precision > 31) {
            precision = 31;
            if (scale >= precision) {
                scale = precision - 1;
            }
        }
        return new int[]{precision, scale};
    }

    private int calculateVarcharLength(int maxLength) {
        int proposed = (int) Math.ceil(maxLength * 1.1d);
        proposed = Math.max(32, proposed);
        return Math.min(4000, proposed);
    }
}
