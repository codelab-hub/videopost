package com.videopost.cli.command;

import com.videopost.application.AppContext;
import com.videopost.cli.ui.ConsoleUi;
import com.videopost.infrastructure.config.AppConfig;
import picocli.CommandLine.Command;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
        name = "init",
        description = "Inicializa a estrutura do VideoPost (.videopost/, banco SQLite, credenciais e diretório de vídeos)"
)
public class InitCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        try {
            Path rootDir = Path.of(".videopost");
            Path videosDir = Path.of("videos");

            Files.createDirectories(rootDir);
            Files.createDirectories(rootDir.resolve("credentials"));
            Files.createDirectories(rootDir.resolve("profiles"));
            Files.createDirectories(videosDir);

            // Inicializa contexto para disparar DDL do banco SQLite e criar config.yml
            AppContext context = new AppContext(rootDir);

            Path configFile = rootDir.resolve("config.yml");
            if (!Files.exists(configFile)) {
                AppConfig config = new AppConfig();
                config.getVideos().setDirectory("./videos");
                context.getConfigLoader().save(configFile, config);
            }

            System.out.println(ConsoleUi.bold("🎬 VideoPost inicializado com sucesso!"));
            System.out.println();
            System.out.println("Estrutura criada:");
            System.out.println(" " + ConsoleUi.checkmark() + " .videopost/config.yml");
            System.out.println(" " + ConsoleUi.checkmark() + " .videopost/videopost.db (SQLite)");
            System.out.println(" " + ConsoleUi.checkmark() + " .videopost/credentials/");
            System.out.println(" " + ConsoleUi.checkmark() + " .videopost/profiles/");
            System.out.println(" " + ConsoleUi.checkmark() + " ./videos (coloque seus arquivos .mp4 e .json aqui)");
            System.out.println();
            System.out.println("Próximos passos:");
            System.out.println("  1. Configure suas credenciais: videopost auth tiktok | facebook | kwai");
            System.out.println("  2. Adicione seus vídeos na pasta ./videos");
            System.out.println("  3. Execute 'videopost scan' ou 'videopost start'");

            return 0;
        } catch (Exception e) {
            System.err.println(ConsoleUi.cross() + " Erro ao inicializar VideoPost: " + e.getMessage());
            return 1;
        }
    }
}
