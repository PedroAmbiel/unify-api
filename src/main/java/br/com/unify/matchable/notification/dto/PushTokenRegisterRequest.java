package br.com.unify.matchable.notification.dto;

/**
 * Registro do token de push do aparelho. {@code token} é o token NATIVO do
 * FCM ({@code Notifications.getDevicePushTokenAsync()} no app), não um
 * ExpoPushToken. {@code platform}: {@code ANDROID} ou {@code IOS}.
 */
public record PushTokenRegisterRequest(String token, String platform) {
}
