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

    public TikTokPublisher(TikTokConfig config, PlatformHttpClient httpClient, ObjectMapper objectMapper) {
        this.config = config;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
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

            Map<String, String> headers = Map.of(
                    "Authorization", "Bearer " + config.accessToken(),
                    "Content-Type", "application/json; charset=UTF-8"
            );

            HttpResponseData initResponse = httpClient.postJson(INIT_URL, headers, requestJson);

            if (!initResponse.isSuccessful()) {
                String errorMsg = parseErrorMessage(initResponse);
                log.error("Falha ao inicializar post no TikTok (HTTP {}): {}", initResponse.statusCode(), errorMsg);
                return PublishResult.failure("TikTok API error (HTTP " + initResponse.statusCode() + "): " + errorMsg, isRetryableStatus(initResponse.statusCode()));
            }

            JsonNode root = objectMapper.readTree(initResponse.body());
            JsonNode errorNode = root.path("error");
            String errorCode = errorNode.path("code").asText("");

            if (!"ok".equalsIgnoreCase(errorCode) && !errorCode.isBlank()) {
                String errorMsg = errorNode.path("message").asText("Erro desconhecido retornado pelo TikTok");
                log.error("TikTok API retornou erro de negócio code={}: {}", errorCode, errorMsg);
                return PublishResult.failure("TikTok [" + errorCode + "]: " + errorMsg, !errorCode.contains("invalid_token"));
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
}
