package com.videopost.domain.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Resultado do processo de publicação em uma plataforma.
 */
public record PublishResult(
        boolean success,
        String externalId,
        String errorMessage,
        boolean retryable
) {
    public static PublishResult success(String externalId) {
        Objects.requireNonNull(externalId, "externalId cannot be null on success");
        return new PublishResult(true, externalId, null, false);
    }

    public static PublishResult failure(String errorMessage, boolean retryable) {
        return new PublishResult(false, null, errorMessage, retryable);
    }

    public static PublishResult failure(String errorMessage) {
        return failure(errorMessage, true);
    }

    public Optional<String> optExternalId() {
        return Optional.ofNullable(externalId);
    }

    public Optional<String> optErrorMessage() {
        return Optional.ofNullable(errorMessage);
    }
}
