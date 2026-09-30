package com.rackpay.api.shared.adapters.in.web;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> validation(MethodArgumentNotValidException ex) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
            "One or more request fields are invalid.", fields);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiErrorResponse> methodValidation(HandlerMethodValidationException ex) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
            "One or more request parameters are invalid.", Map.of());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiErrorResponse> constraintViolation(ConstraintViolationException ex) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
            "One or more request parameters are invalid.", Map.of());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> malformedRequest(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
            "The request body could not be read.", Map.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiErrorResponse> authentication(AuthenticationException ex) {
        return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
            "Authentication is required.", Map.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiErrorResponse> accessDenied(AccessDeniedException ex) {
        return error(HttpStatus.FORBIDDEN, "FORBIDDEN",
            "You are not authorized to perform this operation.", Map.of());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiErrorResponse> illegalArgument(IllegalArgumentException ex) {
        String message = safeMessage(ex);
        String code = switch (message) {
            case "page must be zero or greater", "size must be between 1 and 100" ->
                "INVALID_PAGINATION";
            case "remittance not found" -> "REMITTANCE_NOT_FOUND";
            case "Destination country is not enabled" -> "REMITTANCE_COUNTRY_DISABLED";
            case "Destination country does not accept remittances" ->
                "REMITTANCE_COUNTRY_NOT_RECEIVABLE";
            case "countryCode is required" -> "REMITTANCE_COUNTRY_REQUIRED";
            case "Recipient does not belong to the authenticated user" -> "RECIPIENT_NOT_FOUND";
            case "No enabled remittance corridor exists for this route" ->
                "REMITTANCE_CORRIDOR_UNAVAILABLE";
            case "Amount is below the corridor minimum" -> "REMITTANCE_AMOUNT_BELOW_MINIMUM";
            case "Amount exceeds the corridor maximum" -> "REMITTANCE_AMOUNT_ABOVE_MAXIMUM";
            case "remittance quote not found" -> "REMITTANCE_QUOTE_NOT_FOUND";
            case "remittance quote has expired" -> "REMITTANCE_QUOTE_EXPIRED";
            case "remittance quote has already been used" -> "REMITTANCE_QUOTE_ALREADY_USED";
            case "wallet is not provisioned", "RackPay wallet is not provisioned" ->
                "WALLET_NOT_PROVISIONED";
            case "idempotency key was already used for a different remittance request" ->
                "IDEMPOTENCY_KEY_REUSED";
            case "remittance quote does not belong to the authenticated user" ->
                "REMITTANCE_QUOTE_NOT_FOUND";
            default -> "INVALID_REQUEST";
        };
        HttpStatus status = switch (code) {
            case "REMITTANCE_NOT_FOUND", "RECIPIENT_NOT_FOUND", "REMITTANCE_QUOTE_NOT_FOUND" ->
                HttpStatus.NOT_FOUND;
            case "IDEMPOTENCY_KEY_REUSED" -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
        return error(status, code, publicMessage(code, message), Map.of());
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ApiErrorResponse> illegalState(IllegalStateException ex) {
        String message = safeMessage(ex);
        String code = switch (message) {
            case "remittance quote has expired" -> "REMITTANCE_QUOTE_EXPIRED";
            case "remittance quote has already been used" -> "REMITTANCE_QUOTE_ALREADY_USED";
            case "wallet is not provisioned", "RackPay wallet is not provisioned" ->
                "WALLET_NOT_PROVISIONED";
            case "Corridor fee currency must match source currency" ->
                "REMITTANCE_CORRIDOR_CONFIGURATION_ERROR";
            case "FX provider returned an invalid rate" ->
                "FX_RATE_UNAVAILABLE";
            case "Calculated destination amount is invalid" ->
                "REMITTANCE_AMOUNT_INVALID";
            default -> "INVALID_STATE";
        };
        return error(
            code.equals("INVALID_STATE") ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST,
            code,
            publicMessage(code, message),
            Map.of()
        );
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> unexpected(Exception ex) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
            "An unexpected error occurred.", Map.of());
    }

    private ResponseEntity<ApiErrorResponse> error(
        HttpStatus status,
        String code,
        String message,
        Map<String, Object> details
    ) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
            code,
            message,
            MDC.get("correlationId"),
            details == null ? Map.of() : details
        ));
    }

    private static String safeMessage(Exception ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? "Request could not be processed." : message;
    }

    private static String publicMessage(String code, String fallback) {
        return switch (code) {
            case "INVALID_PAGINATION" -> fallback;
            case "REMITTANCE_NOT_FOUND" -> "The remittance could not be found.";
            case "REMITTANCE_COUNTRY_DISABLED" -> "The selected destination country is not enabled.";
            case "REMITTANCE_COUNTRY_NOT_RECEIVABLE" ->
                "The selected destination country does not accept remittances.";
            case "REMITTANCE_COUNTRY_REQUIRED" -> "A destination country is required.";
            case "RECIPIENT_NOT_FOUND", "REMITTANCE_QUOTE_NOT_FOUND" ->
                "The requested remittance resource could not be found.";
            case "REMITTANCE_CORRIDOR_UNAVAILABLE" ->
                "The selected remittance route is not currently available.";
            case "REMITTANCE_AMOUNT_BELOW_MINIMUM" ->
                "The amount is below the minimum allowed for this corridor.";
            case "REMITTANCE_AMOUNT_ABOVE_MAXIMUM" ->
                "The amount exceeds the maximum allowed for this corridor.";
            case "REMITTANCE_QUOTE_EXPIRED" -> "The remittance quote has expired.";
            case "REMITTANCE_QUOTE_ALREADY_USED" -> "The remittance quote has already been used.";
            case "WALLET_NOT_PROVISIONED" -> "The wallet is not provisioned.";
            case "IDEMPOTENCY_KEY_REUSED" ->
                "The idempotency key was already used for a different request.";
            case "REMITTANCE_CORRIDOR_CONFIGURATION_ERROR" ->
                "The remittance corridor configuration is invalid.";
            case "FX_RATE_UNAVAILABLE" ->
                "A valid exchange rate is not currently available.";
            case "REMITTANCE_AMOUNT_INVALID" ->
                "The remittance amount is invalid.";
            default -> fallback;
        };
    }
}
