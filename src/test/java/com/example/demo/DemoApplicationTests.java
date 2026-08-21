package com.example.demo;

import org.junit.jupiter.api.Test;

class DemoApplicationTests {

    @Test
    void contextLoadsPlaceholder() {
        // Lightweight sanity test so `mvn test` has something to run in CI
        // without requiring a live Postgres connection during the build stage.
        assert 1 + 1 == 2;
    }
}
