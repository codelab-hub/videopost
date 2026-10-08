package com.videopost.publisher.tiktok;

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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Adapter oficial para publicação no TikTok utilizando o TikTok Content Posting API v2 (Direct Post).
 * Endpoint oficial: https://open.tiktokapis.com/v2/post/publish/video/init/
 */
public class TikTokPublisher implements VideoPublisher {

    private static final Logger log = LoggerFactory.getLogger(TikTokPublisher.class);
    private static final String INIT_URL = "https://open.tiktokapis.com/v2/post/publish/video/init/";

    private final TikTokConfig config;
    private final PlatformHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final com.videopost.infrastructure.security.CredentialStore credentialStore;
    private final String profileName;
    private volatile String currentAccessToken;

    public TikTokPublisher(TikTokConfig config, PlatformHttpClient httpClient, ObjectMapper objectMapper) {
        this(config, httpClient, objectMapper, null, null);
    }

    public TikTokPublisher(TikTokConfig config, PlatformHttpClient httpClient, ObjectMapper objectMapper, com.videopost.infrastructure.security.CredentialStore credentialStore, String profileName) {
        this.config = config;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.credentialStore = credentialStore;
        this.profileName = profileName;
        this.currentAccessToken = (config != null) ? config.accessToken() : null;
    }

    @Override
    public Platform platform() {
        return Platform.TIKTOK;
    }

    @Override
    public boolean isAuthenticated() {
        return config != null && config.isValid();
    }

    @Override
    public PublishResult publish(Video video) {
        if (!isAuthenticated()) {
            return PublishResult.failure("TikTok: credenciais (OAuth Bearer Token) não configuradas.", false);
        }

        Path videoPath = Path.of(video.getPath());
        if (!Files.exists(videoPath)) {
            return PublishResult.failure("TikTok: arquivo de vídeo não encontrado em " + video.getPath(), false);
        }

        try {
            long fileSize = Files.size(videoPath);
            String fullCaption = video.toMetadata().formattedCaption();

            log.info("Iniciando publicação no TikTok para o vídeo id={} arquivo={}", video.getId(), video.getFilename());

            // 1. Inicializar upload no endpoint oficial da TikTok
            Map<String, Object> postInfo = new HashMap<>();
            postInfo.put("title", fullCaption);
            postInfo.put("privacy_level", config.privacyLevel());
            postInfo.put("disable_duet", config.disableDuet());
            postInfo.put("disable_comment", config.disableComment());
            postInfo.put("disable_stitch", config.disableStitch());

            Map<String, Object> sourceInfo = new HashMap<>();
            sourceInfo.put("source", "FILE_UPLOAD");
            sourceInfo.put("video_size", fileSize);
            sourceInfo.put("chunk_size", fileSize);
            sourceInfo.put("total_chunk_count", 1);

            Map<String, Object> requestPayload = new HashMap<>();
            requestPayload.put("post_info", postInfo);
            requestPayload.put("source_info", sourceInfo);

            String requestJson = objectMapper.writeValueAsString(requestPayload);

            String token = (currentAccessToken != null && !currentAccessToken.isBlank()) ? currentAccessToken : (config != null ? config.accessToken() : null);
            if ((token == null || token.isBlank()) && config != null && config.hasRefreshCredentials()) {
                token = refreshAccessToken();
            }

            Map<String, String> headers = Map.of(
                    "Authorization", "Bearer " + (token != null ? token.replaceAll("\\s+", "").trim() : ""),
                    "Content-Type", "application/json; charset=UTF-8"
            );

            HttpResponseData initResponse = httpClient.postJson(INIT_URL, headers, requestJson);

            boolean isTokenError = false;
            if (initResponse.statusCode() == 401) {
                isTokenError = true;
            } else if (initResponse.body() != null && initResponse.body().contains("invalid_token")) {
                isTokenError = true;
            }

            if (isTokenError && config != null && config.hasRefreshCredentials()) {
                log.warn("Token do TikTok expirado ou inválido. Renovando automaticamente via Refresh Token...");
                String refreshed = refreshAccessToken();
                if (refreshed != null) {
                    token = refreshed;
                    headers = Map.of(
                            "Authorization", "Bearer " + token.replaceAll("\\s+", "").trim(),
                            "Content-Type", "application/json; charset=UTF-8"
                    );
                    initResponse = httpClient.postJson(INIT_URL, headers, requestJson);
                }
            }

            if (!initResponse.isSuccessful()) {
                // Se falhar no Direct Post, tenta o endpoint de Rascunhos/Inbox (video.upload)
                log.info("Tentando envio via TikTok Inbox / Rascunhos (/v2/post/publish/inbox/video/init/)...");
                Map<String, Object> inboxPayload = Map.of(
                        "source_info", Map.of(
                                "source", "FILE_UPLOAD",
                                "video_size", fileSize,
                                "chunk_size", fileSize,
                                "total_chunk_count", 1
                        )
                );
                String inboxJson = objectMapper.writeValueAsString(inboxPayload);
                initResponse = httpClient.postJson("https://open.tiktokapis.com/v2/post/publish/inbox/video/init/", headers, inboxJson);
            }

            if (!initResponse.isSuccessful()) {
                String errorMsg = parseErrorMessage(initResponse);
                log.error("Falha ao inicializar post no TikTok (HTTP {}): {}", initResponse.statusCode(), errorMsg);
                return PublishResult.failure("TikTok API error (HTTP " + initResponse.statusCode() + "): " + errorMsg, isRetryableStatus(initResponse.statusCode()));
            }

            JsonNode root = objectMapper.readTree(initResponse.body());
            JsonNode errorNode = root.path("error");
            String errorCode = errorNode.path("code").asText("");

            if (!"ok".equalsIgnoreCase(errorCode) && !errorCode.isBlank()) {
                // Tenta fallback para Inbox também caso a resposta de negócio recuse direct post
                log.info("Direct Post recusado pelo TikTok ({}). Tentando envio para Inbox/Rascunhos...", errorCode);
                Map<String, Object> inboxPayload = Map.of(
                        "source_info", Map.of(
                                "source", "FILE_UPLOAD",
                                "video_size", fileSize,
                                "chunk_size", fileSize,
                                "total_chunk_count", 1
                        )
                );
                String inboxJson = objectMapper.writeValueAsString(inboxPayload);
                HttpResponseData inboxResp = httpClient.postJson("https://open.tiktokapis.com/v2/post/publish/inbox/video/init/", headers, inboxJson);
                if (inboxResp.isSuccessful()) {
                    JsonNode inboxRoot = objectMapper.readTree(inboxResp.body());
                    if ("ok".equalsIgnoreCase(inboxRoot.path("error").path("code").asText("ok"))) {
                        root = inboxRoot;
                        errorCode = "ok";
                    }
                }
                if (!"ok".equalsIgnoreCase(errorCode)) {
                    String errorMsg = errorNode.path("message").asText("Erro retornado pelo TikTok");
                    log.error("TikTok API retornou erro de negócio code={}: {}", errorCode, errorMsg);
                    return PublishResult.failure("TikTok [" + errorCode + "]: " + errorMsg, !errorCode.contains("invalid_token"));
                }
            }

            JsonNode dataNode = root.path("data");
            String publishId = dataNode.path("publish_id").asText();
            String uploadUrl = dataNode.path("upload_url").asText();

            if (uploadUrl.isBlank() || publishId.isBlank()) {
                return PublishResult.failure("TikTok API não retornou upload_url ou publish_id válidos.", true);
            }

            // 2. Upload do binário do vídeo
            log.info("Enviando binário de vídeo para o endpoint de upload do TikTok (tamanho={} bytes)", fileSize);
            Map<String, String> uploadHeaders = Map.of(
                    "Content-Type", "video/mp4",
                    "Content-Range", "bytes 0-" + (fileSize - 1) + "/" + fileSize
            );

            HttpResponseData uploadResponse = httpClient.putBinary(uploadUrl, uploadHeaders, videoPath, "video/mp4");
            if (!uploadResponse.isSuccessful()) {
                log.error("Falha ao fazer upload binário para o TikTok (HTTP {})", uploadResponse.statusCode());
                return PublishResult.failure("TikTok Upload error (HTTP " + uploadResponse.statusCode() + ")", true);
            }

            log.info("Vídeo publicado com sucesso no TikTok! publish_id={}", publishId);
            return PublishResult.success(publishId);

        } catch (Exception e) {
            log.error("Erro inesperado ao publicar vídeo no TikTok: {}", e.getMessage(), e);
            return PublishResult.failure("Erro de comunicação com TikTok: " + e.getMessage(), true);
        }
    }

