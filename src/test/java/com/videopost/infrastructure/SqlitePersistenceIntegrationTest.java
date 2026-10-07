package com.videopost.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.videopost.domain.model.Platform;
import com.videopost.domain.model.Publication;
import com.videopost.domain.model.PublicationStatus;
import com.videopost.domain.model.Video;
import com.videopost.domain.model.VideoMetadata;
import com.videopost.domain.model.VideoStatus;
import com.videopost.infrastructure.persistence.DatabaseConnectionManager;
import com.videopost.infrastructure.persistence.SqlitePublicationRepository;
import com.videopost.infrastructure.persistence.SqliteVideoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqlitePersistenceIntegrationTest {

    private DatabaseConnectionManager dbManager;
    private SqliteVideoRepository videoRepository;
    private SqlitePublicationRepository publicationRepository;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        Path dbPath = tempDir.resolve("videopost_test.db");
        dbManager = new DatabaseConnectionManager(dbPath);
        videoRepository = new SqliteVideoRepository(dbManager, new ObjectMapper());
        publicationRepository = new SqlitePublicationRepository(dbManager);
    }

    @Test
    @DisplayName("Deve persistir, buscar e atualizar vídeo no SQLite com integridade")
    void shouldPersistAndRetrieveVideo() {
        Video video = Video.createNew("test-001.mp4", "/videos/test-001.mp4", VideoMetadata.of("Legenda 1", List.of("#pix")));
        videoRepository.save(video);

        Optional<Video> found = videoRepository.findById(video.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getFilename()).isEqualTo("test-001.mp4");
        assertThat(found.get().getCaption()).isEqualTo("Legenda 1");
        assertThat(found.get().getHashtags()).containsExactly("#pix");
        assertThat(found.get().getStatus()).isEqualTo(VideoStatus.PENDING);

        // Atualização
        video.setStatus(VideoStatus.SCHEDULED);
        video.setScheduledAt(Instant.now());
        videoRepository.update(video);

        Optional<Video> updated = videoRepository.findById(video.getId());
        assertThat(updated).isPresent();
        assertThat(updated.get().getStatus()).isEqualTo(VideoStatus.SCHEDULED);
        assertThat(updated.get().getScheduledAt()).isNotNull();
    }

    @Test
    @DisplayName("Deve garantir unicidade de publicação por (video_id, platform) no banco")
    void shouldEnforceUniquePublicationConstraint() {
        Video video = Video.createNew("video.mp4", "/path/video.mp4", VideoMetadata.empty());
        videoRepository.save(video);

        Publication pub1 = Publication.createPending(video.getId(), Platform.TIKTOK);
        publicationRepository.save(pub1);

        Publication duplicate = Publication.createPending(video.getId(), Platform.TIKTOK);

        assertThatThrownBy(() -> publicationRepository.save(duplicate))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("SQLite");
    }

    @Test
    @DisplayName("Deve gerenciar status de publicações e consultas de falhas")
    void shouldQueryFailedPublications() {
        Video video = Video.createNew("video.mp4", "/path/video.mp4", VideoMetadata.empty());
        videoRepository.save(video);

        Publication pubTiktok = Publication.createPending(video.getId(), Platform.TIKTOK);
        pubTiktok.markPublished("tt-999", Instant.now());
        publicationRepository.save(pubTiktok);

        Publication pubKwai = Publication.createPending(video.getId(), Platform.KWAI);
        pubKwai.markProcessing();
        pubKwai.markFailed("API Error", Instant.now());
        publicationRepository.save(pubKwai);

        List<Publication> failed = publicationRepository.findFailed();
        assertThat(failed).hasSize(1);
        assertThat(failed.getFirst().getPlatform()).isEqualTo(Platform.KWAI);

        assertThat(publicationRepository.countByStatus(PublicationStatus.PUBLISHED)).isEqualTo(1);
        assertThat(publicationRepository.countByStatus(PublicationStatus.FAILED)).isEqualTo(1);
    }
}
