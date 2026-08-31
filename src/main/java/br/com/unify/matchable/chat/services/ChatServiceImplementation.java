package br.com.unify.matchable.chat.services;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import br.com.unify.matchable.chat.dto.ChatMediaPayload;
import br.com.unify.matchable.chat.dto.ChatMessagePageResponse;
import br.com.unify.matchable.chat.dto.ChatMessagePreviewResponse;
import br.com.unify.matchable.chat.dto.ChatMessageResponse;
import br.com.unify.matchable.chat.dto.ChatReadResponse;
import br.com.unify.matchable.chat.dto.ConversationListResponse;
import br.com.unify.matchable.chat.dto.ConversationResponse;
import br.com.unify.matchable.chat.dto.ConversationSummaryResponse;
import br.com.unify.matchable.chat.entity.ChatMessage;
import br.com.unify.matchable.chat.entity.Conversation;
import br.com.unify.matchable.chat.enums.ChatMessageType;
import br.com.unify.matchable.common.UUIDv7Generator;
import br.com.unify.matchable.common.image.OidImageService;
import br.com.unify.matchable.user.dto.UserProfileImageResponse;
import br.com.unify.matchable.user.entity.User;
import br.com.unify.matchable.user.entity.UserPossibleMatch;
import br.com.unify.matchable.user.entity.UserProfile;
import br.com.unify.matchable.user.entity.UserProfileImage;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ChatServiceImplementation implements ChatService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_PAGE_SIZE = 30;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_SINCE_RESULTS = 200;
    private static final int PREVIEW_MAX_LENGTH = 120;

    private static final String MEDIA_URL_PREFIX = "/chats/messages/";
    private static final String MEDIA_URL_SUFFIX = "/media";
    private static final String MATCH_IMAGE_URL_PREFIX = "/users/me/matches/images/";

    private static final String CONVERSATION_NOT_FOUND = "Conversa não encontrada";
    private static final String NOT_A_PARTICIPANT = "Você não participa desta conversa";
    private static final String MEDIA_NOT_FOUND = "Mídia não encontrada";

    private static final String INVALID_PAGE_MESSAGE =
            "O parâmetro 'page' deve ser maior ou igual a zero";
    private static final String INVALID_SIZE_MESSAGE =
            "O parâmetro 'size' deve estar entre 1 e " + MAX_PAGE_SIZE;

    @ConfigProperty(name = "unify.chat.message.max-length", defaultValue = "4000")
    int maxMessageLength;

    @Inject
    OidImageService oidImageService;

    // ================= listagem =================

    @Override
    public ConversationListResponse listConversations(User user) {
        UserProfile currentProfile = requireProfile(user);

        List<Conversation> conversations = Conversation.listForParticipant(currentProfile);
        List<ConversationSummaryResponse> summaries = new ArrayList<>(conversations.size());
        long totalUnread = 0;

        for (Conversation conversation : conversations) {
            ChatMessage lastMessage = ChatMessage.findLastOfConversation(conversation);
            long unread = ChatMessage.countUnreadFor(conversation, currentProfile);
            totalUnread += unread;

            UserProfile otherProfile = conversation.otherParticipant(currentProfile);
            summaries.add(new ConversationSummaryResponse(
                    conversation.id,
                    conversation.possibleMatch == null ? null : conversation.possibleMatch.id,
                    otherProfile == null || otherProfile.user == null ? null : otherProfile.user.id,
                    otherProfile == null ? null : otherProfile.id,
                    buildFullName(otherProfile),
                    buildProfilePhoto(otherProfile),
                    toPreview(lastMessage, currentProfile),
                    unread,
                    conversation.lastMessageAt
            ));
        }

        // Conversas sem mensagem alguma vão para o fim da lista.
        summaries.sort(Comparator.comparing(
                ConversationSummaryResponse::lastMessageAt,
                Comparator.nullsLast(Comparator.reverseOrder())
        ));

        return new ConversationListResponse(summaries, totalUnread, Instant.now());
    }

    // ================= abertura =================

    @Override
    public ConversationResponse openConversation(User user, UUID matchId) {
        if (matchId == null) {
            throw new IllegalArgumentException("O identificador do match é obrigatório");
        }

        UserProfile currentProfile = requireProfile(user);
        UserPossibleMatch match = UserPossibleMatch.findById(matchId);

        if (match == null) {
            throw new NoSuchElementException("Match não encontrado");
        }

        // 1) O usuário precisa participar do match.
        boolean isStarter = match.starterProfile != null
                && Objects.equals(match.starterProfile.id, currentProfile.id);
        boolean isPending = match.pendingProfile != null
                && Objects.equals(match.pendingProfile.id, currentProfile.id);
        if (!isStarter && !isPending) {
            throw new SecurityException("Você não participa deste match");
        }

        // 2) O match precisa ser MÚTUO. Sem isso não existe conversa.
        if (!match.starterAccepted || !Boolean.TRUE.equals(match.pendingAccepted)) {
            throw new IllegalStateException(
                    "A conversa só fica disponível depois que as duas pessoas se conectam");
        }

        Conversation conversation = Conversation.findByPossibleMatch(match);
        if (conversation == null) {
            conversation = createConversation(match);
        }

        return toConversationResponse(conversation, currentProfile);
    }

    /**
     * Ordena os participantes por UUID para tornar a linha determinística
     * (participantOne é sempre o de menor id).
     */
    private Conversation createConversation(UserPossibleMatch match) {
        UserProfile first = match.starterProfile;
        UserProfile second = match.pendingProfile;

        if (first.id.compareTo(second.id) > 0) {
            UserProfile swap = first;
            first = second;
            second = swap;
        }

        Conversation conversation = new Conversation();
        conversation.id = UUIDv7Generator.generate();
        conversation.possibleMatch = match;
        conversation.participantOne = first;
        conversation.participantTwo = second;
        conversation.createdAt = Instant.now();
        conversation.lastMessageAt = null;
        conversation.persist();

        return conversation;
    }

    // ================= mensagens =================

    @Override
    public ChatMessagePageResponse getMessages(
            User user, UUID conversationId, Integer page, Integer size, Instant since) {

        UserProfile currentProfile = requireProfile(user);
        Conversation conversation = requireParticipation(conversationId, currentProfile);

        long unread = ChatMessage.countUnreadFor(conversation, currentProfile);

        // Modo polling incremental: ignora paginação, devolve o que chegou depois de `since`.
        if (since != null) {
            List<ChatMessage> newMessages = ChatMessage.listSince(conversation, since);
            if (newMessages.size() > MAX_SINCE_RESULTS) {
                newMessages = newMessages.subList(newMessages.size() - MAX_SINCE_RESULTS, newMessages.size());
            }
            List<ChatMessageResponse> responses = newMessages.stream()
                    .map(message -> toMessageResponse(message, currentProfile))
                    .toList();
            return new ChatMessagePageResponse(
                    responses, 0, responses.size(), responses.size(), 1, false, unread, Instant.now());
        }

        int resolvedPage = validatePage(page);
        int resolvedSize = validateSize(size);

        PanacheQuery<ChatMessage> query = ChatMessage.findByConversationNewestFirst(conversation);
        long totalElements = query.count();
        List<ChatMessageResponse> messages = query.page(Page.of(resolvedPage, resolvedSize))
                .<ChatMessage>list()
                .stream()
                .map(message -> toMessageResponse(message, currentProfile))
                .toList();

        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / resolvedSize);

        return new ChatMessagePageResponse(
                messages,
                resolvedPage,
                resolvedSize,
                totalElements,
                totalPages,
                resolvedPage + 1 < totalPages,
                unread,
                Instant.now()
        );
    }

    @Override
    public ChatMessageResponse sendTextMessage(User user, UUID conversationId, String body) {
        UserProfile currentProfile = requireProfile(user);
        Conversation conversation = requireParticipation(conversationId, currentProfile);

        String normalizedBody = body == null ? "" : body.trim();
        if (normalizedBody.isEmpty()) {
            throw new IllegalArgumentException("A mensagem não pode estar vazia");
        }
        if (normalizedBody.length() > maxMessageLength) {
            throw new IllegalArgumentException(
                    "A mensagem excede o limite de " + maxMessageLength + " caracteres");
        }

        ChatMessage message = newMessage(conversation, currentProfile, ChatMessageType.TEXT);
        message.body = normalizedBody;
        message.persist();

        conversation.lastMessageAt = message.createdAt;

        return toMessageResponse(message, currentProfile);
    }

    @Override
    public ChatMessageResponse sendMediaMessage(
            User user, UUID conversationId, ChatMediaPayload payload, String caption) {

        UserProfile currentProfile = requireProfile(user);
        Conversation conversation = requireParticipation(conversationId, currentProfile);

        if (payload == null || payload.bytes() == null || payload.bytes().length == 0) {
            throw new IllegalArgumentException("Nenhuma mídia foi enviada");
        }

        ChatMessage message = newMessage(conversation, currentProfile, payload.type());
        message.body = caption == null || caption.isBlank() ? null : caption.trim();
        message.mediaOid = oidImageService.toOidBlob(payload.bytes());
        message.mediaContentType = payload.contentType();
        message.mediaSizeBytes = (long) payload.bytes().length;
        message.mediaDurationSeconds = payload.durationSeconds();
        message.persist();

        conversation.lastMessageAt = message.createdAt;

        return toMessageResponse(message, currentProfile);
    }

    @Override
    public ChatMediaPayload getMessageMedia(User user, UUID messageId) {
        UserProfile currentProfile = requireProfile(user);

        if (messageId == null) {
            throw new IllegalArgumentException("O identificador da mensagem é obrigatório");
        }

        ChatMessage message = ChatMessage.findById(messageId);
        if (message == null || message.mediaOid == null) {
            throw new NoSuchElementException(MEDIA_NOT_FOUND);
        }

        // Autorização: só participantes da conversa leem a mídia.
        if (message.conversation == null || !message.conversation.hasParticipant(currentProfile)) {
            // 404 e não 403: não revelar a existência da mensagem para quem não participa.
            throw new NoSuchElementException(MEDIA_NOT_FOUND);
        }

        byte[] bytes = oidImageService.readOidBlob(message.mediaOid);
        return new ChatMediaPayload(
                message.type, bytes, message.mediaContentType, message.mediaDurationSeconds);
    }

    @Override
    public ChatReadResponse markConversationAsRead(User user, UUID conversationId) {
        UserProfile currentProfile = requireProfile(user);
        Conversation conversation = requireParticipation(conversationId, currentProfile);

        Instant readAt = Instant.now();
        long marked = ChatMessage.markAsRead(conversation, currentProfile, readAt);

        return new ChatReadResponse(conversation.id, marked, readAt);
    }

    // ================= apoio =================

    private ChatMessage newMessage(Conversation conversation, UserProfile sender, ChatMessageType type) {
        ChatMessage message = new ChatMessage();
        message.id = UUIDv7Generator.generate();
        message.conversation = conversation;
        message.sender = sender;
        message.type = type;
        message.createdAt = Instant.now();
        message.readAt = null;
        return message;
    }

    /** Ponto ÚNICO de verificação de autorização de conversa. Toda operação passa por aqui. */
    private Conversation requireParticipation(UUID conversationId, UserProfile currentProfile) {
        if (conversationId == null) {
            throw new IllegalArgumentException("O identificador da conversa é obrigatório");
        }

        Conversation conversation = Conversation.findById(conversationId);
        if (conversation == null) {
            throw new NoSuchElementException(CONVERSATION_NOT_FOUND);
        }
        if (!conversation.hasParticipant(currentProfile)) {
            throw new SecurityException(NOT_A_PARTICIPANT);
        }
        return conversation;
    }

    private UserProfile requireProfile(User user) {
        UserProfile profile = UserProfile.findByUser(user);
        if (profile == null) {
            throw new IllegalArgumentException("Complete o perfil antes de usar o chat");
        }
        return profile;
    }

    private ChatMessagePreviewResponse toPreview(ChatMessage message, UserProfile currentProfile) {
        if (message == null) {
            return null;
        }

        String preview = switch (message.type) {
            case TEXT -> truncate(message.body, PREVIEW_MAX_LENGTH);
            case IMAGE -> "Imagem";
            case AUDIO -> message.mediaDurationSeconds == null
                    ? "Mensagem de áudio"
                    : "Áudio de " + message.mediaDurationSeconds + " segundos";
            case VIDEO -> "Vídeo";
        };

        return new ChatMessagePreviewResponse(
                message.id,
                message.type,
                preview,
                message.sender != null && Objects.equals(message.sender.id, currentProfile.id),
                message.createdAt
        );
    }

    private ChatMessageResponse toMessageResponse(ChatMessage message, UserProfile currentProfile) {
        return new ChatMessageResponse(
                message.id,
                message.conversation == null ? null : message.conversation.id,
                message.sender == null ? null : message.sender.id,
                buildFullName(message.sender),
                message.sender != null && Objects.equals(message.sender.id, currentProfile.id),
                message.type,
                message.body,
                message.type.isMedia() ? MEDIA_URL_PREFIX + message.id + MEDIA_URL_SUFFIX : null,
                message.mediaContentType,
                message.mediaSizeBytes,
                message.mediaDurationSeconds,
                message.createdAt,
                message.readAt
        );
    }

    private ConversationResponse toConversationResponse(Conversation conversation, UserProfile currentProfile) {
        UserProfile otherProfile = conversation.otherParticipant(currentProfile);
        User otherUser = otherProfile == null ? null : otherProfile.user;

        return new ConversationResponse(
                conversation.id,
                conversation.possibleMatch == null ? null : conversation.possibleMatch.id,
                otherUser == null ? null : otherUser.id,
                otherProfile == null ? null : otherProfile.id,
                buildFullName(otherProfile),
                calculateAge(otherUser),
                buildProfilePhoto(otherProfile),
                conversation.createdAt,
                conversation.lastMessageAt
        );
    }

    private UserProfileImageResponse buildProfilePhoto(UserProfile profile) {
        if (profile == null) {
            return null;
        }
        UserProfileImage picture = UserProfileImage.findActiveProfilePicture(profile);
        if (picture == null) {
            return null;
        }
        // Reaproveita o endpoint já autorizado por match: /users/me/matches/images/{id}
        return new UserProfileImageResponse(
                picture.id,
                picture.profilePicture,
                picture.active,
                MATCH_IMAGE_URL_PREFIX + picture.id
        );
    }

    private String buildFullName(UserProfile profile) {
        if (profile == null) {
            return null;
        }
        return buildFullName(profile.user);
    }

    private String buildFullName(User user) {
        if (user == null) {
            return null;
        }

        String firstName = user.name == null ? "" : user.name.trim();
        String lastName = user.lastName == null ? "" : user.lastName.trim();
        String fullName = (firstName + " " + lastName).trim();
        return fullName.isEmpty() ? null : fullName;
    }

    private Integer calculateAge(User user) {
        if (user == null || user.birthdate == null) {
            return null;
        }

        LocalDate today = LocalDate.now();
        if (user.birthdate.isAfter(today)) {
            return null;
        }

        return Period.between(user.birthdate, today).getYears();
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private int validatePage(Integer page) {
        int resolvedPage = page == null ? DEFAULT_PAGE : page;
        if (resolvedPage < 0) {
            throw new IllegalArgumentException(INVALID_PAGE_MESSAGE);
        }
        return resolvedPage;
    }

    private int validateSize(Integer size) {
        int resolvedSize = size == null ? DEFAULT_PAGE_SIZE : size;
        if (resolvedSize < 1 || resolvedSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(INVALID_SIZE_MESSAGE);
        }
        return resolvedSize;
    }
}
