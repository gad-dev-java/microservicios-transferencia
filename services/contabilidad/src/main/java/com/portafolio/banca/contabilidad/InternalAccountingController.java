package com.portafolio.banca.contabilidad;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1")
public class InternalAccountingController {
    private final AccountingService accounting;

    public InternalAccountingController(AccountingService accounting) {
        this.accounting = accounting;
    }

    @GetMapping("/cuentas/{accountId}")
    public AccountView account(@PathVariable UUID accountId) {
        return accounting.account(accountId);
    }

    @PostMapping("/movimientos/transferencia")
    public TransferResult transfer(@RequestBody TransferCommand command) {
        return accounting.transfer(command);
    }

    @GetMapping("/movimientos/transferencia/{transferId}")
    public TransferResult result(@PathVariable UUID transferId) {
        return accounting.result(transferId);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(NotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("code", "NOT_FOUND"));
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<Map<String, String>> conflict(IdempotencyConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("code", "IDEMPOTENCY_CONFLICT"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalid(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("code", "INVALID_TRANSFER_COMMAND"));
    }
}
