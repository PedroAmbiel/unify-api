package br.com.unify.matchable.user.services;

import br.com.unify.matchable.user.dto.ProfileCompletionResponse;
import br.com.unify.matchable.user.dto.ProfileOptionsResponse;
import br.com.unify.matchable.user.dto.UserMatchPreferencesResponse;
import br.com.unify.matchable.user.dto.UserMatchPreferencesUpsertRequest;
import br.com.unify.matchable.user.dto.UserProfileResponse;
import br.com.unify.matchable.user.dto.UserProfileUpsertRequest;
import br.com.unify.matchable.user.entity.User;

public interface UserProfileService {

    UserProfileResponse getProfile(User user);

    UserProfileResponse saveProfile(User user, UserProfileUpsertRequest request);

    UserMatchPreferencesResponse getMatchPreferences(User user);

    UserMatchPreferencesResponse saveMatchPreferences(User user, UserMatchPreferencesUpsertRequest request);

    ProfileCompletionResponse getCompletionStatus(User user);

    ProfileOptionsResponse getProfileOptions();
}