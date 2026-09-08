-- ============================================================================
-- Chat: status de entrega em três estados, edição e exclusão lógica
--
--  delivered_at : o destinatário buscou a mensagem (lista de conversas ou
--                 conversa aberta). Nulo = só enviada.
--  read_at      : já existia — o destinatário abriu a conversa.
--  edited_at    : o remetente alterou o texto (a mensagem mostra "editada").
--  deleted_at   : exclusão LÓGICA. A linha fica no histórico, mas o conteúdo
--                 (texto e mídia) é removido; o cliente mostra "Mensagem apagada".
--  updated_at   : cursor do polling incremental. Antes o `since` comparava com
--                 created_at e o remetente nunca via o status mudar nem uma
--                 edição/exclusão chegar; agora qualquer mudança move o cursor.
-- ============================================================================

alter table chat_messages
    add column if not exists delivered_at timestamp(6) with time zone,
    add column if not exists edited_at    timestamp(6) with time zone,
    add column if not exists deleted_at   timestamp(6) with time zone,
    add column if not exists updated_at   timestamp(6) with time zone;

update chat_messages
   set updated_at = coalesce(read_at, created_at)
 where updated_at is null;

alter table chat_messages
    alter column updated_at set not null;

-- Mensagens já lidas antes desta migração contam como entregues.
update chat_messages
   set delivered_at = read_at
 where delivered_at is null
   and read_at is not null;

-- A exclusão lógica zera texto e mídia: a regra de payload só vale para
-- mensagens vivas.
alter table chat_messages
    drop constraint if exists ck_chat_messages_payload;

alter table chat_messages
    add constraint ck_chat_messages_payload check (
        deleted_at is not null
        or
        (type = 'TEXT'  and body is not null and media_oid is null)
        or
        (type <> 'TEXT' and media_oid is not null and media_content_type is not null)
    );

create index if not exists idx_chat_messages_conversation_updated_at
    on chat_messages (fk_conversation, updated_at);

-- Índice parcial de não lidas passa a ignorar mensagens apagadas.
drop index if exists idx_chat_messages_unread;

create index if not exists idx_chat_messages_unread
    on chat_messages (fk_conversation, fk_sender_user_profile)
    where read_at is null and deleted_at is null;

-- ============================================================================
-- Ao apagar a mensagem a coluna media_oid vira nula. Sem isto o large object
-- ficaria órfão em pg_largeobject (o trigger de DELETE não cobre UPDATE).
-- ============================================================================
create or replace function unlink_chat_message_large_object_on_update()
returns trigger as $$
begin
    if old.media_oid is not null
       and (new.media_oid is null or new.media_oid <> old.media_oid) then
        perform lo_unlink(old.media_oid);
    end if;
    return new;
exception
    when undefined_object then
        return new;   -- o large object já foi removido
end;
$$ language plpgsql;

drop trigger if exists trg_chat_messages_update_large_object on chat_messages;

create trigger trg_chat_messages_update_large_object
    before update of media_oid on chat_messages
    for each row execute function unlink_chat_message_large_object_on_update();
