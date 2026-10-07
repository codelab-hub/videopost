package com.videopost.application;

import com.videopost.application.service.QueueService;
import com.videopost.domain.model.Platform;
import com.videopost.domain.model.Publication;
import com.videopost.domain.model.PublicationStatus;
import com.videopost.domain.model.Video;
import com.videopost.domain.model.VideoMetadata;
import com.videopost.domain.model.VideoStatus;
import com.videopost.domain.repository.PublicationRepository;
import com.videopost.domain.repository.VideoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueueServiceTest {

    @Mock
    private VideoRepository videoRepository;

    @Mock
    private PublicationRepository publicationRepository;

    private QueueService queueService;

    @BeforeEach
    void setUp() {
        queueService = new QueueService(videoRepository, publicationRepository);
    }

    @Test
    @DisplayName("Deve retornar itens da fila agregando publicações associadas")
    void shouldReturnQueueItemsWithPublications() {
        Video video = Video.createNew("v1.mp4", "/videos/v1.mp4", VideoMetadata.empty());
        Publication pub = Publication.createPending(video.getId(), Platform.TIKTOK);

        when(videoRepository.findPendingOrScheduled()).thenReturn(List.of(video));
        when(publicationRepository.findByVideoId(video.getId())).thenReturn(List.of(pub));

        List<QueueService.QueueItem> queue = queueService.getPendingOrScheduledQueue();

        assertThat(queue).hasSize(1);
        assertThat(queue.getFirst().video().getFilename()).isEqualTo("v1.mp4");
        assertThat(queue.getFirst().publications()).hasSize(1);
    }

    @Test
    @DisplayName("Deve calcular estatísticas corretas da fila")
    void shouldCalculateCorrectQueueStats() {
        when(videoRepository.countByStatus(VideoStatus.PENDING)).thenReturn(5L);
        when(videoRepository.countByStatus(VideoStatus.SCHEDULED)).thenReturn(7L);
        when(videoRepository.countByStatus(VideoStatus.PROCESSING)).thenReturn(3L);
        when(videoRepository.countByStatus(VideoStatus.COMPLETED)).thenReturn(42L);
        when(videoRepository.countByStatus(VideoStatus.PARTIALLY_COMPLETED)).thenReturn(1L);
        when(videoRepository.countByStatus(VideoStatus.FAILED)).thenReturn(2L);
        when(videoRepository.findAll()).thenReturn(List.of()); // dummy
        when(publicationRepository.countByStatus(PublicationStatus.PUBLISHED)).thenReturn(80L);
        when(publicationRepository.countByStatus(PublicationStatus.FAILED)).thenReturn(4L);

        QueueService.QueueStats stats = queueService.getStats();

        assertThat(stats.pendingVideos()).isEqualTo(12L); // 5 + 7
        assertThat(stats.processingVideos()).isEqualTo(3L);
        assertThat(stats.completedVideos()).isEqualTo(42L);
        assertThat(stats.partiallyCompletedVideos()).isEqualTo(1L);
        assertThat(stats.failedVideos()).isEqualTo(2L);
        assertThat(stats.publishedPublications()).isEqualTo(80L);
        assertThat(stats.failedPublications()).isEqualTo(4L);
    }
}
