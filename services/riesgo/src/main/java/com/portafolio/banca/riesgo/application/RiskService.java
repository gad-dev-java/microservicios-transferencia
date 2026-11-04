package com.portafolio.banca.riesgo.application;

import com.portafolio.banca.riesgo.api.EvaluationRequest;
import com.portafolio.banca.riesgo.api.EvaluationResponse;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RiskService {
    private final JdbcTemplate jdbc;

    public RiskService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public EvaluationResponse evaluate(EvaluationRequest request) {
        validate(request);
        String hash = fingerprint(request);
        BigDecimal amount = new BigDecimal(request.amount());
        boolean approved = amount.compareTo(new BigDecimal("3000.00")) <= 0;
        String decision = approved ? "APROBADA" : "RECHAZADA";
        String reason = approved ? "NONE" : "RISK_REJECTED";
        jdbc.update("""
                INSERT INTO evaluaciones (transfer_id, request_hash, decision, reason_code, rule_version)
                VALUES (?, ?, ?, ?, 'demo-v1') ON CONFLICT (transfer_id) DO NOTHING
                """, request.transferId(), hash, decision, reason);
        var saved = jdbc.queryForObject(
                "SELECT request_hash, decision, reason_code, rule_version FROM evaluaciones WHERE transfer_id = ?",
                (rs, row) -> new Saved(
                        rs.getString("request_hash"),
                        new EvaluationResponse(
                                rs.getString("decision"), rs.getString("reason_code"), rs.getString("rule_version"))),
                request.transferId());
        if (saved == null || !hash.equals(saved.hash())) {
            throw new DuplicateKeyException("transferId usado con datos diferentes");
        }
        return saved.response();
    }

    private void validate(EvaluationRequest request) {
        if (request == null
                || request.transferId() == null
                || request.sourceAccountId() == null
                || request.destinationAccountId() == null
                || request.sourceAccountId().equals(request.destinationAccountId())
                || request.amount() == null
                || !request.amount().matches("(0|[1-9][0-9]{0,16})\\.[0-9]{2}")
                || new BigDecimal(request.amount()).signum() <= 0
                || !"PEN".equals(request.currency())) {
            throw new IllegalArgumentException("Evaluación inválida");
        }
    }

    private String fingerprint(EvaluationRequest request) {
        try {
            String value = request.sourceAccountId() + "|" + request.destinationAccountId() + "|" + request.amount()
                    + "|" + request.currency();
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record Saved(String hash, EvaluationResponse response) {}
}
