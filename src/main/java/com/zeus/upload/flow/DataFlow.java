package com.zeus.upload.flow;

import com.zeus.upload.connector.SourceConnector;
import com.zeus.upload.connector.TargetConnector;
import java.util.Objects;
import java.util.stream.Stream;

public class DataFlow {

    private final SourceConnector source;
    private final TargetConnector target;

    public DataFlow(SourceConnector source, TargetConnector target) {
        this.source = Objects.requireNonNull(source, "source must not be null");
        this.target = Objects.requireNonNull(target, "target must not be null");
    }

    public void execute() {
        try (Stream<DataRecord> records = source.read()) {
            target.write(records);
        }
    }
}
