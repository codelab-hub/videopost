package com.videopost.application.service;

import com.videopost.domain.model.Platform;
import com.videopost.infrastructure.security.CredentialStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

/**
 * Serviço responsável por gerenciar a autenticação e credenciais seguras das plataformas.
 */
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final CredentialStore credentialStore;

    public AuthService(CredentialStore credentialStore) {
        this.credentialStore = credentialStore;
    }

    public void authenticateTikTok(String profile, String accessToken, String privacyLevel) {
        authenticateTikTok(profile, accessToken, privacyLevel, null, null, null);
    }

    public void authenticateTikTok(String profile, String accessToken, String privacyLevel, String refreshToken, String clientKey, String clientSecret) {
        java.util.Map<String, String> creds = new java.util.HashMap<>();
        if (accessToken != null && !accessToken.isBlank()) {
            creds.put("accessToken", accessToken.trim());
        }
        creds.put("privacyLevel", (privacyLevel != null && !privacyLevel.isBlank()) ? privacyLevel.trim() : "PUBLIC_TO_EVERYONE");
        if (refreshToken != null && !refreshToken.isBlank()) {
            creds.put("refreshToken", refreshToken.trim());
        }
        if (clientKey != null && !clientKey.isBlank()) {
            creds.put("clientKey", clientKey.trim());
        }
        if (clientSecret != null && !clientSecret.isBlank()) {
            creds.put("clientSecret", clientSecret.trim());
        }
        if (creds.isEmpty() || (!creds.containsKey("accessToken") && !creds.containsKey("refreshToken"))) {
            throw new IllegalArgumentException("TikTok access token ou refresh token deve ser fornecido.");
        }
        credentialStore.saveCredentials(profile, Platform.TIKTOK, creds);
        log.info("TikTok autenticado com sucesso para o perfil '{}'.", profile);
    }

    public void authenticateFacebook(String profile, String pageId, String pageAccessToken) {
        if (pageId == null || pageId.isBlank() || pageAccessToken == null || pageAccessToken.isBlank()) {
            throw new IllegalArgumentException("Facebook pageId e pageAccessToken são obrigatórios.");
        }
        Map<String, String> creds = Map.of(
                "pageId", pageId.trim(),
                "pageAccessToken", pageAccessToken.trim()
        );
        credentialStore.saveCredentials(profile, Platform.FACEBOOK, creds);
        log.info("Facebook autenticado com sucesso para o perfil '{}'.", profile);
    }

    public void authenticateKwai(String profile, String appId, String accessToken) {
        if (appId == null || appId.isBlank() || accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("Kwai appId e accessToken são obrigatórios.");
        }
        Map<String, String> creds = Map.of(
                "appId", appId.trim(),
                "accessToken", accessToken.trim()
        );
        credentialStore.saveCredentials(profile, Platform.KWAI, creds);
        log.info("Kwai autenticado com sucesso para o perfil '{}'.", profile);
    }

    public void authenticateYouTube(String profile, String accessToken) {
        authenticateYouTube(profile, accessToken, null, null, null);
    }

    public void authenticateYouTube(String profile, String accessToken, String refreshToken, String clientId, String clientSecret) {
        java.util.Map<String, String> creds = new java.util.HashMap<>();
        if (accessToken != null && !accessToken.isBlank()) {
            creds.put("accessToken", accessToken.replaceAll("\\s+", "").trim());
        }
        creds.put("privacyStatus", "public");
        if (refreshToken != null && !refreshToken.isBlank()) {
            creds.put("refreshToken", refreshToken.trim());
        }
        if (clientId != null && !clientId.isBlank()) {
            creds.put("clientId", clientId.trim());
        }
        if (clientSecret != null && !clientSecret.isBlank()) {
            creds.put("clientSecret", clientSecret.trim());
        }
        if (creds.isEmpty() || (!creds.containsKey("accessToken") && !creds.containsKey("refreshToken"))) {
            throw new IllegalArgumentException("YouTube access token ou refresh token deve ser fornecido.");
        }
        credentialStore.saveCredentials(profile, Platform.YOUTUBE_SHORTS, creds);
        log.info("YouTube autenticado com sucesso para o perfil '{}'.", profile);
    }

    public boolean isPlatformAuthenticated(String profile, Platform platform) {
        return credentialStore.hasCredentials(profile, platform);
    }

    public Optional<Map<String, String>> getCredentials(String profile, Platform platform) {
        return credentialStore.getCredentials(profile, platform);
    }
}
