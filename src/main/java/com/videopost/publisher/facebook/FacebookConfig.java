package com.videopost.publisher.facebook;

/**
 * Configurações e credenciais de autenticação para o Meta / Facebook Graph API.
 */
public record FacebookConfig(
        String pageId,
        String pageAccessToken,
        String apiVersion
) {
    public FacebookConfig {
        apiVersion = (apiVersion == null || apiVersion.isBlank()) ? "v20.0" : apiVersion;
    }

    public static FacebookConfig of(String pageId, String pageAccessToken) {
        return new FacebookConfig(pageId, pageAccessToken, "v20.0");
    }

    public boolean isValid() {
        return pageId != null && !pageId.isBlank()
                && pageAccessToken != null && !pageAccessToken.isBlank();
    }
}
