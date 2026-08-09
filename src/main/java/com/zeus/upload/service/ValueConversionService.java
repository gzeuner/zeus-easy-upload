package com.zeus.upload.service;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Converts CSV string cells to JDBC values for CREATE and existing-table imports.
 * Shared by {@link ImportService} (binding) and {@link MappingService} (preflight).
 */
@Service
public class ValueConversionService {

    public enum SqlTypeFamily {
        INTEGER,
        BIGINT,
        DECIMAL,
        DATE,
        TIMESTAMP,
        VARCHAR
    }

    private final TypeInferenceService typeInferenceService;

    public ValueConversionService(TypeInferenceService typeInferenceService) {
        this.typeInferenceService = typeInferenceService;
    }

    public SqlTypeFamily familyFromSqlType(String sqlType) {
        return familyFrom(sqlType, null);
    }

    public SqlTypeFamily familyFrom(String typeName, Integer jdbcType) {
        String upper = typeName == null ? "" : typeName.trim().toUpperCase(Locale.ROOT);
        if (upper.contains("BIGINT") || upper.contains("INT8") || (jdbcType != null && jdbcType == Types.BIGINT)) {
            return SqlTypeFamily.BIGINT;
        }
        if (upper.contains("INT") || upper.contains("SMALLINT") || upper.equals("SERIAL")
                || (jdbcType != null && (jdbcType == Types.INTEGER || jdbcType == Types.SMALLINT || jdbcType == Types.TINYINT))) {
            return SqlTypeFamily.INTEGER;
        }
        if (upper.contains("DEC") || upper.contains("NUMERIC") || upper.contains("NUMBER")
                || (jdbcType != null && (jdbcType == Types.DECIMAL || jdbcType == Types.NUMERIC))) {
            return SqlTypeFamily.DECIMAL;
        }
        if (upper.contains("TIMESTAMP") || upper.contains("DATETIME")
                || (jdbcType != null && jdbcType == Types.TIMESTAMP)) {
            return SqlTypeFamily.TIMESTAMP;
        }
        if (upper.equals("DATE") || (jdbcType != null && jdbcType == Types.DATE)) {
            return SqlTypeFamily.DATE;
        }
        return SqlTypeFamily.VARCHAR;
    }

    /**
     * @return null when conversion succeeds (including blank → NULL), otherwise error text
     */
    public String conversionError(String rawValue, SqlTypeFamily family) {
        try {
            convert(rawValue, family);
            return null;
        } catch (IllegalArgumentException ex) {
            return ex.getMessage();
        }
    }

    public Object convert(String rawValue, SqlTypeFamily family) {
        String trimmed = rawValue == null ? "" : rawValue.trim();
        if (!StringUtils.hasText(trimmed)) {
            return null;
        }
        return switch (family == null ? SqlTypeFamily.VARCHAR : family) {
            case INTEGER -> {
                try {
                    yield Integer.parseInt(trimmed);
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("Invalid INTEGER value: '" + trimmed + "'");
                }
            }
            case BIGINT -> {
                try {
                    yield Long.parseLong(trimmed);
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("Invalid BIGINT value: '" + trimmed + "'");
                }
            }
            case DECIMAL -> {
                try {
                    yield new BigDecimal(trimmed.replace(',', '.'));
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("Invalid DECIMAL value: '" + trimmed + "'");
                }
            }
            case DATE -> {
                LocalDate date = typeInferenceService.parseDate(trimmed);
                if (date == null) {
                    throw new IllegalArgumentException("Invalid DATE value: '" + trimmed + "'");
                }
                yield date;
            }
            case TIMESTAMP -> {
                LocalDateTime timestamp = typeInferenceService.parseTimestamp(trimmed);
                if (timestamp == null) {
                    // allow date-only as midnight timestamp
                    LocalDate date = typeInferenceService.parseDate(trimmed);
                    if (date == null) {
                        throw new IllegalArgumentException("Invalid TIMESTAMP value: '" + trimmed + "'");
                    }
                    yield date.atStartOfDay();
                }
                yield timestamp;
            }
            case VARCHAR -> trimmed;
        };
    }

    public void bind(PreparedStatement statement, int parameterIndex, String rawValue, SqlTypeFamily family)
            throws SQLException {
        Object value;
        try {
            value = convert(rawValue, family);
        } catch (IllegalArgumentException ex) {
            throw new SQLException(ex.getMessage(), ex);
        }
        if (value == null) {
            statement.setNull(parameterIndex, jdbcType(family));
            return;
        }
        switch (family == null ? SqlTypeFamily.VARCHAR : family) {
            case INTEGER -> statement.setInt(parameterIndex, (Integer) value);
            case BIGINT -> statement.setLong(parameterIndex, (Long) value);
            case DECIMAL -> statement.setBigDecimal(parameterIndex, (BigDecimal) value);
            case DATE -> statement.setDate(parameterIndex, Date.valueOf((LocalDate) value));
            case TIMESTAMP -> statement.setTimestamp(parameterIndex, Timestamp.valueOf((LocalDateTime) value));
            case VARCHAR -> statement.setString(parameterIndex, (String) value);
        }
    }

    public int jdbcType(SqlTypeFamily family) {
        return switch (family == null ? SqlTypeFamily.VARCHAR : family) {
            case INTEGER -> Types.INTEGER;
            case BIGINT -> Types.BIGINT;
            case DECIMAL -> Types.DECIMAL;
            case DATE -> Types.DATE;
            case TIMESTAMP -> Types.TIMESTAMP;
            case VARCHAR -> Types.VARCHAR;
        };
    }
}
