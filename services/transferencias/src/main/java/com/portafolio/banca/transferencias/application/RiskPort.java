package com.portafolio.banca.transferencias.application;

import com.portafolio.banca.transferencias.domain.Transfer;

public interface RiskPort {
    RiskDecision evaluate(Transfer transfer);

    record RiskDecision(String decision, String reasonCode, String ruleVersion) {}
}
