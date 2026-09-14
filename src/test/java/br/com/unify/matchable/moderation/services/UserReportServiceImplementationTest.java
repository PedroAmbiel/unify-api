package br.com.unify.matchable.moderation.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.unify.matchable.common.UUIDv7Generator;
import br.com.unify.matchable.community.entity.Community;
import br.com.unify.matchable.post.entity.Post;
import br.com.unify.matchable.community.enums.CommunityPrivacy;
import br.com.unify.matchable.post.enums.PostOrigin;
import br.com.unify.matchable.moderation.dto.ReportCreateRequest;
import br.com.unify.matchable.moderation.dto.ReportReasonOptionResponse;
import br.com.unify.matchable.moderation.dto.ReportResponse;
import br.com.unify.matchable.moderation.entity.UserReport;
import br.com.unify.matchable.moderation.enums.ReportReason;
import br.com.unify.matchable.moderation.enums.UserReportStatus;
import br.com.unify.matchable.user.entity.User;
import br.com.unify.matchable.user.entity.UserProfile;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Integração com banco real (padrão de UserMatchDiscoveryIntegrationTest):
 * cada teste roda numa transação revertida ao final. Sem Mockito no projeto,
 * os finders estáticos do Panache só são exercitáveis assim.
 */
@QuarkusTest
class UserReportServiceImplementationTest {

    @Inject
    UserReportService service;

    @Test
    @TestTransaction
    void createReport_deveFalhar_quandoAutodenuncia() {
        User reporter = persistUser("Ana", "Auto");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.createReport(reporter, new ReportCreateRequest(reporter.id, null, ReportReason.SCAM, null))
        );

