package com.vcs.customvcs.exception;

import java.util.LinkedHashMap;
import java.util.Map;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import com.vcs.core.RepositoryStateException;

// Keep API error formatting consistent across controllers.
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    // The base advice also formats standard MVC errors such as 405 and 415.
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {

        Map<String, String> errors = new LinkedHashMap<>();

        // Return one validation message per field without exposing rejected values.
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "One or more request fields are invalid."
        );

        problem.setTitle("Validation failed");
        // Expose field errors in the response so forms can show messages beside inputs.
        problem.setProperty("errors", errors);

        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    // Body decoding fails before field validation; keep parser details out of the response.
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Request body is missing or contains invalid JSON."
        );

        problem.setTitle("Invalid request body");
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    // UUID and pagination conversion failures are client errors, not server failures.
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status,
                "A request parameter has an invalid format.");
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    // A dirty tree or unresolved merge is a normal VCS condition, not a broken server.
    @ExceptionHandler(RepositoryStateException.class)
    public ProblemDetail handleRepositoryState(RepositoryStateException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    // Log the storage exception and stack trace; return a generic message to clients.
    @ExceptionHandler(IOException.class)
    public ProblemDetail handleStorageFailure(IOException exception) {
        log.error("Repository storage operation failed", exception);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "A repository storage operation failed."
        );

        problem.setTitle("Storage operation failed");
        return problem;
    }

    // Database and unexpected failures get diagnostics in logs, never raw SQL in the response.
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpectedFailure(Exception exception) {
        log.error("Unexpected API operation failure", exception);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "The operation could not be completed.");
    }
}
