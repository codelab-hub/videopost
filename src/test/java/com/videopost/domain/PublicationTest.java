package com.videopost.domain;

import com.videopost.domain.model.Platform;
import com.videopost.domain.model.Publication;
import com.videopost.domain.model.PublicationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PublicationTest {

    @Test
    @DisplayName("Deve inicializar publicação pendente com zero tentativas")
    void shouldInitializePendingPublication() {
        Publication pub = Publication.createPending("vid-1", Platform.TIKTOK);

        assertThat(pub.getStatus()).isEqualTo(PublicationStatus.PENDING);
        assertThat(pub.getAttempts()).isZero();
        assertThat(pub.getExternalId()).isNull();
        assertThat(pub.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("Deve incrementar tentativas ao marcar como PROCESSING")
    void shouldIncrementAttemptsOnProcessing() {
        Publication pub = Publication.createPending("vid-1", Platform.TIKTOK);

        pub.markProcessing();
        assertThat(pub.getAttempts()).isEqualTo(1);
        assertThat(pub.getStatus()).isEqualTo(PublicationStatus.PROCESSING);
        assertThat(pub.getLastAttemptAt()).isNotNull();

        pub.markProcessing();
        assertThat(pub.getAttempts()).isEqualTo(2);
    }

    @Test
    @DisplayName("Deve registrar sucesso e id externo ao marcar como PUBLISHED")
    void shouldRecordSuccessOnPublished() {
        Publication pub = Publication.createPending("vid-1", Platform.FACEBOOK);
        pub.markProcessing();

        Instant now = Instant.now();
        pub.markPublished("fb_video_999", now);

        assertThat(pub.getStatus()).isEqualTo(PublicationStatus.PUBLISHED);
        assertThat(pub.getExternalId()).isEqualTo("fb_video_999");
        assertThat(pub.getPublishedAt()).isEqualTo(now);
        assertThat(pub.getErrorMessage()).isNull();
        assertThat(pub.isPublished()).isTrue();
    }

    @Test
    @DisplayName("Deve registrar erro e validar política de retry")
    void shouldRecordErrorAndCheckRetry() {
        Publication pub = Publication.createPending("vid-1", Platform.KWAI);
        pub.markProcessing(); // attempt 1
        pub.markFailed("Erro 500", Instant.now());

        assertThat(pub.getStatus()).isEqualTo(PublicationStatus.FAILED);
        assertThat(pub.getErrorMessage()).isEqualTo("Erro 500");
        assertThat(pub.isFailed()).isTrue();
        assertThat(pub.canRetry(3)).isTrue();

        pub.markProcessing(); // attempt 2
        pub.markFailed("Erro 500", Instant.now());
        assertThat(pub.canRetry(3)).isTrue();

        pub.markProcessing(); // attempt 3
        pub.markFailed("Erro 500", Instant.now());
        assertThat(pub.canRetry(3)).isFalse(); // Limite atingido
    }
}
