package com.portafolio.banca.contabilidad.api;

import com.portafolio.banca.contabilidad.application.AccountingService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1")
public class InternalAccountingController {
    private final AccountingService accounting;

    public InternalAccountingController(AccountingService accounting) {
        this.accounting = accounting;
    }

    @GetMapping("/cuentas/{accountId}")
    public AccountResponse account(@PathVariable UUID accountId) {
        var account = accounting.account(accountId);
        return AccountResponse.from(account);
    }

    @GetMapping("/cuentas")
    public List<AccountResponse> accounts(@RequestParam String ownerSubject) {
        return accounting.accounts(ownerSubject).stream()
                .map(AccountResponse::from)
                .toList();
    }

    @PostMapping("/movimientos/transferencia")
    public TransferResponse transfer(@RequestBody TransferRequest request) {
        var instruction = request.toInstruction();
        var result = accounting.transfer(instruction);
        return TransferResponse.from(result);
    }

    @GetMapping("/movimientos/transferencia/{transferId}")
    public TransferResponse result(@PathVariable UUID transferId) {
        var result = accounting.result(transferId);
        return TransferResponse.from(result);
    }
}
