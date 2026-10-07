package com.videopost.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Entidade raiz que representa um vídeo a ser publicado.
 */
public class Video {

    private final String id;
    private final String filename;
    private final String path;
    private VideoStatus status;
    private final Instant createdAt;
    private Instant scheduledAt;
    private Instant publishedAt;
    private String caption;
    private List<String> hashtags;

    public Video(String id,
                 String filename,
                 String path,
                 VideoStatus status,
                 Instant createdAt,
                 Instant scheduledAt,
                 Instant publishedAt,
                 String caption,
                 List<String> hashtags) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.filename = Objects.requireNonNull(filename, "filename cannot be null");
        this.path = Objects.requireNonNull(path, "path cannot be null");
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
        this.scheduledAt = scheduledAt;
        this.publishedAt = publishedAt;
        this.caption = caption;
        this.hashtags = hashtags == null ? new ArrayList<>() : new ArrayList<>(hashtags);
    }

    public static Video createNew(String filename, String path, VideoMetadata metadata) {
        return new Video(
                UUID.randomUUID().toString(),
                filename,
                path,
                VideoStatus.PENDING,
                Instant.now(),
                null,
                null,
                metadata != null ? metadata.caption() : null,
                metadata != null ? metadata.hashtags() : Collections.emptyList()
        );
    }

    public void markScheduled(Instant scheduledAt) {
        this.scheduledAt = scheduledAt;
        if (this.status == VideoStatus.PENDING) {
            this.status = VideoStatus.SCHEDULED;
        }
    }

    public void markProcessing() {
        this.status = VideoStatus.PROCESSING;
    }

    /**
     * Atualiza o status do vídeo com base no status agregado de suas publicações por plataforma.
     */
    public void evaluateStatusFromPublications(List<Publication> publications) {
        if (publications == null || publications.isEmpty()) {
            return;
        }

        long publishedCount = publications.stream()
                .filter(p -> p.getStatus() == PublicationStatus.PUBLISHED)
                .count();

        long failedCount = publications.stream()
                .filter(p -> p.getStatus() == PublicationStatus.FAILED)
                .count();

        long processingCount = publications.stream()
                .filter(p -> p.getStatus() == PublicationStatus.PROCESSING)
                .count();

        int total = publications.size();

        if (processingCount > 0) {
            this.status = VideoStatus.PROCESSING;
            return;
        }

        if (publishedCount == total) {
            this.status = VideoStatus.COMPLETED;
            if (this.publishedAt == null) {
                this.publishedAt = Instant.now();
            }
        } else if (failedCount == total) {
            this.status = VideoStatus.FAILED;
        } else if (publishedCount > 0) {
            this.status = VideoStatus.PARTIALLY_COMPLETED;
        }
    }

    public String getId() {
        return id;
    }

    public String getFilename() {
        return filename;
    }

    public String getPath() {
        return path;
    }

    public VideoStatus getStatus() {
        return status;
    }

    public void setStatus(VideoStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(Instant scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public List<String> getHashtags() {
        return Collections.unmodifiableList(hashtags);
    }

    public void setHashtags(List<String> hashtags) {
        this.hashtags = hashtags == null ? new ArrayList<>() : new ArrayList<>(hashtags);
    }

    public VideoMetadata toMetadata() {
        return new VideoMetadata(this.caption, this.hashtags);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Video video)) return false;
        return Objects.equals(id, video.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Video{" +
                "id='" + id + '\'' +
                ", filename='" + filename + '\'' +
                ", status=" + status +
                ", scheduledAt=" + scheduledAt +
                '}';
    }
}
