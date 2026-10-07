package com.videopost.domain;

import com.videopost.domain.model.Platform;
import com.videopost.domain.model.Publication;
import com.videopost.domain.model.PublicationStatus;
import com.videopost.domain.model.Video;
import com.videopost.domain.model.VideoMetadata;
import com.videopost.domain.model.VideoStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VideoTest {

    @Test
    @DisplayName("Deve inicializar vídeo com status PENDING e metadados corretos")
    void shouldInitializeVideoCorrectly() {
        VideoMetadata metadata = VideoMetadata.of("Legenda teste", List.of("#tag1", "#tag2"));
        Video video = Video.createNew("video.mp4", "/path/video.mp4", metadata);

        assertThat(video.getStatus()).isEqualTo(VideoStatus.PENDING);
        assertThat(video.getCaption()).isEqualTo("Legenda teste");
        assertThat(video.getHashtags()).containsExactly("#tag1", "#tag2");
        assertThat(video.getPublishedAt()).isNull();
        assertThat(video.getScheduledAt()).isNull();
    }

    @Test
    @DisplayName("Deve transitar para SCHEDULED ao definir agendamento")
    void shouldTransitionToScheduled() {
        Video video = Video.createNew("video.mp4", "/path/video.mp4", VideoMetadata.empty());
        Instant scheduledTime = Instant.now().plusSeconds(1800);

        video.markScheduled(scheduledTime);

        assertThat(video.getStatus()).isEqualTo(VideoStatus.SCHEDULED);
        assertThat(video.getScheduledAt()).isEqualTo(scheduledTime);
    }

    @Test
    @DisplayName("Deve avaliar status como COMPLETED quando todas as publicações tiverem sucesso")
    void shouldEvaluateAsCompletedWhenAllPublished() {
        Video video = Video.createNew("video.mp4", "/path/video.mp4", VideoMetadata.empty());

        Publication pub1 = Publication.createPending(video.getId(), Platform.TIKTOK);
        pub1.markPublished("tt-123", Instant.now());

        Publication pub2 = Publication.createPending(video.getId(), Platform.FACEBOOK);
        pub2.markPublished("fb-456", Instant.now());

        video.evaluateStatusFromPublications(List.of(pub1, pub2));

        assertThat(video.getStatus()).isEqualTo(VideoStatus.COMPLETED);
        assertThat(video.getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("Deve avaliar status como PARTIALLY_COMPLETED quando apenas algumas publicações tiverem sucesso")
    void shouldEvaluateAsPartiallyCompletedWhenSomeFail() {
        Video video = Video.createNew("video.mp4", "/path/video.mp4", VideoMetadata.empty());

        Publication pub1 = Publication.createPending(video.getId(), Platform.TIKTOK);
        pub1.markPublished("tt-123", Instant.now());

        Publication pub2 = Publication.createPending(video.getId(), Platform.FACEBOOK);
        pub2.markPublished("fb-456", Instant.now());

        Publication pub3 = Publication.createPending(video.getId(), Platform.KWAI);
        pub3.markFailed("Erro de quota", Instant.now());

        video.evaluateStatusFromPublications(List.of(pub1, pub2, pub3));

        assertThat(video.getStatus()).isEqualTo(VideoStatus.PARTIALLY_COMPLETED);
    }

    @Test
    @DisplayName("Deve avaliar status como FAILED quando todas as publicações falharem")
    void shouldEvaluateAsFailedWhenAllFail() {
        Video video = Video.createNew("video.mp4", "/path/video.mp4", VideoMetadata.empty());

        Publication pub1 = Publication.createPending(video.getId(), Platform.TIKTOK);
        pub1.markFailed("Erro 1", Instant.now());

        Publication pub2 = Publication.createPending(video.getId(), Platform.FACEBOOK);
        pub2.markFailed("Erro 2", Instant.now());

        video.evaluateStatusFromPublications(List.of(pub1, pub2));

        assertThat(video.getStatus()).isEqualTo(VideoStatus.FAILED);
    }

    @Test
    @DisplayName("Deve manter PROCESSING se alguma publicação ainda estiver em processamento")
    void shouldMaintainProcessingIfAnyIsProcessing() {
        Video video = Video.createNew("video.mp4", "/path/video.mp4", VideoMetadata.empty());

        Publication pub1 = Publication.createPending(video.getId(), Platform.TIKTOK);
        pub1.markProcessing();

        Publication pub2 = Publication.createPending(video.getId(), Platform.FACEBOOK);
        pub2.markPublished("fb-123", Instant.now());

        video.evaluateStatusFromPublications(List.of(pub1, pub2));

        assertThat(video.getStatus()).isEqualTo(VideoStatus.PROCESSING);
    }
}
