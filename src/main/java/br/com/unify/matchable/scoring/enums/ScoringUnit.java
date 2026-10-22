package br.com.unify.matchable.scoring.enums;

/** Como o peso de uma {@link ScoringDimension} é lido. */
public enum ScoringUnit {
    /** Fatia do score de compatibilidade: as dimensões PERCENT de um grupo somam sempre 100. */
    PERCENT,
    /** Pontos somados ao score (ou descontados, nas penalidades). */
    POINTS,
    /** Horas (meia-vida da recência do feed). */
    HOURS
}
