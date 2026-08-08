package com.zeus.upload.connector.file;

import com.zeus.upload.connector.TargetConnector;
import com.zeus.upload.flow.CsvTargetConfiguration;
import com.zeus.upload.flow.DataRecord;
import com.zeus.upload.flow.FilesystemCsvTargetConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;

public class FilesystemCsvTargetConnector implements TargetConnector {
    private final FilesystemCsvTargetConfiguration configuration;

    public FilesystemCsvTargetConnector(FilesystemCsvTargetConfiguration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
    }

    @Override
    public void write(Stream<DataRecord> records) {
        Path output = FilesystemPathResolver.resolve(configuration.getRootDirectory(), configuration.getRelativeFile());
        try {
            if (output.getParent() != null) Files.createDirectories(output.getParent());
        } catch (IOException ex) {
            throw new IllegalStateException("Could not create filesystem target directory: " + output.getParent(), ex);
        }
        new CsvTargetConnector(new CsvTargetConfiguration(
                output, configuration.getDelimiter(), configuration.getQuote(), configuration.getCharset(), configuration.getHeaders()))
                .write(records);
    }
}
