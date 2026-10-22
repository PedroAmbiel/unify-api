package br.com.unify.matchable.admin.services;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import br.com.unify.matchable.admin.dto.dashboard.DashboardKpi;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

/**
 * Apoio comum às consultas dos painéis por dimensão: SQL nativo agregado
 * (COUNT/SUM com FILTER para o período atual e o anterior numa passada só),
 * filtros opcionais montados como cláusulas com parâmetro nomeado (nunca
 * valor concatenado) e conversões das linhas.
 */
abstract class DashboardSqlSupport {

    @Inject
    EntityManager entityManager;

    /** Cláusulas extras do WHERE (cada uma com o próprio parâmetro) e os parâmetros da consulta. */
    static final class Sql {

        private final List<String> clauses = new ArrayList<>();
        private final Map<String, Object> params = new HashMap<>();

        Sql param(String name, Object value) {
            params.put(name, value);
            return this;
        }

        Sql and(String clause) {
            clauses.add(clause);
            return this;
        }

        Sql and(String clause, String name, Object value) {
            clauses.add(clause);
            params.put(name, value);
            return this;
        }

        /** " and a and b" (vazio sem cláusulas), para colar depois de um WHERE. */
        String where() {
            return clauses.isEmpty() ? "" : " and " + String.join(" and ", clauses);
        }

        Sql copy() {
            Sql copy = new Sql();
            copy.clauses.addAll(clauses);
            copy.params.putAll(params);
            return copy;
        }
    }

    @SuppressWarnings("unchecked")
    List<Object[]> rows(String sql, Sql bindings) {
        Query query = entityManager.createNativeQuery(sql);
        bindings.params.forEach((name, value) -> {
            if (sql.contains(":" + name)) {
                query.setParameter(name, value);
            }
        });
        List<?> result = query.getResultList();
        List<Object[]> rows = new ArrayList<>(result.size());
        for (Object item : result) {
            rows.add(item instanceof Object[] array ? array : new Object[] {item});
        }
        return rows;
    }

    Object[] row(String sql, Sql bindings) {
        List<Object[]> rows = rows(sql, bindings);
        return rows.isEmpty() ? new Object[0] : rows.get(0);
    }

    /** Linhas (grupo, valor) viram mapa para {@link DashboardWindow#series}. */
    static Map<LocalDate, Double> byBucket(List<Object[]> rows) {
        Map<LocalDate, Double> values = new HashMap<>();
        for (Object[] row : rows) {
            values.merge(AdminDashboardQueries.toLocalDate(row[0]), number(row[1]), Double::sum);
        }
        return values;
    }

    static long count(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    static double number(Object value) {
        return value == null ? 0d : ((Number) value).doubleValue();
    }

    static Double numberOrNull(Object value) {
        return value == null ? null : ((Number) value).doubleValue();
    }

    static Object at(Object[] row, int index) {
        return index < row.length ? row[index] : null;
    }

    /** Percentual 0..100; nulo sem denominador. */
    static Double percent(long numerator, long denominator) {
        return denominator <= 0 ? null : 100d * numerator / denominator;
    }

    static Double average(double total, long denominator) {
        return denominator <= 0 ? null : total / denominator;
    }

    static DashboardKpi countKpi(String key, long current, long previous) {
        return new DashboardKpi(key, (double) current, (double) previous, DashboardKpi.COUNT);
    }

    static DashboardKpi snapshotKpi(String key, long current) {
        return new DashboardKpi(key, (double) current, null, DashboardKpi.COUNT);
    }

    static String text(Object value) {
        return value == null ? null : value.toString();
    }
}