        assertEquals(UserReportServiceImplementation.SELF_REPORT_FORBIDDEN_MESSAGE, exception.getMessage());
    }

    @Test
    @TestTransaction
    void createReport_deveFalhar_quandoDenunciaAbertaDuplicada() {
        User reporter = persistUser("Ana", "Dup");
        User reported = persistUser("Beto", "Dup");
        ReportCreateRequest request = new ReportCreateRequest(reported.id, null, ReportReason.HARASSMENT, "x");

        service.createReport(reporter, request);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.createReport(reporter, request)
        );
        assertEquals(UserReportServiceImplementation.DUPLICATE_OPEN_REPORT_MESSAGE, exception.getMessage());
    }

    @Test
    @TestTransaction
    void createReport_devePersistir_quandoDenunciaDePerfilValida() {
        User reporter = persistUser("Ana", "Perfil");
        User reported = persistUser("Beto", "Perfil");

        ReportResponse response = service.createReport(
                reporter,
                new ReportCreateRequest(reported.id, null, ReportReason.FAKE_PROFILE, "  descrição  ")
        );

        assertNotNull(response.id());
        assertEquals(reported.id, response.reportedUserId());
        assertNull(response.reportedPostId());
        assertEquals(UserReportStatus.OPEN, response.status());
        assertEquals("descrição", response.description());
        assertEquals(1, UserReport.listByReportedUser(reported).size());
    }

    @Test
    @TestTransaction
    void createReport_deveAceitarIdDoPerfil_comoAlvo() {
        // O discovery só conhece o id do UserProfile; o serviço resolve para o User.
        User reporter = persistUser("Ana", "ViaPerfil");
        User reported = persistUser("Beto", "ViaPerfil");
        UserProfile reportedProfile = persistProfile(reported);

        ReportResponse response = service.createReport(
                reporter,
                new ReportCreateRequest(reportedProfile.id, null, ReportReason.OTHER, null)
        );

        assertEquals(reported.id, response.reportedUserId());
    }

    @Test
    @TestTransaction
    void createReport_devePersistir_quandoDenunciaDePostValida_ePermitirPostsDiferentes() {
        User reporter = persistUser("Ana", "Post");
        User reported = persistUser("Beto", "Post");
        Community community = persistCommunity(reported);
        Post first = persistPost(community, reported, "primeiro");
        Post second = persistPost(community, reported, "segundo");

        ReportResponse firstResponse = service.createReport(
                reporter, new ReportCreateRequest(reported.id, first.id, ReportReason.INAPPROPRIATE_CONTENT, null)
        );
        ReportResponse secondResponse = service.createReport(
                reporter, new ReportCreateRequest(reported.id, second.id, ReportReason.INAPPROPRIATE_CONTENT, null)
        );

        assertEquals(first.id, firstResponse.reportedPostId());
        assertEquals(second.id, secondResponse.reportedPostId());

        // Denúncia de perfil (sem post) continua independente das denúncias de post.
        ReportResponse profileReport = service.createReport(
                reporter, new ReportCreateRequest(reported.id, null, ReportReason.HATE_SPEECH, null)
        );
        assertNull(profileReport.reportedPostId());

        assertThrows(IllegalStateException.class, () -> service.createReport(
                reporter, new ReportCreateRequest(reported.id, first.id, ReportReason.SCAM, null)
        ));
    }

    @Test
    @TestTransaction
    void createReport_deveFalhar_quandoPostNaoPertenceAoUsuario() {
        User reporter = persistUser("Ana", "Mismatch");
        User reported = persistUser("Beto", "Mismatch");
        User other = persistUser("Caio", "Mismatch");
        Community community = persistCommunity(other);
        Post post = persistPost(community, other, "de outro");

        assertThrows(IllegalArgumentException.class, () -> service.createReport(
                reporter, new ReportCreateRequest(reported.id, post.id, ReportReason.SCAM, null)
        ));
    }

    @Test
    @TestTransaction
    void createReport_deveFalhar_quandoAlvoNaoExiste() {
        User reporter = persistUser("Ana", "Ghost");

        assertThrows(NoSuchElementException.class, () -> service.createReport(
                reporter, new ReportCreateRequest(UUID.randomUUID(), null, ReportReason.SCAM, null)
        ));
        assertThrows(IllegalArgumentException.class, () -> service.createReport(
                reporter, new ReportCreateRequest(UUID.randomUUID(), null, null, null)
        ));
    }

    @Test
    void listReasons_deveRetornarTodosOsValoresDoEnum() {
        List<ReportReasonOptionResponse> reasons = service.listReasons();

        assertEquals(ReportReason.values().length, reasons.size());
        assertEquals("Assédio ou perseguição", reasons.stream()
                .filter(option -> option.value() == ReportReason.HARASSMENT)
                .findFirst().orElseThrow().description());
    }

    // ------------------------------------------------------------ fixtures

    private static User persistUser(String name, String lastName) {
        User user = new User();
        user.id = UUIDv7Generator.generate();
        user.name = name;
        user.lastName = lastName;
        user.email = "report-" + UUID.randomUUID() + "@example.com";
        user.password = "hash";
        user.birthdate = LocalDate.now().minusYears(30);
        user.verified = true;
        user.lastUpdatedAt = Instant.now();
        user.persist();
        return user;
    }

    private static UserProfile persistProfile(User user) {
        UserProfile profile = new UserProfile();
        profile.id = UUIDv7Generator.generate();
        profile.user = user;
        profile.bio = "bio";
        profile.persist();
        return profile;
    }

    private static Community persistCommunity(User owner) {
        Community community = new Community();
        community.id = UUIDv7Generator.generate();
        community.name = "Comunidade " + UUID.randomUUID();
        community.description = "desc";
        community.owner = owner;
        community.privacy = CommunityPrivacy.PUBLIC;
        community.active = true;
        community.persist();
        return community;
    }

    private static Post persistPost(Community community, User author, String body) {
        Post post = new Post();
        post.id = UUIDv7Generator.generate();
        post.community = community;
        post.author = author;
        post.origin = PostOrigin.COMMUNITY;
        post.body = body;
        post.createdAt = Instant.now();
        post.persist();
        return post;
    }
}
