package com.frotafantasma.analytics.api;

import com.frotafantasma.analytics.clash.ClashRoyaleException;
import java.net.URI;
import java.time.Instant;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ClashRoyaleException.class)
    public ProblemDetail handleClashRoyaleException(ClashRoyaleException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage());
        problem.setType(URI.create("about:blank"));
        problem.setTitle("Falha ao consultar o clã");
        problem.setProperty("code", exception.code());
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}

