package com.sequenceiq.authorization.resource;

public class AuthCrnString {

    private String value;

    private AuthorizationVariableType type = AuthorizationVariableType.CRN;

    private AuthorizationResourceAction action;

    public AuthCrnString(String value, AuthorizationResourceAction action) {
        this.value = value;
        this.action = action;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
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
