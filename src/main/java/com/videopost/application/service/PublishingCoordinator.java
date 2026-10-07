package com.videopost.application.service;

import com.videopost.domain.model.Platform;
import com.videopost.domain.model.Publication;
import com.videopost.domain.model.PublicationStatus;
import com.videopost.domain.model.PublishResult;
import com.videopost.domain.model.Video;
import com.videopost.domain.model.VideoStatus;
import com.videopost.domain.repository.PublicationRepository;
import com.videopost.domain.repository.VideoRepository;
import com.videopost.infrastructure.config.AppConfig;
import com.videopost.publisher.PublisherRegistry;
import com.videopost.publisher.VideoPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Coordenador central do fluxo de publicação com isolamento de falhas e idempotência estrita.
 */
public class PublishingCoordinator {

    private static final Logger log = LoggerFactory.getLogger(PublishingCoordinator.class);

    private final VideoRepository videoRepository;
    private final PublicationRepository publicationRepository;
    private final PublisherRegistry publisherRegistry;

    public PublishingCoordinator(VideoRepository videoRepository,
                                 PublicationRepository publicationRepository,
                                 PublisherRegistry publisherRegistry) {
        this.videoRepository = videoRepository;
        this.publicationRepository = publicationRepository;
        this.publisherRegistry = publisherRegistry;
    }

    public record ExecutionSummary(
            Video video,
            List<PlatformExecutionResult> platformResults,
            boolean dryRun
    ) {}

    public record PlatformExecutionResult(
            Platform platform,
            boolean success,
            String externalId,
            String message,
            boolean wasAlreadyPublished
    ) {}

    /**
     * Executa a publicação de um vídeo nas plataformas habilitadas.
     *
     * @param video O vídeo a ser publicado.
     * @param config Configurações contendo plataformas habilitadas.
     * @param dryRun Se verdadeiro, simula o processo sem executar chamadas reais.
     * @return Resumo da execução por plataforma.
     */
    public ExecutionSummary publishVideo(Video video, AppConfig config, boolean dryRun) {
        log.info("Iniciando publicação para o vídeo id={} filename='{}' (dryRun={})",
                video.getId(), video.getFilename(), dryRun);

        if (dryRun) {
            return executeDryRun(video, config);
        }

        // 1. Marca vídeo como PROCESSING
        video.markProcessing();
        videoRepository.update(video);

        List<PlatformExecutionResult> results = new ArrayList<>();
        List<Publication> currentPublications = new ArrayList<>();

        // Identifica plataformas habilitadas
        List<Platform> targetPlatforms = getTargetPlatforms(config);

        for (Platform platform : targetPlatforms) {
            // 2. Mecanismo de Idempotência: busca publicação existente
            Optional<Publication> existingPubOpt = publicationRepository.findByVideoIdAndPlatform(video.getId(), platform);
            Publication publication;

            if (existingPubOpt.isPresent()) {
                publication = existingPubOpt.get();
                // REGRA DE OURO: Nunca republique automaticamente algo já confirmado como publicado!
                if (publication.isPublished()) {
                    log.info("Publicação já confirmada anteriormente. Pulando plataforma={} videoId={}",
                            platform, video.getId());
                    results.add(new PlatformExecutionResult(
                            platform, true, publication.getExternalId(), "Já publicado anteriormente (Idempotência garantida)", true
                    ));
                    currentPublications.add(publication);
                    continue;
                }
            } else {
                publication = Publication.createPending(video.getId(), platform);
                publicationRepository.save(publication);
            }

            // 3. Recupera o publisher correspondente
            Optional<VideoPublisher> publisherOpt = publisherRegistry.getPublisher(platform);
            if (publisherOpt.isEmpty()) {
                String errorMsg = "Nenhum publisher registrado para a plataforma " + platform;
                publication.markFailed(errorMsg, Instant.now());
                publicationRepository.update(publication);
                results.add(new PlatformExecutionResult(platform, false, null, errorMsg, false));
                currentPublications.add(publication);
                continue;
            }

            VideoPublisher publisher = publisherOpt.get();
            if (!publisher.isAuthenticated()) {
                String errorMsg = "Plataforma " + platform.getDisplayName() + " não autenticada no perfil atual.";
                publication.markFailed(errorMsg, Instant.now());
                publicationRepository.update(publication);
                results.add(new PlatformExecutionResult(platform, false, null, errorMsg, false));
                currentPublications.add(publication);
                continue;
            }

            // 4. Executa a publicação isolada para esta plataforma
            publication.markProcessing();
            publicationRepository.update(publication);

            log.info("Publishing video={} platform={}", video.getId(), platform);
            PublishResult publishResult;
            try {
                publishResult = publisher.publish(video);
            } catch (Exception e) {
                log.error("Erro inesperado ao chamar publisher para platform={}: {}", platform, e.getMessage(), e);
                publishResult = PublishResult.failure("Exceção não tratada: " + e.getMessage(), true);
            }

            if (publishResult.success()) {
                log.info("Publication successful video={} platform={} externalId={}",
                        video.getId(), platform, publishResult.externalId());
                publication.markPublished(publishResult.externalId(), Instant.now());
                publicationRepository.update(publication);
                results.add(new PlatformExecutionResult(
                        platform, true, publishResult.externalId(), "Publicado com sucesso", false
                ));
            } else {
                String error = publishResult.errorMessage();
                log.warn("Publication failed video={} platform={} error={}", video.getId(), platform, error);
                publication.markFailed(error, Instant.now());
                publicationRepository.update(publication);
                results.add(new PlatformExecutionResult(
                        platform, false, null, error, false
                ));
            }

            currentPublications.add(publication);
        }

        // 5. Atualiza o status agregado do vídeo (COMPLETED, PARTIALLY_COMPLETED, FAILED)
        video.evaluateStatusFromPublications(currentPublications);
        videoRepository.update(video);

        log.info("Processamento finalizado para o vídeo id={} novo status={}", video.getId(), video.getStatus());
        return new ExecutionSummary(video, results, false);
    }

    private ExecutionSummary executeDryRun(Video video, AppConfig config) {
        List<PlatformExecutionResult> dryResults = new ArrayList<>();
        List<Platform> targetPlatforms = getTargetPlatforms(config);

        for (Platform platform : targetPlatforms) {
            Optional<VideoPublisher> publisherOpt = publisherRegistry.getPublisher(platform);
            boolean auth = publisherOpt.map(VideoPublisher::isAuthenticated).orElse(false);
            String statusNote = auth ? "Pronto para publicação (Autenticado)" : "Não autenticado";
            dryResults.add(new PlatformExecutionResult(platform, auth, null, statusNote, false));
        }

        return new ExecutionSummary(video, dryResults, true);
    }

    private List<Platform> getTargetPlatforms(AppConfig config) {
        List<Platform> list = new ArrayList<>();
        for (Platform p : Platform.values()) {
            if (config.isPlatformEnabled(p)) {
                list.add(p);
            }
        }
        return list;
    }
}
