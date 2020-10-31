package com.sequenceiq.cloudbreak.structuredevent.event;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class RdsDetails implements Serializable {

    @Deprecated
    private Long id;

    @Deprecated
    private String name;

    @Deprecated
    private String description;

    @Deprecated
    private String connectionURL;

    private String sslMode;

    private String databaseEngine;

    @Deprecated
    private String connectionDriver;

    private Long creationDate;

    private String stackVersion;

    private String status;

    private String type;

    @Deprecated
    private String connectorJarUrl;

    @Deprecated
    private Long workspaceId;

    @Deprecated
    private String userId;

    @Deprecated
    private String userName;

    @Deprecated
    private String tenantName;

    private Boolean externalDatabase;

    public String getDatabaseEngine() {
        return databaseEngine;
    }

    public void setDatabaseEngine(String databaseEngine) {
        this.databaseEngine = databaseEngine;
    }

    public Long getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(Long creationDate) {
        this.creationDate = creationDate;
    }

    public String getStackVersion() {
        return stackVersion;
    }

    public void setStackVersion(String stackVersion) {
        this.stackVersion = stackVersion;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Boolean getExternal() {
        return externalDatabase;
    }

    public void setExternal(Boolean external) {
        externalDatabase = external;
    }

    public String getSslMode() {
        return sslMode;
    }

    public void setSslMode(String sslMode) {
        this.sslMode = sslMode;
    }
}
