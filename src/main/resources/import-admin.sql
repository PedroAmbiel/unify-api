-- Unify API - conta ADMIN inicial do backoffice (semana 06). Carregado APENAS
-- no perfil %dev (`%dev.quarkus.hibernate-orm.sql-load-script`). NUNCA em
-- prod/homolog: o DemoSeedGuard aborta o boot se esta conta aparecer la.
--
-- O schema de dev e recriado a cada boot, entao a conta volta sempre que os
-- dois inserts abaixo estiverem ativos. Para subir a API SEM nenhum
-- administrador, comente os dois inserts e reinicie o quarkus:dev.
--
--   e-mail : admin@unify.dev
--   senha  : Admin@123        (bcrypt cost 10, hash abaixo)
--   papel  : ADMIN            (JWT com groups = ["user", "admin"])
--
-- Entre com ela no backoffice web (unify-backoffice, http://localhost:5173).
-- A conta tambem usa o app normalmente (ADMIN acumula o papel USER); o perfil
-- vazio abaixo faz o app pedir o onboarding no primeiro acesso.
--
-- Fora de dev nao existe conta admin semeada: cadastre a conta pelo app e
-- promova com ADMIN_BOOTSTRAP_EMAILS=<e-mail> (AdminBootstrapService) ou
-- com SQL direto: update users set role = 'ADMIN' where email = '<e-mail>';

insert into users(id, verified, last_updated_at, birthdate, email, last_name, name, password, role)
values ('019dd0ad-0000-4000-8000-000000000001', true, '2026-10-20T00:00:00Z', '1990-01-01',
        'admin@unify.dev', 'Unify', 'Administração',
        '$2a$10$4Ucz5nmKCo19EFHwdG1Rs.wj8uFV.jNNgq3K5Ex0Bs8uwyxv3WS3e', 'ADMIN');

insert into user_profiles(id, fk_user)
values ('019dd0ad-0001-4000-8000-000000000001', '019dd0ad-0000-4000-8000-000000000001');
