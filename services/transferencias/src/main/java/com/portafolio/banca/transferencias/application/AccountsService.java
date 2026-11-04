package com.portafolio.banca.transferencias.application;

import com.portafolio.banca.transferencias.domain.AccountSnapshot;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AccountsService {
    private final AccountsPort accounts;

    public AccountsService(AccountsPort accounts) {
        this.accounts = accounts;
    }

    public List<AccountSnapshot> ownAccounts(String subject) {
        return accounts.byOwner(subject);
    }

    public AccountSnapshot ownAccount(String subject, UUID accountId) {
        AccountSnapshot account = accounts.byId(accountId).orElseThrow(ResourceNotFoundException::new);
        if (!subject.equals(account.ownerSubject())) {
            throw new ResourceNotFoundException();
        }
        return account;
    }
}
