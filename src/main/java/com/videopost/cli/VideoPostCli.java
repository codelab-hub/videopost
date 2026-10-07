package com.videopost.cli;

import com.videopost.cli.command.AuthCommand;
import com.videopost.cli.command.InitCommand;
import com.videopost.cli.command.LogsCommand;
import com.videopost.cli.command.ProfileCommand;
import com.videopost.cli.command.PublishCommand;
import com.videopost.cli.command.QueueCommand;
import com.videopost.cli.command.RetryCommand;
import com.videopost.cli.command.ScanCommand;
import com.videopost.cli.command.StartCommand;
import com.videopost.cli.command.StatusCommand;
import com.videopost.cli.command.StopCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

/**
 * Ponto de entrada principal do VideoPost CLI.
 */
@Command(
        name = "videopost",
        description = "VideoPost CLI - Gerenciamento e publicação automatizada de vídeos em múltiplos perfis e redes sociais.",
        mixinStandardHelpOptions = true,
        version = "VideoPost CLI 1.0.0",
        subcommands = {
                InitCommand.class,
                ScanCommand.class,
                QueueCommand.class,
                StatusCommand.class,
                StartCommand.class,
                StopCommand.class,
                PublishCommand.class,
                RetryCommand.class,
                LogsCommand.class,
                ProfileCommand.class,
                AuthCommand.class
        }
)
public class VideoPostCli implements Callable<Integer> {

    @Override
    public Integer call() {
        CommandLine.usage(this, System.out);
        return 0;
    }

    public static void main(String[] args) {
        CommandLine cmd = new CommandLine(new VideoPostCli());
        cmd.setExecutionExceptionHandler((ex, commandLine, parseResult) -> {
            System.err.println("Erro: " + ex.getMessage());
            return 1;
        });
        int exitCode = cmd.execute(args);
        System.exit(exitCode);
    }
}
