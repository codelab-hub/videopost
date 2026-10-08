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
    margin: 13mm 13mm 15mm 13mm;
    @bottom-right {
      content: counter(page);
      font-size: 8.5pt;
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
    font-size: 9pt;
    line-height: 1.48;
  }

  .header {
    background: linear-gradient(135deg, #0f172a 0%, #1e1b4b 50%, #312e81 100%);
    color: #ffffff;
    padding: 18px 22px;
    border-radius: 10px;
    margin-bottom: 16px;
    box-shadow: 0 8px 20px -4px rgba(15, 23, 42, 0.2);
  }

  .header-badges {
    display: flex;
    gap: 8px;
    margin-bottom: 8px;
    flex-wrap: wrap;
  }

  .badge {
    font-size: 7.5pt;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0.5px;
    padding: 3px 8px;
    border-radius: 9999px;
  }

  .badge-yt { background: #ef4444; color: #fff; }
  .badge-tt { background: #06b6d4; color: #fff; }
  .badge-auto { background: #10b981; color: #fff; }
  .badge-gh { background: #6366f1; color: #fff; }

  .header h1 {
    font-size: 17pt;
    font-weight: 800;
    letter-spacing: -0.5px;
    line-height: 1.2;
    margin-bottom: 4px;
  }

  .header p {
    font-size: 9.5pt;
    color: #cbd5e1;
    max-width: 680px;
  }

  .meta-bar {
    margin-top: 10px;
    padding-top: 8px;
    border-top: 1px solid rgba(255, 255, 255, 0.15);
    display: flex;
    justify-content: space-between;
    font-size: 8pt;
    color: #94a3b8;
    flex-wrap: wrap;
    gap: 6px;
  }

  .meta-bar strong { color: #f8fafc; }

  h2 {
    font-size: 11.5pt;
    font-weight: 700;
    color: #0f172a;
    margin: 16px 0 8px 0;
    display: flex;
    align-items: center;
    gap: 6px;
    border-bottom: 2px solid #e2e8f0;
    padding-bottom: 4px;
    page-break-after: avoid;
  }

  h3 {
    font-size: 9.5pt;
    font-weight: 600;
    color: #1e293b;
    margin: 10px 0 4px 0;
    page-break-after: avoid;
  }

  p { margin-bottom: 6px; }

  .grid-2 {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 10px;
    margin: 8px 0;
  }

  .card {
    background: #f8fafc;
    border: 1px solid #e2e8f0;
    border-radius: 8px;
    padding: 10px 12px;
    page-break-inside: avoid;
  }

  .card-yt { border-left: 4px solid #ef4444; }
  .card-tt { border-left: 4px solid #06b6d4; }
  .card-strategy { border-left: 4px solid #8b5cf6; }
  .card-creds { border-left: 4px solid #f59e0b; }

  .card-title {
    font-weight: 700;
    font-size: 9.5pt;
    color: #0f172a;
    margin-bottom: 4px;
    display: flex;
    align-items: center;
    gap: 6px;
  }

  .step-box {
    background: #ffffff;
    border: 1px solid #e2e8f0;
    border-radius: 8px;
    padding: 10px 12px;
    margin-bottom: 8px;
    page-break-inside: avoid;
  }

  .step-header {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 4px;
  }

  .step-num {
    width: 20px;
    height: 20px;
    border-radius: 50%;
    background: #4f46e5;
    color: #fff;
    font-size: 8pt;
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
    font-size: 9.5pt;
    color: #0f172a;
  }

  pre, code {
    font-family: 'JetBrains Mono', monospace;
  }

  code {
    background: #f1f5f9;
    color: #0f172a;
    padding: 2px 4px;
    border-radius: 4px;
    font-size: 8pt;
  }

  pre {
    background: #0f172a;
    color: #e2e8f0;
    padding: 8px 12px;
    border-radius: 6px;
    font-size: 7.8pt;
    line-height: 1.4;
    margin: 5px 0;
    page-break-inside: avoid;
  }

  pre code {
    background: transparent;
    color: inherit;
    padding: 0;
  }

  .table-container {
    margin: 8px 0;
    page-break-inside: avoid;
  }

  table {
    width: 100%;
    border-collapse: collapse;
    font-size: 8pt;
  }

  th {
    background: #0f172a;
    color: #ffffff;
    text-align: left;
    padding: 6px 8px;
    font-weight: 600;
  }

  td {
    padding: 6px 8px;
    border-bottom: 1px solid #e2e8f0;
    vertical-align: top;
  }

  tr:nth-child(even) td {
    background: #f8fafc;
  }

  .callout {
    background: #eff6ff;
    border-left: 4px solid #3b82f6;
    padding: 8px 12px;
    border-radius: 0 6px 6px 0;
    font-size: 8.5pt;
    margin: 8px 0;
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

  .callout-purple {
    background: #faf5ff;
    border-left-color: #a855f7;
    color: #581c87;
  }

  .page-break {
    page-break-before: always;
  }

  ol, ul {
    margin-bottom: 6px;
  }
</style>
</head>
<body>

  <!-- ==================== PÁGINA 1 ==================== -->
  <div class="header">
    <div class="header-badges">
      <span class="badge badge-yt">YouTube Shorts</span>
      <span class="badge badge-tt">TikTok Studio</span>
      <span class="badge badge-auto">Automação 100% Autônoma</span>
      <span class="badge badge-gh">Open Source / GitHub</span>
    </div>
    <h1>VideoPost — Manual de Operação & Crescimento</h1>
    <p>Guia completo de arquitetura, configuração de credenciais, comandos da CLI e estratégias de retenção para YouTube Shorts e TikTok.</p>
    <div class="meta-bar">
      <span>GitHub Oficial: <strong>github.com/codelab-hub/videopost</strong></span>
      <span>Canal YouTube: <strong>youtube.com/@NotíciaBrasil-o1p</strong></span>
      <span>TikTok Studio: <strong>tiktok.com/tiktokstudio</strong></span>
      <span>Versão: <strong>2.0 Modular</strong></span>
    </div>
  </div>

  <h2>📌 1. Visão Geral da Arquitetura Modular</h2>
  <p>
    O VideoPost foi desenhado em <strong>dois microsserviços completamente independentes e isolados</strong> para garantir resiliência máxima: se uma plataforma exigir manutenção ou reautenticação, a outra continua publicando no ar sem qualquer impacto.
  </p>

  <div class="grid-2">
    <div class="card card-yt">
      <div class="card-title">🔴 YouTube Shorts (Java Daemon)</div>
      <p style="font-size: 8pt; color: #475569; margin-bottom: 4px;">
        Opera via <strong>API Oficial do YouTube (OAuth2 + Google Resumable Upload)</strong> em segundo plano.
      </p>
      <ul style="font-size: 8pt; color: #334155; padding-left: 14px;">
        <li>Publica direto no canal oficial Notícia Brasil.</li>
        <li>Renovação transparente de access tokens OAuth.</li>
        <li>Auto-retry contra expiração de sessão HTTP 410.</li>
        <li>Comando raiz: <code>./videopost</code></li>
      </ul>
    </div>

    <div class="card card-tt">
      <div class="card-title">🔵 TikTok (Microsserviço Web Studio)</div>
      <p style="font-size: 8pt; color: #475569; margin-bottom: 4px;">
        Opera via <strong>Playwright + Chrome nativo</strong> direto no TikTok Studio Web.
      </p>
      <ul style="font-size: 8pt; color: #334155; padding-left: 14px;">
        <li>Publica direto no feed público (sem limites de Sandbox).</li>
        <li>Perfil do Chrome persistente (login feito apenas uma vez).</li>
        <li>Grava prints automáticos de auditoria de cada publicação.</li>
        <li>Comando raiz: <code>./tiktok</code></li>
      </ul>
    </div>
  </div>

  <h2>📂 2. Estrutura de Pastas e Metadados</h2>
  <p>Ambos os serviços consomem os vídeos e metadados da pasta local segura:</p>
  <pre><code>/Users/ludmilamoreira/Desktop/frutinhas do brasil/videos curtos campanha.nosync/</code></pre>

  <p>
    Cada vídeo (<code>.mp4</code> ou <code>.mov</code>) possui um arquivo <code>.json</code> companheiro com o mesmo nome contendo legenda base e hashtags:
  </p>

  <div class="step-box">
    <div class="step-header">
      <span class="step-num">📄</span>
      <span class="step-title">Exemplo de arquivo de metadados companheiro (ex: 1791369200733.json)</span>
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
    <p style="font-size: 8pt; color: #475569; margin-top: 4px;">
      💡 <em>O VideoPost injeta dinamicamente <code>#Shorts</code> para o YouTube e <code>#tiktok #fyp</code> para o TikTok, preservando as legendas limpas e maximizando a descoberta algorítmica.</em>
    </p>
  </div>

  <div class="page-break"></div>

  <!-- ==================== PÁGINA 2: CREDENCIAIS ==================== -->
  <h2>🔑 3. Como Obter e Configurar as Client Credentials</h2>
  <p>
    O VideoPost adota padrão de segurança <strong>local-first</strong>: nenhuma senha ou token é enviado para servidores externos. As credenciais são armazenadas criptografadas com <strong>AES-256-GCM</strong> no seu computador.
  </p>

  <div class="card card-yt" style="margin-bottom: 12px;">
    <div class="card-title">🔴 YouTube Shorts: Google Cloud Console</div>
    <p style="font-size: 8pt; color: #334155; margin-bottom: 4px;">
      <strong>Passo 1 — Onde pegar no Google Cloud:</strong>
    </p>
    <ol style="font-size: 8pt; color: #334155; padding-left: 16px; margin-bottom: 6px;">
      <li>Acesse o <strong>Google Cloud Console</strong>: <code>https://console.cloud.google.com</code></li>
      <li>Crie ou selecione seu projeto (ex: <em>NoticiaBrasil-Auto</em>).</li>
      <li>Vá em <strong>APIs e Serviços &gt; Biblioteca</strong>, pesquise por <strong>YouTube Data API v3</strong> e clique em <strong>Ativar</strong>.</li>
      <li>Em <strong>Tela de Consentimento OAuth</strong>, marque <em>Externo</em>, preencha o nome do app e em <em>Usuários de teste</em> adicione o e-mail Google do seu canal.</li>
      <li>Em <strong>Credenciais &gt; Criar Credenciais &gt; ID do cliente OAuth</strong>, escolha o tipo <strong>Aplicativo para Computador (Desktop App)</strong>.</li>
      <li>Copie o <strong>Client ID</strong> (termina em <code>.apps.googleusercontent.com</code>) e o <strong>Client Secret</strong>.</li>
    </ol>
    <p style="font-size: 8pt; color: #334155; margin-bottom: 4px;">
      <strong>Passo 2 — Onde colocar no projeto:</strong> Execute no terminal na raiz do VideoPost:
    </p>
    <pre><code>./videopost auth youtube_shorts \
  --client-id "SEU_CLIENT_ID.apps.googleusercontent.com" \
  --client-secret "SEU_CLIENT_SECRET"</code></pre>
    <p style="font-size: 7.5pt; color: #64748b;">
      O VideoPost abrirá o navegador para consentimento e salvará as credenciais criptografadas em <code>.videopost/credentials/noticiabrasil/youtube_shorts.enc</code>. O token se auto-renova sozinho para sempre.
    </p>
  </div>

  <div class="card card-tt">
    <div class="card-title">🔵 TikTok: Configuração da Conta</div>
    <p style="font-size: 8pt; color: #334155; margin-bottom: 4px;">
      <strong>Opção A — Microsserviço Autônomo Web (Recomendado):</strong>
    </p>
    <p style="font-size: 8pt; color: #334155;">
      Não necessita de aprovação burocrática de empresa ou chaves de desenvolvedor. Basta rodar:
    </p>
    <pre><code>./tiktok login</code></pre>
    <p style="font-size: 7.5pt; color: #64748b; margin-bottom: 8px;">
      Uma janela do Google Chrome se abrirá. Faça login via QR Code ou Google. A sessão é persistida localmente em <code>tiktok-service/browser_profile/</code> e posta direto no feed público.
    </p>

    <p style="font-size: 8pt; color: #334155; margin-bottom: 4px;">
      <strong>Opção B — TikTok Developer Portal (API Oficial Sandbox):</strong>
    </p>
    <ol style="font-size: 8pt; color: #334155; padding-left: 16px; margin-bottom: 4px;">
      <li>Acesse <code>https://developers.tiktok.com</code> ➔ <em>Manage apps</em>.</li>
      <li>Copie a <strong>Client Key</strong> e <strong>Client Secret</strong> em <em>Basic settings</em>.</li>
      <li>Cadastre a Redirect URI: <code>https://httpbin.org/get</code> (ou seu servidor de callback).</li>
      <li>Adicione os escopos <code>video.upload</code> e <code>user.info.basic</code>.</li>
    </ol>
    <pre><code>./videopost auth tiktok \
  --client-key "SUA_CLIENT_KEY" \
  --client-secret "SUA_CLIENT_SECRET"</code></pre>
  </div>

  <div class="page-break"></div>

  <!-- ==================== PÁGINA 3 ==================== -->
  <h2>🚀 4. Estratégia de Crescimento Orgânico & Viralização</h2>
  <p>
    O algoritmo do TikTok e do YouTube Shorts em 2026 prioriza **velocidade de retenção inicial** (os primeiros 3 segundos) e **comentários espontâneos**. Aplicamos três pilares fundamentais de engajamento:
  </p>

  <div class="card card-strategy" style="margin-bottom: 10px;">
    <div class="card-title">✍️ A Técnica das 3 Linhas para Legendas Magnéticas</div>
    <p style="font-size: 8pt; color: #334155;">
      A legenda não deve ser descritiva; ela deve forçar a pessoa a <strong>abrir os comentários enquanto o vídeo continua rodando em segundo plano</strong> (dobrando o tempo de retenção!):
    </p>
    <ul style="font-size: 8pt; color: #334155; padding-left: 14px; margin-top: 4px;">
      <li><strong>Linha 1 (Loop de Curiosidade / Gancho Aberto):</strong> "O detalhe no final que quase ninguém percebeu..." ou "O que você faria nessa situação? 👀"</li>
      <li><strong>Linha 2 (Micro-CTA de Debate):</strong> "Você concorda ou acha que passou do ponto? Deixa sua opinião sincera 👇"</li>
      <li><strong>Linha 3 (SEO Keywords embutidas):</strong> Termos de pesquisa orgânica (ex: "bastidores da tv", "escala 6x1", "humor brasil").</li>
    </ul>
  </div>

  <div class="card card-strategy" style="margin-bottom: 10px;">
    <div class="card-title">🏷️ O Framework 3-2-1 de Hashtags Relevantes</div>
    <p style="font-size: 8pt; color: #334155;">
      Evite o excesso de 20 hashtags genéricas. Use entre <strong>5 a 6 hashtags focadas em indexação semântica</strong>:
    </p>
    <div style="font-size: 8pt; color: #1e293b; margin-top: 4px; display: grid; grid-template-columns: 1fr 1fr; gap: 8px;">
      <div style="background:#f1f5f9; padding:6px 10px; border-radius:6px;">
        <strong>2 a 3 Tags de Nicho / Assunto:</strong><br>
        <code>#bastidorestv</code> <code>#fofocas</code> <code>#escala6x1</code> <code>#clt</code>
      </div>
      <div style="background:#f1f5f9; padding:6px 10px; border-radius:6px;">
        <strong>1 a 2 Tags de Formato / Comunidade:</strong><br>
        <code>#entretenimento</code> <code>#humorbr</code> <code>#noticias</code>
      </div>
    </div>
  </div>

  <div class="card card-strategy">
    <div class="card-title">⏰ As 4 Janelas de Ouro de Horários no Brasil (Sem postar de madrugada!)</div>
    <p style="font-size: 8pt; color: #334155; margin-bottom: 6px;">
      Postar de madrugada (02h às 05h) "mata" o alcance do vídeo porque não há velocidade de visualizações nas primeiras horas. Concentre as publicações nos picos de atenção:
    </p>
    <table>
      <thead>
        <tr>
          <th>Janela</th>
          <th>Horário (Brasília)</th>
          <th>Comportamento do Público</th>
          <th>Tipo de Vídeo Recomendado</th>
        </tr>
      </thead>
      <tbody>
        <tr>
          <td><strong>1. Manhã / Café</strong></td>
          <td><code>07:30 – 09:00</code></td>
          <td>Trajeto para o trabalho, primeira checada no celular.</td>
          <td>Curiosidades rápidas, tecnologia e reflexões leves.</td>
        </tr>
        <tr>
          <td><strong>2. Almoço</strong></td>
          <td><code>12:00 – 13:30</code></td>
          <td>Pausa de descanso; alto consumo de entretenimento.</td>
          <td>Cortes de TV, reality shows (BBB), memes e flagras.</td>
        </tr>
        <tr>
          <td><strong>3. Volta para Casa</strong></td>
          <td><code>17:30 – 19:00</code></td>
          <td>Fim de expediente; alta vontade de rir ou desabafar.</td>
          <td>Humor de trabalho, CLT, convivência e debates sociais.</td>
        </tr>
        <tr>
          <td><strong>4. Horário Nobre</strong></td>
          <td><code>20:30 – 22:30</code></td>
          <td>Relaxamento no sofá/cama; <strong>máxima retenção</strong>.</td>
          <td>Entrevistas marcantes, declarações polêmicas e debates.</td>
        </tr>
      </tbody>
    </table>
  </div>

  <div class="page-break"></div>

  <!-- ==================== PÁGINA 4 ==================== -->
  <h2>🔴 5. Passo a Passo: YouTube Shorts</h2>
  <p>
    O serviço do YouTube Shorts roda em segundo plano gerenciando tentativas, token refresh e controle de cotas diárias de upload.
  </p>

  <div class="step-box">
    <div class="step-header">
      <span class="step-num step-num-yt">1</span>
      <span class="step-title">Verificar o status do YouTube Shorts</span>
    </div>
    <pre><code>./videopost status</code></pre>
    <p style="font-size: 8pt; color: #64748b;">
      Exibe perfil ativo, status do YouTube Shorts (✓), total pendente, publicado e falho.
    </p>
  </div>

  <div class="step-box">
    <div class="step-header">
      <span class="step-num step-num-yt">2</span>
      <span class="step-title">Iniciar o agendador em segundo plano</span>
    </div>
    <pre><code>./videopost start</code></pre>
    <p style="font-size: 8pt; color: #64748b;">
      Lê a pasta de vídeos, monta a fila no SQLite e programa postagens nos intervalos definidos.
    </p>
  </div>

  <div class="step-box">
    <div class="step-header">
      <span class="step-num step-num-yt">3</span>
      <span class="step-title">Parar o agendador & Acompanhar logs ao vivo</span>
    </div>
    <pre><code>./videopost stop
tail -f .videopost/videopost.log</code></pre>
  </div>

  <h2>🔵 6. Passo a Passo: TikTok</h2>
  <p>
    O serviço do TikTok publica <strong>diretamente no feed público</strong> via automação web no TikTok Studio, dispensando intervenção no celular.
  </p>

  <div class="step-box">
    <div class="step-header">
      <span class="step-num step-num-tt">1</span>
      <span class="step-title">Conexão única de conta (Login Inicial)</span>
    </div>
    <pre><code>./tiktok login</code></pre>
    <p style="font-size: 8pt; color: #334155;">
      • Uma janela do Google Chrome será aberta diretamente na sua tela.<br>
      • Conecte com sua conta do TikTok (escaneie o QR Code no app ou use Google/e-mail).<br>
      • O sistema salva a sessão de forma permanente em <code>tiktok-service/browser_profile/</code> e fecha a janela.
    </p>
  </div>

  <div class="step-box">
    <div class="step-header">
      <span class="step-num step-num-tt">2</span>
      <span class="step-title">Iniciar o robô autônomo do TikTok</span>
    </div>
    <pre><code>./tiktok start</code></pre>
    <p style="font-size: 8pt; color: #64748b;">
      O robô rodará silenciosamente no fundo publicando a cada 3 horas, 100% mãos-livres.
    </p>
  </div>

  <div class="step-box">
    <div class="step-header">
      <span class="step-num step-num-tt">3</span>
      <span class="step-title">Verificar a fila ou forçar postagem imediata</span>
    </div>
    <pre><code>./tiktok queue
./tiktok post-next</code></pre>
    <p style="font-size: 8pt; color: #64748b;">
      <code>./tiktok post-next</code> publica o próximo vídeo da fila imediatamente sem esperar o cronômetro. Prints salvos em <code>tiktok-service/logs/screenshots/</code>.
    </p>
  </div>

  <div class="page-break"></div>

  <!-- ==================== PÁGINA 5 ==================== -->
  <h2>⚡ 7. Tabela Rápida de Comandos (Cheatsheet)</h2>

  <div class="table-container">
    <table>
      <thead>
        <tr>
          <th>Ação Desejada</th>
          <th>YouTube Shorts</th>
          <th>TikTok</th>
        </tr>
      </thead>
      <tbody>
        <tr>
          <td><strong>Ver Status Atual</strong></td>
          <td><code>./videopost status</code></td>
          <td><code>./tiktok status</code></td>
        </tr>
        <tr>
          <td><strong>Listar Fila de Vídeos</strong></td>
          <td><code>./videopost queue</code></td>
          <td><code>./tiktok queue</code></td>
        </tr>
        <tr>
          <td><strong>Iniciar no Fundo</strong></td>
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
          <td><em>(cronograma automático)</em></td>
          <td><code>./tiktok post-next</code></td>
        </tr>
        <tr>
          <td><strong>Autenticar Conta</strong></td>
          <td><code>./videopost auth noticiabrasil</code></td>
          <td><code>./tiktok login</code></td>
        </tr>
        <tr>
          <td><strong>Checar Sessão</strong></td>
          <td><em>automático via OAuth</em></td>
          <td><code>./tiktok check-auth</code></td>
        </tr>
        <tr>
          <td><strong>Acompanhar Logs</strong></td>
          <td><code>tail -f .videopost/videopost.log</code></td>
          <td><code>tail -f tiktok-service/logs/tiktok.log</code></td>
        </tr>
      </tbody>
    </table>
  </div>

  <h2>🛡️ 8. Segurança, Git & Boas Práticas Operacionais</h2>

  <div class="callout callout-success">
    <strong>🔒 Repositório Público Seguro no GitHub:</strong><br>
    O projeto está hospedado em <strong>github.com/codelab-hub/videopost</strong>. O arquivo <code>.gitignore</code> foi blindado para que nenhuma credencial, token, chave OAuth, sessão de navegador (<code>browser_profile</code>) ou banco de dados SQLite local possa ser commitado.
  </div>

  <div class="callout callout-purple">
    <strong>🎬 Como adicionar novos vídeos no futuro:</strong><br>
    Basta salvar o novo arquivo de vídeo (ex: <code>novo_video.mp4</code>) e o respectivo arquivo de texto (<code>novo_video.json</code>) dentro da pasta <code>videos curtos campanha.nosync/</code>. Ambos os serviços detectam arquivos novos automaticamente no ciclo seguinte!
  </div>

  <div class="callout">
    <strong>☁️ Por que a pasta tem sufixo .nosync?</strong><br>
    O sufixo <code>.nosync</code> impede que o iCloud do macOS transfira os vídeos pesados para a nuvem deixando atalhos vazios, garantindo acesso instantâneo aos binários de vídeo.
  </div>

  <div class="callout callout-warn">
    <strong>⚠️ Se o Mac for reiniciado:</strong><br>
    Basta abrir o Terminal na pasta do projeto e religar ambos os serviços:
    <pre style="margin-top: 4px;"><code>./videopost start && ./tiktok start</code></pre>
  </div>

  <div style="margin-top: 18px; padding: 12px; background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; text-align: center; font-size: 8pt; color: #64748b;">
    <strong>VideoPost 2.0</strong> • Documentação oficial para <strong>Notícia Brasil</strong> e organização <strong>codelab-hub</strong> • Outubro de 2026
  </div>

</body>
</html>
"""

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
        margin={"top": "12mm", "bottom": "14mm", "left": "12mm", "right": "12mm"}
    )
    browser.close()

if os.path.exists(html_path):
    os.remove(html_path)

print(f"✅ PDF atualizado com sucesso em: {pdf_path}")
