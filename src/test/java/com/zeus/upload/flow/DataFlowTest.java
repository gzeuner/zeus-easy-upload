package com.zeus.upload.flow;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeus.upload.connector.SourceConnector;
import com.zeus.upload.connector.TargetConnector;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class DataFlowTest {

    @Test
    void shouldPassSourceRecordsToTarget() {
        DataRecord first = new DataRecord();
        first.set("ID", 1);
        DataRecord second = new DataRecord();
        second.set("ID", 2);

        SourceConnector source = () -> Stream.of(first, second);
        List<DataRecord> written = new ArrayList<>();
        TargetConnector target = records -> records.forEach(written::add);

        new DataFlow(source, target).execute();

        assertThat(written).containsExactly(first, second);
    }
}
