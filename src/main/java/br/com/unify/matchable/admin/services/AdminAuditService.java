package br.com.unify.matchable.admin.services;

import java.time.Instant;

import br.com.unify.matchable.admin.entity.AdminAuditLog;
import br.com.unify.matchable.common.UUIDv7Generator;
import br.com.unify.matchable.user.entity.User;
import io.quarkus.arc.Arc;
import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Trilha de auditoria (V32). Grava na MESMA transação da ação administrativa
 * ({@code MANDATORY}): se o ban falhar, o registro some junto; se o registro
 * falhar, o ban não acontece.
 */
@ApplicationScoped
public class AdminAuditService {

    public static final String ACTION_BAN_USER = "BAN_USER";
    public static final String ACTION_UNBAN_USER = "UNBAN_USER";
    public static final String ACTION_REPORT_STATUS_CHANGE = "REPORT_STATUS_CHANGE";
    public static final String ACTION_LOOKUP_CREATE = "LOOKUP_CREATE";
    public static final String ACTION_LOOKUP_UPDATE = "LOOKUP_UPDATE";
    public static final String ACTION_LOOKUP_TOGGLE_ACTIVE = "LOOKUP_TOGGLE_ACTIVE";
    public static final String ACTION_LOOKUP_DELETE = "LOOKUP_DELETE";
    public static final String ACTION_SCORING_WEIGHTS_UPDATE = "SCORING_WEIGHTS_UPDATE";
    public static final String ACTION_POST_REMOVE = "POST_REMOVE";
    public static final String ACTION_POST_KEEP = "POST_KEEP";

    public static final String TARGET_USER = "USER";
    public static final String TARGET_REPORT = "REPORT";
    public static final String TARGET_LOOKUP = "LOOKUP";
    public static final String TARGET_SCORING_WEIGHTS = "SCORING_WEIGHTS";
    public static final String TARGET_POST = "POST";

    private static final int MAX_DETAILS_LENGTH = 1000;
    private static final int MAX_IP_LENGTH = 64;

    @Inject
    CurrentVertxRequest currentVertxRequest;

    @Transactional(Transactional.TxType.MANDATORY)
    public void record(User admin, String action, String targetType, String targetId, String details) {
        AdminAuditLog log = new AdminAuditLog();
        log.id = UUIDv7Generator.generate();
        log.adminUser = admin;
        log.action = action;
        log.targetType = targetType;
        log.targetId = targetId;
        log.details = truncate(details, MAX_DETAILS_LENGTH);
        log.ipAddress = truncate(currentIpAddress(), MAX_IP_LENGTH);
        log.createdAt = Instant.now();
        log.persist();
    }

    /**
     * IP de quem fez a ação. Com {@code quarkus.http.proxy.proxy-address-forwarding}
     * ligado o Vert.x já devolve o IP original aqui; o header cru não é lido para
     * não aceitar um X-Forwarded-For forjado. Fora de requisição HTTP (testes,
     * jobs) não há IP.
     */
    private String currentIpAddress() {
        if (!Arc.container().requestContext().isActive()) {
            return null;
        }
        RoutingContext context = currentVertxRequest.getCurrent();
        if (context == null) {
            return null;
        }
        SocketAddress remote = context.request().remoteAddress();
        return remote == null ? null : remote.host();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
