package br.com.unify.matchable.user.services;

import br.com.unify.matchable.user.dto.UserAccessibilitySettingsResponse;
import br.com.unify.matchable.user.dto.UserAccessibilitySettingsUpsertRequest;
import br.com.unify.matchable.user.entity.User;

public interface UserAccessibilitySettingsService {

    UserAccessibilitySettingsResponse getSettings(User user);

    UserAccessibilitySettingsResponse saveSettings(User user, UserAccessibilitySettingsUpsertRequest request);
}
