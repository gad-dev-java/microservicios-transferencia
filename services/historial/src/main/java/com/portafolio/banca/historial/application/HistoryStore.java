package com.portafolio.banca.historial.application;

import com.portafolio.banca.historial.domain.HistoryEntry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface HistoryStore {
    boolean add(HistoryEntry entry);

    List<HistoryEntry> search(String owner, Instant from, Instant to, UUID accountId, int limit);
}
