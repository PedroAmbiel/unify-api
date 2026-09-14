package br.com.unify.matchable.moderation.enums;

public enum ReportReason {
    FAKE_PROFILE("Perfil falso"),
    HARASSMENT("Assédio ou perseguição"),
    INAPPROPRIATE_CONTENT("Conteúdo impróprio"),
    SCAM("Golpe ou fraude"),
    HATE_SPEECH("Discurso de ódio"),
    OTHER("Outro motivo");

    private final String description;

    ReportReason(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
