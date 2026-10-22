package br.com.unify.matchable.admin.enums;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Métricas com série diária no dashboard (semana 07). Cada uma é uma contagem
 * de eventos por dia sobre uma coluna de instante. Tabela, coluna e filtro são
 * constantes de código (nunca entrada do usuário), por isso podem compor o SQL.
 *
 * <p>Posts criados contam todas as publicações (inclusive as apagadas depois ou
 * removidas pela moderação): a série mede atividade, e apagar um post não
 * desfaz o dia em que ele foi publicado.</p>
 */
public enum DashboardMetric {
    NEW_USERS("new_users", "users", "created_at", null),
    NEW_MUTUAL_MATCHES("new_mutual_matches", "user_possible_matches", "matched_at",
            "starter_accepted = true and pending_accepted = true"),
    MESSAGES_SENT("messages_sent", "chat_messages", "created_at", null),
    POSTS_CREATED("posts_created", "posts", "created_at", null),
    REPORTS_OPENED("reports_opened", "user_reports", "created_at", null);

    private final String code;
    private final String table;
    private final String instantColumn;
    private final String extraFilter;

    DashboardMetric(String code, String table, String instantColumn, String extraFilter) {
        this.code = code;
        this.table = table;
        this.instantColumn = instantColumn;
        this.extraFilter = extraFilter;
    }

    public String code() {
        return code;
    }

    public String table() {
        return table;
    }

    public String instantColumn() {
        return instantColumn;
    }

    /** Condição extra do WHERE (sem "and" inicial) ou {@code null}. */
    public String extraFilter() {
        return extraFilter;
    }

    public static Optional<DashboardMetric> fromCode(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(metric -> metric.code.equals(normalized)).findFirst();
    }

    /** "new_users, new_mutual_matches, ... ou reports_opened" para mensagens de erro. */
    public static String describeCodes() {
        String all = Arrays.stream(values()).map(DashboardMetric::code).collect(Collectors.joining(", "));
        int lastComma = all.lastIndexOf(", ");
        return all.substring(0, lastComma) + " ou " + all.substring(lastComma + 2);
    }
}
