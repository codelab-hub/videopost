package com.videopost.cli.command;

import com.videopost.application.AppContext;
import com.videopost.cli.ui.ConsoleUi;
import picocli.CommandLine.Command;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;

@Command(
        name = "stop",
        description = "Encerra o processo do VideoPost em execução no plano de fundo"
)
public class StopCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        AppContext context = AppContext.defaultContext();
        Path pidFile = context.getRootDir().resolve("videopost.pid");

        if (!Files.exists(pidFile)) {
            System.out.println(ConsoleUi.yellow("Nenhum processo do VideoPost ativo encontrado (.videopost/videopost.pid não existe)."));
            return 0;
        }

        try {
            String pidStr = Files.readString(pidFile).trim();
            long pid = Long.parseLong(pidStr);

            Optional<ProcessHandle> handleOpt = ProcessHandle.of(pid);
            if (handleOpt.isPresent()) {
                ProcessHandle handle = handleOpt.get();
                handle.destroy();
                System.out.println(ConsoleUi.checkmark() + " Sinal de parada enviado para o processo PID: " + pid);
            } else {
                System.out.println(ConsoleUi.yellow("Processo PID " + pid + " não está mais em execução. Limpando arquivo PID."));
            }

            Files.deleteIfExists(pidFile);
            return 0;
        } catch (Exception e) {
            System.err.println(ConsoleUi.cross() + " Erro ao parar VideoPost: " + e.getMessage());
            return 1;
        }
    }
}
