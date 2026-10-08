package com.videopost.cli.command;

import com.videopost.application.AppContext;
import com.videopost.application.service.ScanService;
import com.videopost.cli.ui.ConsoleUi;
import picocli.CommandLine.Command;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

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

        // Diagnóstico visual dos arquivos encontrados no diretório
        Path inspectDir = Files.exists(videosDir) ? videosDir : (videosDir.getParent() != null && Files.exists(videosDir.getParent()) ? videosDir.getParent() : videosDir);
        if (Files.exists(inspectDir) && Files.isDirectory(inspectDir)) {
            try (Stream<Path> stream = Files.walk(inspectDir, 4, FileVisitOption.FOLLOW_LINKS)) {
                List<Path> items = stream.filter(p -> !p.equals(inspectDir)).toList();
                if (items.isEmpty()) {
                    System.out.println(ConsoleUi.yellow("⚠️ A pasta " + inspectDir.getFileName() + " está vazia no disco ou requer permissão do macOS."));
                } else {
                    System.out.println("Conteúdo detectado em " + inspectDir.getFileName() + " (" + items.size() + " itens):");
                    for (Path item : items) {
                        String type = Files.isDirectory(item) ? "[PASTA]" : "[ARQUIVO]";
                        System.out.println(" • " + type + " " + inspectDir.relativize(item));
                    }
                }
            } catch (IOException e) {
                System.out.println(ConsoleUi.yellow("Aviso ao ler pasta: " + e.getMessage()));
            }
        } else {
            System.out.println(ConsoleUi.yellow("⚠️ Diretório não localizado no disco: " + videosDir.toAbsolutePath()));
        }

        ScanService.ScanResult result = context.getScanService().scanAndEnqueue(videosDir, interval);

        System.out.println();
        System.out.println(ConsoleUi.checkmark() + " " + result.totalDiscoveredOnDisk() + " arquivos de vídeo válidos encontrados no disco.");
        System.out.println(ConsoleUi.checkmark() + " " + result.newVideosEnqueued() + " novos vídeos adicionados à fila.");
        System.out.println(ConsoleUi.checkmark() + " " + result.currentQueue().size() + " vídeos pendentes/agendados na fila.");
        System.out.println();
        System.out.println("Use " + ConsoleUi.cyan("videopost queue") + " para visualizar os horários agendados.");

        return 0;
    }
}
