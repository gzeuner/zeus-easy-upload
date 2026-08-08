package com.zeus.upload.connector.file;

import com.zeus.upload.connector.SourceConnector;
import com.zeus.upload.flow.DataRecord;
import com.zeus.upload.flow.FilesystemCsvSourceConfiguration;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

public class FilesystemCsvSourceConnector implements SourceConnector {
    private final FilesystemCsvSourceConfiguration configuration;

    public FilesystemCsvSourceConnector(FilesystemCsvSourceConfiguration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
    }

    @Override
    public Stream<DataRecord> read() {
        Path input = FilesystemPathResolver.resolve(configuration.getRootDirectory(), configuration.getRelativeFile());
        try {
            BufferedReader reader = Files.newBufferedReader(input, configuration.getCharset());
            CSVParser parser = CSVFormat.DEFAULT.builder()
                    .setDelimiter(configuration.getDelimiter())
                    .setQuote(configuration.getQuote())
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setIgnoreEmptyLines(true)
                    .build().parse(reader);
            List<String> headers = parser.getHeaderNames();
            if (headers.isEmpty()) {
                closeQuietly(parser, reader);
                throw new IllegalArgumentException("CSV has no header row: " + configuration.getRelativeFile());
            }
            Iterator<DataRecord> records = parser.stream()
                    .map(record -> toDataRecord(headers, record))
                    .iterator();
            return StreamSupport.stream(Spliterators.spliteratorUnknownSize(records, 0), false)
                    .onClose(() -> closeQuietly(parser, reader));
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read filesystem CSV source: " + input, ex);
        }
    }

    private DataRecord toDataRecord(List<String> headers, CSVRecord csvRecord) {
        DataRecord record = new DataRecord();
        for (int index = 0; index < headers.size(); index++) {
            record.set(headers.get(index), csvRecord.isSet(index) ? csvRecord.get(index) : null);
        }
        return record;
    }

    private void closeQuietly(CSVParser parser, BufferedReader reader) {
        try { parser.close(); } catch (IOException ignored) { }
        try { reader.close(); } catch (IOException ignored) { }
    }
}
