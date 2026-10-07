package com.videopost.cli.command;

import com.videopost.application.AppContext;
import com.videopost.application.service.AuthService;
import com.videopost.cli.ui.ConsoleUi;
import com.videopost.domain.model.Platform;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.Callable;

@Command(
        name = "auth",
        description = "Configura a autenticação oficial da plataforma para o perfil ativo com armazenamento criptografado"
)
public class AuthCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Plataforma alvo (tiktok, facebook, kwai)")
    private String platformArg;

    @Option(names = {"--token"}, description = "Access Token / Bearer Token")
    private String token;

    @Option(names = {"--page-id"}, description = "ID da Página no Facebook")
    private String pageId;

    @Option(names = {"--page-token"}, description = "Page Access Token do Facebook")
    private String pageToken;

    @Option(names = {"--app-id"}, description = "App ID (Kwai)")
    private String appId;

    @Option(names = {"--privacy"}, description = "Nível de privacidade TikTok (PUBLIC_TO_EVERYONE, SELF_ONLY)", defaultValue = "PUBLIC_TO_EVERYONE")
    private String privacyLevel;

    @Override
    public Integer call() {
        Optional<Platform> platformOpt = Platform.fromString(platformArg);
        if (platformOpt.isEmpty()) {
            System.err.println(ConsoleUi.cross() + " Plataforma inválida: '" + platformArg + "'. Suportadas: tiktok, facebook, kwai.");
            return 1;
        }

        Platform platform = platformOpt.get();
        AppContext context = AppContext.defaultContext();
        AuthService authService = context.getAuthService();
        String activeProfile = context.getProfileService().getActiveProfile();

        System.out.println(ConsoleUi.bold("🔐 Autenticação: ") + platform.getDisplayName() + " [Perfil: " + ConsoleUi.cyan(activeProfile) + "]");
        System.out.println();

        Scanner scanner = new Scanner(System.in);

        try {
            switch (platform) {
                case TIKTOK -> {
                    System.out.println("Requisitos Oficiais TikTok:");
                    System.out.println(" • App aprovado no portal TikTok for Developers com o escopo 'video.publish'");
                    System.out.println(" • Direct Post v2 API ativado");
                    System.out.println();
                    String tkn = token;
                    if (tkn == null || tkn.isBlank()) {
                        System.out.print("Informe o OAuth User Access Token: ");
                        if (scanner.hasNextLine()) {
                            tkn = scanner.nextLine().trim();
                        }
                    }
                    if (tkn == null || tkn.isBlank()) {
                        System.err.println(ConsoleUi.cross() + " Token não fornecido. Autenticação cancelada.");
                        return 1;
                    }
                    authService.authenticateTikTok(activeProfile, tkn, privacyLevel);
                }

                case FACEBOOK -> {
                    System.out.println("Requisitos Oficiais Meta / Facebook Pages:");
                    System.out.println(" • App registrado no Meta for Developers com permissões 'pages_manage_posts' e 'pages_read_engagement'");
                    System.out.println(" • Page Access Token válido para a página desejada");
                    System.out.println();
                    String pid = pageId;
                    if (pid == null || pid.isBlank()) {
                        System.out.print("Informe o Facebook Page ID: ");
                        if (scanner.hasNextLine()) {
                            pid = scanner.nextLine().trim();
                        }
                    }
                    String pat = pageToken != null ? pageToken : token;
                    if (pat == null || pat.isBlank()) {
                        System.out.print("Informe o Page Access Token: ");
                        if (scanner.hasNextLine()) {
                            pat = scanner.nextLine().trim();
                        }
                    }
                    if (pid == null || pid.isBlank() || pat == null || pat.isBlank()) {
                        System.err.println(ConsoleUi.cross() + " Page ID e Page Access Token são obrigatórios.");
                        return 1;
                    }
                    authService.authenticateFacebook(activeProfile, pid, pat);
                }

                case KWAI -> {
                    System.out.println("Requisitos Oficiais Kwai (Kuaishou Open Platform):");
                    System.out.println(" • Conta de desenvolvedor parceiro empresarial aprovada em open.kuaishou.com");
                    System.out.println(" • Escopo autorizado: user_video_publish");
                    System.out.println();
                    String aid = appId;
                    if (aid == null || aid.isBlank()) {
                        System.out.print("Informe o Kwai App ID: ");
                        if (scanner.hasNextLine()) {
                            aid = scanner.nextLine().trim();
                        }
                    }
                    String tkn = token;
                    if (tkn == null || tkn.isBlank()) {
                        System.out.print("Informe o Kwai Access Token: ");
                        if (scanner.hasNextLine()) {
                            tkn = scanner.nextLine().trim();
                        }
                    }
                    if (aid == null || aid.isBlank() || tkn == null || tkn.isBlank()) {
                        System.err.println(ConsoleUi.cross() + " App ID e Access Token são obrigatórios.");
                        return 1;
                    }
                    authService.authenticateKwai(activeProfile, aid, tkn);
                }

                default -> {
                    System.err.println(ConsoleUi.cross() + " Autenticação ainda não implementada para " + platform.getDisplayName());
                    return 1;
                }
            }

            System.out.println();
            System.out.println(ConsoleUi.checkmark() + " Credenciais criptografadas e salvas com sucesso em .videopost/credentials/" + activeProfile + "/!");
            return 0;

        } catch (Exception e) {
            System.err.println(ConsoleUi.cross() + " Erro ao autenticar: " + e.getMessage());
            return 1;
        }
    }
}
