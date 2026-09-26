package com.shelter.api.error;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(NoSuchElementException.class)
    public ErrorResponse handleNotFound(NoSuchElementException ex) {
        return new ErrorResponse(HttpStatus.NOT_FOUND.value(), "Resource not found");
    }

    public record ErrorResponse(int status, String message) {}
}
