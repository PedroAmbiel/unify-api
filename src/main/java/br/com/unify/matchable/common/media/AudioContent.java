package br.com.unify.matchable.common.media;

/** Bytes de um áudio armazenado + o content-type com que foi gravado. */
public record AudioContent(byte[] bytes, String contentType) {
}
