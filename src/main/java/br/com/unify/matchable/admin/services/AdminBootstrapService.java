package br.com.unify.matchable.admin.services;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import br.com.unify.matchable.user.entity.User;
import br.com.unify.matchable.user.enums.UserRole;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;

/**
 * Único caminho de promoção a ADMIN pela aplicação (semana 06): no boot,
 * promove as contas listadas em {@code unify.admin.bootstrap-emails}
 * ({@code ADMIN_BOOTSTRAP_EMAILS}). Não existe endpoint que mude papel.
 *
 * A conta precisa existir (cadastro normal pelo app). O papel entra no JWT no
 * próximo login ou na próxima renovação do token.
 */
@ApplicationScoped
public class AdminBootstrapService {

    private static final Logger LOG = Logger.getLogger(AdminBootstrapService.class);

    @ConfigProperty(name = "unify.admin.bootstrap-emails")
    Optional<List<String>> bootstrapEmails;

    @Transactional
    void onStart(@Observes StartupEvent event) {
        List<String> emails = bootstrapEmails.orElse(List.of()).stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();

        for (String email : emails) {
            User user = User.find("lower(email) = ?1", email).firstResult();
            if (user == null) {
                LOG.warnf("Bootstrap de admin ignorado: nenhuma conta com o e-mail %s", email);
                continue;
            }
            if (user.role == UserRole.ADMIN) {
                continue;
            }
            if (user.banned) {
                LOG.warnf("Bootstrap de admin ignorado: a conta %s está banida", email);
                continue;
            }
            user.role = UserRole.ADMIN;
            LOG.infof("Conta %s promovida a ADMIN via unify.admin.bootstrap-emails", email);
        }
    }
}
