package com.portafolio.banca.contabilidad;

import java.util.UUID;
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
}
