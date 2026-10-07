package com.videopost.cli.command;

import com.videopost.application.AppContext;
import com.videopost.application.service.ScheduleRunner;
import com.videopost.cli.ui.ConsoleUi;
import com.videopost.domain.model.Platform;
import com.videopost.domain.model.Video;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
        name = "start",
        description = "Inicia o ciclo de monitoramento, agendamento e publicação automática de vídeos"
)
public class StartCommand implements Callable<Integer> {

    @Option(names = {"--once"}, description = "Executa apenas um ciclo de verificação/publicação e encerra")
    private boolean once = false;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault());

    @Override
    public Integer call() {
        AppContext context = AppContext.defaultContext();
        Path pidFile = context.getRootDir().resolve("videopost.pid");

        // Grava PID para suporte ao videopost stop
        long pid = ProcessHandle.current().pid();
        try {
            Files.writeString(pidFile, String.valueOf(pid));
        } catch (IOException ignored) {}

        ScheduleRunner runner = context.getScheduleRunner();

        // Hook para encerramento gracioso (Ctrl+C / SIGINT)
        Thread shutdownHook = new Thread(() -> {
            runner.stop();
            try {
                Files.deleteIfExists(pidFile);
            } catch (IOException ignored) {}
        });
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        System.out.println(ConsoleUi.bold("🎬 VideoPost"));
        System.out.println();

        runner.run(new ScheduleRunner.ScheduleEventListener() {
            @Override
            public void onStartup(String profile, Duration interval, int videosFound, List<ScheduleRunner.PlatformAuthStatus> platforms) {
                System.out.println("Profile: " + ConsoleUi.cyan(profile));
                System.out.println("Interval: " + interval.toMinutes() + "m");
                System.out.println();
                System.out.println(ConsoleUi.checkmark() + " " + videosFound + " videos found");

                for (ScheduleRunner.PlatformAuthStatus p : platforms) {
                    if (p.authenticated()) {
                        System.out.println(ConsoleUi.checkmark() + " " + p.platform().getDisplayName() + " authenticated");
                    } else {
                        System.out.println(ConsoleUi.cross() + " " + p.platform().getDisplayName() + " not authenticated");
                    }
                }
                System.out.println();
            }

            @Override
            public void onPublishStart(Instant time, Video video) {
                System.out.println("[" + TIME_FORMATTER.format(time) + "] Publishing " + ConsoleUi.bold(video.getFilename()));
            }

            @Override
            public void onPlatformResult(Platform platform, boolean success, String message) {
                if (success) {
                    System.out.println(ConsoleUi.checkmark() + " " + platform.getDisplayName() + " published");
                } else {
                    System.out.println(ConsoleUi.cross() + " " + platform.getDisplayName() + " failed: " + message);
                }
            }

            @Override
            public void onNextScheduled(Video nextVideo, Instant scheduledTime) {
                System.out.println();
                System.out.println("Next video: " + ConsoleUi.bold(nextVideo.getFilename()));
                System.out.println("Next publication: " + (scheduledTime != null ? TIME_FORMATTER.format(scheduledTime) : "agendando..."));
                System.out.println();
            }

            @Override
            public void onQueueEmpty() {
                System.out.println("Fila de vídeos vazia. Aguardando novos arquivos...");
            }

            @Override
            public void onOutsideWorkingHours(LocalTime current, String start, String end) {
                System.out.println(ConsoleUi.yellow("Fora do horário de publicação configurado (" + start + " às " + end + "). Aguardando..."));
            }

            @Override
            public void onStopped() {
                System.out.println(ConsoleUi.yellow("VideoPost finalizado."));
            }
        }, once);

        try {
            Files.deleteIfExists(pidFile);
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (Exception ignored) {}

        return 0;
    }
}
