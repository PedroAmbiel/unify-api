package br.com.unify.matchable.notification.enums;

/**
 * Tipos de notificação. O significado de {@code referenceId}/{@code contextId}
 * em cada tipo é contrato com o app (deep link em
 * {@code src/utils/notificationNavigation.ts}):
 *
 * <ul>
 *   <li>{@link #NEW_MATCH}: referência = {@code user_possible_matches.id};
 *       contexto = {@code userProfileId} da outra pessoa.</li>
 *   <li>{@link #NEW_MESSAGE}: referência = conversa; contexto =
 *       {@code userProfileId} de quem mandou.</li>
 *   <li>{@link #NEW_FOLLOWER}, {@link #FOLLOW_REQUEST}: referência =
 *       {@code userProfileId} de quem seguiu/pediu.</li>
 *   <li>{@link #FOLLOW_REQUEST_ACCEPTED}: referência = {@code userProfileId}
 *       de quem aceitou.</li>
 *   <li>{@link #POST_LIKED}, {@link #POST_COMMENTED}: referência = post;
 *       contexto = comunidade (nulo em post pessoal).</li>
 *   <li>{@link #COMMUNITY_POST}: modelado, sem emissão nesta sprint (plano 05,
 *       4.8.6 — avisar todos os membros a cada post é ruído).</li>
 *   <li>{@link #REPORT_UPDATE}: referência = denúncia (emitido pela moderação,
 *       semana 06).</li>
 * </ul>
 */
public enum NotificationType {
    NEW_MATCH,
    NEW_MESSAGE,
    NEW_FOLLOWER,
    FOLLOW_REQUEST,
    FOLLOW_REQUEST_ACCEPTED,
    POST_LIKED,
    POST_COMMENTED,
    COMMUNITY_POST,
    REPORT_UPDATE
}
