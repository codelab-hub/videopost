package com.videopost.cli.command;

import com.videopost.application.AppContext;
import com.videopost.application.service.ProfileService;
import com.videopost.cli.ui.ConsoleUi;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.List;
import java.util.concurrent.Callable;

@Command(
        name = "profile",
        description = "Gerencia múltiplos perfis de redes sociais (criação, listagem e seleção)",
        subcommands = {
                ProfileCommand.CreateCommand.class,
                ProfileCommand.ListCommand.class,
                ProfileCommand.UseCommand.class
        }
)
public class ProfileCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        System.out.println("Use 'videopost profile create <nome>', 'videopost profile list' ou 'videopost profile use <nome>'.");
        return 0;
    }

    @Command(name = "create", description = "Cria um novo perfil de publicação")
    public static class CreateCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Nome do novo perfil")
        private String name;

        @Override
        public Integer call() {
            AppContext context = AppContext.defaultContext();
            try {
                context.getProfileService().createProfile(name);
                System.out.println(ConsoleUi.checkmark() + " Perfil '" + ConsoleUi.bold(name) + "' criado com sucesso!");
                System.out.println("Para ativá-lo, use: " + ConsoleUi.cyan("videopost profile use " + name));
                return 0;
            } catch (Exception e) {
                System.err.println(ConsoleUi.cross() + " Erro ao criar perfil: " + e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "list", description = "Lista todos os perfis cadastrados")
    public static class ListCommand implements Callable<Integer> {
        @Override
        public Integer call() {
            AppContext context = AppContext.defaultContext();
            List<ProfileService.ProfileInfo> profiles = context.getProfileService().listProfiles();

            System.out.println(ConsoleUi.bold("Perfis disponíveis:"));
            System.out.println();
            for (ProfileService.ProfileInfo p : profiles) {
                if (p.isActive()) {
                    System.out.println(" * " + ConsoleUi.green(p.name()) + " " + ConsoleUi.bold("(ativo)"));
                } else {
                    System.out.println("   " + p.name());
                }
            }
            System.out.println();
            return 0;
        }
    }

    @Command(name = "use", description = "Define o perfil ativo")
    public static class UseCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Nome do perfil a ser ativado")
        private String name;

        @Override
        public Integer call() {
            AppContext context = AppContext.defaultContext();
            try {
                context.getProfileService().useProfile(name);
                System.out.println(ConsoleUi.checkmark() + " Perfil ativo alterado para: " + ConsoleUi.green(name));
                return 0;
            } catch (Exception e) {
                System.err.println(ConsoleUi.cross() + " Erro ao ativar perfil: " + e.getMessage());
                return 1;
            }
        }
    }
}