    private String parseErrorMessage(HttpResponseData response) {
        try {
            if (response.body() != null && !response.body().isBlank()) {
                JsonNode node = objectMapper.readTree(response.body());
                if (node.has("error") && node.get("error").has("message")) {
                    return node.get("error").get("message").asText();
                }
                if (node.has("message")) {
                    return node.get("message").asText();
                }
            }
        } catch (Exception ignored) {
        }
        return "HTTP Status " + response.statusCode();
    }

    private boolean isRetryableStatus(int statusCode) {
        return statusCode == 429 || statusCode >= 500;
    }

    public synchronized String refreshAccessToken() {
        if (config == null || !config.hasRefreshCredentials()) {
            return null;
        }
        try {
            log.info("Renovando Access Token do TikTok via Refresh Token...");
            String form = "client_key=" + java.net.URLEncoder.encode(config.clientKey(), java.nio.charset.StandardCharsets.UTF_8)
                    + "&client_secret=" + java.net.URLEncoder.encode(config.clientSecret(), java.nio.charset.StandardCharsets.UTF_8)
                    + "&refresh_token=" + java.net.URLEncoder.encode(config.refreshToken(), java.nio.charset.StandardCharsets.UTF_8)
                    + "&grant_type=refresh_token";

            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(15))
                    .build();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("https://open.tiktokapis.com/v2/oauth/token/"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(form))
                    .build();

            java.net.http.HttpResponse<String> resp = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
            if (resp.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(resp.body());
                JsonNode data = root.path("data");
                String newToken = data.path("access_token").asText();
                String newRefreshToken = data.path("refresh_token").asText();
                if (!newToken.isBlank()) {
                    this.currentAccessToken = newToken;
                    log.info("Access Token do TikTok renovado com sucesso!");
                    if (credentialStore != null && profileName != null) {
                        Map<String, String> creds = new HashMap<>(credentialStore.getCredentials(profileName, Platform.TIKTOK).orElse(Map.of()));
                        creds.put("accessToken", newToken);
                        if (!newRefreshToken.isBlank()) {
                            creds.put("refreshToken", newRefreshToken);
                        }
                        credentialStore.saveCredentials(profileName, Platform.TIKTOK, creds);
                    }
                    return newToken;
                }
            } else {
                log.warn("Falha ao renovar token OAuth do TikTok (HTTP {}): {}", resp.statusCode(), resp.body());
            }
        } catch (Exception e) {
            log.error("Erro inesperado ao renovar token OAuth do TikTok: {}", e.getMessage(), e);
        }
        return null;
    }
}
