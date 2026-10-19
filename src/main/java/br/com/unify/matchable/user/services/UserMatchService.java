package br.com.unify.matchable.user.services;

import java.util.List;
import java.util.UUID;

import br.com.unify.matchable.user.dto.MatchDecisionRequest;
import br.com.unify.matchable.user.dto.MatchDecisionResponse;
import br.com.unify.matchable.user.dto.MutualMatchPageResponse;
import br.com.unify.matchable.user.dto.MutualMatchResponse;
import br.com.unify.matchable.user.dto.MutualMatchSummaryResponse;
import br.com.unify.matchable.user.dto.PotentialMatchesRequest;
import br.com.unify.matchable.user.entity.User;

public interface UserMatchService {

    /** Modo normal: o usuário atual tem coordenada ativa e a distância participa do ranking. */
    String DISCOVERY_MODE_GEO = "geo";

    /** Modo degradado: sem coordenada do usuário atual, o fator distância sai da conta. */
    String DISCOVERY_MODE_NO_LOCATION = "no-location";

    /**
     * Resultado da descoberta com o modo em que ela foi executada.
     * O modo vira o cabeçalho X-Unify-Discovery-Mode na resposta REST (B5.6).
     */
    record DiscoveryResult(List<UUID> profileIds, String mode) {
    }

    List<UUID> getPotentialMatches(User user, PotentialMatchesRequest request);

    /**
     * Mesma descoberta de {@link #getPotentialMatches}, expondo também o modo de execução.
     * Default de compatibilidade: implementações antigas continuam válidas e reportam modo "geo".
     */
    default DiscoveryResult discoverPotentialMatches(User user, PotentialMatchesRequest request) {
        return new DiscoveryResult(getPotentialMatches(user, request), DISCOVERY_MODE_GEO);
    }

    MatchDecisionResponse registerDecision(User user, MatchDecisionRequest request);

    List<MutualMatchResponse> getMutualMatches(User user);

    MutualMatchPageResponse getMutualMatchesPage(User user, Integer page, Integer size);

    /**
     * Matches mútuos cuja tela "Deu match" o usuário ainda não viu (mais antigo
     * primeiro). Quem respondeu o convite já sai visto: viu a comemoração na
     * resposta do {@link #registerDecision}.
     */
    List<MutualMatchSummaryResponse> getUnseenMutualMatches(User user);

    /**
     * Registra que o usuário viu a tela "Deu match" deste par. Idempotente.
     *
     * @throws java.util.NoSuchElementException quando o match não existe, não é
     *         mútuo ou não é do usuário
     */
    void markMutualMatchSeen(User user, UUID matchId);

    byte[] getMatchedProfileImage(User user, UUID imageId);
}