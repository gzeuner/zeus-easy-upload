package com.zeus.upload.connector.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeus.upload.connector.TargetConnector;
import com.zeus.upload.flow.DataRecord;
import com.zeus.upload.flow.RestTargetConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public final class RestJsonTargetConnector implements TargetConnector {

    private final RestTargetConfiguration configuration;
    private final ObjectMapper objectMapper;
    private final RestHttpClient httpClient;
    private long acceptedRecordCount;

    public RestJsonTargetConnector(RestTargetConfiguration configuration) {
        this(configuration, new ObjectMapper());
    }

    public RestJsonTargetConnector(RestTargetConfiguration configuration, ObjectMapper objectMapper) {
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.httpClient = new RestHttpClient(configuration.getSettings());
    }

    @Override
    public void write(Stream<DataRecord> records) {
        String operationId = UUID.randomUUID().toString();
        List<Map<String, Object>> batch = new ArrayList<>();
        AtomicLong batchCounter = new AtomicLong();
        var iterator = records.iterator();
        while (iterator.hasNext()) {
            batch.add(iterator.next().asMap());
            if (batch.size() >= configuration.getBatchSize()) {
                sendSplit(batch, operationId, batchCounter);
                batch = new ArrayList<>();
            }
        }
        if (!batch.isEmpty()) sendSplit(batch, operationId, batchCounter);
    }

    public long getAcceptedRecordCount() { return acceptedRecordCount; }

    private void sendSplit(List<Map<String, Object>> batch, String operationId, AtomicLong batchCounter) {
        byte[] body = serialize(batch);
        if (body.length > configuration.getSettings().getMaxRequestBytes()) {
            if (batch.size() == 1) throw new RestConnectorException(RestConnectorException.Category.PAYLOAD_LIMIT,
                    "REST record exceeds the configured request size limit");
            int midpoint = batch.size() / 2;
            sendSplit(new ArrayList<>(batch.subList(0, midpoint)), operationId, batchCounter);
            sendSplit(new ArrayList<>(batch.subList(midpoint, batch.size())), operationId, batchCounter);
            return;
        }
        java.util.Map<String, String> headers = new java.util.LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        if (configuration.getIdempotencyHeader() != null) {
            headers.put(configuration.getIdempotencyHeader(), operationId + "-" + batchCounter.incrementAndGet());
        }
        boolean retryable = configuration.getMethod() == RestTargetConfiguration.Method.PUT
                || configuration.getIdempotencyHeader() != null;
        httpClient.execute(
                configuration.getEndpoint(), configuration.getMethod().name(), body, headers, retryable);
        acceptedRecordCount += batch.size();
    }

    private byte[] serialize(List<Map<String, Object>> batch) {
        try {
            return objectMapper.writeValueAsBytes(batch);
        } catch (JsonProcessingException ex) {
            throw new RestConnectorException(RestConnectorException.Category.PARSE_FAILURE,
                    "Could not serialize REST request payload", ex);
        }
    }
}
