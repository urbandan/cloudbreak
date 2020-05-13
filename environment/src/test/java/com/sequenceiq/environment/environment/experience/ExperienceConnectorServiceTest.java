package com.sequenceiq.environment.environment.experience;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.sequenceiq.environment.environment.dto.EnvironmentExperienceDto;

class ExperienceConnectorServiceTest {

    private static final boolean SCAN_ENABLED = true;

    private static final String TENANT = "someTenantValue";

    @Mock
    private Experience mockExperience;

    @Mock
    private EnvironmentExperienceDto mockDto;

    private ExperienceConnectorService underTest;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        underTest = new ExperienceConnectorService(SCAN_ENABLED, List.of(mockExperience));
    }

    @Test
    void testWhenScanIsNotEnabledThenNoExperienceCallHappensAndZeroShouldReturn() {
        ExperienceConnectorService underTest = new ExperienceConnectorService(false, List.of(mockExperience));
        long result = underTest.getConnectedExperienceQuantity(mockDto);

        Assert.assertEquals(0L, result);
        verify(mockExperience, never()).hasExistingClusterForEnvironment(any(EnvironmentExperienceDto.class));
    }

    @Test
    void testWhenNoExperienceHasConfiguredThenZeroShouldReturn() {
        ExperienceConnectorService underTest = new ExperienceConnectorService(false, Collections.emptyList());
        long result = underTest.getConnectedExperienceQuantity(mockDto);

        Assert.assertEquals(0L, result);
    }

}