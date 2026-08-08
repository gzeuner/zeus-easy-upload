package com.zeus.upload.domain;

import java.util.LinkedHashMap;
import java.util.Map;

public class ConnectionProfileRequest {

    private String name;
    private ConnectionType type;
    private String endpoint;
    private String description;
    private Map<String, String> credentials = new LinkedHashMap<>();

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public ConnectionType getType() { return type; }
    public void setType(ConnectionType type) { this.type = type; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Map<String, String> getCredentials() { return credentials; }
    public void setCredentials(Map<String, String> credentials) {
        this.credentials = credentials == null ? new LinkedHashMap<>() : new LinkedHashMap<>(credentials);
    }
}
