package com.zeus.upload.connector.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.zeus.upload.connector.ConnectorFactory;
import com.zeus.upload.connector.SourceConnector;
import com.zeus.upload.connector.TargetConnector;
import com.zeus.upload.flow.DataRecord;
import com.zeus.upload.flow.FilesystemCsvSourceConfiguration;
import com.zeus.upload.flow.FilesystemCsvTargetConfiguration;
import com.zeus.upload.service.ImportService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class FilesystemCsvConnectorTest {

    @Test
    void readsCsvLazilyBelowConfiguredRoot() throws Exception {
        Path root = Files.createTempDirectory("zeus-filesystem-source");
        Files.createDirectories(root.resolve("incoming"));
        Files.writeString(root.resolve("incoming/data.csv"), "id;name\n1;\"Ada;Lovelace\"\n2;Grace\n");

        FilesystemCsvSourceConfiguration configuration = new FilesystemCsvSourceConfiguration(
                root, "incoming/data.csv", StandardCharsets.UTF_8, ';', '"');

        List<DataRecord> records;
        try (Stream<DataRecord> stream = new FilesystemCsvSourceConnector(configuration).read()) {
            records = stream.toList();
        }

        assertThat(records).hasSize(2);
        assertThat(records.get(0).get("id")).isEqualTo("1");
        assertThat(records.get(0).get("name")).isEqualTo("Ada;Lovelace");
        assertThat(records.get(1).get("name")).isEqualTo("Grace");
    }

    @Test
    void writesNestedCsvWithConfiguredCharsetAndEscaping() throws Exception {
        Path root = Files.createTempDirectory("zeus-filesystem-target");
        FilesystemCsvTargetConfiguration configuration = new FilesystemCsvTargetConfiguration(
                root, "out/export.csv", StandardCharsets.ISO_8859_1, ';', '"', List.of("id", "name"));
        DataRecord record = new DataRecord();
        record.set("id", 1);
        record.set("name", "Jörg;Test");

        new FilesystemCsvTargetConnector(configuration).write(Stream.of(record));

        assertThat(Files.readString(root.resolve("out/export.csv"), StandardCharsets.ISO_8859_1))
                .isEqualTo("\"id\";\"name\"\r\n\"1\";\"Jörg;Test\"\r\n");
    }

    @Test
    void rejectsPathsOutsideConfiguredRoot() throws Exception {
        Path root = Files.createTempDirectory("zeus-filesystem-security");
        FilesystemCsvSourceConfiguration source = new FilesystemCsvSourceConfiguration(root, "../outside.csv");

        assertThatThrownBy(() -> new FilesystemCsvSourceConnector(source).read())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("escapes");
        assertThatThrownBy(() -> new FilesystemCsvTargetConfiguration(root, root.resolve("absolute.csv").toString(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("relative");
    }

    @Test
    void factoryCreatesFilesystemAdapters() throws Exception {
        ConnectorFactory factory = new ConnectorFactory(mock(ImportService.class));
        Path root = Files.createTempDirectory("zeus-filesystem-factory");

        SourceConnector source = factory.createSource(new FilesystemCsvSourceConfiguration(root, "in.csv"));
        TargetConnector target = factory.createTarget(new FilesystemCsvTargetConfiguration(root, "out.csv", List.of("id")));

        assertThat(source).isInstanceOf(FilesystemCsvSourceConnector.class);
        assertThat(target).isInstanceOf(FilesystemCsvTargetConnector.class);
    }
}
