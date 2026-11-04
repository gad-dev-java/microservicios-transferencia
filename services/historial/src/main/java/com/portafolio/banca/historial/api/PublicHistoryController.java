package com.portafolio.banca.historial.api;

import com.portafolio.banca.historial.application.HistoryService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/historial")
public class PublicHistoryController {
    private final HistoryService history;

    public PublicHistoryController(HistoryService history) {
        this.history = history;
    }

    @GetMapping
    public List<HistoryResponse> own(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Instant desde,
            @RequestParam(required = false) Instant hasta,
            @RequestParam(required = false) UUID cuentaId,
            @RequestParam(defaultValue = "20") int limite) {
        try {
            return history.search(jwt.getSubject(), desde, hasta, cuentaId, limite).stream()
                    .map(HistoryResponse::from)
                    .toList();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }
}
