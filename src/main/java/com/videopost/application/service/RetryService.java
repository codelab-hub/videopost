package com.videopost.application.service;

import com.videopost.domain.model.Publication;
import com.videopost.domain.model.PublicationStatus;
import com.videopost.domain.model.PublishResult;
import com.videopost.domain.model.Video;
import com.videopost.domain.repository.PublicationRepository;
import com.videopost.domain.repository.VideoRepository;
import com.videopost.publisher.PublisherRegistry;
import com.videopost.publisher.VideoPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Serviço responsável por reprocessar publicações que falharam,
 * garantindo que apenas as plataformas com falha sejam retentadas.
 */
public class RetryService {

    private static final Logger log = LoggerFactory.getLogger(RetryService.class);

    private final VideoRepository videoRepository;
    private final PublicationRepository publicationRepository;
    private final PublisherRegistry publisherRegistry;

    public RetryService(VideoRepository videoRepository,
                        PublicationRepository publicationRepository,
                        PublisherRegistry publisherRegistry) {
        this.videoRepository = videoRepository;
        this.publicationRepository = publicationRepository;
        this.publisherRegistry = publisherRegistry;
    }

    public record RetryCandidate(
            Publication publication,
            Video video
    ) {}

    public List<RetryCandidate> getFailedPublications() {
        List<Publication> failedList = publicationRepository.findFailed();
        List<RetryCandidate> candidates = new ArrayList<>();

        for (Publication pub : failedList) {
            Optional<Video> videoOpt = videoRepository.findById(pub.getVideoId());
            videoOpt.ifPresent(v -> candidates.add(new RetryCandidate(pub, v)));
        }

        return candidates;
    }

    public boolean retryPublication(String publicationId, int maxAttempts) {
        Optional<Publication> pubOpt = publicationRepository.findById(publicationId);
        if (pubOpt.isEmpty()) {
            log.warn("Publicação não encontrada para retry: id={}", publicationId);
            return false;
        }

        Publication publication = pubOpt.get();

        // Garantia de idempotência: apenas publicações FAILED são elegíveis
        if (publication.getStatus() != PublicationStatus.FAILED) {
            log.warn("Publicação id={} não está no estado FAILED (status={}). Retry abortado por idempotência.",
                    publicationId, publication.getStatus());
            return false;
        }

        if (publication.getAttempts() >= maxAttempts) {
            log.warn("Publicação id={} excedeu o limite máximo de tentativas ({}/{}).",
                    publicationId, publication.getAttempts(), maxAttempts);
            return false;
        }

        Optional<Video> videoOpt = videoRepository.findById(publication.getVideoId());
        if (videoOpt.isEmpty()) {
            log.error("Vídeo associado id={} não encontrado.", publication.getVideoId());
            return false;
        }

        Video video = videoOpt.get();
        Optional<VideoPublisher> publisherOpt = publisherRegistry.getPublisher(publication.getPlatform());
        if (publisherOpt.isEmpty()) {
            log.error("Nenhum publisher disponível para plataforma={}", publication.getPlatform());
            return false;
        }

        VideoPublisher publisher = publisherOpt.get();
        if (!publisher.isAuthenticated()) {
            log.error("Publisher {} não está autenticado.", publication.getPlatform());
            return false;
        }

        publication.markProcessing();
        publicationRepository.update(publication);

        log.info("Executando retry da publicação id={} video={} platform={} tentativa={}/{}",
                publication.getId(), video.getFilename(), publication.getPlatform(), publication.getAttempts(), maxAttempts);

        try {
            PublishResult result = publisher.publish(video);
            if (result.success()) {
                log.info("Retry bem-sucedido! id={} platform={} externalId={}",
                        publication.getId(), publication.getPlatform(), result.externalId());
                publication.markPublished(result.externalId(), Instant.now());
                publicationRepository.update(publication);
            } else {
                log.warn("Retry falhou: id={} platform={} erro={}",
                        publication.getId(), publication.getPlatform(), result.errorMessage());
                publication.markFailed(result.errorMessage(), Instant.now());
                publicationRepository.update(publication);
            }
        } catch (Exception e) {
            log.error("Erro inesperado durante retry: {}", e.getMessage(), e);
            publication.markFailed("Erro inesperado: " + e.getMessage(), Instant.now());
            publicationRepository.update(publication);
        }

        // Atualiza status agregado do vídeo
        List<Publication> allPubs = publicationRepository.findByVideoId(video.getId());
        video.evaluateStatusFromPublications(allPubs);
        videoRepository.update(video);

        return publication.isPublished();
    }

    public int retryAll(int maxAttempts) {
        List<RetryCandidate> candidates = getFailedPublications();
        int successCount = 0;
        for (RetryCandidate candidate : candidates) {
            if (retryPublication(candidate.publication().getId(), maxAttempts)) {
                successCount++;
            }
        }
        return successCount;
    }
}
