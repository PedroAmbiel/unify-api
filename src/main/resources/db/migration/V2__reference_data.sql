-- ARQUIVO: src/main/resources/db/migration/V2__reference_data.sql
--
-- Dados de referencia (catalogos). Necessarios em TODOS os ambientes.
-- Copiados literalmente de import.sql, com `on conflict (id) do nothing`
-- para permitir reexecucao segura sobre um banco que ja os possua.
--
-- Nenhum dado de demonstracao (usuario, perfil, comunidade) entra aqui:
-- esses vivem em import-dev.sql e so sao carregados no perfil %dev.
--
-- As tabelas de catalogo usam id atribuido explicitamente (sem sequence
-- nem identity no schema gerado), portanto nao ha setval a ajustar.

insert into genders(id, description)
values (1, 'Mulher'),
       (2, 'Homem'),
       (3, 'Não binário'),
       (4, 'Prefiro não informar')
on conflict (id) do nothing;

insert into pronouns(id, description)
values (1, 'Ela/Dela'),
       (2, 'Ele/Dele'),
       (3, 'Elu/Delu'),
       (4, 'Prefiro não informar')
on conflict (id) do nothing;

insert into disabilities(id, description, ionic_icon)
values (1, 'Física', 'walk-outline'),
       (2, 'Visual', 'eye-outline'),
       (3, 'Auditiva', 'ear-outline'),
       (4, 'Intelectual', 'accessibility-outline')
on conflict (id) do nothing;

insert into accessibility_needs(id, description)
values (1, 'Cadeira de rodas'),
       (2, 'Libras'),
       (3, 'Leitor de tela'),
       (4, 'Comunicação assistiva')
on conflict (id) do nothing;

insert into autonomy_levels(id, description)
values (1, 'Independente'),
       (2, 'Parcialmente independente'),
       (3, 'Precisa de apoio')
on conflict (id) do nothing;

insert into communication_forms(id, description)
values (1, 'Texto'),
       (2, 'Áudio'),
       (3, 'Vídeo'),
       (4, 'Libras'),
       (5, 'Comunicação assistiva')
on conflict (id) do nothing;

insert into lifestyle_types(id, description)
values (1, 'Caseiro'),
       (2, 'Social'),
       (3, 'Gosta de viajar'),
       (4, 'Atividades acessíveis')
on conflict (id) do nothing;

insert into love_languages(id, description)
values (1, 'Palavras de afirmação'),
       (2, 'Tempo de qualidade'),
       (3, 'Presentes'),
       (4, 'Atos de serviço'),
       (5, 'Toque físico')
on conflict (id) do nothing;

insert into energy_levels(id, description)
values (1, 'Baixa'),
       (2, 'Moderada'),
       (3, 'Alta')
on conflict (id) do nothing;

insert into interest_types(id, description)
values (1, 'Esportes adaptados'),
       (2, 'Cultura'),
       (3, 'Tecnologia'),
       (4, 'Jogos'),
       (5, 'Música'),
       (6, 'Filmes e séries')
on conflict (id) do nothing;

insert into connection_types(id, description)
values (1, 'Amizade'),
       (2, 'Relacionamento'),
       (3, 'Networking'),
       (4, 'Comunidade')
on conflict (id) do nothing;
