package com.videopost.publisher.youtube;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.videopost.domain.model.Platform;
import com.videopost.domain.model.PublishResult;
import com.videopost.domain.model.Video;
import com.videopost.publisher.VideoPublisher;
import com.videopost.publisher.http.HttpResponseData;
import com.videopost.publisher.http.PlatformHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.videopost.infrastructure.security.CredentialStore;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter oficial para publicação no YouTube Shorts utilizando a Google YouTube Data API v3.
 * Endpoint oficial de upload: https://www.googleapis.com/upload/youtube/v3/videos
 */
public class YouTubeShortsPublisher implements VideoPublisher {

    private static final Logger log = LoggerFactory.getLogger(YouTubeShortsPublisher.class);
    private static final String UPLOAD_INIT_URL = "https://www.googleapis.com/upload/youtube/v3/videos?uploadType=resumable&part=snippet,status";

    private final YouTubeConfig config;
    private final PlatformHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final CredentialStore credentialStore;
    private final String profileName;
    private volatile String currentAccessToken;

    public YouTubeShortsPublisher(YouTubeConfig config, PlatformHttpClient httpClient, ObjectMapper objectMapper) {
        this(config, httpClient, objectMapper, null, null);
    }

    public YouTubeShortsPublisher(YouTubeConfig config, PlatformHttpClient httpClient, ObjectMapper objectMapper, CredentialStore credentialStore, String profileName) {
        this.config = config;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.credentialStore = credentialStore;
        this.profileName = profileName;
        this.currentAccessToken = (config != null) ? config.accessToken() : null;
    }

    @Override
    public Platform platform() {
        return Platform.YOUTUBE_SHORTS;
    }

    @Override
    public boolean isAuthenticated() {
        return config != null && config.isValid();
    }

    @Override
    public PublishResult publish(Video video) {
        if (!isAuthenticated()) {
            return PublishResult.failure("YouTube: credenciais OAuth (Bearer Token) não configuradas.", false);
        }

        Path videoPath = Path.of(video.getPath());
        if (!Files.exists(videoPath)) {
            return PublishResult.failure("YouTube: arquivo de vídeo não encontrado em " + video.getPath(), false);
        }

        try {
            long fileSize = Files.size(videoPath);
            String title = (video.getCaption() != null && !video.getCaption().isBlank())
                    ? video.getCaption()
                    : video.getFilename();

            // Assegura que #Shorts está no título/descrição para categorização correta
            String description = video.toMetadata().formattedCaption();
            if (!description.contains("#Shorts") && !description.contains("#shorts")) {
                description = (description.isBlank() ? "" : description + "\n\n") + "#Shorts";
            }

            log.info("Iniciando publicação no YouTube Shorts: id={} arquivo='{}'", video.getId(), video.getFilename());

            Map<String, Object> snippet = new HashMap<>();
            snippet.put("title", title.length() > 100 ? title.substring(0, 97) + "..." : title);
            snippet.put("description", description);
            snippet.put("tags", List.of("Shorts", "VideoPost"));

            Map<String, Object> status = new HashMap<>();
            status.put("privacyStatus", config.privacyStatus());
            status.put("selfDeclaredMadeForKids", false);

            Map<String, Object> metadataBody = Map.of(
                    "snippet", snippet,
                    "status", status
            );

            String jsonMetadata = objectMapper.writeValueAsString(metadataBody);

            String token = (currentAccessToken != null && !currentAccessToken.isBlank()) ? currentAccessToken : (config != null ? config.accessToken() : null);
            if ((token == null || token.isBlank()) && config != null && config.hasRefreshCredentials()) {
                token = refreshAccessToken();
            }

            Map<String, String> initHeaders = Map.of(
                    "Authorization", "Bearer " + (token != null ? token.replaceAll("\\s+", "").trim() : ""),
                    "Content-Type", "application/json; charset=UTF-8",
                    "X-Upload-Content-Type", "video/mp4",
                    "X-Upload-Content-Length", String.valueOf(fileSize)
            );

            // 1. Inicializa sessão resumable no Google
            HttpResponseData initResponse = httpClient.postJson(UPLOAD_INIT_URL, initHeaders, jsonMetadata);

            if (initResponse.statusCode() == 401 && config != null && config.hasRefreshCredentials()) {
                log.warn("Token do YouTube expirado (HTTP 401). Renovando automaticamente via Refresh Token...");
                String refreshed = refreshAccessToken();
                if (refreshed != null) {
                    token = refreshed;
                    initHeaders = Map.of(
                            "Authorization", "Bearer " + token.replaceAll("\\s+", "").trim(),
                            "Content-Type", "application/json; charset=UTF-8",
                            "X-Upload-Content-Type", "video/mp4",
                            "X-Upload-Content-Length", String.valueOf(fileSize)
                    );
                    initResponse = httpClient.postJson(UPLOAD_INIT_URL, initHeaders, jsonMetadata);
                }
            }

            if (!initResponse.isSuccessful()) {
                String errorMsg = parseErrorMessage(initResponse);
                log.error("YouTube API error (HTTP {}): {}", initResponse.statusCode(), errorMsg);
                return PublishResult.failure("YouTube API error (HTTP " + initResponse.statusCode() + "): " + errorMsg, isRetryable(initResponse.statusCode()));
            }

            String uploadLocation = initResponse.headers().get("Location");
            if (uploadLocation == null || uploadLocation.isBlank()) {
                // Tenta buscar nos headers com caixa baixa
                uploadLocation = initResponse.headers().get("location");
            }

            if (uploadLocation == null || uploadLocation.isBlank()) {
                return PublishResult.failure("YouTube não retornou cabeçalho 'Location' para upload do binário.", true);
            }

            // 2. Upload do binário do vídeo para a URL de sessão
            log.info("Fazendo upload binário do YouTube Shorts (tamanho={} bytes)", fileSize);
            HttpResponseData uploadResponse = httpClient.putBinary(uploadLocation, Map.of("Content-Type", "video/mp4"), videoPath, "video/mp4");

            if (!uploadResponse.isSuccessful() && (uploadResponse.statusCode() == 410 || uploadResponse.statusCode() == 404 || uploadResponse.statusCode() == 401)) {
                log.warn("Sessão de upload do YouTube expirada ou inválida (HTTP {}). Reiniciando com nova sessão...", uploadResponse.statusCode());
                if (uploadResponse.statusCode() == 401 && config != null && config.hasRefreshCredentials()) {
                    String refreshed = refreshAccessToken();
                    if (refreshed != null) {
                        token = refreshed;
                    }
                }
                initHeaders = Map.of(
                        "Authorization", "Bearer " + token.replaceAll("\\s+", "").trim(),
                        "Content-Type", "application/json; charset=UTF-8",
                        "X-Upload-Content-Type", "video/mp4",
                        "X-Upload-Content-Length", String.valueOf(fileSize)
                );
                initResponse = httpClient.postJson(UPLOAD_INIT_URL, initHeaders, jsonMetadata);
                if (initResponse.isSuccessful()) {
                    uploadLocation = initResponse.headers().get("Location");
                    if (uploadLocation == null) {
                        uploadLocation = initResponse.headers().get("location");
                    }
                    if (uploadLocation != null && !uploadLocation.isBlank()) {
                        uploadResponse = httpClient.putBinary(uploadLocation, Map.of("Content-Type", "video/mp4"), videoPath, "video/mp4");
                    }
                }
            }

            if (!uploadResponse.isSuccessful()) {
                log.error("Falha no upload binário para o YouTube (HTTP {})", uploadResponse.statusCode());
                return PublishResult.failure("YouTube binary upload error (HTTP " + uploadResponse.statusCode() + ")", true);
            }

            JsonNode root = objectMapper.readTree(uploadResponse.body());
            String videoId = root.path("id").asText();

            if (videoId.isBlank()) {
                return PublishResult.failure("YouTube respondeu sem o id do vídeo publicado.", true);
            }

            log.info("Vídeo publicado com sucesso no YouTube Shorts! videoId={}", videoId);
            return PublishResult.success(videoId);

        } catch (Exception e) {
            log.error("Erro inesperado ao publicar vídeo no YouTube: {}", e.getMessage(), e);
            return PublishResult.failure("Erro de comunicação com YouTube: " + e.getMessage(), true);
        }
    }

