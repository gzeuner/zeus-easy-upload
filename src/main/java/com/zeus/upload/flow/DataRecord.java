package com.zeus.upload.flow;

import java.util.LinkedHashMap;
import java.util.Map;

public class DataRecord {

    private final Map<String, Object> values = new LinkedHashMap<>();

    public Object get(String key) {
        return values.get(key);
    }

    public void set(String key, Object value) {
        values.put(key, value);
    }

    public Map<String, Object> asMap() {
        return values;
    }
}
