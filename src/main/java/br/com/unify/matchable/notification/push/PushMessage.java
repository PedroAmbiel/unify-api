package br.com.unify.matchable.notification.push;

import java.util.Map;

/**
 * Uma entrega para UM aparelho. {@code tag} identifica a notificação na
 * bandeja do Android: mesma tag substitui a anterior (mensagens da mesma
 * conversa não empilham).
 */
public record PushMessage(String token, String title, String body, Map<String, String> data, String tag) {
}
