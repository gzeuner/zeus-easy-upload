package com.zeus.upload.flow;

import com.zeus.upload.connector.rest.RestConnectorSettings;
import com.zeus.upload.connector.rest.RestPaginationConfiguration;
import java.net.URI;
import java.util.Objects;

public final class RestSourceConfiguration implements SourceConfiguration {
    private final URI endpoint;
    private final String recordsField;
    private final RestPaginationConfiguration pagination;
    private final RestConnectorSettings settings;

    public RestSourceConfiguration(URI endpoint, String recordsField,
                                   RestPaginationConfiguration pagination,
                                   RestConnectorSettings settings) {
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint must not be null");
        this.recordsField = recordsField == null || recordsField.isBlank() ? null : recordsField;
        this.pagination = Objects.requireNonNull(pagination, "pagination must not be null");
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
        if (pagination.getMode() != com.zeus.upload.connector.rest.RestPaginationMode.NONE && this.recordsField == null) {
            throw new IllegalArgumentException("Paginated REST source requires recordsField");
        }
    }

    public URI getEndpoint() { return endpoint; }
    public String getRecordsField() { return recordsField; }
    public RestPaginationConfiguration getPagination() { return pagination; }
    public RestConnectorSettings getSettings() { return settings; }
}
