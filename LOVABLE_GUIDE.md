# 🚀 Guia de Implantação na Nuvem & Integração com Lovable.dev

Este guia explica como colocar o **VideoPost** para rodar **24 horas por dia, 7 dias por semana na Nuvem** (sem precisar deixar seu Mac ou computador ligado) puxando vídeos automaticamente de uma pasta do **Google Drive**, e como conectar o **Lovable.dev** como seu painel de controle visual.

---

## 🏗️ Como Funciona a Arquitetura

```
+-------------------------------------------------------------+
|                     GOOGLE DRIVE                            |
|          (Pasta Compartilhada com seus Vídeos)              |
+-------------------------------------------------------------+
                              │
                              ▼ (Download automático a cada 15m)
+-------------------------------------------------------------+
|                 NUVEM (Railway / VPS / Docker)              |
|                                                             |
|  1. Google Drive Sync  ──▶  Baixa novos .mp4 e gera tags    |
|  2. YouTube Daemon     ──▶  Posta a cada 3h no YouTube      |
|  3. Status & REST API  ──▶  Disponibiliza métricas & status |
+-------------------------------------------------------------+
                              │
                              ▼ (Consome a API REST)
+-------------------------------------------------------------+
|                 FRONTEND (Lovable / Web)                    |
|                                                             |
|  • Dashboard Nativo Embutido (já pronto em / )              |
|  • Ou App personalizado construído no Lovable.dev           |
+-------------------------------------------------------------+
```

---

## 📋 Passo 1: Como Configurar a Pasta do Google Drive

1. Crie uma pasta no seu Google Drive (ex: `Vídeos Campanha`).
2. Clique com botão direito na pasta ➔ **Compartilhar** ➔ **Compartilhar**.
3. Em **Acesso geral**, selecione **"Qualquer pessoa com o link"** (como Leitor).
4. Copie o link da pasta:
   - Exemplo: `https://drive.google.com/drive/folders/1ABC123xyz456_ExemploFolderId`
5. O seu **`GDRIVE_FOLDER_ID`** é o código final do link: `1ABC123xyz456_ExemploFolderId`.

> 💡 **Nota sobre metadados**: Você pode simplesmente jogar arquivos `.mp4` ou `.mov` dentro da pasta. Se você não colocar um arquivo `.json` acompanhando o vídeo, o robô automaticamente gerará as hashtags de alto engajamento (`#flaviobolsonaro`, `#bolsonaro`, `#noticiabrasil`, etc.) e o título limpo!

---

## 📦 Passo 2: Exportar suas Credenciais para a Nuvem

Para que o robô na nuvem já comece autorizado no YouTube (sem pedir login de navegador), execute no terminal do projeto:

```bash
python3 cloud/bundle.py export
```

Este comando gera o arquivo `.env.cloud` com as variáveis prontas para a nuvem:
- `VIDEOPOST_SECRET_KEY=...`
- `VIDEOPOST_PROFILE=noticiabrasil`
- `VIDEOS_DIR=/app/videos`
- `GDRIVE_FOLDER_ID=seu_id_aqui`
- `YOUTUBE_ENC_B64=...`
- `VIDEOPOST_CONFIG_B64=...`

---

## ☁️ Passo 3: Subir na Nuvem (Exemplo: Railway.app)

O repositório já possui `Dockerfile` e `railway.json` configurados.

1. Acesse **[railway.app](https://railway.app)** e faça login com seu GitHub.
2. Clique em **"New Project"** ➔ **"Deploy from GitHub repo"**.
3. Selecione o repositório `codelab-hub/videopost`.
4. Em **Variables** do serviço no Railway:
   - Cole as variáveis geradas no **Passo 2** (`VIDEOPOST_SECRET_KEY`, `YOUTUBE_ENC_B64`, etc.).
   - Preencha `GDRIVE_FOLDER_ID` com o ID da sua pasta do Drive.
5. Em **Settings** ➔ **Networking** ➔ Clique em **"Generate Domain"** (ex: `https://videopost-production.up.railway.app`).

Pronto! Seu robô já estará:
- Sincronizando vídeos da pasta do Drive a cada 15 minutos.
- Publicando no YouTube Shorts a cada 3 horas.
- Disponibilizando um painel web na URL gerada!

---

## 🖥️ Painel Web Nativo (Pronto para Uso)

Ao acessar a URL gerada (ex: `https://videopost-production.up.railway.app/`), você já tem um **Dashboard completo** com:
- Status em tempo real do YouTube Shorts e TikTok.
- Quantidade de vídeos na fila, publicados e próximos disparos.
- Botão interativo **"Sincronizar Google Drive Agora"**.
- Tabela com todos os vídeos e links diretos para assistir os Shorts postados!

---

## 🎨 Passo 4: Criar um Dashboard no Lovable.dev

Se desejar criar um app com a sua própria identidade visual ou funcionalidades extras no **Lovable.dev**:

1. Acesse **[lovable.dev](https://lovable.dev)** e clique em **"Create New App"**.
2. Copie e cole o seguinte **Prompt**:

```text
Crie um dashboard moderno, responsivo e em Dark Mode para gerenciar a publicação de vídeos em redes sociais (YouTube Shorts e TikTok).

O backend já está rodando e fornece uma API REST no endereço:
https://SEU-APP.up.railway.app

Endpoints disponíveis:
1. GET /api/status
   - Retorna status geral, contagem de vídeos pendentes, postados e falhas.
   - Retorna próximo vídeo agendado e último vídeo publicado com link.
2. GET /api/queue
   - Retorna a lista completa de vídeos na fila com título, status ('Na Fila', 'Publicado', 'Falhou'), horário agendado e link do YouTube.
3. POST /api/sync-drive
   - Dispara a sincronização imediata dos vídeos da pasta do Google Drive.
4. GET /api/logs
   - Retorna os últimos logs do sistema.

Requisitos de UI:
- Cards superiores com métricas: Vídeos Publicados, Fila de Espera, Taxa de Sucesso e Status do Google Drive.
- Botão de destaque "Sincronizar Google Drive Agora" que dispara o POST /api/sync-drive com toast feedback.
- Tabela de fila com busca por nome de arquivo e filtros por status.
- Card com o último vídeo publicado e botão para assistir diretamente no YouTube.
- Atualização automática dos dados a cada 15 segundos.
```

3. O Lovable gerará toda a interface React + Tailwind pronta para você usar no computador ou celular!
