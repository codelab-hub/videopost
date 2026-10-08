package com.videopost.publisher.youtube;

/**
 * Configuração e credenciais de acesso para a Google YouTube Data API v3.
 */
public record YouTubeConfig(
        String accessToken,
        String privacyStatus,
        String refreshToken,
        String clientId,
        String clientSecret
) {
    public YouTubeConfig(String accessToken, String privacyStatus) {
        this(accessToken, privacyStatus, null, null, null);
    }

    public YouTubeConfig {
        accessToken = (accessToken != null) ? accessToken.replaceAll("\\s+", "").trim() : null;
        privacyStatus = (privacyStatus == null || privacyStatus.isBlank()) ? "public" : privacyStatus;
        refreshToken = (refreshToken != null) ? refreshToken.trim() : null;
        clientId = (clientId != null) ? clientId.trim() : null;
        clientSecret = (clientSecret != null) ? clientSecret.trim() : null;
    }

    public static YouTubeConfig ofToken(String accessToken) {
        return new YouTubeConfig(accessToken, "public", null, null, null);
    }

    public boolean isValid() {
        return (accessToken != null && !accessToken.isBlank()) || hasRefreshCredentials();
    }

    public boolean hasRefreshCredentials() {
        return refreshToken != null && !refreshToken.isBlank()
                && clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }
}
