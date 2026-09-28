package br.com.unify.matchable.user.services;

import br.com.unify.matchable.user.dto.UserPrivacySettingsResponse;
import br.com.unify.matchable.user.dto.UserPrivacySettingsUpdateRequest;
import br.com.unify.matchable.user.entity.User;

public interface UserPrivacySettingsService {

    UserPrivacySettingsResponse getSettings(User user);

    UserPrivacySettingsResponse saveSettings(User user, UserPrivacySettingsUpdateRequest request);
}
