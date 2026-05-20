package com.kubiki.themis;

import com.kubiki.themis.config.ThemisProperties;
import com.kubiki.themis.model.ExecutionStatus;
import com.kubiki.themis.model.Protocol;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Simple Components Tests")
class SimpleComponentsTest {

    @ParameterizedTest
    @EnumSource(Protocol.class)
    @DisplayName("Protocol enum values should be valid")
    void testProtocolEnum(Protocol protocol) {
        assertAll(
                () -> assertTrue(Protocol.values().length > 0),
                () -> assertEquals(protocol, Protocol.valueOf(protocol.name()))
        );
    }

    @ParameterizedTest
    @EnumSource(ExecutionStatus.class)
    @DisplayName("ExecutionStatus enum values should be valid")
    void testExecutionStatusEnum(ExecutionStatus status) {
        assertAll(
                () -> assertTrue(ExecutionStatus.values().length > 0),
                () -> assertEquals(status, ExecutionStatus.valueOf(status.name()))
        );
    }


    @Test
    @DisplayName("ThemisProperties and its nested records should work correctly")
    void testThemisProperties() {
        ThemisProperties.Secret secret = new ThemisProperties.Secret("token");
        ThemisProperties props = new ThemisProperties(secret);
        assertAll(
                () -> assertEquals("token", props.secret().bearerToken()),
                () -> assertNotNull(props.toString()),
                () -> assertEquals(props, new ThemisProperties(new ThemisProperties.Secret("token")))
        );
    }
}
