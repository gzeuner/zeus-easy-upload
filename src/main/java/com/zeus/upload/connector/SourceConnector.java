package com.zeus.upload.connector;

import com.zeus.upload.flow.DataRecord;
import java.util.stream.Stream;

public interface SourceConnector {

    Stream<DataRecord> read();
}
