package com.portafolio.banca.transferencias.api;

import com.portafolio.banca.transferencias.application.InvalidTransferException;
import com.portafolio.banca.transferencias.application.TransferService;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transferencias")
public class PublicTransferController {
    private final TransferService service;

    public PublicTransferController(TransferService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") UUID key,
            @RequestBody CreateRequest request) {
        var creation = service.create(
                jwt.getSubject(),
                key,
                request.cuentaOrigenId(),
                request.cuentaDestinoId(),
                request.importe(),
                request.moneda());
        var transfer = creation.transfer();
        HttpStatus status = !creation.created()
                ? HttpStatus.OK
                : "PENDIENTE_CONCILIACION".equals(transfer.state()) ? HttpStatus.ACCEPTED : HttpStatus.CREATED;
        return ResponseEntity.status(status)
                .location(URI.create("/api/v1/transferencias/" + transfer.id()))
                .body(TransferResponse.from(transfer));
    }

    @GetMapping("/{id}")
    public TransferResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return TransferResponse.from(service.own(jwt.getSubject(), id));
    }

    @GetMapping
    public PageResponse list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String cursor) {
        if (limit < 1 || limit > 100) {
            throw new InvalidTransferException("limit debe estar entre 1 y 100");
        }
        Instant beforeTime = null;
        UUID beforeId = null;
        if (cursor != null) {
            try {
                String[] parts =
                        new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|", 2);
                beforeTime = Instant.parse(parts[0]);
                beforeId = UUID.fromString(parts[1]);
            } catch (RuntimeException exception) {
                throw new InvalidTransferException("Cursor inválido");
            }
        }
        List<com.portafolio.banca.transferencias.domain.Transfer> rows =
                service.page(jwt.getSubject(), beforeTime, beforeId, limit + 1);
        boolean more = rows.size() > limit;
        List<com.portafolio.banca.transferencias.domain.Transfer> selected = more ? rows.subList(0, limit) : rows;
        String next = null;
        if (more) {
            var last = selected.getLast();
            next = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString((last.createdAt() + "|" + last.id()).getBytes(StandardCharsets.UTF_8));
        }
        return new PageResponse(selected.stream().map(TransferResponse::from).toList(), next);
    }

    public record CreateRequest(UUID cuentaOrigenId, UUID cuentaDestinoId, String importe, String moneda) {}

    public record PageResponse(List<TransferResponse> items, String siguienteCursor) {}
}
