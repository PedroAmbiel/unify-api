package br.com.unify.matchable.user.services;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import br.com.unify.matchable.common.UUIDv7Generator;
import br.com.unify.matchable.user.dto.DisabilityOptionResponse;
import br.com.unify.matchable.user.dto.LocationRequest;
import br.com.unify.matchable.user.dto.LocationResponse;
import br.com.unify.matchable.user.dto.LookupOptionResponse;
import br.com.unify.matchable.user.dto.ProfileCompletionResponse;
import br.com.unify.matchable.user.dto.ProfileOptionsResponse;
import br.com.unify.matchable.user.dto.SimilarityOptionResponse;
import br.com.unify.matchable.user.dto.UserMatchPreferencesResponse;
import br.com.unify.matchable.user.dto.UserMatchPreferencesUpsertRequest;
import br.com.unify.matchable.user.dto.UserProfileResponse;
import br.com.unify.matchable.user.dto.UserProfileUpsertRequest;
import br.com.unify.matchable.user.entity.AccessibilityNeed;
import br.com.unify.matchable.user.entity.AutonomyLevel;
import br.com.unify.matchable.user.entity.CommunicationForm;
import br.com.unify.matchable.user.entity.ConnectionType;
import br.com.unify.matchable.user.entity.Disability;
import br.com.unify.matchable.user.entity.EnergyLevel;
import br.com.unify.matchable.user.entity.Gender;
import br.com.unify.matchable.user.entity.InterestType;
import br.com.unify.matchable.user.entity.LifestyleType;
import br.com.unify.matchable.user.entity.User;
import br.com.unify.matchable.user.entity.UserCoordinates;
import br.com.unify.matchable.user.entity.UserMatchPreference;
import br.com.unify.matchable.user.entity.UserProfile;
import br.com.unify.matchable.user.enums.SimilarityPreference;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class UserProfileServiceImplementation implements UserProfileService {

    private static final String GENDER_FIELD = "gênero";
    private static final String DISABILITY_FIELD = "tipo de deficiência";
    private static final String ACCESSIBILITY_NEED_FIELD = "necessidade de acessibilidade";
    private static final String AUTONOMY_LEVEL_FIELD = "nível de autonomia";
    private static final String COMMUNICATION_FORM_FIELD = "forma de comunicação";
    private static final String LIFESTYLE_FIELD = "estilo de vida";
    private static final String ENERGY_LEVEL_FIELD = "ritmo de energia";
    private static final String INTEREST_FIELD = "interesse";
    private static final String CONNECTION_TYPE_FIELD = "tipo de conexão";
    private static final String DESIRED_GENDER_FIELD = "gênero desejado";

    @Inject
    EntityManager entityManager;

    @Override
    public UserProfileResponse getProfile(User user) {
        UserProfile profile = UserProfile.findByUser(user);
        if (profile == null) {
            return emptyProfileResponse();
        }
        return toProfileResponse(profile);
    }

    @Override
    @Transactional
    public UserProfileResponse saveProfile(User user, UserProfileUpsertRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Corpo da requisição de perfil não informado");
        }

        UserProfile profile = findOrCreateProfile(user);
        profile.bio = normalizeText(request.bio());
        profile.gender = resolveReference(request.genderId(), Gender.class, GENDER_FIELD);
        replaceSet(profile.disabilities, resolveReferenceSet(request.disabilityIds(), Disability.class, DISABILITY_FIELD));
        replaceSet(
                profile.accessibilityNeeds,
                resolveReferenceSet(request.accessibilityNeedIds(), AccessibilityNeed.class, ACCESSIBILITY_NEED_FIELD)
        );
        profile.autonomyLevel = resolveReference(request.autonomyLevelId(), AutonomyLevel.class, AUTONOMY_LEVEL_FIELD);
        replaceSet(
                profile.communicationForms,
                resolveReferenceSet(request.communicationFormIds(), CommunicationForm.class, COMMUNICATION_FORM_FIELD)
        );
        replaceSet(profile.lifestyleTypes, resolveReferenceSet(request.lifestyleTypeIds(), LifestyleType.class, LIFESTYLE_FIELD));
        profile.energyLevel = resolveReference(request.energyLevelId(), EnergyLevel.class, ENERGY_LEVEL_FIELD);
        replaceSet(profile.interestTypes, resolveReferenceSet(request.interestTypeIds(), InterestType.class, INTEREST_FIELD));
        replaceActiveLocation(profile, request.location());
        ensureProfilePersisted(profile);
        return toProfileResponse(profile);
    }

    @Override
    public UserMatchPreferencesResponse getMatchPreferences(User user) {
        UserProfile profile = UserProfile.findByUser(user);
        if (profile == null) {
            return emptyMatchPreferencesResponse();
        }

        UserMatchPreference preference = UserMatchPreference.findByUserProfile(profile);
        if (preference == null) {
            return emptyMatchPreferencesResponse();
        }

        return toMatchPreferencesResponse(preference);
    }

    @Override
    @Transactional
    public UserMatchPreferencesResponse saveMatchPreferences(User user, UserMatchPreferencesUpsertRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Corpo da requisição de preferências não informado");
        }

        UserProfile profile = findOrCreateProfile(user);
        ensureProfilePersisted(profile);

        UserMatchPreference preference = findOrCreateMatchPreference(profile);
        preference.connectionType = resolveReference(request.connectionTypeId(), ConnectionType.class, CONNECTION_TYPE_FIELD);
        preference.accessibilityNeedSimilarity = request.accessibilityNeedSimilarity();
        preference.autonomyCompatibility = request.autonomyCompatibility();
        preference.lifestyleSimilarity = request.lifestyleSimilarity();
        preference.energyLevelSimilarity = request.energyLevelSimilarity();
        preference.maxMatchDistanceKm = validateMaxDistance(request.maxMatchDistanceKm());
        replaceSet(preference.desiredGenders, resolveReferenceSet(request.desiredGenderIds(), Gender.class, DESIRED_GENDER_FIELD));

        if (!preference.isPersistent()) {
            preference.persist();
        }

        return toMatchPreferencesResponse(preference);
    }

    @Override
    public ProfileCompletionResponse getCompletionStatus(User user) {
        UserProfile profile = UserProfile.findByUser(user);
        UserMatchPreference preference = profile == null ? null : UserMatchPreference.findByUserProfile(profile);

        List<String> missingProfileFields = new java.util.ArrayList<>();
        if (profile == null) {
            missingProfileFields.add("gênero");
            missingProfileFields.add("tipo de deficiência");
            missingProfileFields.add("forma de comunicação");
            missingProfileFields.add("estilo de vida");
            missingProfileFields.add("interesses");
        } else {
            if (profile.gender == null) {
                missingProfileFields.add("gênero");
            }
            if (profile.communicationForms == null || profile.communicationForms.isEmpty()) {
                missingProfileFields.add("forma de comunicação");
            }
            if (profile.lifestyleTypes == null || profile.lifestyleTypes.isEmpty()) {
                missingProfileFields.add("estilo de vida");
            }
            if (profile.interestTypes == null || profile.interestTypes.isEmpty()) {
                missingProfileFields.add("interesses");
            }
        }

        List<String> missingMatchFields = new java.util.ArrayList<>();
        if (preference == null) {
            missingMatchFields.add("distância máxima do match");
            missingMatchFields.add("tipo de conexão");
            missingMatchFields.add("estilo de vida preferido");
            missingMatchFields.add("gênero desejado");
        } else {
            if (preference.maxMatchDistanceKm == null) {
                missingMatchFields.add("distância máxima do match");
            }
            if (preference.connectionType == null) {
                missingMatchFields.add("tipo de conexão");
            }
            if (preference.lifestyleSimilarity == null) {
                missingMatchFields.add("estilo de vida preferido");
            }
            if (preference.desiredGenders == null || preference.desiredGenders.isEmpty()) {
                missingMatchFields.add("gênero desejado");
            }
        }

        boolean profileCompleted = missingProfileFields.isEmpty();
        boolean matchPreferencesCompleted = missingMatchFields.isEmpty();
        return new ProfileCompletionResponse(
                profileCompleted,
                matchPreferencesCompleted,
                profileCompleted && matchPreferencesCompleted,
                List.copyOf(missingProfileFields),
                List.copyOf(missingMatchFields)
        );
    }

    @Override
    public ProfileOptionsResponse getProfileOptions() {
        return new ProfileOptionsResponse(
                Gender.<Gender>listAll(Sort.by("id")).stream()
                        .map(gender -> toOption(gender.id, gender.description))
                        .toList(),
                Disability.<Disability>listAll(Sort.by("id")).stream()
                        .map(disability -> new DisabilityOptionResponse(disability.id, disability.description, disability.ionicIcon))
                        .toList(),
                AccessibilityNeed.<AccessibilityNeed>listAll(Sort.by("id")).stream()
                        .map(need -> toOption(need.id, need.description))
                        .toList(),
                AutonomyLevel.<AutonomyLevel>listAll(Sort.by("id")).stream()
                        .map(level -> toOption(level.id, level.description))
                        .toList(),
                CommunicationForm.<CommunicationForm>listAll(Sort.by("id")).stream()
                        .map(form -> toOption(form.id, form.description))
                        .toList(),
                LifestyleType.<LifestyleType>listAll(Sort.by("id")).stream()
                        .map(type -> toOption(type.id, type.description))
                        .toList(),
                EnergyLevel.<EnergyLevel>listAll(Sort.by("id")).stream()
                        .map(level -> toOption(level.id, level.description))
                        .toList(),
                InterestType.<InterestType>listAll(Sort.by("id")).stream()
                        .map(type -> toOption(type.id, type.description))
                        .toList(),
                ConnectionType.<ConnectionType>listAll(Sort.by("id")).stream()
                        .map(type -> toOption(type.id, type.description))
                        .toList(),
                List.of(
                        new SimilarityOptionResponse(SimilarityPreference.ANY.name(), "Indiferente"),
                        new SimilarityOptionResponse(SimilarityPreference.SIMILAR.name(), "Similar"),
                        new SimilarityOptionResponse(SimilarityPreference.DIFFERENT.name(), "Diferente")
                )
        );
    }

    private UserProfile findOrCreateProfile(User user) {
        UserProfile profile = UserProfile.findByUser(user);
        if (profile != null) {
            return profile;
        }

        profile = new UserProfile();
        profile.id = UUIDv7Generator.generate();
        profile.user = user;
        user.profile = profile;
        return profile;
    }

    private UserMatchPreference findOrCreateMatchPreference(UserProfile profile) {
        UserMatchPreference preference = UserMatchPreference.findByUserProfile(profile);
        if (preference != null) {
            return preference;
        }

        preference = new UserMatchPreference();
        preference.id = UUIDv7Generator.generate();
        preference.userProfile = profile;
        profile.matchPreference = preference;
        return preference;
    }

    private void ensureProfilePersisted(UserProfile profile) {
        if (!profile.isPersistent()) {
            profile.persist();
        }
    }

    private void replaceActiveLocation(UserProfile profile, LocationRequest location) {
        profile.coordinates.forEach(coordinate -> coordinate.active = false);

        if (location == null) {
            return;
        }
        if (location.latitude() == null || location.longitude() == null) {
            throw new IllegalArgumentException("Latitude e longitude devem ser informadas juntas");
        }

        validateLatitude(location.latitude());
        validateLongitude(location.longitude());

        UserCoordinates coordinate = new UserCoordinates();
        coordinate.id = UUIDv7Generator.generate();
        coordinate.userProfile = profile;
        coordinate.latitude = location.latitude();
        coordinate.longitude = location.longitude();
        coordinate.active = true;
        profile.coordinates.add(0, coordinate);
    }

    private void validateLatitude(BigDecimal latitude) {
        if (latitude.compareTo(BigDecimal.valueOf(-90)) < 0 || latitude.compareTo(BigDecimal.valueOf(90)) > 0) {
            throw new IllegalArgumentException("Latitude deve estar entre -90 e 90");
        }
    }

    private void validateLongitude(BigDecimal longitude) {
        if (longitude.compareTo(BigDecimal.valueOf(-180)) < 0 || longitude.compareTo(BigDecimal.valueOf(180)) > 0) {
            throw new IllegalArgumentException("Longitude deve estar entre -180 e 180");
        }
    }

    private Integer validateMaxDistance(Integer maxMatchDistanceKm) {
        if (maxMatchDistanceKm == null) {
            return null;
        }
        if (maxMatchDistanceKm < 1) {
            throw new IllegalArgumentException("A distância máxima do match deve ser maior que zero");
        }
        return maxMatchDistanceKm;
    }

    private <T> T resolveReference(Integer id, Class<T> entityType, String fieldLabel) {
        if (id == null) {
            return null;
        }

        T entity = entityManager.find(entityType, id);
        if (entity == null) {
            throw new IllegalArgumentException("Valor inválido para " + fieldLabel);
        }
        return entity;
    }

    private <T> Set<T> resolveReferenceSet(Set<Integer> ids, Class<T> entityType, String fieldLabel) {
        Set<T> entities = new LinkedHashSet<>();
        if (ids == null) {
            return entities;
        }

        for (Integer id : ids) {
            if (id == null) {
                throw new IllegalArgumentException("Um dos valores informados para " + fieldLabel + " é inválido");
            }
            entities.add(resolveReference(id, entityType, fieldLabel));
        }

        return entities;
    }

    private <T> void replaceSet(Set<T> target, Set<T> source) {
        target.clear();
        target.addAll(source);
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private UserProfileResponse toProfileResponse(UserProfile profile) {
        UserCoordinates activeCoordinate = profile.getActiveCoordinate();
        return new UserProfileResponse(
                profile.id,
                profile.bio,
                profile.gender == null ? null : toOption(profile.gender.id, profile.gender.description),
                profile.disabilities.stream()
                        .map(disability -> new DisabilityOptionResponse(disability.id, disability.description, disability.ionicIcon))
                        .toList(),
                profile.accessibilityNeeds.stream()
                        .map(need -> toOption(need.id, need.description))
                        .toList(),
                profile.autonomyLevel == null ? null : toOption(profile.autonomyLevel.id, profile.autonomyLevel.description),
                profile.communicationForms.stream()
                        .map(form -> toOption(form.id, form.description))
                        .toList(),
                profile.lifestyleTypes.stream()
                        .map(type -> toOption(type.id, type.description))
                        .toList(),
                profile.energyLevel == null ? null : toOption(profile.energyLevel.id, profile.energyLevel.description),
                profile.interestTypes.stream()
                        .map(type -> toOption(type.id, type.description))
                        .toList(),
                activeCoordinate == null ? null : new LocationResponse(activeCoordinate.latitude, activeCoordinate.longitude, activeCoordinate.active)
        );
    }

    private UserMatchPreferencesResponse toMatchPreferencesResponse(UserMatchPreference preference) {
        return new UserMatchPreferencesResponse(
                preference.id,
                preference.connectionType == null ? null : toOption(preference.connectionType.id, preference.connectionType.description),
                preference.accessibilityNeedSimilarity == null ? null : preference.accessibilityNeedSimilarity.name(),
                preference.autonomyCompatibility == null ? null : preference.autonomyCompatibility.name(),
                preference.lifestyleSimilarity == null ? null : preference.lifestyleSimilarity.name(),
                preference.energyLevelSimilarity == null ? null : preference.energyLevelSimilarity.name(),
                preference.maxMatchDistanceKm,
                preference.desiredGenders.stream()
                        .map(gender -> toOption(gender.id, gender.description))
                        .toList()
        );
    }

    private LookupOptionResponse toOption(Integer id, String description) {
        return new LookupOptionResponse(id, description);
    }

    private UserProfileResponse emptyProfileResponse() {
        return new UserProfileResponse(null, null, null, null, List.of(), null, List.of(), List.of(), null, List.of(), null);
    }

    private UserMatchPreferencesResponse emptyMatchPreferencesResponse() {
        return new UserMatchPreferencesResponse(null, null, null, null, null, null, null, List.of());
    }
}