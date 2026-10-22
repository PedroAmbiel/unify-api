package br.com.unify.matchable.admin.dto.dashboard;

import java.util.UUID;

/** Comunidade no gráfico de dispersão membros x publicações do período. */
public record CommunityPoint(UUID id, String name, String category, long members, long posts, long interactions) {
}
