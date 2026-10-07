package com.videopost.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.videopost.application.service.ScanService;
import com.videopost.domain.model.Video;
import com.videopost.domain.model.VideoStatus;
import com.videopost.domain.repository.VideoRepository;
import com.videopost.infrastructure.filesystem.JsonMetadataParser;
import com.videopost.infrastructure.filesystem.VideoFileScanner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScanServiceTest {

    @Mock
    private VideoRepository videoRepository;

    private ScanService scanService;

    @BeforeEach
    void setUp() {
        JsonMetadataParser parser = new JsonMetadataParser(new ObjectMapper());
        VideoFileScanner scanner = new VideoFileScanner(parser);
        scanService = new ScanService(scanner, videoRepository);
    }

    @Test
    @DisplayName("Deve detectar vídeos no diretório e agendá-los com intervalo configurado")
    void shouldScanAndScheduleVideos(@TempDir Path tempDir) throws IOException {
        Path video1 = tempDir.resolve("moranguinha-01.mp4");
        Path meta1 = tempDir.resolve("moranguinha-01.json");
        Path video2 = tempDir.resolve("moranguinha-02.mp4");

        Files.writeString(video1, "dummy-video-content-1");
        Files.writeString(meta1, "{\"caption\": \"Episodio 1\", \"hashtags\": [\"#moranguinha\"]}");
        Files.writeString(video2, "dummy-video-content-2");

        List<Video> saved = new ArrayList<>();
        when(videoRepository.findByFilename(anyString())).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            saved.add(invocation.getArgument(0));
            return null;
        }).when(videoRepository).save(any(Video.class));

        when(videoRepository.findByStatus(VideoStatus.PENDING)).thenReturn(saved);
        when(videoRepository.findPendingOrScheduled()).thenReturn(saved);

        ScanService.ScanResult result = scanService.scanAndEnqueue(tempDir, Duration.ofMinutes(30));

        assertThat(result.totalDiscoveredOnDisk()).isEqualTo(2);
        assertThat(result.newVideosEnqueued()).isEqualTo(2);

        verify(videoRepository, times(2)).save(any(Video.class));
    }
}
