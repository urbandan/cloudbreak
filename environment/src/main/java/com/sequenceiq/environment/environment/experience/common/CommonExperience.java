package com.sequenceiq.environment.environment.experience.common;

public class CommonExperience {

    private String name;

    private String pathPrefix;

    private String pathInfix;

    private String port;

    public CommonExperience(String name, String pathPrefix, String pathInfix, String port) {
        this.name = name;
        this.pathPrefix = pathPrefix;
        this.pathInfix = pathInfix;
        this.port = port;
    }

    public CommonExperience() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPathPrefix() {
        return pathPrefix;
    }

    public void setPathPrefix(String pathPrefix) {
        this.pathPrefix = pathPrefix;
    }

    public String getPathInfix() {
        return pathInfix;
    }

    public void setPathInfix(String pathInfix) {
        this.pathInfix = pathInfix;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

}
