package com.zeus.upload.domain;

import java.util.Objects;

public class DbColumnMeta {

    private final String columnName;
    private final String typeName;
    private final Integer jdbcType;
    private final Integer length;
    private final Integer precision;
    private final Integer scale;
    private final boolean nullable;
    private final String defaultValue;
    private final Integer ordinalPosition;

    public DbColumnMeta(
            String columnName,
            String typeName,
            Integer jdbcType,
            Integer length,
            Integer precision,
            Integer scale,
            boolean nullable,
            String defaultValue,
            Integer ordinalPosition
    ) {
        this.columnName = columnName;
        this.typeName = typeName;
        this.jdbcType = jdbcType;
        this.length = length;
        this.precision = precision;
        this.scale = scale;
        this.nullable = nullable;
        this.defaultValue = defaultValue;
        this.ordinalPosition = ordinalPosition;
    }

    public String getColumnName() {
        return columnName;
    }

    public String getTypeName() {
        return typeName;
    }

    public Integer getJdbcType() {
        return jdbcType;
    }

    public Integer getLength() {
        return length;
    }

    public Integer getPrecision() {
        return precision;
    }

    public Integer getScale() {
        return scale;
    }

    public boolean isNullable() {
        return nullable;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public Integer getOrdinalPosition() {
        return ordinalPosition;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DbColumnMeta that)) {
            return false;
        }
        return nullable == that.nullable
                && Objects.equals(columnName, that.columnName)
                && Objects.equals(typeName, that.typeName)
                && Objects.equals(jdbcType, that.jdbcType)
                && Objects.equals(length, that.length)
                && Objects.equals(precision, that.precision)
                && Objects.equals(scale, that.scale)
                && Objects.equals(defaultValue, that.defaultValue)
                && Objects.equals(ordinalPosition, that.ordinalPosition);
    }

    @Override
    public int hashCode() {
        return Objects.hash(columnName, typeName, jdbcType, length, precision, scale, nullable, defaultValue, ordinalPosition);
    }
}
