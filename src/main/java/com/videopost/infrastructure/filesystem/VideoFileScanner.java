package com.videopost.infrastructure.filesystem;

import com.videopost.domain.model.VideoMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Scanner de arquivos de vídeo e associação automática de arquivos .json de metadados.
 */
public class VideoFileScanner {

    private static final Logger log = LoggerFactory.getLogger(VideoFileScanner.class);
    private static final Set<String> VIDEO_EXTENSIONS = Set.of(".mp4", ".mov", ".mkv", ".m4v", ".avi");

    private final JsonMetadataParser metadataParser;

    public VideoFileScanner(JsonMetadataParser metadataParser) {
        this.metadataParser = metadataParser;
    }

    public record ScannedVideo(
            Path videoPath,
            String filename,
            Path metadataPath,
            VideoMetadata metadata
    ) {}

    public List<ScannedVideo> scanDirectory(Path directory) {
        if (directory == null || !Files.exists(directory) || !Files.isDirectory(directory)) {
            log.warn("Diretório de vídeos não existe ou é inválido: {}", directory);
            return List.of();
        }

        List<ScannedVideo> results = new ArrayList<>();
        try (Stream<Path> stream = Files.list(directory)) {
            List<Path> videoFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(this::isVideoFile)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .toList();

            for (Path videoFile : videoFiles) {
                String filename = videoFile.getFileName().toString();
                Path metadataPath = findSiblingMetadataPath(videoFile);
                VideoMetadata metadata = metadataParser.parse(metadataPath);

                results.add(new ScannedVideo(videoFile, filename, metadataPath, metadata));
            }
        } catch (IOException e) {
            log.error("Erro ao listar diretório de vídeos {}: {}", directory, e.getMessage(), e);
        }

        return results;
    }

    private boolean isVideoFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return VIDEO_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    private Path findSiblingMetadataPath(Path videoPath) {
        String filename = videoPath.getFileName().toString();
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex <= 0) {
            return null;
        }
        String baseName = filename.substring(0, dotIndex);
        Path jsonCandidate = videoPath.resolveSibling(baseName + ".json");
        return Files.exists(jsonCandidate) ? jsonCandidate : null;
    }
}
