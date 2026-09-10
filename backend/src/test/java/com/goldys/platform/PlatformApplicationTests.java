package com.goldys.platform;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Context-load smoke test. Requires a reachable Postgres (see
 * docker-compose.yml at repo root) - this is intentionally the only test in
 * the scaffold; real tests land with the raw log / canonical layer / role
 * model implementations, not before.
 */
@SpringBootTest
class PlatformApplicationTests {

    @Test
    void contextLoads() {
    }
}
