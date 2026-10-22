package br.com.unify.matchable.moderation.enums;

import java.util.List;

/**
 * Ciclo da denúncia (semana 06): OPEN -> UNDER_REVIEW -> RESOLVED | DISMISSED.
 * OPEN pode ir direto para o encerramento; denúncia encerrada não reabre.
 */
public enum UserReportStatus {
    OPEN,
    UNDER_REVIEW,
    RESOLVED,
    DISMISSED;

    /** Ainda aguardando decisão da moderação. */
    public static final List<UserReportStatus> PENDING = List.of(OPEN, UNDER_REVIEW);

    public boolean isClosed() {
        return this == RESOLVED || this == DISMISSED;
    }
}
