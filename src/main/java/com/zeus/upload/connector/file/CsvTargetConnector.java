package com.zeus.upload.connector.file;

import com.zeus.upload.connector.TargetConnector;
import com.zeus.upload.flow.CsvTargetConfiguration;
import com.zeus.upload.flow.DataRecord;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class CsvTargetConnector implements TargetConnector {

    private final CsvTargetConfiguration configuration;

    public CsvTargetConnector(CsvTargetConfiguration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
    }

    @Override
    public void write(Stream<DataRecord> records) {
        Iterator<DataRecord> iterator = records.iterator();
        List<String> headers = new ArrayList<>(configuration.getHeaders());
        DataRecord firstRecord = null;
        if (headers.isEmpty() && iterator.hasNext()) {
            firstRecord = iterator.next();
            headers.addAll(firstRecord.asMap().keySet());
        }
        try {
            if (configuration.getOutputFile().getParent() != null) {
                Files.createDirectories(configuration.getOutputFile().getParent());
            }
            try (BufferedWriter writer = Files.newBufferedWriter(configuration.getOutputFile(), configuration.getCharset())) {
                writeRow(writer, headers);
                if (firstRecord != null) writeRecord(writer, headers, firstRecord);
                while (iterator.hasNext()) {
                    writeRecord(writer, headers, iterator.next());
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Could not write CSV target: " + ex.getMessage(), ex);
        }
    }

    private void writeRecord(BufferedWriter writer, List<String> headers, DataRecord record) throws IOException {
        List<String> values = headers.stream()
                .map(header -> String.valueOf(record.get(header) == null ? "" : record.get(header)))
                .toList();
        writeRow(writer, values);
    }

    private void writeRow(BufferedWriter writer, List<String> values) throws IOException {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) writer.write(configuration.getDelimiter());
            String value = values.get(i);
            writer.write(configuration.getQuote());
            writer.write(value.replace(String.valueOf(configuration.getQuote()),
                    String.valueOf(configuration.getQuote()) + configuration.getQuote()));
            writer.write(configuration.getQuote());
        }
        writer.write("\r\n");
    }
}
