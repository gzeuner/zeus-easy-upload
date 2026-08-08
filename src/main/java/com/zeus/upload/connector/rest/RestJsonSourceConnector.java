package com.zeus.upload.connector.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeus.upload.connector.SourceConnector;
import com.zeus.upload.flow.DataRecord;
import com.zeus.upload.flow.RestSourceConfiguration;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class RestJsonSourceConnector implements SourceConnector {

    private final RestSourceConfiguration configuration;
    private final ObjectMapper objectMapper;

    public RestJsonSourceConnector(RestSourceConfiguration configuration) {
        this(configuration, new ObjectMapper());
    }

    public RestJsonSourceConnector(RestSourceConfiguration configuration, ObjectMapper objectMapper) {
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public Stream<DataRecord> read() {
        RestJsonIterator iterator = new RestJsonIterator();
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, 0), false)
                .onClose(iterator::close);
    }

    private final class RestJsonIterator implements Iterator<DataRecord> {
        private final RestHttpClient httpClient = new RestHttpClient(configuration.getSettings());
        private Iterator<JsonNode> currentPage = List.<JsonNode>of().iterator();
        private int pageNumber;
        private String cursor;
        private boolean firstPage = true;
        private boolean finished;
        private boolean closed;

        @Override
        public boolean hasNext() {
            if (closed) return false;
            while (!currentPage.hasNext() && !finished) loadNextPage();
            return currentPage.hasNext();
        }

        @Override
        public DataRecord next() {
            if (!hasNext()) throw new NoSuchElementException();
            return toRecord(currentPage.next());
        }

        private void loadNextPage() {
            RestPaginationConfiguration pagination = configuration.getPagination();
            if (pagination.getMode() == RestPaginationMode.NONE && !firstPage) {
                finished = true;
                return;
            }
            if (pageNumber >= pagination.getMaxPages()) {
                throw new RestConnectorException(RestConnectorException.Category.PAYLOAD_LIMIT,
                        "REST pagination exceeded the configured page limit");
            }
            URI requestUri = requestUri(pagination);
            byte[] response = httpClient.execute(requestUri, "GET", null, Map.of(), true);
            Page page = parsePage(response);
            pageNumber++;
            firstPage = false;
            currentPage = page.records.iterator();
            if (pagination.getMode() == RestPaginationMode.NONE
                    || page.records.isEmpty()
                    || pagination.getMode() == RestPaginationMode.PAGE_NUMBER
                    && page.records.size() < pagination.getPageSize()) {
                finished = true;
            } else if (pagination.getMode() == RestPaginationMode.CURSOR) {
                cursor = page.nextCursor;
                if (cursor == null || cursor.isBlank()) finished = true;
            }
        }

        private URI requestUri(RestPaginationConfiguration pagination) {
            if (pagination.getMode() == RestPaginationMode.NONE) return configuration.getEndpoint();
            if (pagination.getMode() == RestPaginationMode.PAGE_NUMBER) {
                return withQuery(configuration.getEndpoint(), List.of(
                        Map.entry(pagination.getPageParameter(), String.valueOf(pageNumber + 1)),
                        Map.entry(pagination.getPageSizeParameter(), String.valueOf(pagination.getPageSize()))));
            }
            if (cursor == null) return configuration.getEndpoint();
            return withQuery(configuration.getEndpoint(), List.of(Map.entry(pagination.getCursorParameter(), cursor)));
        }
        private URI withQuery(URI endpoint, List<Map.Entry<String, String>> parameters) {
            StringBuilder query = new StringBuilder(endpoint.getRawQuery() == null ? "" : endpoint.getRawQuery());
            for (Map.Entry<String, String> parameter : parameters) {
                if (query.length() > 0) query.append('&');
                query.append(URLEncoder.encode(parameter.getKey(), StandardCharsets.UTF_8));
                query.append('=').append(URLEncoder.encode(parameter.getValue(), StandardCharsets.UTF_8));
            }
            String base = endpoint.getScheme() + "://" + endpoint.getRawAuthority()
                    + (endpoint.getRawPath() == null ? "" : endpoint.getRawPath());
            return URI.create(base + "?" + query);
        }


        private Page parsePage(byte[] response) {
            try {
                JsonNode root = objectMapper.readTree(response);
                List<JsonNode> records = new ArrayList<>();
                JsonNode recordsNode = root;
                String nextCursor = null;
                if (root != null && root.isObject()) {
                    String field = configuration.getRecordsField();
                    if (field == null) throw new RestConnectorException(RestConnectorException.Category.PARSE_FAILURE,
                            "REST object response requires recordsField");
                    recordsNode = root.get(field);
                    if (configuration.getPagination().getMode() == RestPaginationMode.CURSOR) {
                        JsonNode cursorNode = root.get(configuration.getPagination().getNextCursorField());
                        if (cursorNode != null && !cursorNode.isNull()) nextCursor = cursorNode.asText();
                    }
                }
                if (recordsNode == null || !recordsNode.isArray()) {
                    throw new RestConnectorException(RestConnectorException.Category.PARSE_FAILURE,
                            "REST response does not contain a JSON array of records");
                }
                if (recordsNode.size() > configuration.getSettings().getMaxRecordsPerPage()) {
                    throw new RestConnectorException(RestConnectorException.Category.PAYLOAD_LIMIT,
                            "REST response exceeds the configured record limit");
                }
                for (JsonNode record : recordsNode) {
                    if (!record.isObject()) throw new RestConnectorException(RestConnectorException.Category.PARSE_FAILURE,
                            "REST records must be JSON objects");
                    records.add(record);
                }
                return new Page(records, nextCursor);
            } catch (RestConnectorException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new RestConnectorException(RestConnectorException.Category.PARSE_FAILURE,
                        "Could not parse REST JSON response", ex);
            }
        }

        private DataRecord toRecord(JsonNode node) {
            DataRecord record = new DataRecord();
            node.fields().forEachRemaining(entry -> record.set(entry.getKey(), objectMapper.convertValue(entry.getValue(), Object.class)));
            return record;
        }

        private void close() { closed = true; }
    }

    private record Page(List<JsonNode> records, String nextCursor) { }
}
