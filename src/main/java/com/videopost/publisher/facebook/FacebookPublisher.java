package com.videopost.publisher.facebook;

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
 * Adapter oficial para publicação no Facebook Pages utilizando o Meta Graph API.
 * Endpoint oficial: https://graph-video.facebook.com/{version}/{page-id}/videos
 */
public class FacebookPublisher implements VideoPublisher {

    private static final Logger log = LoggerFactory.getLogger(FacebookPublisher.class);
    private static final String GRAPH_VIDEO_HOST = "https://graph-video.facebook.com";

    private final FacebookConfig config;
    private final PlatformHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public FacebookPublisher(FacebookConfig config, PlatformHttpClient httpClient, ObjectMapper objectMapper) {
        this.config = config;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Platform platform() {
        return Platform.FACEBOOK;
    }

    @Override
    public boolean isAuthenticated() {
        return config != null && config.isValid();
    }

    @Override
    public PublishResult publish(Video video) {
        if (!isAuthenticated()) {
            return PublishResult.failure("Facebook: credenciais (Page ID ou Page Access Token) não configuradas.", false);
        }

        Path videoPath = Path.of(video.getPath());
        if (!Files.exists(videoPath)) {
            return PublishResult.failure("Facebook: arquivo de vídeo não encontrado em " + video.getPath(), false);
        }

        try {
            String uploadUrl = String.format("%s/%s/%s/videos",
                    GRAPH_VIDEO_HOST,
                    config.apiVersion(),
                    config.pageId()
            );

            String formattedCaption = video.toMetadata().formattedCaption();

            log.info("Iniciando publicação no Facebook para o vídeo id={} arquivo={} na página id={}",
                    video.getId(), video.getFilename(), config.pageId());

            Map<String, String> formFields = new HashMap<>();
            formFields.put("access_token", config.pageAccessToken());
            formFields.put("description", formattedCaption);
            formFields.put("title", video.getFilename());

            HttpResponseData response = httpClient.postMultipart(
                    uploadUrl,
                    Map.of(),
                    formFields,
                    "source",
                    videoPath
            );

            if (!response.isSuccessful()) {
                String errorMsg = parseErrorMessage(response);
                log.error("Falha ao publicar vídeo no Facebook (HTTP {}): {}", response.statusCode(), errorMsg);
                boolean retryable = response.statusCode() == 429 || response.statusCode() >= 500;
                return PublishResult.failure("Facebook Graph API error (HTTP " + response.statusCode() + "): " + errorMsg, retryable);
            }

            JsonNode root = objectMapper.readTree(response.body());
            if (root.has("error")) {
                String errorMsg = root.path("error").path("message").asText("Erro desconhecido retornado pelo Facebook");
                log.error("Facebook retornou erro: {}", errorMsg);
                return PublishResult.failure("Facebook: " + errorMsg, true);
            }

            String externalId = root.path("id").asText();
            if (externalId.isBlank()) {
                return PublishResult.failure("Facebook API respondeu sem o id do vídeo publicado.", true);
            }

            log.info("Vídeo publicado com sucesso no Facebook! video_id={}", externalId);
            return PublishResult.success(externalId);

        } catch (Exception e) {
            log.error("Erro inesperado ao publicar vídeo no Facebook: {}", e.getMessage(), e);
            return PublishResult.failure("Erro de comunicação com Facebook: " + e.getMessage(), true);
        }
    }

    private String parseErrorMessage(HttpResponseData response) {
        try {
            if (response.body() != null && !response.body().isBlank()) {
                JsonNode node = objectMapper.readTree(response.body());
                if (node.has("error") && node.get("error").has("message")) {
                    return node.get("error").get("message").asText();
                }
            }
        } catch (Exception ignored) {
        }
        return "HTTP Status " + response.statusCode();
    }
}
