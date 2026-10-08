package com.videopost.application.service;

import com.videopost.domain.model.Platform;
import com.videopost.domain.model.Video;
import com.videopost.domain.repository.VideoRepository;
import com.videopost.infrastructure.config.AppConfig;
import com.videopost.publisher.PublisherRegistry;
import com.videopost.publisher.VideoPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Orquestrador do ciclo contínuo de agendamento e publicação.
 * Não utiliza Thread.sleep disperso: utiliza sincronização controlada com interrupção limpa.
 */
public class ScheduleRunner {

    private static final Logger log = LoggerFactory.getLogger(ScheduleRunner.class);

    private final ScanService scanService;
    private final PublishingCoordinator publishingCoordinator;
    private final VideoRepository videoRepository;
    private final PublisherRegistry publisherRegistry;
    private final AppConfig config;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final CountDownLatch stopSignal = new CountDownLatch(1);

    public ScheduleRunner(ScanService scanService,
                          PublishingCoordinator publishingCoordinator,
                          VideoRepository videoRepository,
                          PublisherRegistry publisherRegistry,
                          AppConfig config) {
        this.scanService = scanService;
        this.publishingCoordinator = publishingCoordinator;
        this.videoRepository = videoRepository;
        this.publisherRegistry = publisherRegistry;
        this.config = config;
    }

    public interface ScheduleEventListener {
        void onStartup(String profile, Duration interval, int videosFound, List<PlatformAuthStatus> platforms);
        void onPublishStart(Instant time, Video video);
        void onPlatformResult(Platform platform, boolean success, String message);
        void onNextScheduled(Video nextVideo, Instant scheduledTime);
        void onQueueEmpty();
        void onOutsideWorkingHours(LocalTime current, String start, String end);
        void onStopped();
    }

    public record PlatformAuthStatus(Platform platform, boolean authenticated) {}

    public void run(ScheduleEventListener listener, boolean runOnce) {
        running.set(true);
        Duration interval = config.getSchedule().parseIntervalDuration();
        Path videosDir = Path.of(config.getVideos().getDirectory());

        // 1. Escaneamento inicial
        ScanService.ScanResult scanResult = scanService.scanAndEnqueue(videosDir, interval);

        // 2. Verificação das plataformas
        List<PlatformAuthStatus> authStatuses = new ArrayList<>();
        for (Platform platform : Platform.values()) {
            if (config.isPlatformEnabled(platform)) {
                boolean auth = publisherRegistry.getPublisher(platform)
                        .map(VideoPublisher::isAuthenticated)
                        .orElse(false);
                authStatuses.add(new PlatformAuthStatus(platform, auth));
            }
        }

        listener.onStartup(config.getActiveProfile(), interval, scanResult.totalDiscoveredOnDisk(), authStatuses);

        while (running.get()) {
            // Verifica horário de funcionamento
            if (config.getSchedule().getWorkingHours() != null && config.getSchedule().getWorkingHours().isEnabled()) {
                LocalTime now = LocalTime.now();
                if (!config.getSchedule().getWorkingHours().isWithinHours(now)) {
                    listener.onOutsideWorkingHours(
                            now,
                            config.getSchedule().getWorkingHours().getStart(),
                            config.getSchedule().getWorkingHours().getEnd()
                    );
                    if (waitForNextCheck(Duration.ofMinutes(5))) break;
                    continue;
                }
            }

            // Busca próximo vídeo da fila
            List<Video> pendingQueue = videoRepository.findPendingOrScheduled();
            if (pendingQueue.isEmpty()) {
                listener.onQueueEmpty();
                if (runOnce) {
                    break;
                }
                // Aguarda novo escaneamento após intervalo
                if (waitForNextCheck(interval)) break;
                scanService.scanAndEnqueue(videosDir, interval);
                continue;
            }

            Video currentVideo = pendingQueue.getFirst();

            // Verifica se o horário agendado já chegou
            Instant now = Instant.now();
            if (currentVideo.getScheduledAt() != null && currentVideo.getScheduledAt().isAfter(now)) {
                Duration waitTime = Duration.between(now, currentVideo.getScheduledAt());
                listener.onNextScheduled(currentVideo, currentVideo.getScheduledAt());
                if (runOnce) {
                    break;
                }
                if (waitForNextCheck(waitTime)) break;
            }

            // Publica o vídeo
            listener.onPublishStart(Instant.now(), currentVideo);
            PublishingCoordinator.ExecutionSummary summary = publishingCoordinator.publishVideo(currentVideo, config, false);

            for (PublishingCoordinator.PlatformExecutionResult res : summary.platformResults()) {
                listener.onPlatformResult(res.platform(), res.success(), res.message());
            }

            // Informa próximo vídeo
            List<Video> remaining = videoRepository.findPendingOrScheduled();
            if (!remaining.isEmpty()) {
                Video next = remaining.getFirst();
                listener.onNextScheduled(next, next.getScheduledAt());
            }

            if (runOnce) {
                break;
            }

            // Escaneia novamente para detectar novos vídeos na pasta
            scanService.scanAndEnqueue(videosDir, interval);

            // Aguarda brevemente antes de avaliar o próximo vídeo agendado na fila
            if (waitForNextCheck(Duration.ofSeconds(5))) break;
        }

        listener.onStopped();
    }

    public void stop() {
        if (running.compareAndSet(true, false)) {
            stopSignal.countDown();
            log.info("Sinal de parada do Scheduler recebido.");
        }
    }

    private boolean waitForNextCheck(Duration duration) {
        long millis = Math.max(100, duration.toMillis());
        try {
            boolean stopped = stopSignal.await(millis, TimeUnit.MILLISECONDS);
            return stopped || !running.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return true;
        }
    }
}
