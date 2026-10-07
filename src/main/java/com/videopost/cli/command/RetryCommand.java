package com.videopost.cli.command;

import com.videopost.application.AppContext;
import com.videopost.application.service.RetryService;
import com.videopost.cli.ui.ConsoleUi;
import com.videopost.domain.model.Publication;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Callable;

@Command(
        name = "retry",
        description = "Lista e reprocessa publicações com falha mantendo isolamento e idempotência"
)
public class RetryCommand implements Callable<Integer> {

    @Option(names = {"--all"}, description = "Executa retry de todas as falhas sem solicitar confirmação interativa")
    private boolean all = false;

    @Override
    public Integer call() {
        AppContext context = AppContext.defaultContext();
        RetryService retryService = context.getRetryService();
        int maxAttempts = context.getConfig().getMaxAttempts();

        List<RetryService.RetryCandidate> failedList = retryService.getFailedPublications();

        if (failedList.isEmpty()) {
            System.out.println(ConsoleUi.checkmark() + " Nenhuma publicação com falha elegível para retry.");
            return 0;
        }

        System.out.println(ConsoleUi.bold("Failed publications:"));
        System.out.println();

        int index = 1;
        for (RetryService.RetryCandidate item : failedList) {
            Publication pub = item.publication();
            System.out.printf("%d. %s%n", index++, item.video().getFilename());
            System.out.printf("   Platform: %s%n", pub.getPlatform().name());
            System.out.printf("   Attempts: %d/%d%n", pub.getAttempts(), maxAttempts);
            System.out.printf("   Error: %s%n", pub.getErrorMessage() != null ? pub.getErrorMessage() : "(sem detalhes)");
            System.out.println();
        }

        boolean proceed = all;
        if (!all) {
            System.out.print("Retry? [y/N]: ");
            Scanner scanner = new Scanner(System.in);
            if (scanner.hasNextLine()) {
                String answer = scanner.nextLine().trim();
                proceed = "y".equalsIgnoreCase(answer) || "s".equalsIgnoreCase(answer) || "sim".equalsIgnoreCase(answer);
            }
        }

        if (!proceed) {
            System.out.println("Operação cancelada pelo usuário.");
            return 0;
        }

        System.out.println();
        System.out.println("Executando retry...");
        int success = retryService.retryAll(maxAttempts);

        System.out.println();
        System.out.println(ConsoleUi.bold("Resultado do Retry:"));
        System.out.println(" " + ConsoleUi.checkmark() + " " + success + " publicações recuperadas com sucesso.");
        int remaining = failedList.size() - success;
        if (remaining > 0) {
            System.out.println(" " + ConsoleUi.cross() + " " + remaining + " publicações ainda possuem falhas.");
        }

        return 0;
    }
}
