package com.portafolio.banca.transferencias.api;

import com.portafolio.banca.transferencias.application.AccountsService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cuentas")
public class PublicAccountsController {
    private final AccountsService accounts;

    public PublicAccountsController(AccountsService accounts) {
        this.accounts = accounts;
    }

    @GetMapping
    public List<CuentaResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return accounts.ownAccounts(jwt.getSubject()).stream()
                .map(CuentaResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public CuentaResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        var account = accounts.ownAccount(jwt.getSubject(), id);
        return CuentaResponse.from(account);
    }
}
