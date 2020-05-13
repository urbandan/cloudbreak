package com.sequenceiq.environment.environment.experience.common;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class CommonExperienceValidatorTest {

    private static final String XP_PORT = "somePortValue";

    private static final String XP_PREFIX = "somePrefixValue";

    private static final String XP_INFIX = "someInfixValue";

    @Mock
    private CommonExperience commonExperience;

    private CommonExperienceValidator underTest;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        when(commonExperience.getPort()).thenReturn(XP_PORT);
        when(commonExperience.getPathInfix()).thenReturn(XP_INFIX);
        when(commonExperience.getPathPrefix()).thenReturn(XP_PREFIX);

        underTest = new CommonExperienceValidator();
    }

    @ParameterizedTest
    @ArgumentsSource(InvalidCommonExperienceArgumentProvider.class)
    void testIsExperienceFilledWhenTheInputFieldIsInvalidThenFalseReturns(CommonExperience testData) {
        boolean result = underTest.isExperienceFilled(testData);

        assertFalse(result);
    }

    @Test
    void testIsExperienceFilledWhenAllTheFieldsAreValidThenTrueReturns() {
        boolean result = underTest.isExperienceFilled(new CommonExperience("awesomeXP", XP_PREFIX, XP_INFIX, XP_PORT, "DEFAULT"));

        assertTrue(result);
    }

}