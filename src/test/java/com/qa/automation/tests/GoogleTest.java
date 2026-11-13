package com.qa.automation.tests;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GoogleTest extends BaseTest {
    @Test
    void deveAbrirGoogle() {
        driver.get("https://www.google.com");
        assertEquals("Google", driver.getTitle());
    }
}
