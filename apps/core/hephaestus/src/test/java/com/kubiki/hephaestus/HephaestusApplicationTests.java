package com.kubiki.hephaestus;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class HephaestusApplicationTests {

    @Test
    void contextLoads() {
        // Verifies that the Spring context bootstrap succeeds under the test profile
    }

}
