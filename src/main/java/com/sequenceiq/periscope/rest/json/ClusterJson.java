package com.sequenceiq.periscope.rest.json;

public class ClusterJson implements Json {

    private String id;
    private String host;
    private String port;
    private String state;

    public ClusterJson() {
    }

    public ClusterJson(String id, String host, String port, String state) {
        this.id = id;
        this.host = host;
        this.port = port;
        this.state = state;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public static ClusterJson emptyJson() {
        return new ClusterJson("", "", "", "");
    }
}
