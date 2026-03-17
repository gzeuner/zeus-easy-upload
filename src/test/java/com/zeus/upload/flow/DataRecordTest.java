package com.zeus.upload.flow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class DataRecordTest {

    @Test
    void shouldStoreAndExposeValuesInInsertionOrder() {
        DataRecord record = new DataRecord();

        record.set("ID", 1);
        record.set("NAME", "Alice");

        assertThat(record.get("ID")).isEqualTo(1);
        assertThat(record.get("NAME")).isEqualTo("Alice");
        assertThat(List.copyOf(record.asMap().keySet())).containsExactly("ID", "NAME");
    }
}
