package com.frotafantasma.analytics.controller;

import com.frotafantasma.analytics.client.ClashRoyaleException;
import java.net.URI;
import java.time.Instant;
import org.springframework.http.ProblemDetail;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> handleStatus(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode())
                .body(ProblemDetail.forStatusAndDetail(exception.getStatusCode(), exception.getReason()));
    }

    @ExceptionHandler({HandlerMethodValidationException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ProblemDetail> invalidParameter(Exception exception) {
        return ResponseEntity.badRequest().body(ProblemDetail.forStatusAndDetail(
                org.springframework.http.HttpStatus.BAD_REQUEST, "Parâmetros inválidos: confira tag, identificador e paginação."));
    }

    @ExceptionHandler(ClashRoyaleException.class)
    public ResponseEntity<ProblemDetail> handleClashRoyaleException(ClashRoyaleException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage());
        problem.setType(URI.create("about:blank"));
        problem.setTitle("Falha ao consultar o clã");
        problem.setProperty("code", exception.code());
        problem.setProperty("timestamp", Instant.now());
        var response = ResponseEntity.status(exception.status());
        if (exception.retryAfterSeconds() != null) {
            response.header(HttpHeaders.RETRY_AFTER, Long.toString(exception.retryAfterSeconds()));
        }
        return response.body(problem);
    }
}
