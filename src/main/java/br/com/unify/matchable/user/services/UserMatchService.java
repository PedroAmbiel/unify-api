package br.com.unify.matchable.user.services;

import java.util.List;
import java.util.UUID;

import br.com.unify.matchable.user.dto.MatchDecisionRequest;
import br.com.unify.matchable.user.dto.MatchDecisionResponse;
import br.com.unify.matchable.user.dto.MutualMatchPageResponse;
import br.com.unify.matchable.user.dto.MutualMatchResponse;
import br.com.unify.matchable.user.dto.PotentialMatchesRequest;
import br.com.unify.matchable.user.entity.User;

public interface UserMatchService {

    List<UUID> getPotentialMatches(User user, PotentialMatchesRequest request);

    MatchDecisionResponse registerDecision(User user, MatchDecisionRequest request);

    List<MutualMatchResponse> getMutualMatches(User user);

    MutualMatchPageResponse getMutualMatchesPage(User user, Integer page, Integer size);

    byte[] getMatchedProfileImage(User user, UUID imageId);
}