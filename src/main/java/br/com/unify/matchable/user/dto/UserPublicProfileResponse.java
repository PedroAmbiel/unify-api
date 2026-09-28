package br.com.unify.matchable.user.dto;

import java.util.List;
import java.util.UUID;

public record UserPublicProfileResponse(
        UUID userProfileId,
        String name,
        /** Nulo quando o dono ocultou a idade (privacidade) ou não informou nascimento. */
        Integer age,
        /** Nulo quando o dono ocultou a distância ou falta localização ativa de um dos lados. */
        Double distanceKm,
        String bio,
        LookupOptionResponse gender,
        LookupOptionResponse pronouns,
        List<DisabilityOptionResponse> disabilities,
        List<LookupOptionResponse> accessibilityNeeds,
        LookupOptionResponse autonomyLevel,
        List<LookupOptionResponse> communicationForms,
        List<LookupOptionResponse> lifestyleTypes,
        List<LookupOptionResponse> loveLanguages,
        LookupOptionResponse energyLevel,
        List<LookupOptionResponse> interestTypes,
        List<UUID> galleryImageIds,
        /** Frente D: nulo quando o perfil não tem áudio de apresentação. */
        UserProfileAudioPublicResponse presentationAudio,
        /**
         * Partes do perfil (ProfileField) que quem está vendo NÃO pode ver. Nelas o valor
         * vem nulo / lista vazia; o cliente omite a seção em vez de mostrar "não informado".
         */
        List<String> hiddenFields,
        /**
         * Conta privada e quem vê não a segue (nem tem match mútuo): só nome e id vêm
         * preenchidos; o resto sai nulo/vazio e {@code hiddenFields} traz todas as partes.
         */
        boolean locked,
        /** {@code users.id} do dono (para o avatar e para denúncias). */
        UUID userId,
        /**
         * Foto de perfil (relativa, {@code GET /communities/users/{userId}/avatar}); nula sem foto.
         * Continua disponível com o perfil travado — igual a uma conta privada em redes sociais.
         */
        String avatarUrl
) {
}