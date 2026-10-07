package com.videopost.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Representa uma tentativa ou estado de publicação de um vídeo em uma plataforma específica.
 */
public class Publication {

    private final String id;
    private final String videoId;
    private final Platform platform;
    private PublicationStatus status;
    private String externalId;
    private int attempts;
    private String errorMessage;
    private Instant publishedAt;
    private Instant lastAttemptAt;

    public Publication(String id,
                       String videoId,
                       Platform platform,
                       PublicationStatus status,
                       String externalId,
                       int attempts,
                       String errorMessage,
                       Instant publishedAt,
                       Instant lastAttemptAt) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.videoId = Objects.requireNonNull(videoId, "videoId cannot be null");
        this.platform = Objects.requireNonNull(platform, "platform cannot be null");
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.externalId = externalId;
        this.attempts = attempts;
        this.errorMessage = errorMessage;
        this.publishedAt = publishedAt;
        this.lastAttemptAt = lastAttemptAt;
    }

    public static Publication createPending(String videoId, Platform platform) {
        return new Publication(
                UUID.randomUUID().toString(),
                videoId,
                platform,
                PublicationStatus.PENDING,
                null,
                0,
                null,
                null,
                null
        );
    }

    public void markProcessing() {
        this.status = PublicationStatus.PROCESSING;
        this.lastAttemptAt = Instant.now();
        this.attempts++;
    }

    public void markPublished(String externalId, Instant publishedAt) {
        this.status = PublicationStatus.PUBLISHED;
        this.externalId = externalId;
        this.publishedAt = publishedAt != null ? publishedAt : Instant.now();
        this.errorMessage = null;
    }

    public void markFailed(String errorMessage, Instant attemptAt) {
        this.status = PublicationStatus.FAILED;
        this.errorMessage = errorMessage;
        this.lastAttemptAt = attemptAt != null ? attemptAt : Instant.now();
    }

    public boolean canRetry(int maxAttempts) {
        return this.status == PublicationStatus.FAILED && this.attempts < maxAttempts;
    }

    public boolean isPublished() {
        return this.status == PublicationStatus.PUBLISHED;
    }

    public boolean isFailed() {
        return this.status == PublicationStatus.FAILED;
    }

    public String getId() {
        return id;
    }

    public String getVideoId() {
        return videoId;
    }

    public Platform getPlatform() {
        return platform;
    }

    public PublicationStatus getStatus() {
        return status;
    }

    public void setStatus(PublicationStatus status) {
        this.status = status;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Instant getLastAttemptAt() {
        return lastAttemptAt;
    }

    public void setLastAttemptAt(Instant lastAttemptAt) {
        this.lastAttemptAt = lastAttemptAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Publication that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Publication{" +
                "id='" + id + '\'' +
                ", videoId='" + videoId + '\'' +
                ", platform=" + platform +
                ", status=" + status +
                ", attempts=" + attempts +
                '}';
    }
}
