package com.zeus.upload.service;

import com.zeus.upload.domain.ColumnMapping;
import com.zeus.upload.domain.DbColumnMeta;
import com.zeus.upload.domain.MappingValidationResult;
import com.zeus.upload.domain.ParsedCsv;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MappingService {

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
        MappingValidationResult result = new MappingValidationResult();
        List<ColumnMapping> safeMappings = mappings == null ? List.of() : mappings;
        List<DbColumnMeta> safeDbColumns = dbColumns == null ? List.of() : dbColumns;

        Set<String> existingColumns = new HashSet<>();
        for (DbColumnMeta dbColumn : safeDbColumns) {
            if (!StringUtils.hasText(dbColumn.getColumnName())) {
                continue;
            }
            String key = normalizeDbKey(dbColumn.getColumnName());
            existingColumns.add(key);
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
            if (!dbColumn.isNullable() && !mappedDbColumns.contains(dbColumnKey) && !hasDefault(dbColumn.getDefaultValue())) {
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

        result.setValid(result.getErrors().isEmpty());
        return result;
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
