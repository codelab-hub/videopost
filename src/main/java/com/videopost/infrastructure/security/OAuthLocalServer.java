package com.videopost.infrastructure.security;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.videopost.domain.model.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Servidor HTTP local temporário para captura do callback OAuth 2.0 via navegador.
 */
public class OAuthLocalServer {

    private static final Logger log = LoggerFactory.getLogger(OAuthLocalServer.class);
    private static final int PORT = 8585;

    public record OAuthCapture(
            String codeOrToken,
            String extraId,
            Map<String, String> parameters
    ) {}

    public static OAuthCapture listenForAuth(Platform platform, String profile, String redirectUrl, String authUrl) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", PORT), 0);
        CompletableFuture<OAuthCapture> future = new CompletableFuture<>();

        server.createContext("/callback", exchange -> {
            try {
                Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());

                String code = params.get("code");
                String token = params.get("token");
                String pageId = params.get("page_id");

                String tokenValue = token != null ? token : (code != null ? code : "");
                String extra = pageId != null ? pageId : "";

                String htmlSuccess = """
                <!DOCTYPE html>
                <html lang="pt-BR">
                <head>
                    <meta charset="UTF-8">
                    <title>VideoPost - Autenticado com Sucesso</title>
                    <style>
                        body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; background: #0d1117; color: #c9d1d9; }
                        .card { background: #161b22; border: 1px solid #30363d; border-radius: 12px; padding: 40px; max-width: 480px; text-align: center; box-shadow: 0 8px 24px rgba(0,0,0,0.5); }
                        .icon { font-size: 54px; color: #2ea043; margin-bottom: 16px; }
                        h1 { color: #f0f6fc; margin: 0 0 12px 0; font-size: 24px; }
                        p { color: #8b949e; line-height: 1.6; margin: 0 0 24px 0; }
                        .badge { display: inline-block; background: #238636; color: #fff; padding: 6px 14px; border-radius: 20px; font-weight: 600; font-size: 14px; }
                    </style>
                </head>
                <body>
                    <div class="card">
                        <div class="icon">✓</div>
                        <h1>Autenticação Concluída!</h1>
                        <p>O <strong>VideoPost CLI</strong> recebeu a autorização para o perfil <strong>""" + profile + """
                        </strong> na plataforma <strong>""" + platform.getDisplayName() + """
                        </strong>.</p>
                        <div class="badge">Pode fechar esta janela e voltar ao terminal</div>
                    </div>
                </body>
                </html>
                """;

                byte[] responseBytes = htmlSuccess.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, responseBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBytes);
                }

                future.complete(new OAuthCapture(tokenValue, extra, params));
            } catch (Exception e) {
                log.error("Erro no processamento do callback: {}", e.getMessage());
                future.completeExceptionally(e);
            }
        });

        // Página de autorização local amigável
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if ("/callback".equals(path)) {
                return;
            }

            String isFacebook = platform == Platform.FACEBOOK ? "true" : "false";

            String htmlPortal = """
            <!DOCTYPE html>
            <html lang="pt-BR">
            <head>
                <meta charset="UTF-8">
                <title>VideoPost - Conectar """ + platform.getDisplayName() + """
                </title>
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; display: flex; align-items: center; justify-content: center; min-height: 100vh; margin: 0; background: #0d1117; color: #c9d1d9; }
                    .card { background: #161b22; border: 1px solid #30363d; border-radius: 12px; padding: 36px; width: 100%; max-width: 480px; box-shadow: 0 8px 24px rgba(0,0,0,0.5); }
                    h1 { color: #f0f6fc; margin: 0 0 10px 0; font-size: 22px; }
                    p { color: #8b949e; line-height: 1.5; font-size: 14px; margin-bottom: 24px; }
                    label { display: block; margin-bottom: 6px; font-weight: 500; font-size: 13px; color: #c9d1d9; }
                    input[type=text] { width: 100%; box-sizing: border-box; padding: 10px 12px; margin-bottom: 18px; border-radius: 6px; border: 1px solid #30363d; background: #0d1117; color: #f0f6fc; font-size: 14px; }
                    input[type=text]:focus { outline: none; border-color: #58a6ff; }
                    button { width: 100%; background: #238636; color: #ffffff; border: none; padding: 12px; border-radius: 6px; font-weight: 600; font-size: 15px; cursor: pointer; transition: background .2s; }
                    button:hover { background: #2ea043; }
                    .tag { display: inline-block; background: #1f6feb26; color: #58a6ff; padding: 3px 8px; border-radius: 4px; font-size: 12px; margin-bottom: 12px; }
                </style>
            </head>
            <body>
                <div class="card">
                    <div class="tag">VideoPost CLI OAuth Helper</div>
                    <h1>Conectar """ + platform.getDisplayName() + """
                    </h1>
                    <p>Autorizando perfil <strong>""" + profile + """
                    </strong> no VideoPost local.</p>
                    <form action="/callback" method="GET">
            """ + (platform == Platform.FACEBOOK ? """
                        <label>Facebook Page ID (ID da sua Página)</label>
                        <input type="text" name="page_id" placeholder="Ex: 1092837465019" required />
                        <label>Page Access Token</label>
                        <input type="text" name="token" placeholder="Cole o Page Access Token obtido no Meta for Developers" required />
            """ : """
                        <label>Google / YouTube OAuth Access Token</label>
                        <input type="text" name="token" placeholder="Cole o OAuth Bearer Token obtido no Google Cloud" required />
            """) + """
                        <button type="submit">Confirmar e Salvar no VideoPost</button>
                    </form>
                </div>
            </body>
            </html>
            """;

            byte[] bytes = htmlPortal.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        server.start();

        // Abre o navegador automaticamente
        openBrowser("http://localhost:" + PORT);

        try {
            // Aguarda até 3 minutos pela resposta do usuário no navegador
            return future.get(3, TimeUnit.MINUTES);
        } finally {
            server.stop(1);
        }
    }

    private static void openBrowser(String url) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("mac")) {
                Runtime.getRuntime().exec(new String[]{"open", url});
            } else if (os.contains("win")) {
                Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", url});
            } else {
                Runtime.getRuntime().exec(new String[]{"xdg-open", url});
            }
        } catch (Exception e) {
            log.warn("Não foi possível abrir o navegador automaticamente: {}. Acesse {}", e.getMessage(), url);
        }
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isBlank()) {
            return map;
        }
        for (String pair : query.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String val = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                map.put(key, val);
            }
        }
        return map;
    }
}
