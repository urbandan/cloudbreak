package com.sequenceiq.authorization.service;

import java.util.List;

import org.apache.commons.lang3.NotImplementedException;

import com.sequenceiq.authorization.resource.AuthorizationResourceType;

public interface ResourceBasedCrnProvider {

    default String getResourceCrnByResourceName(String resourceName) {
        throw new NotImplementedException("Logic for getting resource CRN by resource name should have been implemented for authorization!");
    }

    default List<String> getResourceCrnListByResourceNameList(List<String> resourceNames) {
        throw new NotImplementedException("Logic for getting resource CRN list by resource name list should have been implemented for authorization!");
    }

    AuthorizationResourceType getResourceType();
}
