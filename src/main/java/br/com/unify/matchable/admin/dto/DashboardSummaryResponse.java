package br.com.unify.matchable.admin.dto;

/**
 * Totais da plataforma para o dashboard do backoffice (semana 07). Só
 * contagens: nenhuma entidade é carregada para montar este payload.
 *
 * @param generatedAtEpochMillis instante do cálculo; com o cache de 60 s o
 *                               backoffice mostra "atualizado às ...".
 */
public record DashboardSummaryResponse(
        UsersSummary users,
        MatchesSummary matches,
        ChatSummary chat,
        CommunitiesSummary communities,
        PostsSummary posts,
        InteractionsSummary interactions,
        ReportsSummary reports,
        FollowsSummary follows,
        long generatedAtEpochMillis
) {

    /**
     * @param withPresentationAudio perfis com áudio de apresentação (plano 04,
     *                              frente D): adoção do recurso pensado para
     *                              pessoas cegas.
     */
    public record UsersSummary(
            long total,
            long verified,
            long banned,
            long admins,
            long newLast7Days,
            long newLast30Days,
            long withPresentationAudio
    ) {
    }

    /**
     * @param pairsWithDecision pares em que ao menos um lado já curtiu ou recusou
     *                          (cada linha de {@code user_possible_matches}).
     */
    public record MatchesSummary(
            long pairsWithDecision,
            long mutualMatches,
            long mutualLast7Days
    ) {
    }

    public record ChatSummary(
            long conversations,
            long messagesTotal,
            long messagesLast7Days
    ) {
    }

    public record CommunitiesSummary(
            long total,
            long active,
            long memberships
    ) {
    }

    /** Publicações visíveis por origem e as removidas pela moderação. */
    public record PostsSummary(
            long communityPosts,
            long personalPosts,
            long removedByModeration
    ) {
    }

    public record InteractionsSummary(
            long communityLikes,
            long communityComments,
            long personalLikes,
            long personalComments,
            long totalInteractions
    ) {
    }

    /**
     * Status reais da denúncia (OPEN, UNDER_REVIEW, RESOLVED, DISMISSED).
     *
     * @param pending         OPEN + UNDER_REVIEW.
     * @param pendingPostReports pendentes que apontam para uma publicação.
     */
    public record ReportsSummary(
            long open,
            long underReview,
            long resolved,
            long dismissed,
            long pending,
            long pendingPostReports,
            long total
    ) {
    }

    public record FollowsSummary(
            long totalFollowRelationships
    ) {
    }
}
