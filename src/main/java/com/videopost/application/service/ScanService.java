package com.videopost.application.service;

import com.videopost.domain.model.Video;
import com.videopost.domain.model.VideoStatus;
import com.videopost.domain.repository.VideoRepository;
import com.videopost.infrastructure.filesystem.VideoFileScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Serviço responsável por detectar vídeos na pasta configurada, associar metadados
 * e inseri-los na fila com agendamento escalonado.
 */
public class ScanService {

    private static final Logger log = LoggerFactory.getLogger(ScanService.class);

    private final VideoFileScanner scanner;
    private final VideoRepository videoRepository;

    public ScanService(VideoFileScanner scanner, VideoRepository videoRepository) {
        this.scanner = scanner;
        this.videoRepository = videoRepository;
    }

    public record ScanResult(
            int totalDiscoveredOnDisk,
            int newVideosEnqueued,
            List<Video> currentQueue
    ) {}

    public ScanResult scanAndEnqueue(Path videosDirectory, Duration interval) {
        log.info("Iniciando escaneamento de vídeos no diretório: {}", videosDirectory);
        List<VideoFileScanner.ScannedVideo> scanned = scanner.scanDirectory(videosDirectory);

        int newlyAdded = 0;
        for (VideoFileScanner.ScannedVideo item : scanned) {
            Optional<Video> existing = videoRepository.findByFilename(item.filename());
            if (existing.isEmpty()) {
                Video newVideo = Video.createNew(item.filename(), item.videoPath().toAbsolutePath().toString(), item.metadata());
                videoRepository.save(newVideo);
                newlyAdded++;
                log.info("Novo vídeo detectado e adicionado à fila: {}", item.filename());
            }
        }

        // Reavalia e ajusta agendamentos para vídeos PENDING/SCHEDULED
        alignSchedules(interval);

        List<Video> queue = videoRepository.findPendingOrScheduled();
        log.info("Varredura concluída. Detectados: {}, Novos inseridos: {}, Fila ativa: {}",
                scanned.size(), newlyAdded, queue.size());

        return new ScanResult(scanned.size(), newlyAdded, queue);
    }

    private void alignSchedules(Duration interval) {
        List<Video> pendingVideos = videoRepository.findByStatus(VideoStatus.PENDING);
        List<Video> scheduledVideos = videoRepository.findByStatus(VideoStatus.SCHEDULED);

        List<Video> toSchedule = new ArrayList<>();
        toSchedule.addAll(scheduledVideos);
        toSchedule.addAll(pendingVideos);

        if (toSchedule.isEmpty()) {
            return;
        }

        Instant nextScheduleTime = Instant.now();
        for (Video video : toSchedule) {
            if (video.getScheduledAt() == null || video.getScheduledAt().isBefore(Instant.now())) {
                video.markScheduled(nextScheduleTime);
                videoRepository.update(video);
            } else {
                nextScheduleTime = video.getScheduledAt();
            }
            nextScheduleTime = nextScheduleTime.plus(interval);
        }
    }
}
