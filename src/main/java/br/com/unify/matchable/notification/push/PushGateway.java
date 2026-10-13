package br.com.unify.matchable.notification.push;

import java.util.List;

/**
 * Provedor de push. Hoje só existe {@link FcmPushGateway} (Firebase Cloud
 * Messaging HTTP v1); a interface existe para os testes trocarem a entrega
 * real por um stub ({@code QuarkusMock}) sem rede.
 */
public interface PushGateway {

    /** {@code false} quando falta configuração (ex.: arquivo da conta de serviço). */
    boolean isEnabled();

    /**
     * Um resultado por mensagem, na mesma ordem. Nunca lança: falha de rede
     * vira {@link PushDeliveryResult.Status#FAILED}.
     */
    List<PushDeliveryResult> send(List<PushMessage> messages);
}
