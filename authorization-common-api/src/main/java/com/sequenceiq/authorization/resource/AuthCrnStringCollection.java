package com.sequenceiq.authorization.resource;

import java.util.Collection;

public class AuthCrnStringCollection {

    private Collection<String> value;

    private AuthorizationVariableType type = AuthorizationVariableType.CRN_LIST;

    private AuthorizationResourceAction action;

    public AuthCrnStringCollection(Collection<String> value, AuthorizationResourceAction action) {
        this.value = value;
        this.action = action;
    }

    public Collection<String> getValue() {
        return value;
    }

    public void setValue(Collection<String> value) {
        this.value = value;
    }

    public AuthorizationVariableType getType() {
        return type;
    }

    public void setType(AuthorizationVariableType type) {
        this.type = type;
    }

    public AuthorizationResourceAction getAction() {
        return action;
    }

    public void setAction(AuthorizationResourceAction action) {
        this.action = action;
    }
}
