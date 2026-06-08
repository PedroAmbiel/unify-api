package br.com.unify.matchable.user.services;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

import br.com.unify.matchable.common.UUIDv7Generator;
import br.com.unify.matchable.common.image.OidImageService;
import br.com.unify.matchable.user.dto.DisabilityOptionResponse;
import br.com.unify.matchable.user.dto.LocationRequest;
import br.com.unify.matchable.user.dto.LocationResponse;
import br.com.unify.matchable.user.dto.LookupOptionResponse;
import br.com.unify.matchable.user.dto.ProfileCompletionResponse;
import br.com.unify.matchable.user.dto.ProfileOptionsResponse;
import br.com.unify.matchable.user.dto.SimilarityOptionResponse;
import br.com.unify.matchable.user.dto.UserPublicProfileGalleryImagesResponse;
import br.com.unify.matchable.user.dto.UserPublicProfileResponse;
import br.com.unify.matchable.user.dto.UserProfileImageResponse;
import br.com.unify.matchable.user.dto.UserProfileImagesResponse;
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
import br.com.unify.matchable.user.entity.LoveLanguage;
import br.com.unify.matchable.user.entity.Pronoun;
import br.com.unify.matchable.user.entity.User;
import br.com.unify.matchable.user.entity.UserCoordinates;
import br.com.unify.matchable.user.entity.UserMatchPreference;
import br.com.unify.matchable.user.entity.UserProfile;
import br.com.unify.matchable.user.entity.UserProfileImage;
import br.com.unify.matchable.user.enums.SimilarityPreference;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class UserProfileServiceImplementation implements UserProfileService {

    private static final int MINIMUM_PREFERRED_AGE = 18;
    private static final int MAX_ACTIVE_GALLERY_IMAGES = 5;
    private static final String IMAGE_NOT_FOUND_MESSAGE = "Imagem não encontrada";
    private static final String PUBLIC_PROFILE_NOT_FOUND_MESSAGE = "Perfil público não encontrado";
    private static final String PUBLIC_GALLERY_IMAGE_NOT_FOUND_MESSAGE = "Imagem pública não encontrada";
    private static final String IMAGE_DOWNLOAD_URL_PREFIX = "/users/me/profile/images/";

    private static final String GENDER_FIELD = "gênero";
    private static final String PRONOUNS_FIELD = "pronomes";
    private static final String DISABILITY_FIELD = "tipo de deficiência";
    private static final String ACCESSIBILITY_NEED_FIELD = "necessidade de acessibilidade";
    private static final String AUTONOMY_LEVEL_FIELD = "nível de autonomia";
    private static final String COMMUNICATION_FORM_FIELD = "forma de comunicação";
    private static final String LIFESTYLE_FIELD = "estilo de vida";
    private static final String LOVE_LANGUAGE_FIELD = "linguagem do amor";
    private static final String ENERGY_LEVEL_FIELD = "ritmo de energia";
    private static final String INTEREST_FIELD = "interesse";
    private static final String CONNECTION_TYPE_FIELD = "tipo de conexão";
    private static final String DESIRED_GENDER_FIELD = "gênero desejado";

    @Inject
    EntityManager entityManager;

    @Inject
    OidImageService oidImageService;

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
        profile.pronouns = resolveReference(request.pronounsId(), Pronoun.class, PRONOUNS_FIELD);
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
            replaceSet(profile.loveLanguages, resolveReferenceSet(request.loveLanguageIds(), LoveLanguage.class, LOVE_LANGUAGE_FIELD));
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
        preference.loveLanguageSimilarity = request.loveLanguageSimilarity();
        preference.energyLevelSimilarity = request.energyLevelSimilarity();
        Integer minAge = validatePreferredAge(request.minAge(), "A idade mínima desejada");
        Integer maxAge = validatePreferredAge(request.maxAge(), "A idade máxima desejada");
        validateAgeRange(minAge, maxAge);
        preference.minAge = minAge;
        preference.maxAge = maxAge;
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
            missingProfileFields.add("pronomes");
            missingProfileFields.add("tipo de deficiência");
            missingProfileFields.add("forma de comunicação");
            missingProfileFields.add("estilo de vida");
            missingProfileFields.add("linguagens do amor");
            missingProfileFields.add("interesses");
        } else {
            if (profile.gender == null) {
                missingProfileFields.add("gênero");
            }
            if (profile.pronouns == null) {
                missingProfileFields.add("pronomes");
            }
            if (profile.communicationForms == null || profile.communicationForms.isEmpty()) {
                missingProfileFields.add("forma de comunicação");
            }
            if (profile.lifestyleTypes == null || profile.lifestyleTypes.isEmpty()) {
                missingProfileFields.add("estilo de vida");
            }
            if (profile.loveLanguages == null || profile.loveLanguages.isEmpty()) {
                missingProfileFields.add("linguagens do amor");
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
            missingMatchFields.add("linguagem do amor preferida");
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
            if (preference.loveLanguageSimilarity == null) {
                missingMatchFields.add("linguagem do amor preferida");
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
            Pronoun.<Pronoun>listAll(Sort.by("id")).stream()
                .map(pronoun -> toOption(pronoun.id, pronoun.description))
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
                LoveLanguage.<LoveLanguage>listAll(Sort.by("id")).stream()
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

    @Override
    public UserPublicProfileResponse getPublicProfile(UUID userProfileId) {
        UserProfile profile = findPublicProfile(userProfileId);
        return toPublicProfileResponse(profile);
    }

    @Override
    public UserPublicProfileGalleryImagesResponse getPublicGalleryImages(UUID userProfileId) {
        UserProfile profile = findPublicProfile(userProfileId);
        return new UserPublicProfileGalleryImagesResponse(profile.id, listPublicGalleryImageIds(profile));
    }

    @Override
    public byte[] getPublicGalleryImageContent(UUID userProfileId, UUID imageId) {
        if (imageId == null) {
            throw new IllegalArgumentException("Informe o identificador da imagem pública");
        }

        UserProfile profile = findPublicProfile(userProfileId);
        UserProfileImage image = UserProfileImage.findActiveByIdAndUserProfile(imageId, profile);
        if (image == null || image.profilePicture) {
            throw new NoSuchElementException(PUBLIC_GALLERY_IMAGE_NOT_FOUND_MESSAGE);
        }

        return readStoredImage(image);
    }

    @Override
    public UserProfileImagesResponse getActiveImages(User user) {
        UserProfile profile = UserProfile.findByUser(user);
        if (profile == null) {
            return emptyImagesResponse();
        }
        return toImagesResponse(profile);
    }

    @Override
    @Transactional
    public UserProfileImagesResponse uploadProfilePicture(User user, byte[] imageBytes) {
        UserProfile profile = findOrCreateProfile(user);
        ensureProfilePersisted(profile);

        byte[] compressedImage = oidImageService.compressToJpeg(imageBytes);
        deactivateCurrentProfilePicture(profile);
        createImage(profile, compressedImage, true);
        return toImagesResponse(profile);
    }

    @Override
    @Transactional
    public UserProfileImagesResponse uploadGalleryImage(User user, byte[] imageBytes) {
        UserProfile profile = findOrCreateProfile(user);
        ensureProfilePersisted(profile);

        if (UserProfileImage.countActiveGalleryImages(profile) >= MAX_ACTIVE_GALLERY_IMAGES) {
            throw new IllegalStateException(
                    "Limite de 5 imagens ativas no carrossel atingido. Desative uma imagem antes de enviar outra"
            );
        }

        byte[] compressedImage = oidImageService.compressToJpeg(imageBytes);
        createImage(profile, compressedImage, false);
        return toImagesResponse(profile);
    }

    @Override
    @Transactional
    public UserProfileImagesResponse deactivateImage(User user, UUID imageId) {
        UserProfile profile = UserProfile.findByUser(user);
        if (profile == null) {
            throw new NoSuchElementException(IMAGE_NOT_FOUND_MESSAGE);
        }

        UserProfileImage image = UserProfileImage.findByIdAndUserProfile(imageId, profile);
        if (image == null) {
            throw new NoSuchElementException(IMAGE_NOT_FOUND_MESSAGE);
        }

        image.active = false;
        return toImagesResponse(profile);
    }

    @Override
    public byte[] getImageContent(User user, UUID imageId) {
        UserProfile profile = UserProfile.findByUser(user);
        if (profile == null) {
            throw new NoSuchElementException(IMAGE_NOT_FOUND_MESSAGE);
        }

        UserProfileImage image = UserProfileImage.findActiveByIdAndUserProfile(imageId, profile);
        if (image == null) {
            throw new NoSuchElementException(IMAGE_NOT_FOUND_MESSAGE);
        }

        return readStoredImage(image);
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

    private void deactivateCurrentProfilePicture(UserProfile profile) {
        UserProfileImage currentProfilePicture = UserProfileImage.findActiveProfilePicture(profile);
        if (currentProfilePicture != null) {
            currentProfilePicture.active = false;
        }
    }

    private void createImage(UserProfile profile, byte[] compressedImage, boolean profilePicture) {
        UserProfileImage image = new UserProfileImage();
        image.id = UUIDv7Generator.generate();
        image.userProfile = profile;
        image.oid = oidImageService.toOidBlob(compressedImage);
        image.profilePicture = profilePicture;
        image.active = true;
        image.persist();
        profile.images.add(0, image);
    }

    private void replaceActiveLocation(UserProfile profile, LocationRequest location) {
        boolean hadActiveCoordinate = profile.coordinates.stream().anyMatch(coordinate -> coordinate.active);
        profile.coordinates.forEach(coordinate -> coordinate.active = false);

        if (location == null) {
            return;
        }
        if (location.latitude() == null || location.longitude() == null) {
            throw new IllegalArgumentException("Latitude e longitude devem ser informadas juntas");
        }

        validateLatitude(location.latitude());
        validateLongitude(location.longitude());

        if (hadActiveCoordinate) {
            // Flush the deactivation first so the partial unique index sees only one active coordinate.
            entityManager.flush();
        }

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

    private Integer validatePreferredAge(Integer age, String fieldLabel) {
        if (age == null) {
            return null;
        }
        if (age < MINIMUM_PREFERRED_AGE) {
            throw new IllegalArgumentException(fieldLabel + " deve ser maior ou igual a " + MINIMUM_PREFERRED_AGE);
        }
        return age;
    }

    private void validateAgeRange(Integer minAge, Integer maxAge) {
        if (minAge != null && maxAge != null && minAge > maxAge) {
            throw new IllegalArgumentException("A idade mínima desejada não pode ser maior que a idade máxima desejada");
        }
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

    private byte[] readStoredImage(UserProfileImage image) {
        return oidImageService.readOidBlob(image.oid);
    }

    private UserProfile findPublicProfile(UUID userProfileId) {
        if (userProfileId == null) {
            throw new IllegalArgumentException("Informe o parâmetro de consulta 'userProfileId'");
        }

        UserProfile profile = UserProfile.findById(userProfileId);
        if (profile == null || profile.user == null || !profile.user.verified) {
            throw new NoSuchElementException(PUBLIC_PROFILE_NOT_FOUND_MESSAGE);
        }

        return profile;
    }

    private List<UUID> listPublicGalleryImageIds(UserProfile profile) {
        return UserProfileImage.listActiveGalleryImages(profile).stream()
                .map(image -> image.id)
                .toList();
    }

    private UserProfileResponse toProfileResponse(UserProfile profile) {
        UserCoordinates activeCoordinate = profile.getActiveCoordinate();
        UserProfileImagesResponse imagesResponse = toImagesResponse(profile);
        return new UserProfileResponse(
                profile.id,
            profile.user == null ? null : profile.user.name,
            profile.user == null ? null : profile.user.lastName,
            calculateAge(profile.user),
                profile.bio,
                profile.gender == null ? null : toOption(profile.gender.id, profile.gender.description),
            profile.pronouns == null ? null : toOption(profile.pronouns.id, profile.pronouns.description),
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
                profile.loveLanguages.stream()
                    .map(type -> toOption(type.id, type.description))
                    .toList(),
                profile.energyLevel == null ? null : toOption(profile.energyLevel.id, profile.energyLevel.description),
                profile.interestTypes.stream()
                        .map(type -> toOption(type.id, type.description))
                        .toList(),
                activeCoordinate == null ? null : new LocationResponse(activeCoordinate.latitude, activeCoordinate.longitude, activeCoordinate.active),
                imagesResponse.profilePicture(),
                imagesResponse.galleryImages()
        );
    }

            private UserPublicProfileResponse toPublicProfileResponse(UserProfile profile) {
            return new UserPublicProfileResponse(
                profile.id,
                profile.user == null ? null : profile.user.name,
                calculateAge(profile.user),
                profile.bio,
                profile.gender == null ? null : toOption(profile.gender.id, profile.gender.description),
                profile.pronouns == null ? null : toOption(profile.pronouns.id, profile.pronouns.description),
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
                profile.loveLanguages.stream()
                    .map(type -> toOption(type.id, type.description))
                    .toList(),
                profile.energyLevel == null ? null : toOption(profile.energyLevel.id, profile.energyLevel.description),
                profile.interestTypes.stream()
                    .map(type -> toOption(type.id, type.description))
                    .toList(),
                listPublicGalleryImageIds(profile)
            );
            }

    private Integer calculateAge(User user) {
        if (user == null || user.birthdate == null) {
            return null;
        }

        LocalDate today = LocalDate.now();
        if (user.birthdate.isAfter(today)) {
            return null;
        }

        return Period.between(user.birthdate, today).getYears();
    }

            private UserProfileImagesResponse toImagesResponse(UserProfile profile) {
            UserProfileImage activeProfilePicture = UserProfileImage.findActiveProfilePicture(profile);
            return new UserProfileImagesResponse(
                activeProfilePicture == null ? null : toImageResponse(activeProfilePicture),
                UserProfileImage.listActiveGalleryImages(profile).stream()
                    .map(this::toImageResponse)
                    .toList()
            );
            }

            private UserProfileImageResponse toImageResponse(UserProfileImage image) {
            return new UserProfileImageResponse(
                image.id,
                image.profilePicture,
                image.active,
                IMAGE_DOWNLOAD_URL_PREFIX + image.id
            );
            }

    private UserMatchPreferencesResponse toMatchPreferencesResponse(UserMatchPreference preference) {
        return new UserMatchPreferencesResponse(
                preference.id,
                preference.connectionType == null ? null : toOption(preference.connectionType.id, preference.connectionType.description),
                preference.accessibilityNeedSimilarity == null ? null : preference.accessibilityNeedSimilarity.name(),
                preference.autonomyCompatibility == null ? null : preference.autonomyCompatibility.name(),
                preference.lifestyleSimilarity == null ? null : preference.lifestyleSimilarity.name(),
                preference.loveLanguageSimilarity == null ? null : preference.loveLanguageSimilarity.name(),
                preference.energyLevelSimilarity == null ? null : preference.energyLevelSimilarity.name(),
                preference.minAge,
                preference.maxAge,
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
        return new UserProfileResponse(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of(),
                null,
                null,
                List.of()
        );
    }

    private UserMatchPreferencesResponse emptyMatchPreferencesResponse() {
        return new UserMatchPreferencesResponse(null, null, null, null, null, null, null, null, null, null, List.of());
    }

    private UserProfileImagesResponse emptyImagesResponse() {
        return new UserProfileImagesResponse(null, List.of());
    }
}