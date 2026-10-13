-- ============================================================================
-- Semana 05 (plano 05, seção 5.9 item 4b) — preferência opt-in de tocar o
-- áudio de apresentação sozinho no Encontros.
--
-- Desligada por padrão (WCAG 1.4.2: som não começa sem a pessoa pedir). O app
-- só toca DEPOIS de o leitor in-app terminar de falar o perfil e nunca com
-- TalkBack/VoiceOver ativo; o backend só guarda a escolha.
-- ============================================================================

alter table user_accessibility_settings
    add column if not exists autoplay_presentation_audio boolean not null default false;
