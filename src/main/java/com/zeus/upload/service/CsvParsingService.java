package com.zeus.upload.service;

import com.zeus.upload.config.AppProperties;
import com.zeus.upload.domain.ColumnProposal;
import com.zeus.upload.domain.ParseError;
import com.zeus.upload.domain.ParsedCsv;
import com.zeus.upload.util.ColumnNameSanitizer;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CsvParsingService {

    private static final Logger log = LoggerFactory.getLogger(CsvParsingService.class);

    private final TypeInferenceService typeInferenceService;
    private final ColumnNameSanitizer columnNameSanitizer;
    private final AppProperties appProperties;

    public CsvParsingService(
            TypeInferenceService typeInferenceService,
            ColumnNameSanitizer columnNameSanitizer,
            AppProperties appProperties
    ) {
        this.typeInferenceService = typeInferenceService;
        this.columnNameSanitizer = columnNameSanitizer;
        this.appProperties = appProperties;
    }

    public ParsedCsv parse(MultipartFile file) throws IOException {
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        char delimiter = detectDelimiter(content);
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiter)
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .build();

        ParsedCsv parsedCsv = new ParsedCsv();
        parsedCsv.setDelimiter(delimiter);

        try (CSVParser parser = CSVParser.parse(new StringReader(content), format)) {
            List<String> headers = parser.getHeaderNames();
            if (headers.isEmpty()) {
                throw new IllegalArgumentException("CSV has no header row.");
            }
            parsedCsv.getOriginalHeaders().addAll(headers);

            List<List<String>> sampleValues = initColumnLists(headers.size());
            Set<String> usedColumnNames = new HashSet<>();
            Set<String> seenSanitizedBase = new HashSet<>();

            for (int i = 0; i < headers.size(); i++) {
                String header = headers.get(i);
                String base = columnNameSanitizer.sanitizeBase(header);
                boolean duplicate = !seenSanitizedBase.add(base);
                String unique = columnNameSanitizer.uniquify(base, usedColumnNames, ColumnNameSanitizer.MAX_COLUMN_LENGTH);

                ColumnProposal proposal = new ColumnProposal();
                proposal.setIndex(i);
                proposal.setOriginalName(header);
                proposal.setSanitizedName(unique);
                proposal.setFinalName(unique);
                proposal.setDuplicate(duplicate);
                parsedCsv.getProposals().add(proposal);
            }

            long csvRowNumber = 1L;
            for (CSVRecord record : parser) {
                csvRowNumber++;
                List<String> row = new ArrayList<>();
                for (int i = 0; i < headers.size(); i++) {
                    String value = record.isSet(i) ? record.get(i) : "";
                    row.add(value);
                    if (sampleValues.get(i).size() < appProperties.getSampleRows()) {
                        sampleValues.get(i).add(value);
                    }
                }

                if (record.size() != headers.size()) {
                    parsedCsv.getParseErrors().add(new ParseError(
                            csvRowNumber,
                            "",
                            "",
                            "Column count mismatch. Expected " + headers.size() + ", got " + record.size()
                    ));
                }

                parsedCsv.getRows().add(row);
                if (parsedCsv.getPreviewRows().size() < 20) {
                    parsedCsv.getPreviewRows().add(new ArrayList<>(row));
                }
            }

            for (int i = 0; i < parsedCsv.getProposals().size(); i++) {
                ColumnProposal inferred = typeInferenceService.inferColumn(
                        i,
                        headers.get(i),
                        parsedCsv.getProposals().get(i).getSanitizedName(),
                        sampleValues.get(i)
                );
                inferred.setDuplicate(parsedCsv.getProposals().get(i).isDuplicate());
                parsedCsv.getProposals().set(i, inferred);
            }
        }

        log.info("Parsed CSV with {} rows and {} columns using delimiter '{}'",
                parsedCsv.getRows().size(), parsedCsv.getProposals().size(), delimiter);
        return parsedCsv;
    }

    private List<List<String>> initColumnLists(int columns) {
        List<List<String>> sampleValues = new ArrayList<>();
        for (int i = 0; i < columns; i++) {
            sampleValues.add(new ArrayList<>());
        }
        return sampleValues;
    }

    private char detectDelimiter(String content) {
        String[] lines = content.split("\\R");
        for (String line : lines) {
            if (!line.isBlank()) {
                int comma = count(line, ',');
                int semicolon = count(line, ';');
                int tab = count(line, '\t');
                int pipe = count(line, '|');
                int max = Math.max(Math.max(comma, semicolon), Math.max(tab, pipe));
                if (max == semicolon) {
                    return ';';
                }
                if (max == tab) {
                    return '\t';
                }
                if (max == pipe) {
                    return '|';
                }
                return ',';
            }
        }
        return ',';
    }

    private int count(String line, char ch) {
        int count = 0;
        for (char c : line.toCharArray()) {
            if (c == ch) {
                count++;
            }
        }
        return count;
    }
}
