package com.portafolio.banca.contabilidad;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AccountingErrorHandler {
    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail notFound(NotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "NOT_FOUND", "Recurso inexistente");
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ProblemDetail conflict(IdempotencyConflictException exception) {
        return problem(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "El ID ya pertenece a otro comando");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail invalid(IllegalArgumentException exception) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_TRANSFER_COMMAND", "Comando de transferencia inválido");
    }

    private ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail result = ProblemDetail.forStatusAndDetail(status, detail);
        result.setProperty("code", code);
        return result;
    }
}
