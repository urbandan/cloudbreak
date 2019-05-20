package com.sequenceiq.cloudbreak.auth.security;

public interface AuthResource {

    String getAccountId();

    void setAccountId(String accountId);

    String getResourceCRN();

    void setResourceCRN(String resourceCRN);
}
