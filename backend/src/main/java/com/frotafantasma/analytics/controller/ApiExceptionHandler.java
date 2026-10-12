package com.frotafantasma.analytics.controller;

import com.frotafantasma.analytics.client.ClashRoyaleException;
import java.net.URI;
import java.time.Instant;
import org.springframework.http.ProblemDetail;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
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
