-- ARQUIVO: src/main/resources/db/migration/V5__drop_screen_reader_optimized.sql
--
-- Remove a preferencia "Leitor de tela" (`screen_reader_optimized`) das
-- configuracoes de acessibilidade. A otimizacao para leitores de tela do
-- sistema (TalkBack/VoiceOver) nunca dependeu desse flag — as telas ja usam
-- `accessibilityRole`/`accessibilityLabel` sempre — e a narracao in-app passou
-- a ser controlada exclusivamente pela "Leitura por voz", que e uma preferencia
-- LOCAL do dispositivo (nao trafega por esta tabela).
--
-- `if exists` mantem a migracao idempotente em bases que ja tenham sido
-- ajustadas manualmente.
alter table user_accessibility_settings
    drop column if exists screen_reader_optimized;
