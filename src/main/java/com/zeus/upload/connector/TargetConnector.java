package com.zeus.upload.connector;

import com.zeus.upload.flow.DataRecord;
import java.util.stream.Stream;

public interface TargetConnector {

    void write(Stream<DataRecord> records);
}
