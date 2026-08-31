package br.com.unify.matchable.chat.enums;

public enum ChatMessageType {

    TEXT,
    IMAGE,
    AUDIO,
    /**
     * Reservado para a Fase 2. O enum já existe para que a migração não precise
     * alterar o schema, mas o ChatResource/ChatMediaValidator rejeitam uploads
     * deste tipo com 400.
     */
    VIDEO;

    public boolean isMedia() {
        return this != TEXT;
    }
}
