package br.com.unify.matchable.admin.dto.dashboard;

/**
 * Fatia de uma distribuição (categoria, faixa, situação).
 *
 * @param label     texto vindo do banco (nome do catálogo) ou nulo quando o
 *                  backoffice rotula pela {@code key}.
 * @param secondary valor complementar opcional (ex.: membros da categoria).
 */
public record DashboardSlice(String key, String label, long value, Long secondary) {

    public static DashboardSlice of(String key, long value) {
        return new DashboardSlice(key, null, value, null);
    }
}
