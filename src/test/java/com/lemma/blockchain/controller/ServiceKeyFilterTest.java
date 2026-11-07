package com.lemma.blockchain.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceKeyFilterTest {

    private final ServiceKeyFilter filter = new ServiceKeyFilter("test-service-key");

    @Test
    void acceptsCorrectKey() {
        assertTrue(filter.isValid("test-service-key"));
    }

    @Test
    void rejectsWrongKey() {
        assertFalse(filter.isValid("test-service-kez"));
        assertFalse(filter.isValid("test-service-key-longer"));
        assertFalse(filter.isValid("test"));
    }

    @Test
    void rejectsMissingOrEmptyKey() {
        assertFalse(filter.isValid(null));
        assertFalse(filter.isValid(""));
    }
}
