package com.videopost.cli.command;

import com.videopost.application.AppContext;
import com.videopost.cli.ui.ConsoleUi;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
        name = "logs",
        description = "Exibe o histórico de logs do VideoPost para diagnóstico"
)
public class LogsCommand implements Callable<Integer> {

    @Option(names = {"-n", "--lines"}, description = "Número de linhas a exibir", defaultValue = "50")
    private int lines = 50;

    @Override
    public Integer call() {
        AppContext context = AppContext.defaultContext();
        Path logFile = context.getRootDir().resolve("videopost.log");

        if (!Files.exists(logFile)) {
            System.out.println(ConsoleUi.yellow("Arquivo de log ainda não criado (" + logFile + ")."));
            return 0;
        }

        try {
            List<String> allLines = Files.readAllLines(logFile);
            int start = Math.max(0, allLines.size() - lines);
            List<String> tail = allLines.subList(start, allLines.size());

            System.out.println(ConsoleUi.bold("=== VideoPost Logs (últimas " + tail.size() + " linhas) ==="));
            System.out.println();
            for (String line : tail) {
                System.out.println(line);
            }
            return 0;
        } catch (IOException e) {
            System.err.println(ConsoleUi.cross() + " Erro ao ler arquivo de log: " + e.getMessage());
            return 1;
        }
    }
}
