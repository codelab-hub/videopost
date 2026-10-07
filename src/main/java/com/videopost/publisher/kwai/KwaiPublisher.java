package com.videopost.publisher.kwai;

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
 * Adapter para a Kwai Open Platform (Kuaishou Open API).
 * Documentação oficial: https://open.kuaishou.com/platform/openApi
 * 
 * Requisitos Oficiais:
 * - Conta empresarial aprovada na Kwai Open Platform (Kuaishou Open Platform).
 * - Escopo autorizado: user_video_publish.
 * - Endpoint oficial para início do upload: POST https://open.kuaishou.com/openapi/photo/start_upload
 * 
 * Em conformidade com as diretrizes do VideoPost, nenhum método de scraping,
 * simulação de navegador ou contorno de CAPTCHA é utilizado.
 */
public class KwaiPublisher implements VideoPublisher {

    private static final Logger log = LoggerFactory.getLogger(KwaiPublisher.class);
    private static final String KWAI_START_UPLOAD_URL = "https://open.kuaishou.com/openapi/photo/start_upload";

    private final KwaiConfig config;
    private final PlatformHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public KwaiPublisher(KwaiConfig config, PlatformHttpClient httpClient, ObjectMapper objectMapper) {
        this.config = config;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Platform platform() {
        return Platform.KWAI;
    }

    @Override
    public boolean isAuthenticated() {
        return config != null && config.isValid();
    }

    @Override
    public PublishResult publish(Video video) {
        if (!isAuthenticated()) {
            return PublishResult.failure(
                    "Kwai: credenciais não configuradas. A Kwai Open Platform requer aprovação comercial de parceiro empresarial (app_id e access_token com escopo 'user_video_publish').",
                    false
            );
        }

        Path videoPath = Path.of(video.getPath());
        if (!Files.exists(videoPath)) {
            return PublishResult.failure("Kwai: arquivo de vídeo não encontrado em " + video.getPath(), false);
        }

        try {
            log.info("Iniciando publicação no Kwai para o vídeo id={} arquivo={}", video.getId(), video.getFilename());

            Map<String, String> headers = Map.of(
                    "Content-Type", "application/json; charset=UTF-8"
            );

            Map<String, Object> body = new HashMap<>();
            body.put("app_id", config.appId());
            body.put("access_token", config.accessToken());

            String jsonBody = objectMapper.writeValueAsString(body);
            HttpResponseData response = httpClient.postJson(KWAI_START_UPLOAD_URL, headers, jsonBody);

            if (!response.isSuccessful()) {
                String errorMsg = parseErrorMessage(response);
                log.error("Falha ao iniciar upload no Kwai (HTTP {}): {}", response.statusCode(), errorMsg);
                boolean retryable = response.statusCode() == 429 || response.statusCode() >= 500;
                return PublishResult.failure("Kwai API error (HTTP " + response.statusCode() + "): " + errorMsg, retryable);
            }

            JsonNode root = objectMapper.readTree(response.body());
            int result = root.path("result").asInt(1);
            if (result != 1) {
                String errorMsg = root.path("error_msg").asText("Erro reportado pela Kwai Open Platform");
                log.error("Kwai API retornou erro de resultado result={}: {}", result, errorMsg);
                return PublishResult.failure("Kwai [" + result + "]: " + errorMsg, true);
            }

            String uploadToken = root.path("upload_token").asText();
            String endpoint = root.path("endpoint").asText();

            if (uploadToken.isBlank() || endpoint.isBlank()) {
                return PublishResult.failure("Kwai API não retornou upload_token ou endpoint válidos.", true);
            }

            // Upload binário no gateway do Kwai
            String uploadUrl = "https://" + endpoint + "/openapi/photo/upload?upload_token=" + uploadToken;
            HttpResponseData uploadResp = httpClient.postMultipart(
                    uploadUrl,
                    Map.of(),
                    Map.of(),
                    "file",
                    videoPath
            );

            if (!uploadResp.isSuccessful()) {
                log.error("Falha no upload do vídeo para o gateway do Kwai (HTTP {})", uploadResp.statusCode());
                return PublishResult.failure("Kwai gateway upload failed (HTTP " + uploadResp.statusCode() + ")", true);
            }

            log.info("Vídeo enviado com sucesso para o Kwai! upload_token={}", uploadToken);
            return PublishResult.success(uploadToken);

        } catch (Exception e) {
            log.error("Erro inesperado ao publicar vídeo no Kwai: {}", e.getMessage(), e);
            return PublishResult.failure("Erro de comunicação com Kwai: " + e.getMessage(), true);
        }
    }

    private String parseErrorMessage(HttpResponseData response) {
        try {
            if (response.body() != null && !response.body().isBlank()) {
                JsonNode node = objectMapper.readTree(response.body());
                if (node.has("error_msg")) {
                    return node.get("error_msg").asText();
                }
            }
        } catch (Exception ignored) {
        }
        return "HTTP Status " + response.statusCode();
    }
}
