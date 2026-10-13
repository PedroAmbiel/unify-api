package br.com.unify.matchable.notification.push;

/** Resultado por token. {@link Status#INVALID_TOKEN} = apagar o token do banco. */
public record PushDeliveryResult(String token, Status status, String detail) {

    public enum Status {
        SENT,
        /** App desinstalado, token expirado/malformado ou de outro projeto Firebase. */
        INVALID_TOKEN,
        /** Falha transitória (rede, cota, 5xx): o token continua válido. */
        FAILED
    }

    public static PushDeliveryResult sent(String token) {
        return new PushDeliveryResult(token, Status.SENT, null);
    }

    public static PushDeliveryResult invalidToken(String token, String detail) {
        return new PushDeliveryResult(token, Status.INVALID_TOKEN, detail);
    }

    public static PushDeliveryResult failed(String token, String detail) {
        return new PushDeliveryResult(token, Status.FAILED, detail);
    }
}
