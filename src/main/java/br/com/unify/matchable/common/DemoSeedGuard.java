package br.com.unify.matchable.common;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.configuration.ConfigUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

/**
 * Falha o boot se dados de demonstracao forem detectados fora de dev/test.
 *
 * Defesa em profundidade: se alguem reintroduzir o seed no perfil errado
 * (por exemplo, colocando `sql-load-script` no bloco default ou importando
 * um dump de dev em homolog), a aplicacao nao sobe em silencio com contas
 * de senha publicamente documentada em import-dev.sql.
 *
 * O heuristico casa exatamente com o que os seeds produzem:
 * - import-dev.sql   -> teste@gmail.com, verificar@gmail.com
 * - import-users.sql -> seed.userNN@unify.dev
 */
@ApplicationScoped
public class DemoSeedGuard {

    private static final Logger LOG = Logger.getLogger(DemoSeedGuard.class);

    private static final String SEED_EMAIL_PATTERN = "seed.user%@unify.dev";
    private static final String DEMO_EMAIL_A = "teste@gmail.com";
    private static final String DEMO_EMAIL_B = "verificar@gmail.com";

    @Inject
    EntityManager entityManager;

    @Transactional
    void onStart(@Observes StartupEvent event) {
        if (ConfigUtils.isProfileActive("dev") || ConfigUtils.isProfileActive("test")) {
            return;
        }

        Long demoUsers = entityManager
                .createQuery(
                        "select count(u) from User u "
                                + "where u.email like :seedPattern "
                                + "   or u.email = :demoEmailA "
                                + "   or u.email = :demoEmailB",
                        Long.class)
                .setParameter("seedPattern", SEED_EMAIL_PATTERN)
                .setParameter("demoEmailA", DEMO_EMAIL_A)
                .setParameter("demoEmailB", DEMO_EMAIL_B)
                .getSingleResult();

        if (demoUsers > 0) {
            LOG.errorf(
                    "Detectados %d usuarios com padrao de seed de demonstracao nos perfis ativos %s.",
                    demoUsers, ConfigUtils.getProfiles());
            throw new IllegalStateException(
                    "Dados de demonstracao detectados fora de dev/test. "
                            + "Verifique quarkus.hibernate-orm.sql-load-script e as migracoes Flyway.");
        }
    }
}
