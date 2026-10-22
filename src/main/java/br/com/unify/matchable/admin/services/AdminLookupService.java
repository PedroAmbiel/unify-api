package br.com.unify.matchable.admin.services;

import java.util.List;

import br.com.unify.matchable.admin.dto.AdminLookupItemRequest;
import br.com.unify.matchable.admin.dto.AdminLookupItemResponse;
import br.com.unify.matchable.admin.dto.LookupCatalogResponse;
import br.com.unify.matchable.admin.enums.LookupType;
import br.com.unify.matchable.user.entity.User;

/**
 * CRUD genérico dos catálogos de seleção (semana 06): um serviço e um resource
 * para os 12 catálogos, guiados por {@link LookupType}.
 *
 * Erros seguem o mapper global: {@code IllegalArgumentException} = 400,
 * {@code NoSuchElementException} = 404, {@code IllegalStateException} = 409.
 */
public interface AdminLookupService {

    List<LookupCatalogResponse> listCatalogs();

    List<AdminLookupItemResponse> listItems(LookupType type, boolean includeInactive);

    AdminLookupItemResponse create(User admin, LookupType type, AdminLookupItemRequest request);

    AdminLookupItemResponse update(User admin, LookupType type, Integer id, AdminLookupItemRequest request);

    /** Soft delete: inativo some das opções e continua válido onde já é usado. */
    AdminLookupItemResponse setActive(User admin, LookupType type, Integer id, boolean active);

    /** Exclusão física, só para item sem nenhuma referência. */
    void delete(User admin, LookupType type, Integer id);
}
