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
    private static final Set<String> VIDEO_EXTENSIONS = Set.of(".mp4", ".mov", ".mkv", ".m4v", ".avi", ".webm", ".flv", ".wmv");

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
        Path resolved = resolveDirectory(directory);
        if (resolved == null || !Files.exists(resolved) || !Files.isDirectory(resolved)) {
            log.warn("Diretório de vídeos não existe ou é inválido: {}", directory);
            return List.of();
        }

        List<ScannedVideo> results = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(resolved, 10, java.nio.file.FileVisitOption.FOLLOW_LINKS)) {
            List<Path> allPaths = stream.toList();
            List<Path> videoFiles = allPaths.stream()
                    .filter(Files::isRegularFile)
                    .filter(this::isVideoFile)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .toList();

            if (videoFiles.isEmpty()) {
                long icloudCount = allPaths.stream().filter(p -> p.getFileName().toString().endsWith(".icloud")).count();
                if (icloudCount > 0) {
                    System.out.println("⚠️ " + icloudCount + " arquivos estão na nuvem do iCloud e não foram baixados localmente.");
                    System.out.println("   No Finder, clique no ícone da nuvem ao lado dos arquivos para baixá-los.");
                }
                List<String> subItems = allPaths.stream()
                        .filter(p -> !p.equals(resolved))
                        .map(p -> resolved.relativize(p).toString())
                        .limit(20)
                        .toList();
                if (!subItems.isEmpty()) {
                    System.out.println("Conteúdo detectado na pasta:");
                    for (String item : subItems) {
                        System.out.println(" • " + item);
                    }
                }
            }

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

    private Path resolveDirectory(Path directory) {
        if (directory == null) {
            return null;
        }
        if (Files.exists(directory) && Files.isDirectory(directory)) {
            return directory;
        }
        // Se não encontrou o caminho exato, tenta o diretório pai ou variações
        Path parent = directory.getParent();
        if (parent != null && Files.exists(parent) && Files.isDirectory(parent)) {
            String targetName = normalizeString(directory.getFileName().toString());
            try (Stream<Path> list = Files.list(parent)) {
                java.util.Optional<Path> match = list
                        .filter(Files::isDirectory)
                        .filter(p -> {
                            String name = normalizeString(p.getFileName().toString());
                            return name.equals(targetName) || name.contains(targetName) || targetName.contains(name);
                        })
                        .findFirst();
                if (match.isPresent()) {
                    log.info("Diretório correspondente localizado: {}", match.get());
                    return match.get();
                }
            } catch (IOException ignored) {}

            // Usa o diretório pai para escaneamento recursivo (walk de profundidade 4)
            log.info("Utilizando diretório pai existente para escaneamento recursivo: {}", parent);
            return parent;
        }
        return directory;
    }

    private String normalizeString(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT)
                .replace(".nosync", "")
                .replace("í", "i")
                .replace("é", "e")
                .replace("á", "a")
                .replace("ó", "o")
                .replace("ú", "u")
                .replace("ã", "a")
                .replace("õ", "o")
                .replaceAll("[^a-z0-9]", "");
    }
}
