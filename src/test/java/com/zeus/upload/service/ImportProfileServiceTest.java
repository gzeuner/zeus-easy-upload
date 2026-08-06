package com.zeus.upload.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeus.upload.domain.ImportRequest;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

class ImportProfileServiceTest {

    @Test
    void shouldSaveLoadListAndDeleteProfile() throws Exception {
        var directory = Files.createTempDirectory("zeus-profiles");
        var service = new ImportProfileService(new ObjectMapper(), directory);
        var request = new ImportRequest();
        request.setLibrary("BIB");
        request.setTableName("CUSTOMER");
        request.setOperation("UPSERT");

        service.save("customer-sync", request);

        assertThat(service.list()).containsExactly("customer-sync");
        assertThat(service.load("customer-sync").getRequest().getOperation()).isEqualTo("UPSERT");
        service.delete("customer-sync");
        assertThat(service.list()).isEmpty();
    }

    @Test
    void shouldRejectUnsafeProfileNames() throws Exception {
        var service = new ImportProfileService(new ObjectMapper(), Files.createTempDirectory("zeus-profiles"));

        assertThat(service.list()).isEmpty();
        assertThatThrownBy(() -> service.delete("../outside"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
