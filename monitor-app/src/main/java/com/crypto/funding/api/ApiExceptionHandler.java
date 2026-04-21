package com.crypto.funding.api;

import com.crypto.funding.api.dto.ApiErrorResponse;
import com.crypto.funding.application.DomainValidationException;
import com.crypto.funding.application.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler
{
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound( ResourceNotFoundException ex, HttpServletRequest request )
    {
        return build( HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI() );
    }

    @ExceptionHandler(DomainValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict( RuntimeException ex, HttpServletRequest request )
    {
        return build( HttpStatus.CONFLICT, ex.getMessage(), request.getRequestURI() );
    }

    @ExceptionHandler({ IllegalArgumentException.class, IllegalStateException.class })
    public ResponseEntity<ApiErrorResponse> handleBadRequest( RuntimeException ex, HttpServletRequest request )
    {
        return build( HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI() );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
        MethodArgumentNotValidException ex,
        HttpServletRequest request
    )
    {
        String message = ex.getBindingResult()
                           .getFieldErrors()
                           .stream()
                           .map( this::formatFieldError )
                           .collect( Collectors.joining( "; " ) );
        return build( HttpStatus.BAD_REQUEST, message, request.getRequestURI() );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResource(
        NoResourceFoundException ex,
        HttpServletRequest request
    )
    {
        return build( HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI() );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected( Exception ex, HttpServletRequest request )
    {
        return build( HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), request.getRequestURI() );
    }

    private String formatFieldError( FieldError error )
    {
        return error.getField() + ": " + error.getDefaultMessage();
    }

    private ResponseEntity<ApiErrorResponse> build( HttpStatus status, String message, String path )
    {
        return ResponseEntity.status( status )
                             .body( new ApiErrorResponse(
                                 Instant.now(),
                                 status.value(),
                                 status.getReasonPhrase(),
                                 message,
                                 path
                             ) );
    }
}
