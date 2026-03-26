package br.com.unify.matchable.common;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;

import java.util.UUID;

public final class UUIDv7Generator {

    private static final TimeBasedEpochGenerator GENERATOR = Generators.timeBasedEpochGenerator();

    private UUIDv7Generator() {
    }

    public static UUID generate() {
        return GENERATOR.generate();
    }
}
