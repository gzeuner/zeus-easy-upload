package com.zeus.upload.domain;

public class ImportProfile {

    private String name;
    private ImportRequest request;

    public ImportProfile() {
    }

    public ImportProfile(String name, ImportRequest request) {
        this.name = name;
        this.request = request;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ImportRequest getRequest() {
        return request;
    }

    public void setRequest(ImportRequest request) {
        this.request = request;
    }
}
