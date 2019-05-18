package com.sequenceiq.environment.proxy;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import org.hibernate.annotations.Where;

import com.sequenceiq.cloudbreak.auth.security.AuthResource;
import com.sequenceiq.environment.environment.domain.EnvironmentAwareResource;
import com.sequenceiq.environment.environment.domain.EnvironmentView;
import com.sequenceiq.secret.SecretValue;
import com.sequenceiq.secret.domain.Secret;
import com.sequenceiq.secret.domain.SecretToString;

@Entity
@Where(clause = "archived = false")
@Table
public class ProxyConfig implements Serializable, EnvironmentAwareResource, AuthResource {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "proxyconfig_generator")
    @SequenceGenerator(name = "proxyconfig_generator", sequenceName = "proxyconfig_id_seq", allocationSize = 1)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000000, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String serverHost;

    @Column(nullable = false)
    private Integer serverPort;

    @Column(nullable = false)
    private String protocol;

    @Convert(converter = SecretToString.class)
    @SecretValue
    private Secret userName = Secret.EMPTY;

    @Convert(converter = SecretToString.class)
    @SecretValue
    private Secret password = Secret.EMPTY;

    @Column
    private String accountId;

    @ManyToMany(cascade = CascadeType.MERGE, fetch = FetchType.EAGER)
    @JoinTable(name = "env_proxy", joinColumns = @JoinColumn(name = "proxyid"), inverseJoinColumns = @JoinColumn(name = "envid"))
    private Set<EnvironmentView> environments = new HashSet<>();

    private boolean archived;

    private Long deletionTimestamp = -1L;

    @Override
    public String getAccountId() {
        return accountId;
    }

    @Override
    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getServerHost() {
        return serverHost;
    }

    public void setServerHost(String serverHost) {
        this.serverHost = serverHost;
    }

    public Integer getServerPort() {
        return serverPort;
    }

    public void setServerPort(Integer serverPort) {
        this.serverPort = serverPort;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getUserName() {
        return userName.getRaw();
    }

    public String getUserNameSecret() {
        return userName.getSecret();
    }

    public void setUserName(String userName) {
        this.userName = new Secret(userName);
    }

    public String getPassword() {
        return password.getRaw();
    }

    public String getPasswordSecret() {
        return password.getSecret();
    }

    public void setPassword(String password) {
        this.password = new Secret(password);
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public Set<EnvironmentView> getEnvironments() {
        return environments;
    }

    public void setEnvironments(Set<EnvironmentView> environments) {
        this.environments = environments;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ProxyConfig that = (ProxyConfig) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public boolean isArchived() {
        return archived;
    }

    public Long getDeletionTimestamp() {
        return deletionTimestamp;
    }
}
