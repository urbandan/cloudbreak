package com.sequenceiq.redbeams;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.sequenceiq.authorization.EnforceAuthorizationLogicsUtil;

public class EnforceAuthorizationAnnotationsTest {

    @Test
    public void testIfControllerClassHasProperAnnotation() {
        EnforceAuthorizationLogicsUtil.testIfControllerClassHasProperAnnotation();
    }

    @Test
    public void testIfControllerMethodsHaveProperAuthorizationAnnotation() {
        EnforceAuthorizationLogicsUtil.testIfControllerMethodsHaveProperAuthorizationAnnotation(Set.of("ExampleAuthorizationResourceClass"));
    }
}
