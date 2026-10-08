import os
from playwright.sync_api import sync_playwright

html_content = """<!DOCTYPE html>
<html lang="pt-BR">
<head>
<meta charset="UTF-8">
<title>Manual VideoPost - YouTube Shorts e TikTok</title>
<style>
  @import url('https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@300;400;500;600;700;800&family=JetBrains+Mono:wght@400;500;600&display=swap');

  @page {
    size: A4;
    margin: 16mm 14mm 16mm 14mm;
    @bottom-right {
      content: counter(page);
      font-size: 9pt;
      font-family: 'Plus Jakarta Sans', sans-serif;
      color: #94a3b8;
    }
  }

  * {
    box-sizing: border-box;
    margin: 0;
    padding: 0;
  }

  body {
    font-family: 'Plus Jakarta Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    color: #1e293b;
    background: #ffffff;
    font-size: 10pt;
    line-height: 1.55;
  }

  .header {
    background: linear-gradient(135deg, #0f172a 0%, #1e1b4b 50%, #312e81 100%);
    color: #ffffff;
    padding: 24px 28px;
    border-radius: 12px;
    margin-bottom: 22px;
    box-shadow: 0 10px 25px -5px rgba(15, 23, 42, 0.2);
  }

  .header-badges {
    display: flex;
    gap: 8px;
    margin-bottom: 12px;
  }

  .badge {
    font-size: 8pt;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0.5px;
    padding: 4px 10px;
    border-radius: 9999px;
  }

  .badge-yt { background: #ef4444; color: #fff; }
  .badge-tt { background: #06b6d4; color: #fff; }
  .badge-auto { background: #10b981; color: #fff; }

  .header h1 {
    font-size: 20pt;
    font-weight: 800;
    letter-spacing: -0.5px;
    line-height: 1.2;
    margin-bottom: 6px;
  }

  .header p {
    font-size: 10.5pt;
    color: #cbd5e1;
    max-width: 650px;
  }

  .meta-bar {
    margin-top: 14px;
    padding-top: 12px;
    border-top: 1px solid rgba(255, 255, 255, 0.15);
    display: flex;
    justify-content: space-between;
    font-size: 8.5pt;
    color: #94a3b8;
  }

  h2 {
    font-size: 13pt;
    font-weight: 700;
    color: #0f172a;
    margin: 22px 0 10px 0;
    display: flex;
    align-items: center;
    gap: 8px;
    border-bottom: 2px solid #e2e8f0;
    padding-bottom: 6px;
    page-break-after: avoid;
  }

  h3 {
    font-size: 11pt;
    font-weight: 600;
    color: #1e293b;
    margin: 14px 0 8px 0;
    page-break-after: avoid;
  }

  p { margin-bottom: 8px; }

  .grid-2 {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 14px;
    margin: 12px 0;
  }

  .card {
    background: #f8fafc;
    border: 1px solid #e2e8f0;
    border-radius: 10px;
    padding: 14px 16px;
    page-break-inside: avoid;
  }

  .card-yt {
    border-left: 4px solid #ef4444;
  }

  .card-tt {
    border-left: 4px solid #06b6d4;
  }

  .card-title {
    font-weight: 700;
    font-size: 10.5pt;
    color: #0f172a;
    margin-bottom: 6px;
    display: flex;
    align-items: center;
    gap: 6px;
  }

  .step-box {
    background: #ffffff;
    border: 1px solid #e2e8f0;
    border-radius: 10px;
    padding: 12px 14px;
    margin-bottom: 10px;
    page-break-inside: avoid;
  }

  .step-header {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 6px;
  }

  .step-num {
    width: 24px;
    height: 24px;
    border-radius: 50%;
    background: #4f46e5;
    color: #fff;
    font-size: 9pt;
    font-weight: 700;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
  }

  .step-num-yt { background: #ef4444; }
  .step-num-tt { background: #06b6d4; }

  .step-title {
    font-weight: 700;
    font-size: 10pt;
    color: #0f172a;
  }

  pre, code {
    font-family: 'JetBrains Mono', monospace;
  }

  code {
    background: #f1f5f9;
    color: #0f172a;
    padding: 2px 5px;
    border-radius: 4px;
    font-size: 8.5pt;
  }

  pre {
    background: #0f172a;
    color: #e2e8f0;
    padding: 10px 14px;
    border-radius: 8px;
    font-size: 8.5pt;
    line-height: 1.45;
    margin: 8px 0;
    overflow-x: auto;
    page-break-inside: avoid;
  }

  pre code {
    background: transparent;
    color: inherit;
    padding: 0;
  }

  .table-container {
    margin: 12px 0;
    page-break-inside: avoid;
  }

  table {
    width: 100%;
    border-collapse: collapse;
    font-size: 8.5pt;
  }

  th {
    background: #0f172a;
    color: #ffffff;
    text-align: left;
    padding: 8px 10px;
    font-weight: 600;
  }

  td {
    padding: 8px 10px;
    border-bottom: 1px solid #e2e8f0;
    vertical-align: top;
  }

  tr:nth-child(even) td {
    background: #f8fafc;
  }

  .callout {
    background: #eff6ff;
    border-left: 4px solid #3b82f6;
    padding: 10px 14px;
    border-radius: 0 8px 8px 0;
    font-size: 9pt;
    margin: 12px 0;
    page-break-inside: avoid;
  }

  .callout-warn {
    background: #fefce8;
    border-left-color: #eab308;
    color: #713f12;
  }

  .callout-success {
    background: #f0fdf4;
    border-left-color: #22c55e;
    color: #14532d;
  }

  .page-break {
    page-break-before: always;
  }

  .folder-tree {
    background: #1e293b;
    color: #cbd5e1;
    padding: 10px 14px;
    border-radius: 8px;
    font-family: 'JetBrains Mono', monospace;
    font-size: 8pt;
    line-height: 1.5;
    margin: 8px 0;
  }
</style>
</head>
<body>

  <!-- CAPA / CABEÇALHO -->
  <div class="header">
    <div class="header-badges">
      <span class="badge badge-yt">YouTube Shorts</span>
      <span class="badge badge-tt">TikTok</span>
      <span class="badge badge-auto">Automação 100% Autônoma</span>
    </div>
    <h1>VideoPost — Manual de Operação & Automação</h1>
    <p>Guia prático passo a passo para publicação e agendamento autônomo de vídeos no YouTube Shorts e TikTok.</p>
    <div class="meta-bar">
      <span>Canal / Perfil: <strong>Notícia Brasil</strong></span>
      <span>Intervalo Padrão: <strong>3 horas</strong></span>
      <span>Ambiente: <strong>macOS Apple Silicon</strong></span>
      <span>Versão: <strong>2.0 Modular</strong></span>
    </div>
  </div>

  <!-- VISÃO GERAL -->
  <h2>📌 1. Visão Geral da Arquitetura</h2>
  <p>
    O sistema foi desenhado em <strong>dois microsserviços completamente independentes e isolados</strong>. Isso garante estabilidade máxima: se uma plataforma precisar de ajuste ou autenticação, a outra continua publicando no ar sem qualquer interrupção.
  </p>

  <div class="grid-2">
    <div class="card card-yt">
      <div class="card-title">🔴 YouTube Shorts (Java Daemon)</div>
      <p style="font-size: 8.5pt; color: #475569; margin-bottom: 6px;">
        Opera via <strong>API Oficial do YouTube (OAuth2 + Google Resumable Upload)</strong> em segundo plano.
      </p>
      <ul style="font-size: 8.5pt; color: #334155; padding-left: 16px;">
        <li>Publica direto no canal público.</li>
        <li>Renovação de tokens OAuth automática.</li>
        <li>Auto-recuperação contra expiração de sessão HTTP 410.</li>
        <li>Comando raiz: <code>./videopost</code></li>
      </ul>
    </div>

    <div class="card card-tt">
      <div class="card-title">🔵 TikTok (Microsserviço Web Studio)</div>
      <p style="font-size: 8.5pt; color: #475569; margin-bottom: 6px;">
        Opera via <strong>Playwright + Chrome nativo</strong> direto no TikTok Studio Web.
      </p>
      <ul style="font-size: 8.5pt; color: #334155; padding-left: 16px;">
        <li>Publica direto no feed público (sem limites de Sandbox).</li>
        <li>Perfil do Chrome persistente (login feito apenas uma vez).</li>
        <li>Grava prints automáticos de auditoria de cada post.</li>
        <li>Comando raiz: <code>./tiktok</code></li>
      </ul>
    </div>
  </div>

  <!-- DIRETÓRIO E METADADOS -->
  <h2>📂 2. Estrutura de Pastas e Metadados</h2>
  <p>Ambos os serviços consomem os vídeos e metadados da mesma pasta local:</p>
  <pre><code>/Users/ludmilamoreira/Desktop/frutinhas do brasil/videos curtos campanha.nosync/</code></pre>

  <p>
    Cada vídeo (<code>.mp4</code> ou <code>.mov</code>) possui um arquivo <code>.json</code> correspondente com mesmo nome contendo a legenda e as hashtags:
  </p>

  <div class="step-box">
    <div class="step-header">
      <span class="step-num">📄</span>
      <span class="step-title">Exemplo de arquivo de metadados (ex: 1791369200733.json)</span>
    </div>
    <pre><code>{
  "caption": "Debates que movimentam as redes sociais no Brasil 💬",
  "hashtags": [
    "#opiniao",
    "#brasil",
    "#debates",
    "#redessociais",
    "#cotidiano"
  ]
}</code></pre>
    <p style="font-size: 8.5pt; color: #475569; margin-top: 6px;">
      💡 <em>O sistema adiciona automaticamente <code>#Shorts</code> para o YouTube e <code>#tiktok #fyp</code> para o TikTok, mantendo as legendas limpas e com alto alcance.</em>
    </p>
  </div>

  <!-- PARTE 1: YOUTUBE SHORTS -->
  <h2>🔴 3. Passo a Passo: YouTube Shorts</h2>
  <p>
    O serviço do YouTube Shorts roda como um daemon Java em segundo plano. Ele gerencia fila, controle de tentativas e agendamento a cada 3 horas.
  </p>

  <div class="step-box">
    <div class="step-header">
      <span class="step-num step-num-yt">1</span>
      <span class="step-title">Verificar o status do YouTube Shorts</span>
    </div>
    <p>Para ver se o daemon está rodando e quantos vídeos estão na fila:</p>
    <pre><code>./videopost status</code></pre>
    <p style="font-size: 8.5pt; color: #64748b;">
      Retorna: perfil ativo, status do YouTube Shorts (✓), total pendente, publicado e falho.
    </p>
  </div>

  <div class="step-box">
    <div class="step-header">
      <span class="step-num step-num-yt">2</span>
      <span class="step-title">Iniciar o agendador em segundo plano</span>
    </div>
    <p>Se o serviço estiver parado e você desejar iniciá-lo:</p>
    <pre><code>./videopost start</code></pre>
    <p style="font-size: 8.5pt; color: #64748b;">
      O serviço lê a pasta de vídeos, monta a fila no SQLite e programa postagens no intervalo de 3 horas.
    </p>
  </div>

  <div class="step-box">
    <div class="step-header">
      <span class="step-num step-num-yt">3</span>
      <span class="step-title">Parar o agendador</span>
    </div>
    <pre><code>./videopost stop</code></pre>
  </div>

  <div class="step-box">
    <div class="step-header">
      <span class="step-num step-num-yt">4</span>
      <span class="step-title">Acompanhar logs em tempo real</span>
    </div>
    <pre><code>tail -f .videopost/videopost.log</code></pre>
  </div>

  <!-- PARTE 2: TIKTOK -->
  <h2>🔵 4. Passo a Passo: TikTok</h2>
  <p>
    O serviço do TikTok foi construído especificamente para contornar as restrições da API Sandbox, postando <strong>diretamente no feed público</strong> via automação web no TikTok Studio.
  </p>

  <div class="step-box">
    <div class="step-header">
      <span class="step-num step-num-tt">1</span>
      <span class="step-title">Conexão única de conta (Login Inicial)</span>
    </div>
    <p>Você só precisa conectar sua conta do TikTok <strong>uma única vez</strong>. Execute:</p>
    <pre><code>./tiktok login</code></pre>
    <p style="font-size: 8.5pt; color: #334155;">
      • Uma janela do Google Chrome será aberta diretamente na sua tela.<br>
      • Conecte com sua conta do TikTok (você pode escanear o QR Code pelo aplicativo do celular ou usar Google/e-mail).<br>
      • Assim que você entrar, o sistema salva a sessão de forma permanente em <code>tiktok-service/browser_profile/</code> e fecha a janela.
    </p>
  </div>

  <div class="step-box">
    <div class="step-header">
      <span class="step-num step-num-tt">2</span>
      <span class="step-title">Iniciar o robô autônomo do TikTok</span>
    </div>
    <p>Após logar uma vez, inicie o serviço em segundo plano:</p>
    <pre><code>./tiktok start</code></pre>
    <p style="font-size: 8.5pt; color: #64748b;">
      O robô rodará silenciosamente no fundo publicando a cada 3 horas, sem precisar de celular ou intervenção manual.
    </p>
  </div>

  <div class="step-box">
    <div class="step-header">
      <span class="step-num step-num-tt">3</span>
      <span class="step-title">Verificar o status e a fila de postagens</span>
    </div>
    <pre><code>./tiktok status
./tiktok queue</code></pre>
    <p style="font-size: 8.5pt; color: #64748b;">
      Mostra a lista completa de vídeos, horários agendados e confirmação de publicações.
    </p>
  </div>

  <div class="step-box">
    <div class="step-header">
      <span class="step-num step-num-tt">4</span>
      <span class="step-title">Forçar a publicação imediata de um vídeo</span>
    </div>
    <p>Se quiser que o próximo vídeo seja postado agora mesmo sem esperar o agendamento de 3 horas:</p>
    <pre><code>./tiktok post-next</code></pre>
  </div>

  <div class="step-box">
    <div class="step-header">
      <span class="step-num step-num-tt">5</span>
      <span class="step-title">Auditoria e Prints de confirmação</span>
    </div>
    <p>
      A cada publicação, o TikTok salva automaticamente um print da tela de confirmação em:
    </p>
    <pre><code>tiktok-service/logs/screenshots/</code></pre>
  </div>

  <div class="page-break"></div>

  <!-- CHEATSHEET / TABELA DE COMANDOS -->
  <h2>⚡ 5. Tabela Rápida de Comandos (Cheatsheet)</h2>

  <div class="table-container">
    <table>
      <thead>
        <tr>
          <th>Objetivo</th>
          <th>Comando YouTube Shorts</th>
          <th>Comando TikTok</th>
        </tr>
      </thead>
      <tbody>
        <tr>
          <td><strong>Ver Status do Serviço</strong></td>
          <td><code>./videopost status</code></td>
          <td><code>./tiktok status</code></td>
        </tr>
        <tr>
          <td><strong>Ver Fila de Vídeos</strong></td>
          <td><code>./videopost queue</code></td>
          <td><code>./tiktok queue</code></td>
        </tr>
        <tr>
          <td><strong>Iniciar Serviço no Fundo</strong></td>
          <td><code>./videopost start</code></td>
          <td><code>./tiktok start</code></td>
        </tr>
        <tr>
          <td><strong>Parar Serviço</strong></td>
          <td><code>./videopost stop</code></td>
          <td><code>./tiktok stop</code></td>
        </tr>
        <tr>
          <td><strong>Postar Imediatamente</strong></td>
          <td><em>(segue cronograma)</em></td>
          <td><code>./tiktok post-next</code></td>
        </tr>
        <tr>
          <td><strong>Login / Autenticação</strong></td>
          <td><code>./videopost auth noticiabrasil</code></td>
          <td><code>./tiktok login</code></td>
        </tr>
        <tr>
          <td><strong>Conferir Sessão</strong></td>
          <td><em>automático via OAuth</em></td>
          <td><code>./tiktok check-auth</code></td>
        </tr>
        <tr>
          <td><strong>Ver Logs ao Vivo</strong></td>
          <td><code>tail -f .videopost/videopost.log</code></td>
          <td><code>tail -f tiktok-service/logs/tiktok.log</code></td>
        </tr>
      </tbody>
    </table>
  </div>

  <!-- DICAS E BOAS PRÁTICAS -->
  <h2>💡 6. Dicas e Boas Práticas Operacionais</h2>

  <div class="callout callout-success">
    <strong>✅ Como adicionar novos vídeos no futuro:</strong><br>
    Basta salvar o novo arquivo de vídeo (ex: <code>meu_video.mp4</code>) e o seu respectivo arquivo de texto (<code>meu_video.json</code>) dentro da pasta <code>videos curtos campanha.nosync/</code>. Ambos os serviços detectam arquivos novos automaticamente e adicionam ao final da fila no próximo ciclo!
  </div>

  <div class="callout">
    <strong>☁️ Por que usar a pasta .nosync?</strong><br>
    O sufixo <code>.nosync</code> impede que o iCloud do macOS transfira os vídeos para a nuvem e deixe apenas atalhos locais. Assim, o sistema sempre tem acesso instantâneo aos arquivos pesados de vídeo.
  </div>

  <div class="callout callout-warn">
    <strong>⚠️ Se reiniciar o Mac:</strong><br>
    Caso o seu computador seja reiniciado ou desligado, basta abrir o Terminal na pasta do projeto e subir os dois serviços novamente com:
    <pre style="margin-top: 6px;"><code>./videopost start && ./tiktok start</code></pre>
  </div>

  <div style="margin-top: 30px; padding: 14px; background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; text-align: center; font-size: 8.5pt; color: #64748b;">
    Manual gerado automaticamente para o canal <strong>Notícia Brasil</strong> • Outubro de 2026
  </div>

</body>
</html>
"""

# Salva arquivo HTML temporário
html_path = "/Users/ludmilamoreira/videopost/manual_temp.html"
pdf_path = "/Users/ludmilamoreira/videopost/MANUAL_VIDEOPOST_YOUTUBE_TIKTOK.pdf"

with open(html_path, "w", encoding="utf-8") as f:
    f.write(html_content)

print(f"HTML gravado em {html_path}. Gerando PDF...")

with sync_playwright() as p:
    browser = p.chromium.launch(channel="chrome")
    page = browser.new_page()
    page.goto(f"file://{html_path}")
    page.wait_for_load_state("networkidle")
    page.pdf(
        path=pdf_path,
        format="A4",
        print_background=True,
        margin={"top": "15mm", "bottom": "18mm", "left": "14mm", "right": "14mm"}
    )
    browser.close()

if os.path.exists(html_path):
    os.remove(html_path)

print(f"✅ PDF gerado com sucesso em: {pdf_path}")
