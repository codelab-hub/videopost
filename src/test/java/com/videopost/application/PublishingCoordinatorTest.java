package com.videopost.application;

import com.videopost.application.service.PublishingCoordinator;
import com.videopost.domain.model.Platform;
import com.videopost.domain.model.Publication;
import com.videopost.domain.model.PublicationStatus;
import com.videopost.domain.model.PublishResult;
import com.videopost.domain.model.Video;
import com.videopost.domain.model.VideoMetadata;
import com.videopost.domain.model.VideoStatus;
import com.videopost.domain.repository.PublicationRepository;
import com.videopost.domain.repository.VideoRepository;
import com.videopost.infrastructure.config.AppConfig;
import com.videopost.publisher.PublisherRegistry;
import com.videopost.publisher.VideoPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PublishingCoordinatorTest {

    @Mock
    private VideoRepository videoRepository;

    @Mock
    private PublicationRepository publicationRepository;

    @Mock
    private VideoPublisher tiktokPublisher;

    @Mock
    private VideoPublisher facebookPublisher;

    @Mock
    private VideoPublisher kwaiPublisher;

    private PublisherRegistry publisherRegistry;
    private PublishingCoordinator coordinator;
    private AppConfig config;

    @BeforeEach
    void setUp() {
        publisherRegistry = new PublisherRegistry();
        when(tiktokPublisher.platform()).thenReturn(Platform.TIKTOK);
        when(facebookPublisher.platform()).thenReturn(Platform.FACEBOOK);
        when(kwaiPublisher.platform()).thenReturn(Platform.KWAI);

        publisherRegistry.register(tiktokPublisher)
                         .register(facebookPublisher)
                         .register(kwaiPublisher);

        coordinator = new PublishingCoordinator(videoRepository, publicationRepository, publisherRegistry);

        config = new AppConfig();
        config.setPlatformEnabled(Platform.TIKTOK, true);
        config.setPlatformEnabled(Platform.FACEBOOK, true);
        config.setPlatformEnabled(Platform.KWAI, true);
    }

    @Test
    @DisplayName("Cenário de Sucesso Parcial: TikTok OK, Facebook OK, Kwai Falha -> Vídeo PARTIALLY_COMPLETED")
    void shouldHandlePartialFailureWithIsolation() {
        Video video = Video.createNew("moranguinha-01.mp4", "/videos/moranguinha-01.mp4", VideoMetadata.empty());

        when(tiktokPublisher.isAuthenticated()).thenReturn(true);
        when(facebookPublisher.isAuthenticated()).thenReturn(true);
        when(kwaiPublisher.isAuthenticated()).thenReturn(true);

        when(tiktokPublisher.publish(video)).thenReturn(PublishResult.success("tt-id-1"));
        when(facebookPublisher.publish(video)).thenReturn(PublishResult.success("fb-id-2"));
        when(kwaiPublisher.publish(video)).thenReturn(PublishResult.failure("Kwai quota exceeded", true));

        PublishingCoordinator.ExecutionSummary summary = coordinator.publishVideo(video, config, false);

        assertThat(summary.video().getStatus()).isEqualTo(VideoStatus.PARTIALLY_COMPLETED);
        assertThat(summary.platformResults()).hasSize(3);

        verify(tiktokPublisher).publish(video);
        verify(facebookPublisher).publish(video);
        verify(kwaiPublisher).publish(video);

        // Verifica que as publicações de sucesso não foram afetadas pela falha do Kwai
        ArgumentCaptor<Publication> pubCaptor = ArgumentCaptor.forClass(Publication.class);
        verify(publicationRepository, atLeastOnce()).update(pubCaptor.capture());

        List<Publication> updatedPubs = pubCaptor.getAllValues();
        assertThat(updatedPubs.stream().anyMatch(p -> p.getPlatform() == Platform.TIKTOK && p.isPublished())).isTrue();
        assertThat(updatedPubs.stream().anyMatch(p -> p.getPlatform() == Platform.FACEBOOK && p.isPublished())).isTrue();
        assertThat(updatedPubs.stream().anyMatch(p -> p.getPlatform() == Platform.KWAI && p.isFailed())).isTrue();
    }

    @Test
    @DisplayName("Garantia de Idempotência: Não deve republicar se a plataforma já tiver status PUBLISHED")
    void shouldNotRepublishIfAlreadyPublished() {
        Video video = Video.createNew("moranguinha-01.mp4", "/videos/moranguinha-01.mp4", VideoMetadata.empty());

        // Simula publicação existente no banco com status PUBLISHED
        Publication existingTiktok = Publication.createPending(video.getId(), Platform.TIKTOK);
        existingTiktok.markPublished("previous-tt-id", Instant.now());

        when(publicationRepository.findByVideoIdAndPlatform(video.getId(), Platform.TIKTOK))
                .thenReturn(Optional.of(existingTiktok));

        when(facebookPublisher.isAuthenticated()).thenReturn(true);
        when(facebookPublisher.publish(video)).thenReturn(PublishResult.success("fb-99"));

        when(kwaiPublisher.isAuthenticated()).thenReturn(true);
        when(kwaiPublisher.publish(video)).thenReturn(PublishResult.success("kw-88"));

        PublishingCoordinator.ExecutionSummary summary = coordinator.publishVideo(video, config, false);

        // TikTok NUNCA deve ser invocado novamente
        verify(tiktokPublisher, never()).publish(any());
        // Facebook e Kwai devem ser executados normalmente
        verify(facebookPublisher).publish(video);
        verify(kwaiPublisher).publish(video);

        assertThat(summary.video().getStatus()).isEqualTo(VideoStatus.COMPLETED);
    }

    @Test
    @DisplayName("Modo Dry-Run: Não deve invocar os publishers reais nem alterar persistência")
    void shouldNotInvokePublishersOnDryRun() {
        Video video = Video.createNew("moranguinha-01.mp4", "/videos/moranguinha-01.mp4", VideoMetadata.of("Legenda teste", List.of("#tag")));

        when(tiktokPublisher.isAuthenticated()).thenReturn(true);
        when(facebookPublisher.isAuthenticated()).thenReturn(true);
        when(kwaiPublisher.isAuthenticated()).thenReturn(false);

        PublishingCoordinator.ExecutionSummary summary = coordinator.publishVideo(video, config, true);

        assertThat(summary.dryRun()).isTrue();
        assertThat(summary.platformResults()).hasSize(3);

        verify(tiktokPublisher, never()).publish(any());
        verify(facebookPublisher, never()).publish(any());
        verify(kwaiPublisher, never()).publish(any());
        verify(videoRepository, never()).update(any());
        verify(publicationRepository, never()).update(any());
    }

    @Test
    @DisplayName("Cenário de Sucesso Total: Todas as plataformas OK -> Vídeo COMPLETED")
    void shouldCompleteVideoWhenAllPlatformsSucceed() {
        Video video = Video.createNew("video.mp4", "/path/video.mp4", VideoMetadata.empty());

        when(tiktokPublisher.isAuthenticated()).thenReturn(true);
        when(facebookPublisher.isAuthenticated()).thenReturn(true);
        when(kwaiPublisher.isAuthenticated()).thenReturn(true);

        when(tiktokPublisher.publish(video)).thenReturn(PublishResult.success("id-1"));
        when(facebookPublisher.publish(video)).thenReturn(PublishResult.success("id-2"));
        when(kwaiPublisher.publish(video)).thenReturn(PublishResult.success("id-3"));

        PublishingCoordinator.ExecutionSummary summary = coordinator.publishVideo(video, config, false);

        assertThat(summary.video().getStatus()).isEqualTo(VideoStatus.COMPLETED);
        verify(videoRepository, atLeastOnce()).update(video);
    }
}
