package com.zeus.upload.service;

import com.zeus.upload.domain.ColumnMapping;
import com.zeus.upload.domain.DbColumnMeta;
import com.zeus.upload.domain.MappingValidationResult;
import com.zeus.upload.domain.ParsedCsv;
import com.zeus.upload.service.ValueConversionService.SqlTypeFamily;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MappingService {

    private static final int PREFLIGHT_SAMPLE_ROWS = 50;
    private static final int MAX_TYPE_WARNINGS_PER_COLUMN = 3;

    private final ValueConversionService valueConversionService;

    public MappingService() {
        this(new ValueConversionService(new TypeInferenceService()));
    }

    public MappingService(ValueConversionService valueConversionService) {
        this.valueConversionService = valueConversionService;
    }

    public List<ColumnMapping> autoMap(ParsedCsv csv, List<DbColumnMeta> dbColumns) {
        List<DbColumnMeta> safeDbColumns = dbColumns == null ? List.of() : dbColumns;
        Map<String, String> exactMatches = new HashMap<>();
        Map<String, String> underscoreInsensitiveMatches = new HashMap<>();
        for (DbColumnMeta dbColumn : safeDbColumns) {
            String name = dbColumn.getColumnName();
            if (!StringUtils.hasText(name)) {
                continue;
            }
            String normalized = normalize(name);
            if (!normalized.isEmpty()) {
                exactMatches.putIfAbsent(normalized, name);
                underscoreInsensitiveMatches.putIfAbsent(normalized.replace("_", ""), name);
            }
        }

        List<ColumnMapping> mappings = new ArrayList<>();
        List<String> headers = csv == null ? Collections.emptyList() : csv.getOriginalHeaders();
        for (int index = 0; index < headers.size(); index++) {
            String header = headers.get(index);
            String normalizedHeader = normalize(header);
            String target = exactMatches.get(normalizedHeader);
            if (!StringUtils.hasText(target)) {
                target = underscoreInsensitiveMatches.get(normalizedHeader.replace("_", ""));
            }

            ColumnMapping mapping = new ColumnMapping();
            mapping.setCsvIndex(index);
            mapping.setCsvColumn(header);
            mapping.setIgnored(false);
            mapping.setTargetColumn(target);
            if (!StringUtils.hasText(target)) {
                mapping.setNote("CSV column " + header + " unmapped");
            }
            mappings.add(mapping);
        }
        return mappings;
    }

    public MappingValidationResult validate(ParsedCsv csv, List<DbColumnMeta> dbColumns, List<ColumnMapping> mappings) {
        return validate(csv, dbColumns, mappings, false, List.of());
    }

    public MappingValidationResult validate(
            ParsedCsv csv,
            List<DbColumnMeta> dbColumns,
            List<ColumnMapping> mappings,
            boolean upsertEnabled,
            List<String> keyColumns
    ) {
        return validate(csv, dbColumns, mappings, upsertEnabled ? "UPSERT" : "INSERT", keyColumns);
    }

    public MappingValidationResult validate(
            ParsedCsv csv,
            List<DbColumnMeta> dbColumns,
            List<ColumnMapping> mappings,
            String operation,
            List<String> keyColumns
    ) {
        MappingValidationResult result = new MappingValidationResult();
        List<ColumnMapping> safeMappings = mappings == null ? List.of() : mappings;
        List<DbColumnMeta> safeDbColumns = dbColumns == null ? List.of() : dbColumns;

        Set<String> existingColumns = new HashSet<>();
        Map<String, DbColumnMeta> byNormalizedName = new HashMap<>();
        boolean keyOnlyOperation = "UPDATE".equalsIgnoreCase(operation) || "DELETE".equalsIgnoreCase(operation);
        for (DbColumnMeta dbColumn : safeDbColumns) {
            if (!StringUtils.hasText(dbColumn.getColumnName())) {
                continue;
            }
            String key = normalizeDbKey(dbColumn.getColumnName());
            existingColumns.add(key);
            byNormalizedName.put(key, dbColumn);
        }

        Set<String> mappedDbColumns = new HashSet<>();
        Set<String> duplicateGuard = new HashSet<>();
        for (ColumnMapping mapping : safeMappings) {
            if (mapping == null || mapping.isIgnored() || !StringUtils.hasText(mapping.getTargetColumn())) {
                continue;
            }

            String dbColumnKey = normalizeDbKey(mapping.getTargetColumn());
            if (!existingColumns.contains(dbColumnKey)) {
                result.getErrors().add("Mapped target column '" + mapping.getTargetColumn() + "' does not exist.");
                continue;
            }
            if (!duplicateGuard.add(dbColumnKey)) {
                result.getErrors().add("Target column '" + mapping.getTargetColumn() + "' is mapped more than once.");
                continue;
            }
            mappedDbColumns.add(dbColumnKey);
        }

        for (DbColumnMeta dbColumn : safeDbColumns) {
            if (!StringUtils.hasText(dbColumn.getColumnName())) {
                continue;
            }
            String dbColumnKey = normalizeDbKey(dbColumn.getColumnName());
            if (!keyOnlyOperation && !dbColumn.isNullable() && !mappedDbColumns.contains(dbColumnKey) && !hasDefault(dbColumn.getDefaultValue())) {
                result.getErrors().add("Required target column '" + dbColumn.getColumnName()
                        + "' is not mapped and has no default value.");
            }
            if (!mappedDbColumns.contains(dbColumnKey)) {
                result.getWarnings().add("DB column '" + dbColumn.getColumnName() + "' is not used.");
            }
        }

        Set<Integer> mappedCsvIndexes = new HashSet<>();
        Set<Integer> ignoredCsvIndexes = new HashSet<>();
        Set<Integer> warnedUnmappedCsvIndexes = new HashSet<>();
        for (ColumnMapping mapping : safeMappings) {
            if (mapping == null) {
                continue;
            }
            if (mapping.isIgnored()) {
                ignoredCsvIndexes.add(mapping.getCsvIndex());
                continue;
            }
            if (!StringUtils.hasText(mapping.getTargetColumn())) {
                result.getWarnings().add("CSV column '" + mapping.getCsvColumn() + "' is unmapped.");
                warnedUnmappedCsvIndexes.add(mapping.getCsvIndex());
            } else {
                mappedCsvIndexes.add(mapping.getCsvIndex());
            }
        }

        List<String> headers = csv == null ? List.of() : csv.getOriginalHeaders();
        for (int i = 0; i < headers.size(); i++) {
            if (!mappedCsvIndexes.contains(i) && !ignoredCsvIndexes.contains(i) && !warnedUnmappedCsvIndexes.contains(i)) {
                result.getWarnings().add("CSV column '" + headers.get(i) + "' is unmapped.");
            }
        }

        if ("UPDATE".equalsIgnoreCase(operation) || "DELETE".equalsIgnoreCase(operation)
                || "UPSERT".equalsIgnoreCase(operation)) {
            Set<String> normalizedKeys = new LinkedHashSet<>();
            List<String> safeKeyColumns = keyColumns == null ? List.of() : keyColumns;
            for (String keyColumn : safeKeyColumns) {
                if (!StringUtils.hasText(keyColumn)) {
                    continue;
                }
                normalizedKeys.add(normalizeDbKey(keyColumn));
            }
            if (normalizedKeys.isEmpty()) {
                result.getErrors().add("At least one key column is required for " + operation.toLowerCase() + " mode.");
            } else {
                for (String normalizedKey : normalizedKeys) {
                    if (!existingColumns.contains(normalizedKey)) {
                        result.getErrors().add("Key column '" + normalizedKey + "' does not exist in table metadata.");
                        continue;
                    }
                    if (!mappedDbColumns.contains(normalizedKey)) {
                        result.getErrors().add("Key column '" + normalizedKey + "' must be mapped and not ignored.");
                        continue;
                    }
                    DbColumnMeta keyMeta = byNormalizedName.get(normalizedKey);
                    if (keyMeta != null && keyMeta.isNullable()) {
                        result.getWarnings().add(
                                "Key column '" + keyMeta.getColumnName() + "' is nullable; NOT NULL keys are recommended."
                        );
                    }
                }
            }
        }

        // Type preflight: try converting sample CSV values to the target DB column type.
        if (!"DELETE".equalsIgnoreCase(operation)) {
            validateSampleConversions(csv, safeMappings, byNormalizedName, result);
        }

        result.setValid(result.getErrors().isEmpty());
        return result;
    }

    private void validateSampleConversions(
            ParsedCsv csv,
            List<ColumnMapping> mappings,
            Map<String, DbColumnMeta> byNormalizedName,
            MappingValidationResult result
    ) {
        if (csv == null || csv.getRows() == null || csv.getRows().isEmpty()) {
            return;
        }
        int sampleLimit = Math.min(PREFLIGHT_SAMPLE_ROWS, csv.getRows().size());
        for (ColumnMapping mapping : mappings) {
            if (mapping == null || mapping.isIgnored() || !StringUtils.hasText(mapping.getTargetColumn())) {
                continue;
            }
            DbColumnMeta meta = byNormalizedName.get(normalizeDbKey(mapping.getTargetColumn()));
            if (meta == null) {
                continue;
            }
            SqlTypeFamily family = valueConversionService.familyFrom(meta.getTypeName(), meta.getJdbcType());
            int csvIndex = mapping.getCsvIndex();
            int warningsForColumn = 0;
            int failures = 0;
            String firstFailure = null;
            for (int row = 0; row < sampleLimit; row++) {
                List<String> values = csv.getRows().get(row);
                String raw = csvIndex < values.size() ? values.get(csvIndex) : null;
                if (!StringUtils.hasText(raw == null ? null : raw.trim())) {
                    if (!meta.isNullable() && !hasDefault(meta.getDefaultValue())) {
                        failures++;
                        if (firstFailure == null) {
                            firstFailure = "empty value not allowed for NOT NULL column";
                        }
                    }
                    continue;
                }
                String error = valueConversionService.conversionError(raw, family);
                if (error != null) {
                    failures++;
                    if (firstFailure == null) {
                        firstFailure = error;
                    }
                    if (warningsForColumn < MAX_TYPE_WARNINGS_PER_COLUMN) {
                        result.getWarnings().add(
                                "Type preflight: CSV '" + mapping.getCsvColumn() + "' → "
                                        + meta.getColumnName() + " (" + family + "), sample row "
                                        + (row + 2) + ": " + error);
                        warningsForColumn++;
                    }
                }
            }
            if (failures > 0 && firstFailure != null) {
                // Soft by default: warnings only. Hard error if majority of samples fail.
                if (failures * 2 >= sampleLimit) {
                    result.getErrors().add(
                            "Type conversion likely fails for '" + mapping.getCsvColumn() + "' → "
                                    + meta.getColumnName() + " (" + family + "): " + firstFailure
                                    + " (" + failures + "/" + sampleLimit + " sample values).");
                }
            }
            if (StringUtils.hasText(mapping.getNote()) && mapping.getNote().contains("unmapped")) {
                continue;
            }
            if (mapping.getNote() == null || mapping.getNote().isBlank()) {
                mapping.setNote("Target type: " + family
                        + (meta.getTypeName() == null ? "" : " (" + meta.getTypeName() + ")"));
            }
        }
    }

    private boolean hasDefault(String defaultValue) {
        return StringUtils.hasText(defaultValue);
    }

    private String normalizeDbKey(String value) {
        return (value == null ? "" : value.trim().toUpperCase(Locale.ROOT));
    }

    private String normalize(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        normalized = normalized.replaceAll("[\\s-]+", "_");
        normalized = normalized.replaceAll("[^A-Z0-9_]", "");
        normalized = normalized.replaceAll("_+", "_");
        normalized = normalized.replaceAll("^_+", "");
        normalized = normalized.replaceAll("_+$", "");
        return normalized;
    }
}
