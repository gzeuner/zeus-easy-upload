package com.zeus.upload.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    @NotBlank
    private String defaultLibrary = "BIB";

    @Min(1)
    @Max(10_000)
    private int sampleRows = 200;

    @Min(1)
    @Max(10_000)
    private int batchSize = 500;

    private String profileDirectory = "profiles";

    private String connectionProfileDirectory = "connections";

    private String connectionMasterKey = "";

    public String getDefaultLibrary() {
        return defaultLibrary;
    }

    public void setDefaultLibrary(String defaultLibrary) {
        this.defaultLibrary = defaultLibrary;
    }

    public int getSampleRows() {
        return sampleRows;
    }

    public void setSampleRows(int sampleRows) {
        this.sampleRows = sampleRows;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public String getProfileDirectory() {
        return profileDirectory;
    }

    public void setProfileDirectory(String profileDirectory) {
        this.profileDirectory = profileDirectory;
    }
    public String getConnectionProfileDirectory() {
        return connectionProfileDirectory;
    }

    public void setConnectionProfileDirectory(String connectionProfileDirectory) {
        this.connectionProfileDirectory = connectionProfileDirectory;
    }

    public String getConnectionMasterKey() {
        return connectionMasterKey;
    }

    public void setConnectionMasterKey(String connectionMasterKey) {
        this.connectionMasterKey = connectionMasterKey;
    }
}
