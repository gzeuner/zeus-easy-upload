package com.zeus.upload.connector.file;

import com.zeus.upload.connector.TargetConnector;
import com.zeus.upload.flow.CsvTargetConfiguration;
import com.zeus.upload.flow.DataRecord;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
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
        List<DataRecord> materialized = records.toList();
        List<String> headers = new ArrayList<>();
        for (DataRecord record : materialized) {
            for (String key : record.asMap().keySet()) {
                if (!headers.contains(key)) headers.add(key);
            }
        }
        try {
            if (configuration.getOutputFile().getParent() != null) {
                Files.createDirectories(configuration.getOutputFile().getParent());
            }
            try (BufferedWriter writer = Files.newBufferedWriter(configuration.getOutputFile(), StandardCharsets.UTF_8)) {
                writeRow(writer, headers);
                for (DataRecord record : materialized) {
                    List<String> values = headers.stream()
                            .map(header -> String.valueOf(record.get(header) == null ? "" : record.get(header)))
                            .toList();
                    writeRow(writer, values);
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Could not write CSV target: " + ex.getMessage(), ex);
        }
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
