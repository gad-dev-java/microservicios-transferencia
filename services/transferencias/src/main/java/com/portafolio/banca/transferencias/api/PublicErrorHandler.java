package com.portafolio.banca.transferencias.api;

import com.portafolio.banca.transferencias.application.IdempotencyConflictException;
import com.portafolio.banca.transferencias.application.InvalidTransferException;
import com.portafolio.banca.transferencias.application.ResourceNotFoundException;
import io.micrometer.tracing.Tracer;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class PublicErrorHandler {
    private final ObjectProvider<Tracer> tracers;

    public PublicErrorHandler(ObjectProvider<Tracer> tracers) {
        this.tracers = tracers;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<PublicError> notFound(ResourceNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new PublicError("ACCOUNT_NOT_FOUND", "Recurso no encontrado", traceId()));
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<PublicError> conflict(IdempotencyConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new PublicError("IDEMPOTENCY_CONFLICT", "Clave reutilizada con otra solicitud", traceId()));
    }

    @ExceptionHandler(InvalidTransferException.class)
    public ResponseEntity<PublicError> invalid(InvalidTransferException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new PublicError("INVALID_REQUEST", exception.getMessage(), traceId()));
    }

    private String traceId() {
        Tracer tracer = tracers.getIfAvailable();
        if (tracer != null && tracer.currentSpan() != null) {
            return tracer.currentSpan().context().traceId();
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    public record PublicError(String codigo, String mensaje, String traceId) {}
}
