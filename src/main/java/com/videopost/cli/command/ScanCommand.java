package com.videopost.cli.command;

import com.videopost.application.AppContext;
import com.videopost.application.service.ScanService;
import com.videopost.cli.ui.ConsoleUi;
import picocli.CommandLine.Command;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Callable;

@Command(
        name = "scan",
        description = "Detecta vídeos e metadados .json na pasta e sincroniza com a fila de agendamento"
)
public class ScanCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        AppContext context = AppContext.defaultContext();
        Path videosDir = Path.of(context.getConfig().getVideos().getDirectory());
        Duration interval = context.getConfig().getSchedule().parseIntervalDuration();

        System.out.println(ConsoleUi.bold("🔍 Escaneando diretório: ") + videosDir.toAbsolutePath());

        ScanService.ScanResult result = context.getScanService().scanAndEnqueue(videosDir, interval);

        System.out.println();
        System.out.println(ConsoleUi.checkmark() + " " + result.totalDiscoveredOnDisk() + " arquivos de vídeo encontrados no disco.");
        System.out.println(ConsoleUi.checkmark() + " " + result.newVideosEnqueued() + " novos vídeos adicionados à fila.");
        System.out.println(ConsoleUi.checkmark() + " " + result.currentQueue().size() + " vídeos pendentes/agendados na fila.");
        System.out.println();
        System.out.println("Use " + ConsoleUi.cyan("videopost queue") + " para visualizar os horários agendados.");

        return 0;
    }
}
