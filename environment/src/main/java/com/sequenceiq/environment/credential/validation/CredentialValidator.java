package com.sequenceiq.environment.credential.validation;

import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.ws.rs.BadRequestException;

import org.springframework.stereotype.Component;

import com.sequenceiq.cloudbreak.cloud.model.Platform;
import com.sequenceiq.environment.credential.validation.definition.CredentialDefinitionService;

@Component
public class CredentialValidator {

    public static final Set<String> ENABLED_PLATFORMS = Set.of("AWS", "AZURE");

    @Inject
    private CredentialDefinitionService credentialDefinitionService;

    public void validateParameters(Platform platform, Map<String, Object> parameters) {
        credentialDefinitionService.checkProperties(platform, parameters);
    }

    public void validateCredentialCloudPlatform(String cloudPlatform) {
        if (!ENABLED_PLATFORMS.contains(cloudPlatform)) {
            throw new BadRequestException(String.format("There is no such cloud platform as '%s'", cloudPlatform));
        }
    }

}
