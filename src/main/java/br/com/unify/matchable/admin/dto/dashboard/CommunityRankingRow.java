package br.com.unify.matchable.admin.dto.dashboard;

import java.util.UUID;

/** Linha do ranking de comunidades; {@code newMembers}, {@code posts} e {@code interactions} são do período. */
public record CommunityRankingRow(
        UUID id,
        String name,
        String category,
        String privacy,
        long members,
        long newMembers,
        long posts,
        long interactions
) {
}
