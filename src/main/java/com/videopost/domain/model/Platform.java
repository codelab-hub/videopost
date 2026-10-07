package com.videopost.domain.model;

import java.util.Arrays;
import java.util.Optional;

/**
 * Representa as plataformas de redes sociais suportadas ou planejadas.
 */
public enum Platform {
    TIKTOK("TikTok"),
    FACEBOOK("Facebook"),
    KWAI("Kwai"),
    INSTAGRAM("Instagram"),
    YOUTUBE_SHORTS("YouTube Shorts");

    private final String displayName;

    Platform(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static Optional<Platform> fromString(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String clean = value.trim().toUpperCase().replace("-", "_").replace(" ", "_");
        return Arrays.stream(values())
                .filter(p -> p.name().equalsIgnoreCase(clean) || p.displayName.equalsIgnoreCase(value.trim()))
                .findFirst();
    }
}
