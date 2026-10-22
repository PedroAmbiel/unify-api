package br.com.unify.matchable.common.exceptions;

/**
 * Uso da API por conta banida (semana 06). Sempre vira 403
 * {@code USER_DISABLED} (4003) com o motivo do banimento na mensagem — o
 * cliente usa o código para encerrar a sessão e mostrar "Conta suspensa".
 */
public class UserBannedException extends RuntimeException {

    private final String banReason;

    public UserBannedException(String banReason) {
        super("Conta suspensa");
        this.banReason = banReason;
    }

    public String getBanReason() {
        return banReason;
    }
}
