package com.shelter.api.error;

import org.junit.jupiter.api.Test;
import java.util.NoSuchElementException;
import static org.junit.jupiter.api.Assertions.*;

class ApiExceptionHandlerTest {
    @Test
    void mapsMissingResourceToNotFound() {
        var response = new ApiExceptionHandler().handleNotFound(new NoSuchElementException());
        assertEquals(404, response.status());
        assertEquals("Resource not found", response.message());
    }

    @Test
    void mapsInvalidInputToBadRequest() {
        var response = new ApiExceptionHandler().handleBadRequest(new IllegalArgumentException("Invalid role"));
        assertEquals(400, response.status());
        assertEquals("Invalid role", response.message());
    }

    @Test
    void mapsInvalidStateToConflict() {
        var response = new ApiExceptionHandler().handleConflict(new IllegalStateException("Only open tasks can be completed"));
        assertEquals(409, response.status());
        assertEquals("Only open tasks can be completed", response.message());
    }
}
