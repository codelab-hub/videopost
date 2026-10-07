package com.videopost.cli.command;

import com.videopost.application.AppContext;
import com.videopost.application.service.PublishingCoordinator;
import com.videopost.cli.ui.ConsoleUi;
import com.videopost.domain.model.Platform;
import com.videopost.domain.model.Video;
import com.videopost.domain.model.VideoMetadata;
import com.videopost.infrastructure.filesystem.JsonMetadataParser;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;

@Command(
        name = "publish",
        description = "Publica manualmente um arquivo de vídeo específico nas plataformas configuradas"
)
public class PublishCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Caminho ou nome do arquivo de vídeo (ex: moranguinha-01.mp4)")
    private String videoPathArg;

    @Option(names = {"--dry-run"}, description = "Simula a publicação sem realizar chamadas de rede ou alterar estados")
    private boolean dryRun = false;

    @Override
    public Integer call() {
        AppContext context = AppContext.defaultContext();
        Path targetFile = resolveVideoPath(context, videoPathArg);

        if (!Files.exists(targetFile)) {
            System.err.println(ConsoleUi.cross() + " Arquivo de vídeo não encontrado: " + targetFile);
            return 1;
        }

        String filename = targetFile.getFileName().toString();
        Optional<Video> videoOpt = context.getVideoRepository().findByFilename(filename);

        Video video;
        if (videoOpt.isPresent()) {
            video = videoOpt.get();
        } else {
            // Se ainda não estava no banco, faz parsing dos metadados e cadastra
            JsonMetadataParser parser = new JsonMetadataParser(new com.fasterxml.jackson.databind.ObjectMapper());
            Path siblingJson = findSiblingJson(targetFile);
            VideoMetadata metadata = parser.parse(siblingJson);
            video = Video.createNew(filename, targetFile.toAbsolutePath().toString(), metadata);
            if (!dryRun) {
                context.getVideoRepository().save(video);
            }
        }

        PublishingCoordinator coordinator = context.getPublishingCoordinator();
        PublishingCoordinator.ExecutionSummary summary = coordinator.publishVideo(video, context.getConfig(), dryRun);

        if (dryRun) {
            System.out.println(ConsoleUi.bold("DRY RUN"));
            System.out.println();
            System.out.println("Video:\n" + video.getFilename());
            System.out.println();
            System.out.println("Caption:\n" + (video.getCaption() != null && !video.getCaption().isBlank() ? video.getCaption() : "(sem legenda)"));
            if (!video.getHashtags().isEmpty()) {
                System.out.println("\nHashtags:\n" + String.join(" ", video.getHashtags()));
            }
            System.out.println();
            System.out.println("Platforms:");
            for (PublishingCoordinator.PlatformExecutionResult res : summary.platformResults()) {
                if (res.success()) {
                    System.out.println(ConsoleUi.checkmark() + " " + res.platform().getDisplayName());
                } else {
                    System.out.println(ConsoleUi.cross() + " " + res.platform().getDisplayName() + " (" + res.message() + ")");
                }
            }
            System.out.println();
            System.out.println(ConsoleUi.yellow("No publication was performed."));
            return 0;
        }

        // Modo publicação real
        System.out.println("Publicação finalizada para " + ConsoleUi.bold(video.getFilename()));
        System.out.println("Status final do vídeo: " + video.getStatus());
        System.out.println();
        for (PublishingCoordinator.PlatformExecutionResult res : summary.platformResults()) {
            if (res.success()) {
                System.out.println(ConsoleUi.checkmark() + " " + res.platform().getDisplayName() + ": " + res.message() + (res.externalId() != null ? " [ID: " + res.externalId() + "]" : ""));
            } else {
                System.out.println(ConsoleUi.cross() + " " + res.platform().getDisplayName() + " falhou: " + res.message());
            }
        }

        return 0;
    }

    private Path resolveVideoPath(AppContext context, String arg) {
        Path direct = Path.of(arg);
        if (Files.exists(direct)) {
            return direct;
        }
        Path insideVideosDir = Path.of(context.getConfig().getVideos().getDirectory()).resolve(arg);
        if (Files.exists(insideVideosDir)) {
            return insideVideosDir;
        }
        return direct;
    }

    private Path findSiblingJson(Path videoPath) {
        String filename = videoPath.getFileName().toString();
        int dot = filename.lastIndexOf('.');
        if (dot <= 0) return null;
        String base = filename.substring(0, dot);
        Path jsonCandidate = videoPath.resolveSibling(base + ".json");
        return Files.exists(jsonCandidate) ? jsonCandidate : null;
    }
}
