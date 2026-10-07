package com.videopost.publisher.tiktok;

import java.util.Objects;

/**
 * Configurações e credenciais de autenticação para o TikTok Content Posting API v2.
 */
public record TikTokConfig(
        String accessToken,
        String privacyLevel,
        boolean disableDuet,
        boolean disableComment,
        boolean disableStitch
) {
    public TikTokConfig {
        privacyLevel = (privacyLevel == null || privacyLevel.isBlank()) ? "PUBLIC_TO_EVERYONE" : privacyLevel;
    }

    public static TikTokConfig ofToken(String accessToken) {
        return new TikTokConfig(accessToken, "PUBLIC_TO_EVERYONE", false, false, false);
    }

    public boolean isValid() {
        return accessToken != null && !accessToken.isBlank();
    }
}
