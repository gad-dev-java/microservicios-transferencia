package com.portafolio.banca.historial.infrastructure.persistence;

import com.portafolio.banca.historial.application.HistoryStore;
import com.portafolio.banca.historial.domain.HistoryEntry;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

@Repository
public class MongoHistoryStore implements HistoryStore {
    private final MongoTemplate mongo;

    public MongoHistoryStore(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @PostConstruct
    void indexes() {
        mongo.indexOps(HistoryDocument.class)
                .ensureIndex(
                        new Index().on("transferenciaId", Sort.Direction.ASC).unique());
        mongo.indexOps(HistoryDocument.class)
                .ensureIndex(
                        new Index().on("titularSubject", Sort.Direction.ASC).on("ocurridaEn", Sort.Direction.DESC));
    }

    @Override
    public boolean add(HistoryEntry entry) {
        var document = HistoryDocument.from(entry);
        try {
            mongo.insert(document);
            return true;
        } catch (DuplicateKeyException duplicate) {
            HistoryDocument saved = mongo.findById(entry.eventId().toString(), HistoryDocument.class);
            if (saved != null && saved.eventHash().equals(entry.eventHash())) {
                return false;
            }
            throw new DuplicateKeyException("Evento o transferencia reutilizada con otro contenido", duplicate);
        }
    }

    @Override
    public List<HistoryEntry> search(String owner, Instant from, Instant to, UUID accountId, int limit) {
        Criteria criteria = Criteria.where("titularSubject").is(owner);
        if (from != null) {
            criteria = criteria.and("ocurridaEn").gte(from);
        }
        if (to != null) {
            criteria = criteria.and("ocurridaEn").lte(to);
        }
        if (accountId != null) {
            String account = accountId.toString();
            criteria = new Criteria()
                    .andOperator(
                            criteria,
                            new Criteria()
                                    .orOperator(
                                            Criteria.where("cuentaOrigenId").is(account),
                                            Criteria.where("cuentaDestinoId").is(account)));
        }
        Query query = new Query(criteria)
                .with(Sort.by(Sort.Direction.DESC, "ocurridaEn", "eventId"))
                .limit(limit);
        return mongo.find(query, HistoryDocument.class).stream()
                .map(HistoryDocument::toDomain)
                .toList();
    }

    @Document("transferencias_confirmadas")
    record HistoryDocument(
            @Id String eventId,
            String transferenciaId,
            String titularSubject,
            String cuentaOrigenId,
            String cuentaDestinoId,
            String importe,
            String moneda,
            Instant ocurridaEn,
            String eventHash) {
        static HistoryDocument from(HistoryEntry entry) {
            return new HistoryDocument(
                    entry.eventId().toString(),
                    entry.transferenciaId().toString(),
                    entry.titularSubject(),
                    entry.cuentaOrigenId().toString(),
                    entry.cuentaDestinoId().toString(),
                    entry.importe(),
                    entry.moneda(),
                    entry.ocurridaEn(),
                    entry.eventHash());
        }

        HistoryEntry toDomain() {
            return new HistoryEntry(
                    UUID.fromString(eventId),
                    UUID.fromString(transferenciaId),
                    titularSubject,
                    UUID.fromString(cuentaOrigenId),
                    UUID.fromString(cuentaDestinoId),
                    importe,
                    moneda,
                    ocurridaEn,
                    eventHash);
        }
    }
}
