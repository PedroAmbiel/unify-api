-- ============================================================================
-- Semana 06 (A) — geração de ids para itens criados pelo backoffice.
--
-- Os catálogos têm id manual (seeds com ids fixos, referenciados por regra:
-- genders.id = 4 é "Prefiro não informar" no SQL de discovery). Itens novos
-- pegam o id de uma sequência por tabela, que começa acima do maior id já
-- semeado. `max(id) + 1` em Java colidiria com dois admins criando ao mesmo
-- tempo; a sequência não.
--
-- Precisa rodar DEPOIS dos seeds (V2/V4): por isso o número alto.
-- Em %dev/%test (Hibernate drop-and-create) as mesmas sequências são criadas
-- no fim do import.sql.
-- ============================================================================

do $$
declare
    t text;
    max_id bigint;
begin
    foreach t in array array[
        'genders', 'pronouns', 'disabilities', 'accessibility_needs', 'autonomy_levels',
        'communication_forms', 'lifestyle_types', 'love_languages', 'energy_levels',
        'interest_types', 'connection_types', 'community_categories'
    ]
    loop
        execute format('select coalesce(max(id), 0) from %I', t) into max_id;
        execute format('create sequence if not exists %I start with %s increment by 1', 'seq_' || t || '_id', max_id + 1);
        execute format('alter table %I alter column id set default nextval(%L)', t, 'seq_' || t || '_id');
        execute format('alter sequence %I owned by %I.id', 'seq_' || t || '_id', t);
    end loop;
end $$;
