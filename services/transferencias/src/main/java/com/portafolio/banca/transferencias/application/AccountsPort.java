package com.portafolio.banca.transferencias.application;

import com.portafolio.banca.transferencias.domain.AccountSnapshot;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountsPort {
    List<AccountSnapshot> byOwner(String ownerSubject);

    Optional<AccountSnapshot> byId(UUID accountId);
}
