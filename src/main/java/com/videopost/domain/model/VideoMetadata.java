package com.videopost.domain.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Metadados associados a um vídeo (legenda, hashtags).
 */
public record VideoMetadata(String caption, List<String> hashtags) {

    public VideoMetadata {
        hashtags = hashtags == null ? Collections.emptyList() : List.copyOf(hashtags);
    }

    public static VideoMetadata empty() {
        return new VideoMetadata("", Collections.emptyList());
    }

    public static VideoMetadata of(String caption, List<String> hashtags) {
        return new VideoMetadata(caption, hashtags);
    }

    public String formattedCaption() {
        String base = caption == null ? "" : caption.trim();
        if (hashtags.isEmpty()) {
            return base;
        }
        String tags = String.join(" ", hashtags);
        if (base.isEmpty()) {
            return tags;
        }
        return base + "\n\n" + tags;
    }
}
