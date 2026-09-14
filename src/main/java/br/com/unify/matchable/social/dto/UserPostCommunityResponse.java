package br.com.unify.matchable.social.dto;

import java.util.UUID;

/** Comunidade de origem de um post do feed; nula em posts pessoais. */
public record UserPostCommunityResponse(UUID id, String name, String iconUrl) {
}
