package com.videopost.cli.command;

import com.videopost.application.AppContext;
import com.videopost.application.service.AuthService;
import com.videopost.cli.ui.ConsoleUi;
import com.videopost.domain.model.Platform;
import com.videopost.infrastructure.security.OAuthLocalServer;
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

    @Parameters(index = "0", description = "Plataforma alvo (tiktok, facebook, kwai, youtube)")
    private String platformArg;

    @Option(names = {"--browser"}, description = "Inicia fluxo de autorização via navegador local (localhost:8585)")
    private boolean browser = false;

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

    @Option(names = {"--refresh-token"}, description = "OAuth Refresh Token (YouTube/TikTok)")
    private String refreshToken;

    @Option(names = {"--client-id", "--client-key"}, description = "Google Client ID / TikTok Client Key")
    private String clientId;

    @Option(names = {"--client-secret"}, description = "Google OAuth / TikTok Client Secret")
    private String clientSecret;

    @Override
    public Integer call() {
        Optional<Platform> platformOpt = Platform.fromString(platformArg);
        if (platformOpt.isEmpty()) {
            System.err.println(ConsoleUi.cross() + " Plataforma inválida: '" + platformArg + "'. Suportadas: tiktok, facebook, kwai, youtube.");
            return 1;
        }

        Platform platform = platformOpt.get();
        AppContext context = AppContext.defaultContext();
        AuthService authService = context.getAuthService();
        String activeProfile = context.getProfileService().getActiveProfile();

        System.out.println(ConsoleUi.bold("🔐 Autenticação: ") + platform.getDisplayName() + " [Perfil: " + ConsoleUi.cyan(activeProfile) + "]");
        System.out.println();

        if (browser) {
            return executeBrowserFlow(platform, activeProfile, authService);
        }

        Scanner scanner = new Scanner(System.in);

        try {
            switch (platform) {
                case TIKTOK -> {
                    System.out.println("Requisitos Oficiais TikTok:");
                    System.out.println(" • App aprovado no portal TikTok for Developers com o escopo 'video.publish'");
                    System.out.println(" • Direct Post v2 API ativado");
                    System.out.println();
                    String tkn = token;
                    if ((tkn == null || tkn.isBlank()) && (refreshToken == null || refreshToken.isBlank())) {
                        System.out.print("Informe o OAuth User Access Token (ou Refresh Token): ");
                        if (scanner.hasNextLine()) {
                            tkn = scanner.nextLine().trim();
                        }
                    }
                    if ((tkn == null || tkn.isBlank()) && (refreshToken == null || refreshToken.isBlank())) {
                        System.err.println(ConsoleUi.cross() + " Nenhum token fornecido. Autenticação cancelada.");
                        return 1;
                    }
                    authService.authenticateTikTok(activeProfile, tkn, privacyLevel, refreshToken, clientId, clientSecret);
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

                case YOUTUBE_SHORTS -> {
                    System.out.println("Requisitos Oficiais Google / YouTube Shorts:");
                    System.out.println(" • Projeto no Google Cloud Console com YouTube Data API v3 ativada");
                    System.out.println(" • Escopo: https://www.googleapis.com/auth/youtube.upload");
                    System.out.println();
                    String tkn = token;
                    if ((tkn == null || tkn.isBlank()) && (refreshToken == null || refreshToken.isBlank())) {
                        System.out.print("Informe o Google OAuth Bearer Token (ou Refresh Token): ");
                        if (scanner.hasNextLine()) {
                            tkn = scanner.nextLine().trim();
                        }
                    }
                    if ((tkn == null || tkn.isBlank()) && (refreshToken == null || refreshToken.isBlank())) {
                        System.err.println(ConsoleUi.cross() + " Nenhum token fornecido. Autenticação cancelada.");
                        return 1;
                    }
                    authService.authenticateYouTube(activeProfile, tkn, refreshToken, clientId, clientSecret);
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

    private int executeBrowserFlow(Platform platform, String profile, AuthService authService) {
        System.out.println(ConsoleUi.cyan("🌐 Iniciando servidor local em http://localhost:8585..."));
        System.out.println("Abrindo navegador padrão para autorização...");
        System.out.println(ConsoleUi.yellow("Aguardando confirmação no navegador (limite: 3 minutos)..."));
        System.out.println();

        try {
            OAuthLocalServer.OAuthCapture capture = OAuthLocalServer.listenForAuth(
                    platform,
                    profile,
                    "http://localhost:8585/callback",
                    null
            );

            if (capture.codeOrToken() == null || capture.codeOrToken().isBlank()) {
                System.err.println(ConsoleUi.cross() + " Nenhuma credencial recebida pelo navegador. Operação abortada.");
                return 1;
            }

            if (platform == Platform.FACEBOOK) {
                String pid = capture.extraId();
                if (pid == null || pid.isBlank()) {
                    pid = pageId != null ? pageId : "default_page";
                }
                authService.authenticateFacebook(profile, pid, capture.codeOrToken());
            } else if (platform == Platform.YOUTUBE_SHORTS) {
                authService.authenticateYouTube(profile, capture.codeOrToken());
            } else if (platform == Platform.TIKTOK) {
                authService.authenticateTikTok(profile, capture.codeOrToken(), privacyLevel);
            } else if (platform == Platform.KWAI) {
                authService.authenticateKwai(profile, capture.extraId(), capture.codeOrToken());
            }

            System.out.println(ConsoleUi.checkmark() + " Autorização via navegador recebida com sucesso!");
            System.out.println(ConsoleUi.checkmark() + " Credenciais criptografadas com AES-256-GCM salvas em .videopost/credentials/" + profile + "/");
            return 0;
        } catch (Exception e) {
            System.err.println(ConsoleUi.cross() + " Falha no fluxo do navegador: " + e.getMessage());
            return 1;
        }
    }
}
