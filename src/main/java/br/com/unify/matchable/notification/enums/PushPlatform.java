package br.com.unify.matchable.notification.enums;

import java.util.Locale;

/** Plataforma do aparelho que registrou o token de push. */
public enum PushPlatform {
    ANDROID,
    IOS;

    /** Aceita "android"/"IOS"/" Android "; {@code null} quando não reconhece. */
    public static PushPlatform parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
