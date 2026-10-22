package br.com.unify.matchable.admin.services;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.regex.Pattern;

import br.com.unify.matchable.admin.dto.AdminLookupItemRequest;
import br.com.unify.matchable.admin.dto.AdminLookupItemResponse;
import br.com.unify.matchable.admin.dto.LookupCatalogResponse;
import br.com.unify.matchable.admin.enums.LookupIconMode;
import br.com.unify.matchable.admin.enums.LookupType;
import br.com.unify.matchable.community.entity.CommunityCategory;
import br.com.unify.matchable.user.entity.Disability;
import br.com.unify.matchable.user.entity.LookupEntity;
import br.com.unify.matchable.user.entity.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class AdminLookupServiceImplementation implements AdminLookupService {

    /** Menor coluna de descrição entre os catálogos: community_categories.description varchar(100). */
    static final int MAX_DESCRIPTION_LENGTH = 100;
    static final int MAX_ICON_LENGTH = 60;
    /** Nome de ícone Ionicons: minúsculas, dígitos e hífen ("ear-outline"). */
    private static final Pattern ICON_PATTERN = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    static final String DESCRIPTION_REQUIRED_MESSAGE = "Informe a descrição do item";
    static final String DESCRIPTION_TOO_LONG_MESSAGE =
            "A descrição deve ter no máximo " + MAX_DESCRIPTION_LENGTH + " caracteres";
    static final String ICON_REQUIRED_MESSAGE = "Informe o ícone do item (nome do Ionicons, ex.: ear-outline)";
    static final String ICON_INVALID_MESSAGE =
            "Ícone inválido: use o nome do Ionicons, com letras minúsculas, números e hífen (ex.: ear-outline)";
    static final String PROTECTED_ITEM_MESSAGE =
            "Este item é usado por uma regra fixa do algoritmo de match e não pode ser desativado nem excluído";
    static final String LAST_ACTIVE_ITEM_MESSAGE = "O catálogo precisa manter ao menos um item ativo";

    @Inject
    EntityManager entityManager;

    @Inject
    AdminAuditService auditService;

    @Override
    @Transactional
    public List<LookupCatalogResponse> listCatalogs() {
        return Arrays.stream(LookupType.values())
                .map(type -> new LookupCatalogResponse(
                        type.code(),
                        type.label(),
                        type.iconMode(),
                        countItems(type, false),
                        countItems(type, true)))
                .toList();
    }

    @Override
    @Transactional
    public List<AdminLookupItemResponse> listItems(LookupType type, boolean includeInactive) {
        String jpql = "select e from " + entityName(type) + " e "
                + (includeInactive ? "" : "where e.active = true ")
                + "order by e.id";
        List<? extends LookupEntity> entities = entityManager.createQuery(jpql, type.entityClass()).getResultList();

        // Uma consulta agrupada por referência (no máximo 2 por catálogo), não uma por item.
        Map<Integer, Long> usage = usageByItem(type);
        List<AdminLookupItemResponse> items = new ArrayList<>(entities.size());
        for (LookupEntity entity : entities) {
            items.add(toResponse(type, entity, usage.getOrDefault(entity.id, 0L)));
        }
        return items;
    }

    @Override
    @Transactional
    public AdminLookupItemResponse create(User admin, LookupType type, AdminLookupItemRequest request) {
        String description = normalizeDescription(request == null ? null : request.description());
        ensureDescriptionAvailable(type, description, null);

        LookupEntity entity = newInstance(type);
        entity.id = nextId(type);
        entity.description = description;
        entity.active = true;

        String icon = normalizeIcon(request == null ? null : request.ionicIcon());
        if (type.iconMode() == LookupIconMode.REQUIRED && icon == null) {
            throw new IllegalArgumentException(ICON_REQUIRED_MESSAGE);
        }
        if (type.iconMode() != LookupIconMode.NONE) {
            setIcon(entity, icon);
        }

        entity.persist();

        auditService.record(admin, AdminAuditService.ACTION_LOOKUP_CREATE, AdminAuditService.TARGET_LOOKUP,
                targetId(type, entity.id), "descrição='" + description + "'" + iconDetail(entity));
        return toResponse(type, entity, 0L);
    }

    @Override
    @Transactional
    public AdminLookupItemResponse update(User admin, LookupType type, Integer id, AdminLookupItemRequest request) {
        LookupEntity entity = findRequired(type, id);
        String description = normalizeDescription(request == null ? null : request.description());
        ensureDescriptionAvailable(type, description, id);

        String previousDescription = entity.description;
        String previousIcon = getIcon(entity);
        entity.description = description;

        // Ícone nulo = mantém o atual; vazio = remove (só onde o ícone é opcional).
        String rawIcon = request == null ? null : request.ionicIcon();
        if (type.iconMode() != LookupIconMode.NONE && rawIcon != null) {
            String icon = normalizeIcon(rawIcon);
            if (icon == null && type.iconMode() == LookupIconMode.REQUIRED) {
                throw new IllegalArgumentException(ICON_REQUIRED_MESSAGE);
            }
            setIcon(entity, icon);
        }

        StringBuilder details = new StringBuilder("descrição: '").append(previousDescription)
                .append("' -> '").append(description).append("'");
        if (type.iconMode() != LookupIconMode.NONE && !Objects.equals(previousIcon, getIcon(entity))) {
            details.append(" | ícone: ").append(previousIcon).append(" -> ").append(getIcon(entity));
        }
        auditService.record(admin, AdminAuditService.ACTION_LOOKUP_UPDATE, AdminAuditService.TARGET_LOOKUP,
                targetId(type, id), details.toString());
        return toResponse(type, entity, countUsage(type, id));
    }

    @Override
    @Transactional
    public AdminLookupItemResponse setActive(User admin, LookupType type, Integer id, boolean active) {
        LookupEntity entity = findRequired(type, id);
        if (entity.active == active) {
            return toResponse(type, entity, countUsage(type, id));
        }

        if (!active && type.isProtectedId(id)) {
            throw new IllegalStateException(PROTECTED_ITEM_MESSAGE);
        }
        if (!active && countItems(type, true) <= 1) {
            throw new IllegalStateException(LAST_ACTIVE_ITEM_MESSAGE);
        }

        entity.active = active;

        auditService.record(admin, AdminAuditService.ACTION_LOOKUP_TOGGLE_ACTIVE, AdminAuditService.TARGET_LOOKUP,
                targetId(type, id), "ativo=" + active + " | descrição='" + entity.description + "'");
        return toResponse(type, entity, countUsage(type, id));
    }

    @Override
    @Transactional
    public void delete(User admin, LookupType type, Integer id) {
        LookupEntity entity = findRequired(type, id);
        if (type.isProtectedId(id)) {
            throw new IllegalStateException(PROTECTED_ITEM_MESSAGE);
        }

        long usage = countUsage(type, id);
        if (usage > 0) {
            throw new IllegalStateException(inUseMessage(usage));
        }
        if (entity.active && countItems(type, true) <= 1) {
            throw new IllegalStateException(LAST_ACTIVE_ITEM_MESSAGE);
        }

        String description = entity.description;
        entity.delete();
        try {
            // Corrida: alguém passou a usar o item entre a contagem e o delete.
            entityManager.flush();
        } catch (PersistenceException exception) {
            throw new IllegalStateException(inUseMessage(countUsage(type, id)), exception);
        }

        auditService.record(admin, AdminAuditService.ACTION_LOOKUP_DELETE, AdminAuditService.TARGET_LOOKUP,
                targetId(type, id), "descrição='" + description + "'");
    }

    // ------------------------------------------------------------------ apoio

    static String inUseMessage(long usage) {
        return "Não é possível excluir: " + usage + (usage == 1 ? " registro usa" : " registros usam")
                + " este item. Desative-o para tirá-lo das opções sem perder os dados existentes.";
    }

    private LookupEntity findRequired(LookupType type, Integer id) {
        LookupEntity entity = id == null ? null : entityManager.find(type.entityClass(), id);
        if (entity == null) {
            throw new NoSuchElementException("Item " + id + " não encontrado no catálogo " + type.label());
        }
        return entity;
    }

    private LookupEntity newInstance(LookupType type) {
        try {
            return type.entityClass().getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException
                 | NoSuchMethodException exception) {
            throw new IllegalStateException("Não foi possível instanciar o catálogo " + type.code(), exception);
        }
    }

    /** {@code sequenceName()} é constante do enum, nunca input: concatenação segura. */
    private Integer nextId(LookupType type) {
        Number next = (Number) entityManager
                .createNativeQuery("select nextval('" + type.sequenceName() + "')")
                .getSingleResult();
        return next.intValue();
    }

    private long countItems(LookupType type, boolean onlyActive) {
        return entityManager.createQuery(
                        "select count(e) from " + entityName(type) + " e" + (onlyActive ? " where e.active = true" : ""),
                        Long.class)
                .getSingleResult();
    }

    long countUsage(LookupType type, Integer id) {
        long total = 0;
        for (LookupType.UsageRef ref : type.usageRefs()) {
            Number count = (Number) entityManager
                    .createNativeQuery("select count(*) from " + ref.table() + " where " + ref.column() + " = :id")
                    .setParameter("id", id)
                    .getSingleResult();
            total += count.longValue();
        }
        return total;
    }

    private Map<Integer, Long> usageByItem(LookupType type) {
        Map<Integer, Long> usage = new HashMap<>();
        for (LookupType.UsageRef ref : type.usageRefs()) {
            @SuppressWarnings("unchecked")
            List<Object[]> rows = entityManager.createNativeQuery(
                            "select " + ref.column() + ", count(*) from " + ref.table()
                                    + " where " + ref.column() + " is not null group by " + ref.column())
                    .getResultList();
            for (Object[] row : rows) {
                usage.merge(((Number) row[0]).intValue(), ((Number) row[1]).longValue(), Long::sum);
            }
        }
        return usage;
    }

    /** Descrição é única por catálogo (constraint no banco); aqui sem diferenciar maiúsculas. */
    private void ensureDescriptionAvailable(LookupType type, String description, Integer ignoredId) {
        TypedQuery<Long> query = entityManager.createQuery(
                        "select count(e) from " + entityName(type) + " e "
                                + "where lower(e.description) = :description"
                                + (ignoredId == null ? "" : " and e.id <> :ignoredId"),
                        Long.class)
                .setParameter("description", description.toLowerCase(Locale.ROOT));
        if (ignoredId != null) {
            query.setParameter("ignoredId", ignoredId);
        }
        if (query.getSingleResult() > 0) {
            throw new IllegalStateException("Já existe um item \"" + description + "\" neste catálogo");
        }
    }

    private static String normalizeDescription(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(DESCRIPTION_REQUIRED_MESSAGE);
        }
        normalized = normalized.replaceAll("\\s+", " ");
        if (normalized.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException(DESCRIPTION_TOO_LONG_MESSAGE);
        }
        return normalized;
    }

    private static String normalizeIcon(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() > MAX_ICON_LENGTH || !ICON_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(ICON_INVALID_MESSAGE);
        }
        return normalized;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String getIcon(LookupEntity entity) {
        if (entity instanceof Disability disability) {
            return disability.ionicIcon;
        }
        if (entity instanceof CommunityCategory category) {
            return category.ionicIcon;
        }
        return null;
    }

    private static void setIcon(LookupEntity entity, String icon) {
        if (entity instanceof Disability disability) {
            disability.ionicIcon = icon;
        } else if (entity instanceof CommunityCategory category) {
            category.ionicIcon = icon;
        }
    }

    private static String iconDetail(LookupEntity entity) {
        String icon = getIcon(entity);
        return icon == null ? "" : " | ícone=" + icon;
    }

    private static String entityName(LookupType type) {
        return type.entityClass().getSimpleName();
    }

    private static String targetId(LookupType type, Integer id) {
        return type.code() + ":" + id;
    }

    private static AdminLookupItemResponse toResponse(LookupType type, LookupEntity entity, long usageCount) {
        return new AdminLookupItemResponse(
                entity.id,
                entity.description,
                entity.active,
                getIcon(entity),
                usageCount,
                type.isProtectedId(entity.id));
    }
}
