package com.videopost.application;

import com.videopost.application.service.RetryService;
import com.videopost.domain.model.Platform;
import com.videopost.domain.model.Publication;
import com.videopost.domain.model.PublicationStatus;
import com.videopost.domain.model.PublishResult;
import com.videopost.domain.model.Video;
import com.videopost.domain.model.VideoMetadata;
import com.videopost.domain.model.VideoStatus;
import com.videopost.domain.repository.PublicationRepository;
import com.videopost.domain.repository.VideoRepository;
import com.videopost.publisher.PublisherRegistry;
import com.videopost.publisher.VideoPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetryServiceTest {

    @Mock
    private VideoRepository videoRepository;

    @Mock
    private PublicationRepository publicationRepository;

    @Mock
    private VideoPublisher kwaiPublisher;

    private PublisherRegistry publisherRegistry;
    private RetryService retryService;

    @BeforeEach
    void setUp() {
        publisherRegistry = new PublisherRegistry();
        when(kwaiPublisher.platform()).thenReturn(Platform.KWAI);
        publisherRegistry.register(kwaiPublisher);

        retryService = new RetryService(videoRepository, publicationRepository, publisherRegistry);
    }

    @Test
    @DisplayName("Deve reprocessar com sucesso publicação FAILED e transitar vídeo para COMPLETED")
    void shouldRetryFailedPublicationSuccessfully() {
        Video video = Video.createNew("moranguinha-01.mp4", "/videos/moranguinha-01.mp4", VideoMetadata.empty());
        video.setStatus(VideoStatus.PARTIALLY_COMPLETED);

        // Suponha TikTok e Facebook já publicados
        Publication pubTiktok = Publication.createPending(video.getId(), Platform.TIKTOK);
        pubTiktok.markPublished("tt-1", Instant.now());

        // Kwai falhou anteriormente
        Publication pubKwai = Publication.createPending(video.getId(), Platform.KWAI);
        pubKwai.markProcessing();
        pubKwai.markFailed("Network timeout", Instant.now());

        when(publicationRepository.findById(pubKwai.getId())).thenReturn(Optional.of(pubKwai));
        when(videoRepository.findById(video.getId())).thenReturn(Optional.of(video));
        when(kwaiPublisher.isAuthenticated()).thenReturn(true);
        when(kwaiPublisher.publish(video)).thenReturn(PublishResult.success("kw-retry-999"));
        when(publicationRepository.findByVideoId(video.getId())).thenReturn(List.of(pubTiktok, pubKwai));

        boolean result = retryService.retryPublication(pubKwai.getId(), 3);

        assertThat(result).isTrue();
        assertThat(pubKwai.getStatus()).isEqualTo(PublicationStatus.PUBLISHED);
        assertThat(pubKwai.getExternalId()).isEqualTo("kw-retry-999");
        assertThat(video.getStatus()).isEqualTo(VideoStatus.COMPLETED);

        verify(kwaiPublisher).publish(video);
        verify(publicationRepository, atLeastOnce()).update(pubKwai);
        verify(videoRepository).update(video);
    }

    @Test
    @DisplayName("Idempotência no Retry: Não deve tentar retry em publicação com status PUBLISHED")
    void shouldRefuseRetryOnAlreadyPublished() {
        Publication pub = Publication.createPending("vid-1", Platform.KWAI);
        pub.markPublished("kw-already-published", Instant.now());

        when(publicationRepository.findById(pub.getId())).thenReturn(Optional.of(pub));

        boolean result = retryService.retryPublication(pub.getId(), 3);

        assertThat(result).isFalse();
        verify(kwaiPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("Não deve executar retry se número de tentativas exceder o máximo permitido")
    void shouldRefuseRetryIfMaxAttemptsExceeded() {
        Publication pub = Publication.createPending("vid-1", Platform.KWAI);
        pub.setAttempts(3);
        pub.setStatus(PublicationStatus.FAILED);

        when(publicationRepository.findById(pub.getId())).thenReturn(Optional.of(pub));

        boolean result = retryService.retryPublication(pub.getId(), 3);

        assertThat(result).isFalse();
        verify(kwaiPublisher, never()).publish(any());
    }
}
