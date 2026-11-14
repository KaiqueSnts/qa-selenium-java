package com.qa.automation.tests;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GoogleTest extends BaseTest {
    @Test
    @Tag("regression")
    void deveAbrirGoogle() {
        driver.get("https://www.google.com");
        assertEquals("Google", driver.getTitle());
    }
}
