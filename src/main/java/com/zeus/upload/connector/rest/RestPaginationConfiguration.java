package com.zeus.upload.connector.rest;

import java.util.Objects;

public final class RestPaginationConfiguration {

    private final RestPaginationMode mode;
    private final String pageParameter;
    private final String pageSizeParameter;
    private final String cursorParameter;
    private final String recordsField;
    private final String nextCursorField;
    private final int pageSize;
    private final int maxPages;

    private RestPaginationConfiguration(Builder builder) {
        mode = builder.mode;
        pageParameter = builder.pageParameter;
        pageSizeParameter = builder.pageSizeParameter;
        cursorParameter = builder.cursorParameter;
        recordsField = builder.recordsField;
        nextCursorField = builder.nextCursorField;
        pageSize = builder.pageSize;
        maxPages = builder.maxPages;
        if (mode == RestPaginationMode.CURSOR && (recordsField == null || nextCursorField == null)) {
            throw new IllegalArgumentException("Cursor pagination requires recordsField and nextCursorField");
        }
        if (mode != RestPaginationMode.NONE && recordsField == null) {
            throw new IllegalArgumentException("Pagination requires recordsField");
        }
    }

    public static RestPaginationConfiguration none() { return builder().mode(RestPaginationMode.NONE).build(); }
    public static Builder builder() { return new Builder(); }
    public RestPaginationMode getMode() { return mode; }
    public String getPageParameter() { return pageParameter; }
    public String getPageSizeParameter() { return pageSizeParameter; }
    public String getCursorParameter() { return cursorParameter; }
    public String getRecordsField() { return recordsField; }
    public String getNextCursorField() { return nextCursorField; }
    public int getPageSize() { return pageSize; }
    public int getMaxPages() { return maxPages; }

    public static final class Builder {
        private RestPaginationMode mode = RestPaginationMode.NONE;
        private String pageParameter = "page";
        private String pageSizeParameter = "pageSize";
        private String cursorParameter = "cursor";
        private String recordsField;
        private String nextCursorField;
        private int pageSize = 100;
        private int maxPages = 1_000;

        public Builder mode(RestPaginationMode value) { mode = Objects.requireNonNull(value); return this; }
        public Builder pageParameter(String value) { pageParameter = text(value, "pageParameter"); return this; }
        public Builder pageSizeParameter(String value) { pageSizeParameter = text(value, "pageSizeParameter"); return this; }
        public Builder cursorParameter(String value) { cursorParameter = text(value, "cursorParameter"); return this; }
        public Builder recordsField(String value) { recordsField = text(value, "recordsField"); return this; }
        public Builder nextCursorField(String value) { nextCursorField = text(value, "nextCursorField"); return this; }
        public Builder pageSize(int value) { if (value < 1 || value > 10_000) throw new IllegalArgumentException("pageSize is outside the allowed range"); pageSize = value; return this; }
        public Builder maxPages(int value) { if (value < 1 || value > 10_000) throw new IllegalArgumentException("maxPages is outside the allowed range"); maxPages = value; return this; }
        public RestPaginationConfiguration build() { return new RestPaginationConfiguration(this); }

        private String text(String value, String name) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
            return value;
        }
    }
}