    private String parseErrorMessage(HttpResponseData response) {
        try {
            if (response.body() != null && !response.body().isBlank()) {
                JsonNode root = objectMapper.readTree(response.body());
                if (root.has("error") && root.get("error").has("message")) {
                    return root.get("error").get("message").asText();
                }
            }
        } catch (Exception ignored) {}
        return "HTTP Status " + response.statusCode();
    }

    private boolean isRetryable(int statusCode) {
        return statusCode == 429 || statusCode >= 500;
    }

    public synchronized String refreshAccessToken() {
        if (config == null || !config.hasRefreshCredentials()) {
            return null;
        }
        try {
            log.info("Renovando Access Token do YouTube via Refresh Token...");
            String form = "client_id=" + URLEncoder.encode(config.clientId(), StandardCharsets.UTF_8)
                    + "&client_secret=" + URLEncoder.encode(config.clientSecret(), StandardCharsets.UTF_8)
                    + "&refresh_token=" + URLEncoder.encode(config.refreshToken(), StandardCharsets.UTF_8)
                    + "&grant_type=refresh_token";

            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(15))
                    .build();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(URI.create("https://oauth2.googleapis.com/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(form))
                    .build();

            java.net.http.HttpResponse<String> resp = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() == 200) {
                JsonNode node = objectMapper.readTree(resp.body());
                String newToken = node.path("access_token").asText();
                if (!newToken.isBlank()) {
                    this.currentAccessToken = newToken;
                    log.info("Access Token do YouTube renovado com sucesso!");
                    if (credentialStore != null && profileName != null) {
                        Map<String, String> creds = new HashMap<>(credentialStore.getCredentials(profileName, Platform.YOUTUBE_SHORTS).orElse(Map.of()));
                        creds.put("accessToken", newToken);
                        credentialStore.saveCredentials(profileName, Platform.YOUTUBE_SHORTS, creds);
                    }
                    return newToken;
                }
            } else {
                log.warn("Falha ao renovar token OAuth do YouTube (HTTP {}): {}", resp.statusCode(), resp.body());
            }
        } catch (Exception e) {
            log.error("Erro inesperado ao renovar token OAuth do YouTube: {}", e.getMessage(), e);
        }
        return null;
    }
}
