package com.zeus.upload.domain;

public class ConnectionProfile {

    private String name;
    private ConnectionType type;
    private String endpoint;
    private String description;
    private boolean credentialsConfigured;
    private String updatedAt;

    public ConnectionProfile() {
    }

    public ConnectionProfile(String name, ConnectionType type, String endpoint, String description,
                             boolean credentialsConfigured, String updatedAt) {
        this.name = name;
        this.type = type;
        this.endpoint = endpoint;
        this.description = description;
        this.credentialsConfigured = credentialsConfigured;
        this.updatedAt = updatedAt;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public ConnectionType getType() { return type; }
    public void setType(ConnectionType type) { this.type = type; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isCredentialsConfigured() { return credentialsConfigured; }
    public void setCredentialsConfigured(boolean credentialsConfigured) { this.credentialsConfigured = credentialsConfigured; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
