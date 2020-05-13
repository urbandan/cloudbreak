package com.sequenceiq.environment.environment.experience.common;

import java.util.stream.Stream;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;

public class InvalidCommonExperienceArgumentProvider implements ArgumentsProvider {

    private static final String XP_PORT = "somePortValue";

    private static final String XP_PREFIX = "somePrefixValue";

    private static final String XP_INFIX = "someInfixValue";

    private static final String SOURCE = "DEFAULT";

    private static final String VALUE_NOT_SET = "${somexp}";

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) throws Exception {
        return Stream.of(
                Arguments.of(new CommonExperience()),
                Arguments.of(new CommonExperience("", "", "", "", SOURCE)),
                Arguments.of(new CommonExperience("", XP_PREFIX, XP_INFIX, "", SOURCE)),
                Arguments.of(new CommonExperience("", XP_PREFIX, XP_INFIX, null, SOURCE)),
                Arguments.of(new CommonExperience("", XP_PREFIX, "", XP_PORT, SOURCE)),
                Arguments.of(new CommonExperience("", XP_PREFIX, null, XP_PORT, SOURCE)),
                Arguments.of(new CommonExperience("", "", XP_INFIX, XP_PORT, SOURCE)),
                Arguments.of(new CommonExperience("", null, XP_INFIX, XP_PORT, SOURCE)),
                Arguments.of(new CommonExperience("", null, "", XP_PORT, SOURCE)),
                Arguments.of(new CommonExperience("", null, null, XP_PORT, SOURCE)),
                Arguments.of(new CommonExperience("", "", "", XP_PORT, SOURCE)),
                Arguments.of(new CommonExperience("", "", null, XP_PORT, SOURCE)),
                Arguments.of(new CommonExperience("", null, "", XP_PORT, SOURCE)),
                Arguments.of(new CommonExperience("", null, XP_INFIX, null, SOURCE)),
                Arguments.of(new CommonExperience("", null, XP_INFIX, "", SOURCE)),
                Arguments.of(new CommonExperience("", XP_PREFIX, null, "", SOURCE)),
                Arguments.of(new CommonExperience("", XP_PREFIX, "", null, SOURCE)),
                Arguments.of(new CommonExperience("", "", XP_INFIX, null, SOURCE)),
                Arguments.of(new CommonExperience("", null, XP_INFIX, null, SOURCE)),
                Arguments.of(new CommonExperience("", "", XP_INFIX, "", SOURCE)),
                Arguments.of(new CommonExperience("", null, XP_INFIX, "", SOURCE)),
                Arguments.of(new CommonExperience("", XP_PREFIX, XP_INFIX, VALUE_NOT_SET, SOURCE)),
                Arguments.of(new CommonExperience("", XP_PREFIX, VALUE_NOT_SET, XP_PORT, SOURCE)),
                Arguments.of(new CommonExperience("", VALUE_NOT_SET, XP_INFIX, VALUE_NOT_SET, SOURCE)),
                Arguments.of(new CommonExperience("", XP_PREFIX, VALUE_NOT_SET, VALUE_NOT_SET, SOURCE)),
                Arguments.of(new CommonExperience("", VALUE_NOT_SET, XP_INFIX, VALUE_NOT_SET, SOURCE)),
                Arguments.of(new CommonExperience("", VALUE_NOT_SET, VALUE_NOT_SET, XP_PORT, SOURCE))
        );
    }

}
