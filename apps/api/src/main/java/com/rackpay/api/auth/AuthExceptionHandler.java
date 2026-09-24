package com.rackpay.api.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestControllerAdvice
public class AuthExceptionHandler {
    @ExceptionHandler(RegistrationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleRegistration(RegistrationException ex) {
        return Map.of("error", "registration_conflict", "message", ex.getMessage());
    }
}
