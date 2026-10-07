package com.videopost.application.service;

import com.videopost.domain.model.Publication;
import com.videopost.domain.model.PublicationStatus;
import com.videopost.domain.model.Video;
import com.videopost.domain.model.VideoStatus;
import com.videopost.domain.repository.PublicationRepository;
import com.videopost.domain.repository.VideoRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Serviço de consulta e visualização do estado da fila e estatísticas.
 */
public class QueueService {

    private final VideoRepository videoRepository;
    private final PublicationRepository publicationRepository;

    public QueueService(VideoRepository videoRepository, PublicationRepository publicationRepository) {
        this.videoRepository = videoRepository;
        this.publicationRepository = publicationRepository;
    }

    public record QueueItem(Video video, List<Publication> publications) {}

    public record QueueStats(
            long pendingVideos,
            long processingVideos,
            long completedVideos,
            long partiallyCompletedVideos,
            long failedVideos,
            long totalVideos,
            long publishedPublications,
            long failedPublications
    ) {}

    public List<QueueItem> getFullQueue() {
        List<Video> videos = videoRepository.findAll();
        List<QueueItem> items = new ArrayList<>();
        for (Video v : videos) {
            List<Publication> pubs = publicationRepository.findByVideoId(v.getId());
            items.add(new QueueItem(v, pubs));
        }
        return items;
    }

    public List<QueueItem> getPendingOrScheduledQueue() {
        List<Video> videos = videoRepository.findPendingOrScheduled();
        List<QueueItem> items = new ArrayList<>();
        for (Video v : videos) {
            List<Publication> pubs = publicationRepository.findByVideoId(v.getId());
            items.add(new QueueItem(v, pubs));
        }
        return items;
    }

    public QueueStats getStats() {
        long pending = videoRepository.countByStatus(VideoStatus.PENDING) + videoRepository.countByStatus(VideoStatus.SCHEDULED);
        long processing = videoRepository.countByStatus(VideoStatus.PROCESSING);
        long completed = videoRepository.countByStatus(VideoStatus.COMPLETED);
        long partial = videoRepository.countByStatus(VideoStatus.PARTIALLY_COMPLETED);
        long failed = videoRepository.countByStatus(VideoStatus.FAILED);
        long total = videoRepository.findAll().size();

        long pubPublished = publicationRepository.countByStatus(PublicationStatus.PUBLISHED);
        long pubFailed = publicationRepository.countByStatus(PublicationStatus.FAILED);

        return new QueueStats(pending, processing, completed, partial, failed, total, pubPublished, pubFailed);
    }
}
