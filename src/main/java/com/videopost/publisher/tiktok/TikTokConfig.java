package com.videopost.publisher.tiktok;

/**
 * Configurações e credenciais de autenticação para o TikTok Content Posting API v2.
 */
public record TikTokConfig(
        String accessToken,
        String privacyLevel,
        boolean disableDuet,
        boolean disableComment,
        boolean disableStitch,
        String refreshToken,
        String clientKey,
        String clientSecret
) {
    public TikTokConfig(String accessToken, String privacyLevel, boolean disableDuet, boolean disableComment, boolean disableStitch) {
        this(accessToken, privacyLevel, disableDuet, disableComment, disableStitch, null, null, null);
    }

    public TikTokConfig {
        accessToken = (accessToken != null) ? accessToken.replaceAll("\\s+", "").trim() : null;
        privacyLevel = (privacyLevel == null || privacyLevel.isBlank()) ? "PUBLIC_TO_EVERYONE" : privacyLevel;
        refreshToken = (refreshToken != null) ? refreshToken.trim() : null;
        clientKey = (clientKey != null) ? clientKey.trim() : null;
        clientSecret = (clientSecret != null) ? clientSecret.trim() : null;
    }

    public static TikTokConfig ofToken(String accessToken) {
        return new TikTokConfig(accessToken, "PUBLIC_TO_EVERYONE", false, false, false, null, null, null);
    }

    public boolean isValid() {
        return (accessToken != null && !accessToken.isBlank()) || hasRefreshCredentials();
    }

    public boolean hasRefreshCredentials() {
        return refreshToken != null && !refreshToken.isBlank()
                && clientKey != null && !clientKey.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }
}
