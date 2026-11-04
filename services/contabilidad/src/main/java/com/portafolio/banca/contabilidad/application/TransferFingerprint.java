package com.portafolio.banca.contabilidad.application;

import com.portafolio.banca.contabilidad.domain.TransferInstruction;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

final class TransferFingerprint {
    private TransferFingerprint() {}

    static String from(TransferInstruction instruction) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : List.of(
                    instruction.actorSubject(),
                    instruction.sourceAccountId().toString(),
                    instruction.destinationAccountId().toString(),
                    instruction.amount().toPlainString(),
                    instruction.currency())) {
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                digest.update(
                        ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no disponible", exception);
        }
    }
}
