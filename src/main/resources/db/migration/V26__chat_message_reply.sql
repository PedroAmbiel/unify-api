-- ============================================================================
-- Chat: responder a uma mensagem específica (citação, estilo WhatsApp)
--
--  fk_reply_to_message : mensagem citada, sempre da MESMA conversa (garantido
--                        no serviço). Nula = mensagem solta. A citação é montada
--                        na leitura a partir da mensagem original, então edição
--                        e exclusão lógica da original aparecem na citação.
--                        ON DELETE SET NULL: exclusão física (cascata da
--                        conversa) nunca é barrada pela citação.
-- ============================================================================

alter table chat_messages
    add column if not exists fk_reply_to_message uuid;

alter table chat_messages
    drop constraint if exists fk_chat_messages_reply_to_message;

alter table chat_messages
    add constraint fk_chat_messages_reply_to_message
        foreign key (fk_reply_to_message) references chat_messages (id) on delete set null;

create index if not exists idx_chat_messages_reply_to_message
    on chat_messages (fk_reply_to_message)
    where fk_reply_to_message is not null;
