package br.com.unify.matchable.chat.services;

import java.time.Instant;
import java.util.UUID;

import br.com.unify.matchable.chat.dto.ChatMediaPayload;
import br.com.unify.matchable.chat.dto.ChatMessagePageResponse;
import br.com.unify.matchable.chat.dto.ChatMessageResponse;
import br.com.unify.matchable.chat.dto.ChatReadResponse;
import br.com.unify.matchable.chat.dto.ConversationListResponse;
import br.com.unify.matchable.chat.dto.ConversationResponse;
import br.com.unify.matchable.user.entity.User;

public interface ChatService {

    /** Lista as conversas do usuário, com última mensagem e contagem de não lidas. */
    ConversationListResponse listConversations(User user);

    /** Abre (ou devolve, se já existir) a conversa de um match mútuo. */
    ConversationResponse openConversation(User user, UUID matchId);

    /**
     * Página de mensagens, da mais recente para a mais antiga.
     *
     * @param since quando informado, devolve apenas mensagens criadas DEPOIS desse instante
     *              (modo polling incremental — ignora page/size e devolve no máximo 200).
     */
    ChatMessagePageResponse getMessages(User user, UUID conversationId, Integer page, Integer size, Instant since);

    ChatMessageResponse sendTextMessage(User user, UUID conversationId, String body);

    ChatMessageResponse sendMediaMessage(User user, UUID conversationId, ChatMediaPayload payload, String caption);

    /** Bytes + content-type da mídia de uma mensagem, validando participação na conversa. */
    ChatMediaPayload getMessageMedia(User user, UUID messageId);

    ChatReadResponse markConversationAsRead(User user, UUID conversationId);

    /** Edita o texto de uma mensagem TEXT enviada pelo próprio usuário. */
    ChatMessageResponse editMessage(User user, UUID conversationId, UUID messageId, String body);

    /**
     * Exclusão lógica de uma mensagem enviada pelo próprio usuário: a linha permanece
     * no histórico, mas texto e mídia são removidos. Idempotente.
     */
    ChatMessageResponse deleteMessage(User user, UUID conversationId, UUID messageId);
}
