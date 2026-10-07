package com.videopost.publisher.kwai;

/**
 * Configurações para a integração oficial com a Kwai Open Platform (Kuaishou).
 */
public record KwaiConfig(
        String appId,
        String accessToken
) {
    public static KwaiConfig of(String appId, String accessToken) {
        return new KwaiConfig(appId, accessToken);
    }

    public boolean isValid() {
        return appId != null && !appId.isBlank()
                && accessToken != null && !accessToken.isBlank();
    }
}
