package com.sequenceiq.cloudbreak.orchestrator.model;

import java.util.Map;

public class Node {
    private final String privateIp;

    private final String publicIp;

    private final String instanceId;

    private final String instanceType;

    private String hostname;

    private String domain;

    private String hostGroup;

    private String dataVolumes;

    private String serialIds;

    private String fstab;

    private String uuids;

    // Used for generic attributes associated with the node. e.g. YARN attributes when running NMs, Spot vs non-spot, etc
    private Map<String, Map<String, String>> attributes;

    public Node(String privateIp, String publicIp, String instanceId, String instanceType, String fqdn, String hostGroup) {
        this(privateIp, publicIp, instanceId, instanceType, fqdn, null, hostGroup);
    }

    public Node(String privateIp, String publicIp, String instanceId, String instanceType, String fqdn,
            String hostGroup, Map<String, Map<String, String>> attributes) {
        this(privateIp, publicIp, instanceId, instanceType, fqdn, null, hostGroup, attributes);
    }

    public Node(String privateIp, String publicIp, String instanceId, String instanceType, String fqdn, String hostGroup, String dataVolumes,
            String serialIds, String fstab, String uuids) {
        this(privateIp, publicIp, instanceId, instanceType, fqdn, hostGroup);
        this.dataVolumes = dataVolumes;
        this.serialIds = serialIds;
        this.fstab = fstab;
        this.uuids = uuids;
    }

    public Node(String privateIp, String publicIp, String instanceId, String instanceType, String fqdn, String domain, String hostGroup,
            Map<String, Map<String, String>> attributes) {
        this(privateIp, publicIp, instanceId, instanceType, fqdn, domain, hostGroup);
        this.attributes = attributes;
    }

    public Node(String privateIp, String publicIp, String instanceId, String instanceType, String fqdn, String domain, String hostGroup) {
        this(privateIp, publicIp, instanceId, instanceType);
        hostname = fqdn;
        this.hostGroup = hostGroup;
        this.domain = domain;
    }

    public Node(String privateIp, String publicIp, String instanceId, String instanceType) {
        this.privateIp = privateIp;
        this.publicIp = publicIp;
        this.instanceId = instanceId;
        this.instanceType = instanceType;
    }

    public String getPrivateIp() {
        return privateIp;
    }

    public String getPublicIp() {
        return publicIp;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getDataVolumes() {
        return dataVolumes;
    }

    public String getSerialIds() {
        return serialIds;
    }

    public String getHostGroup() {
        return hostGroup;
    }

    public String getDomain() {
        return domain;
    }

    public String getFstab() {
        return fstab;
    }

    public String getUuids() {
        return uuids;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public String getInstanceType() {
        return instanceType;
    }

    public Map<String, Map<String, String>> getAttributes() {
        return attributes;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Node{");
        sb.append("privateIp='").append(privateIp).append('\'');
        sb.append(", publicIp='").append(publicIp).append('\'');
        sb.append(", hostname='").append(hostname).append('\'');
        sb.append(", domain='").append(domain).append('\'');
        sb.append(", hostGroup='").append(hostGroup).append('\'');
        sb.append(", dataVolumes='").append(dataVolumes).append('\'');
        sb.append(", serialIds='").append(serialIds).append('\'');
        sb.append(", fstab='").append(fstab).append('\'');
        sb.append(", uuids='").append(uuids).append('\'');
        sb.append(", instanceId='").append(instanceId).append('\'');
        sb.append(", instanceType='").append(instanceType).append('\'');
        sb.append(", attributes='").append(attributes).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
