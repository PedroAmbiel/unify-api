-- ============================================================================
-- Semana 06 (A) — soft delete dos catálogos de seleção de perfil.
--
--  active : item inativo some das listas de opções (/users/me/profile/options
--           e /communities/categories), mas continua válido nos perfis e
--           comunidades que já o referenciam. Nenhuma FK quebra, nenhum dado
--           se perde. Todos os itens existentes continuam ativos.
-- ============================================================================

alter table genders              add column if not exists active boolean not null default true;
alter table pronouns             add column if not exists active boolean not null default true;
alter table disabilities         add column if not exists active boolean not null default true;
alter table accessibility_needs  add column if not exists active boolean not null default true;
alter table autonomy_levels      add column if not exists active boolean not null default true;
alter table communication_forms  add column if not exists active boolean not null default true;
alter table lifestyle_types      add column if not exists active boolean not null default true;
alter table love_languages       add column if not exists active boolean not null default true;
alter table energy_levels        add column if not exists active boolean not null default true;
alter table interest_types       add column if not exists active boolean not null default true;
alter table connection_types     add column if not exists active boolean not null default true;
alter table community_categories add column if not exists active boolean not null default true;
