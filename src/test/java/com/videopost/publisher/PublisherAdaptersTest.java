package com.videopost.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.videopost.domain.model.Platform;
import com.videopost.domain.model.PublishResult;
import com.videopost.domain.model.Video;
import com.videopost.domain.model.VideoMetadata;
import com.videopost.publisher.facebook.FacebookConfig;
import com.videopost.publisher.facebook.FacebookPublisher;
import com.videopost.publisher.http.HttpResponseData;
import com.videopost.publisher.http.PlatformHttpClient;
import com.videopost.publisher.kwai.KwaiConfig;
import com.videopost.publisher.kwai.KwaiPublisher;
import com.videopost.publisher.tiktok.TikTokConfig;
import com.videopost.publisher.tiktok.TikTokPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublisherAdaptersTest {

    @Mock
    private PlatformHttpClient httpClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("TikTokPublisher: Deve realizar fluxo oficial de init e upload binário com sucesso")
    void shouldPublishSuccessfullyOnTikTok(@TempDir Path tempDir) throws Exception {
        Path videoFile = tempDir.resolve("video.mp4");
        Files.writeString(videoFile, "conteudo-video-dummy");

        Video video = Video.createNew("video.mp4", videoFile.toAbsolutePath().toString(), VideoMetadata.empty());
        TikTokConfig config = TikTokConfig.ofToken("valid-tiktok-token");
        TikTokPublisher publisher = new TikTokPublisher(config, httpClient, objectMapper);

        assertThat(publisher.isAuthenticated()).isTrue();
        assertThat(publisher.platform()).isEqualTo(Platform.TIKTOK);

        String initResponseBody = """
        {
          "data": {
            "publish_id": "v_pub_tiktok_789",
            "upload_url": "https://open-upload.tiktokapis.com/upload/endpoint"
          },
          "error": {
            "code": "ok",
            "message": ""
          }
        }
        """;

        when(httpClient.postJson(anyString(), any(), anyString()))
                .thenReturn(new HttpResponseData(200, initResponseBody, Map.of()));

        when(httpClient.putBinary(eq("https://open-upload.tiktokapis.com/upload/endpoint"), any(), eq(videoFile), eq("video/mp4")))
                .thenReturn(new HttpResponseData(200, "OK", Map.of()));

        PublishResult result = publisher.publish(video);

        assertThat(result.success()).isTrue();
        assertThat(result.externalId()).isEqualTo("v_pub_tiktok_789");
    }

    @Test
    @DisplayName("FacebookPublisher: Deve publicar vídeo via Meta Graph API multipart com sucesso")
    void shouldPublishSuccessfullyOnFacebook(@TempDir Path tempDir) throws Exception {
        Path videoFile = tempDir.resolve("video.mp4");
        Files.writeString(videoFile, "conteudo-video-dummy");

        Video video = Video.createNew("video.mp4", videoFile.toAbsolutePath().toString(), VideoMetadata.empty());
        FacebookConfig config = FacebookConfig.of("page_123", "token_abc");
        FacebookPublisher publisher = new FacebookPublisher(config, httpClient, objectMapper);

        assertThat(publisher.isAuthenticated()).isTrue();
        assertThat(publisher.platform()).isEqualTo(Platform.FACEBOOK);

        String responseBody = "{\"id\": \"meta_video_98765\"}";
        when(httpClient.postMultipart(anyString(), any(), any(), eq("source"), eq(videoFile)))
                .thenReturn(new HttpResponseData(200, responseBody, Map.of()));

        PublishResult result = publisher.publish(video);

        assertThat(result.success()).isTrue();
        assertThat(result.externalId()).isEqualTo("meta_video_98765");
    }

    @Test
    @DisplayName("KwaiPublisher: Deve realizar fluxo oficial da Kwai Open Platform quando autenticado")
    void shouldPublishSuccessfullyOnKwai(@TempDir Path tempDir) throws Exception {
        Path videoFile = tempDir.resolve("video.mp4");
        Files.writeString(videoFile, "conteudo-video-dummy");

        Video video = Video.createNew("video.mp4", videoFile.toAbsolutePath().toString(), VideoMetadata.empty());
        KwaiConfig config = KwaiConfig.of("app_kwai_1", "token_kwai_2");
        KwaiPublisher publisher = new KwaiPublisher(config, httpClient, objectMapper);

        assertThat(publisher.isAuthenticated()).isTrue();
        assertThat(publisher.platform()).isEqualTo(Platform.KWAI);

        String initResponse = """
        {
          "result": 1,
          "upload_token": "kwai_token_123",
          "endpoint": "upload.kuaishou.com"
        }
        """;
        when(httpClient.postJson(anyString(), any(), anyString()))
                .thenReturn(new HttpResponseData(200, initResponse, Map.of()));

        when(httpClient.postMultipart(eq("https://upload.kuaishou.com/openapi/photo/upload?upload_token=kwai_token_123"), any(), any(), eq("file"), eq(videoFile)))
                .thenReturn(new HttpResponseData(200, "{\"result\": 1}", Map.of()));

        PublishResult result = publisher.publish(video);

        assertThat(result.success()).isTrue();
        assertThat(result.externalId()).isEqualTo("kwai_token_123");
    }

    @Test
    @DisplayName("UnsupportedPublisher: Deve registrar impossibilidade de publicação sem quebrar fluxo")
    void shouldHandleUnsupportedPlatformCleanly() {
        UnsupportedPublisher publisher = new UnsupportedPublisher(Platform.INSTAGRAM, "Requer App Review para Content Publishing");
        Video video = Video.createNew("video.mp4", "/path/video.mp4", VideoMetadata.empty());

        assertThat(publisher.isAuthenticated()).isFalse();
        PublishResult result = publisher.publish(video);

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("Requer App Review");
    }
}
