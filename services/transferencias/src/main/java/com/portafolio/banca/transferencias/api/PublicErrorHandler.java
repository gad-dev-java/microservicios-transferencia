package com.portafolio.banca.transferencias.api;

import com.portafolio.banca.transferencias.application.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class PublicErrorHandler {
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<PublicError> notFound(ResourceNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new PublicError("ACCOUNT_NOT_FOUND", "Recurso no encontrado", traceId()));
    }

    private String traceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public record PublicError(String codigo, String mensaje, String traceId) {}
}
