package org.testcontainers.example;

import org.junit.jupiter.api.Test;

class RedisContainer3Test extends AbstractRedisContainer {

    @Test
    void testSimple() {
        imageIsNotAvailableBeforeToRun();
    }
}
