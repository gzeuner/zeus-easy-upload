package com.zeus.upload.connector.file;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeus.upload.flow.CsvTargetConfiguration;
import com.zeus.upload.flow.DataRecord;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class CsvTargetConnectorTest {

    @Test
    void shouldWriteHeadersValuesAndEscapeQuotes() throws Exception {
        var file = Files.createTempFile("zeus-export", ".csv");
        var first = new DataRecord();
        first.set("ID", 1);
        first.set("NAME", "Alice, \"A\"");
        var second = new DataRecord();
        second.set("ID", 2);
        second.set("NAME", "Bob");

        new CsvTargetConnector(new CsvTargetConfiguration(file, ';', '"')).write(Stream.of(first, second));

        assertThat(Files.readString(file)).isEqualTo("\"ID\";\"NAME\"\r\n\"1\";\"Alice, \"\"A\"\"\"\r\n\"2\";\"Bob\"\r\n");
    }

    @Test
    void shouldStreamRecordsUsingConfiguredHeaderOrder() throws Exception {
        var file = Files.createTempFile("zeus-export-stream", ".csv");
        var first = new DataRecord();
        first.set("NAME", "Alice");
        first.set("ID", 1);
        var second = new DataRecord();
        second.set("NAME", "Bob");
        second.set("ID", 2);
        second.set("IGNORED", "not part of the schema");

        new CsvTargetConnector(new CsvTargetConfiguration(file, ';', '"', List.of("ID", "NAME")))
                .write(Stream.of(first, second));

        assertThat(Files.readString(file)).isEqualTo("\"ID\";\"NAME\"\r\n\"1\";\"Alice\"\r\n\"2\";\"Bob\"\r\n");
    }
}
