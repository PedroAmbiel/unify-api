package br.com.unify.matchable.admin.services;

import java.util.List;

import br.com.unify.matchable.admin.dto.ScoringGroupResponse;
import br.com.unify.matchable.admin.dto.ScoringWeightsUpdateRequest;
import br.com.unify.matchable.scoring.enums.ScoringGroup;
import br.com.unify.matchable.user.entity.User;

/**
 * Pesos de pontuação pelo backoffice: Encontros, feed da aba Início e
 * descoberta de comunidades (ver {@code ScoringDimension}).
 *
 * Erros seguem o mapper global: {@code IllegalArgumentException} = 400,
 * {@code NoSuchElementException} = 404, {@code IllegalStateException} = 409.
 */
public interface AdminScoringWeightService {

    /** Valores salvos no banco (fonte da verdade), com padrão e limites de cada dimensão. */
    List<ScoringGroupResponse> listGroups();

    ScoringGroupResponse getGroup(ScoringGroup group);

    /**
     * Grava os pesos, audita e — só depois do commit — troca a memória da API.
     * 409 se {@code expectedRevision} não é mais a revisão atual do grupo.
     */
    ScoringGroupResponse update(User admin, ScoringGroup group, ScoringWeightsUpdateRequest request);
}
