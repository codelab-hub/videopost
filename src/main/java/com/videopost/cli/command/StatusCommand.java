package com.videopost.cli.command;

import com.videopost.application.AppContext;
import com.videopost.application.service.QueueService;
import com.videopost.cli.ui.ConsoleUi;
import com.videopost.domain.model.Platform;
import com.videopost.infrastructure.config.AppConfig;
import com.videopost.publisher.VideoPublisher;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Command(
        name = "status",
        description = "Exibe o status do perfil ativo, autenticação das plataformas e métricas da fila"
)
public class StatusCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        AppContext context = AppContext.defaultContext();
        AppConfig config = context.getConfig();
        String activeProfile = context.getProfileService().getActiveProfile();
        QueueService.QueueStats stats = context.getQueueService().getStats();

        System.out.println(ConsoleUi.bold("VideoPost"));
        System.out.println();
        System.out.println("Profile: " + ConsoleUi.cyan(activeProfile));
        System.out.println("Interval: " + config.getSchedule().getInterval());
        System.out.println();

        System.out.println(ConsoleUi.bold("Platforms:"));
        for (Platform platform : Platform.values()) {
            if (config.isPlatformEnabled(platform)) {
                boolean auth = context.getPublisherRegistry().getPublisher(platform)
                        .map(VideoPublisher::isAuthenticated)
                        .orElse(false);

                if (auth) {
                    System.out.println(ConsoleUi.checkmark() + " " + platform.getDisplayName());
                } else {
                    System.out.println(ConsoleUi.cross() + " " + platform.getDisplayName() + " " + ConsoleUi.yellow("(não autenticado)"));
                }
            }
        }
        System.out.println();

        System.out.println(ConsoleUi.bold("Queue:"));
        System.out.println(stats.pendingVideos() + " pending");
        System.out.println(stats.processingVideos() + " processing");
        System.out.println(stats.completedVideos() + " published");
        if (stats.partiallyCompletedVideos() > 0) {
            System.out.println(stats.partiallyCompletedVideos() + " partially completed");
        }
        System.out.println(stats.failedVideos() + " failed");
        System.out.println();

        return 0;
    }
}
