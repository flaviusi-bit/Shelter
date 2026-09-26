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
}
