package com.videopost.cli.command;

import com.videopost.application.AppContext;
import com.videopost.application.service.QueueService;
import com.videopost.cli.ui.ConsoleUi;
import com.videopost.domain.model.Video;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
        name = "queue",
        description = "Exibe a fila de vídeos agendados e status de processamento"
)
public class QueueCommand implements Callable<Integer> {

    @Option(names = {"-a", "--all"}, description = "Exibir histórico completo de vídeos (incluindo finalizados)")
    private boolean showAll = false;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault());

    @Override
    public Integer call() {
        AppContext context = AppContext.defaultContext();
        QueueService queueService = context.getQueueService();

        List<QueueService.QueueItem> items = showAll ? queueService.getFullQueue() : queueService.getPendingOrScheduledQueue();

        System.out.println(ConsoleUi.bold("VIDEOPOST QUEUE"));
        System.out.println();

        if (items.isEmpty()) {
            System.out.println("Nenhum vídeo na fila.");
            System.out.println("Adicione vídeos na pasta configurada e execute 'videopost scan'.");
            return 0;
        }

        System.out.printf("%-3s  %-30s  %-12s  %-10s%n", "#", "Video", "Status", "Scheduled");
        System.out.println("─".repeat(60));

        int index = 1;
        for (QueueService.QueueItem item : items) {
            Video v = item.video();
            String scheduledStr = v.getScheduledAt() != null ? TIME_FORMATTER.format(v.getScheduledAt()) : "--:--";

            String plainStatus = String.format("%-12s", v.getStatus().name());
            String statusColor = switch (v.getStatus()) {
                case COMPLETED -> ConsoleUi.green(plainStatus);
                case FAILED -> ConsoleUi.red(plainStatus);
                case PARTIALLY_COMPLETED -> ConsoleUi.yellow(plainStatus);
                case PROCESSING -> ConsoleUi.cyan(plainStatus);
                default -> plainStatus;
            };

            System.out.printf("%-3d  %-30s  %s  %-10s%n",
                    index++,
                    truncate(v.getFilename(), 30),
                    statusColor,
                    scheduledStr
            );
        }

        System.out.println();
        return 0;
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }
}
