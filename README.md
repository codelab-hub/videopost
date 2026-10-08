# VideoPost CLI 🎬

CLI profissional em Java 21 para gerenciamento, agendamento e publicação automatizada de vídeos em múltiplos perfis de redes sociais (TikTok, Facebook, Kwai e extensível para Instagram, YouTube Shorts, etc.).

Desenvolvido com foco em **Clean Architecture**, **local-first** (sem dependência de servidores centralizados), **isolamento estrito de falhas** e **idempotência de publicações**.

---

## 📌 Sumário
1. [Objetivo e Filosofia](#-objetivo-e-filosofia)
2. [Arquitetura e Princípios de Design](#-arquitetura-e-princípios-de-design)
3. [Requisitos](#-requisitos)
4. [Instalação e Build](#-instalação-e-build)
5. [Inicialização e Estrutura de Diretórios](#-inicialização-e-estrutura-de-diretórios)
6. [Configuração (`config.yml`)](#-configuração-configyml)
7. [Gerenciamento de Múltiplos Perfis](#-gerenciamento-de-múltiplos-perfis)
8. [Estratégia de Segurança das Credenciais](#-estratégia-de-segurança-das-credenciais)
9. [Autenticação das Plataformas](#-autenticação-das-plataformas)
10. [Metadados dos Vídeos (.mp4 + .json)](#-metadados-dos-vídeos-mp4--json)
11. [Guia de Comandos da CLI](#-guia-de-comandos-da-cli)
12. [Ciclo de Publicação e Scheduler](#-ciclo-de-publicação-e-scheduler)
13. [Isolamento de Falhas, Idempotência e Retry](#-isolamento-de-falhas-idempotência-e-retry)
14. [Status das APIs Oficiais e Limitações Conhecidas](#-status-das-apis-oficiais-e-limitações-conhecidas)
15. [Como Adicionar uma Nova Plataforma](#-como-adicionar-uma-nova-plataforma)
16. [Troubleshooting e Diagnóstico](#-troubleshooting-e-diagnóstico)

---

## 🎯 Objetivo e Filosofia

O **VideoPost CLI** foi concebido para criadoras de conteúdo e operações de mídia que desejam automatizar o fluxo de postagem de vídeos sem depender de servidores em nuvem dispendiosos ou ferramentas de scraping frágeis.

**Fluxo central:**
1. A usuária coloca os arquivos `.mp4` (e opcionais `.json` com legendas e hashtags) na pasta `./videos`.
2. A CLI detecta automaticamente novos arquivos e sincroniza a fila com agendamento escalonado no SQLite local.
3. No horário configurado, o sistema publica o vídeo nas plataformas ativas.
4. Cada plataforma opera de maneira independente: se o TikTok e o Facebook publicarem com sucesso, mas o Kwai falhar, o status do vídeo torna-se `PARTIALLY_COMPLETED`. O TikTok e Facebook **nunca** serão republicados em duplicidade.
5. Apenas a plataforma com falha é elegível para retry.
6. Nenhum token ou secret é gravado em texto plano ou exposto em logs.

---

## 🏛 Arquitetura e Princípios de Design

O projeto adota os princípios de **Clean Architecture** e **Ports and Adapters (Hexagonal)**:

```text
videopost/
├── pom.xml
├── README.md
├── MANUAL_VIDEOPOST_YOUTUBE_TIKTOK.pdf   <-- Manual ilustrado em PDF (A4)
├── videopost                             <-- Runner do daemon Java (YouTube Shorts & APIs)
├── tiktok                                <-- Runner do microsserviço TikTok Studio
├── tiktok-service/                       <-- Microsserviço Playwright isolado para TikTok Web
│   ├── config.example.yml
│   ├── main.py
│   ├── uploader.py
│   ├── scheduler.py
│   └── db.py
├── src/
│   ├── main/
│   │   ├── java/com/videopost/
│   │   │   ├── domain/                  <-- Núcleo de domínio (Entidades, Enums, Contratos)
│   │   │   │   ├── model/               (Video, Publication, Platform, VideoMetadata, PublishResult)
│   │   │   │   └── repository/          (VideoRepository, PublicationRepository)
│   │   │   ├── publisher/               <-- Adapters das plataformas e HTTP
│   │   │   │   ├── VideoPublisher.java  (Interface obrigatória)
│   │   │   │   ├── PublisherRegistry.java
│   │   │   │   ├── http/                (PlatformHttpClient, HttpResponseData)
│   │   │   │   ├── youtube/             (YouTubeShortsPublisher, YouTubeConfig)
│   │   │   │   ├── tiktok/              (TikTokPublisher, TikTokConfig)
│   │   │   │   ├── facebook/            (FacebookPublisher, FacebookConfig)
│   │   │   │   └── kwai/                (KwaiPublisher, KwaiConfig)
│   │   │   ├── application/             <-- Casos de uso e orquestração
│   │   │   │   ├── AppContext.java      (Container de DI e composição)
│   │   │   │   └── service/             (ScanService, QueueService, PublishingCoordinator,
│   │   │   │                             RetryService, ScheduleRunner, ProfileService, AuthService)
│   │   │   ├── infrastructure/          <-- Implementações técnicas (SQLite, Disco, Criptografia)
│   │   │   │   ├── config/              (AppConfig, YamlConfigLoader)
│   │   │   │   ├── security/            (CredentialStore, SecureFileCredentialStore - AES-256-GCM)
│   │   │   │   ├── filesystem/          (VideoFileScanner, JsonMetadataParser)
│   │   │   │   ├── persistence/         (DatabaseConnectionManager, SqliteVideoRepository, SqlitePublicationRepository)
│   │   │   │   └── http/                (JdkPlatformHttpClient)
│   │   │   └── cli/                     <-- Interface de linha de comando (Picocli)
│   │   │       ├── VideoPostCli.java    (Comando raiz e despacho)
│   │   │       ├── ui/                  (ConsoleUi, Ansi styling)
│   │   │       └── command/             (Init, Scan, Queue, Status, Start, Stop, Publish, Retry, Logs, Profile, Auth)
│   │   └── resources/
│   │       └── logback.xml              (Configuração de logs estruturados)
│   └── test/
│       └── java/com/videopost/          (Suite com 32 testes unitários e de integração)
└── .gitignore
```

### Regras Arquiteturais Seguidas à Risca:
* **Domínio Puro:** `Video` e `Publication` não conhecem SQLite, Picocli, TikTok, Facebook ou bibliotecas externas.
* **Comandos Magros:** Nenhuma regra de negócio reside nos comandos Picocli. Eles apenas tratam parâmetros de linha de comando, delegam aos serviços da aplicação e formatam a saída no terminal.
* **Sem `Thread.sleep()` disperso:** O scheduler (`ScheduleRunner`) utiliza temporização atômica coordenada (`CountDownLatch`, timeouts calibrados) com suporte a interrupção limpa e sinais de encerramento (`videopost stop` / Ctrl+C).
* **Sem dependência de Spring Boot:** Aplicação Java 21 pura, com inicialização instantânea (< 300ms) e empacotamento em Single Fat JAR autossuficiente via `maven-shade-plugin`.

---

## 💻 Requisitos

* **Java:** JDK 21 ou superior (`java -version`).
* **Maven:** 3.8+ (ou utilize o script `./mvnw` incluso no repositório).
* **Sistema Operacional:** macOS, Linux ou Windows (testado nativamente em macOS).

---

## 🚀 Instalação e Build

### 1. Clonar ou Acessar a Pasta
```bash
cd /Users/ludmilamoreira/videopost
```

### 2. Executar os Testes Unitários
```bash
./mvnw clean test
```
*Garante a validação de fila, idempotência, parser de metadados, SQLite, retentativas e adapters com mocks.*

### 3. Gerar o Executável
```bash
./mvnw package -DskipTests
```
O executável fat JAR será gerado em `target/videopost-cli-1.0.0-SNAPSHOT.jar`.

### 4. Executando o CLI
Você pode usar o script wrapper incluso:
```bash
./videopost --help
```
*(Opcional: crie um alias `alias videopost="/Users/ludmilamoreira/videopost/videopost"` no seu `~/.zshrc` ou `~/.bashrc` para executar `videopost` de qualquer diretório).*

---

## 📁 Inicialização e Estrutura de Diretórios

Para criar a estrutura local do VideoPost no diretório atual, execute:

```bash
./videopost init
```

Isso gera automaticamente:
```text
.videopost/
├── config.yml           <-- Configurações gerais (intervalos, pastas, flags)
├── videopost.db         <-- Banco SQLite local com índices e integridade referencial
├── videopost.log        <-- Arquivo de log detalhado
├── credentials/         <-- Cofre de credenciais criptografadas por perfil
└── profiles/            <-- Diretórios de metadados de cada perfil
videos/                  <-- Diretório padrão para depositar seus vídeos e JSONs
```

---

## ⚙️ Configuração (`config.yml`)

O arquivo `.videopost/config.yml` permite customização completa do fluxo:

```yaml
videos:
  directory: "./videos"

schedule:
  interval: "30m"          # Suporta formatos como: 15m, 30m, 1h, 45s
  workingHours:
    enabled: false         # Restringe publicações ao horário comercial
    start: "08:00"
    end: "22:00"

platforms:
  tiktok:
    enabled: true
  facebook:
    enabled: true
  kwai:
    enabled: true

maxAttempts: 3
captionFormat: "{caption}\n\n{hashtags}"
activeProfile: "default"
```

---

## 👤 Gerenciamento de Múltiplos Perfis

O VideoPost suporta isolamento completo entre perfis (ex: canais de nicho diferentes, contas corporativas vs pessoais).

### Criar um perfil
```bash
./videopost profile create frutinhas
./videopost profile create canal_tech
```

### Listar perfis cadastrados
```bash
./videopost profile list
```
Saída:
```text
Perfis disponíveis:
 * frutinhas (ativo)
   canal_tech
   default
```

### Alternar perfil ativo
```bash
./videopost profile use frutinhas
```
Cada perfil possui suas próprias credenciais isoladas e criptografadas em `.videopost/credentials/<perfil>/`.

---

## 🔐 Estratégia de Segurança das Credenciais

**Nenhum token ou segredo é salvo em texto plano ou comitado no Git.**

### Como funciona:
1. **Algoritmo:** **AES-256-GCM** (Galois/Counter Mode) com tag de autenticação de 128 bits e vetor de inicialização (IV) aleatório de 96 bits por gravação.
2. **Derivação de Chave (KDF):** A chave de 256 bits é derivada via **PBKDF2WithHmacSHA256** com 100.000 iterações e Salt criptográfico de 16 bytes.
3. **Chave Mestre:** 
   - Por padrão, uma chave aleatória única da máquina é gerada e gravada em `~/.videopost_key` com permissões estritas POSIX `0600` (`rw-------`).
   - Opcionalmente, pode ser sobrescrita pela variável de ambiente `VIDEOPOST_SECRET_KEY`.
4. **Isolamento de Arquivos:** Os arquivos em `.videopost/credentials/<perfil>/<plataforma>.enc` são gravados com permissão `0600` (somente o usuário atual pode ler/gravar).
5. **Logs Seguros:** Emissores de log suprimem qualquer cabeçalho `Authorization` ou token. Somente identificadores de negócio (`video_id`, `filename`, `external_id`) são registrados.

---

## 🔑 Autenticação das Plataformas

Para autenticar uma plataforma no perfil ativo:

```bash
./videopost auth <plataforma>
```

Você pode passar parâmetros diretamente na linha de comando ou responder interativamente no terminal.

### 1. YouTube Shorts (Google Cloud Console)
O VideoPost utiliza a **YouTube Data API v3** oficial com fluxo OAuth 2.0 e renovação automática de sessão sem expiração.

#### Onde pegar as credenciais:
1. Acesse o **Google Cloud Console**: [console.cloud.google.com](https://console.cloud.google.com).
2. Crie ou selecione seu projeto (ex: `NoticiaBrasil-Auto`).
3. Vá em **APIs e Serviços > Biblioteca**, pesquise por **YouTube Data API v3** e clique em **Ativar**.
4. Em **Tela de Consentimento OAuth**:
   - Tipo de usuário: **Externo**;
   - Preencha o nome do aplicativo e e-mail de suporte;
   - Em **Usuários de teste**, adicione o e-mail da conta Google dona do canal no YouTube.
5. Em **Credenciais > Criar Credenciais > ID do cliente OAuth**:
   - Tipo de aplicativo: **Aplicativo para Computador (Desktop App)**;
   - Nome: `VideoPost CLI`.
6. Copie o **Client ID** (formato: `...apps.googleusercontent.com`) e o **Client Secret**.

#### Onde colocar no projeto:
Execute na raiz do projeto:
```bash
./videopost auth youtube_shorts \
  --client-id "SEU_CLIENT_ID.apps.googleusercontent.com" \
  --client-secret "SEU_CLIENT_SECRET"
```
*O VideoPost abrirá automaticamente uma janela do navegador no servidor local temporário (`http://localhost:8585/callback`). Assim que você autorizar, as credenciais e o token permanente de refresh serão criptografados em `.videopost/credentials/<perfil>/youtube_shorts.enc`.*

---

### 2. TikTok
Você possui duas vias de publicação:

#### Opção A — Microsserviço Web Autônomo (Recomendado para Feed Público)
Não necessita de auditoria corporativa ou credenciais de desenvolvedor. Publica diretamente no TikTok Studio Web via perfil persistente:
```bash
./tiktok login
```
*Uma janela do Chrome se abrirá. Conecte sua conta do TikTok (via QR Code ou Google). A sessão é salva de forma isolada em `tiktok-service/browser_profile/` e dispensa qualquer interação manual.*

#### Opção B — TikTok Developer Portal (API Sandbox)
1. Acesse [developers.tiktok.com](https://developers.tiktok.com) e crie um app em *Manage apps*.
2. Em *Basic settings*, copie a **Client Key** e **Client Secret**.
3. Cadastre a Redirect URI: `https://httpbin.org/get`.
4. Adicione os escopos `video.upload` e `user.info.basic`.
5. Execute no terminal:
```bash
./videopost auth tiktok \
  --client-key "SUA_CLIENT_KEY" \
  --client-secret "SUA_CLIENT_SECRET"
```

---

### 3. Facebook (Meta Pages)
```bash
./videopost auth facebook --page-id 123456789012345 --page-token SEU_PAGE_ACCESS_TOKEN
```
* **Mecanismo Oficial:** Meta Graph API (v20.0) Video Publishing.
* **Permissões Exigidas:** `pages_manage_posts`, `pages_read_engagement`.
* **Como obter:** Crie um aplicativo no [Meta for Developers](https://developers.facebook.com/), adicione a funcionalidade de Pages e gere um **Page Access Token** permanente via Graph API Explorer ou fluxo OAuth de administrador da página.

---

### 4. Kwai (Kuaishou Open Platform)
```bash
./videopost auth kwai --app-id SEU_APP_ID --token SEU_ACCESS_TOKEN
```
* **Mecanismo Oficial:** Kwai Open Platform (`https://open.kuaishou.com/openapi/photo/start_upload`).
* **Escopo Exigido:** `user_video_publish`.
* **Requisito Oficial:** Conta comercial aprovada de parceiro empresarial (detalhes na seção de limitações).

---

## 📹 Metadados dos Vídeos (.mp4 + .json)

Deposite seus vídeos na pasta `./videos`:

```text
videos/
├── moranguinha-01.mp4
├── moranguinha-01.json
├── moranguinha-02.mp4
├── moranguinha-02.json
└── moranguinha-03.mp4
```

### Formato do arquivo `.json`:
```json
{
  "caption": "Você sabia disso sobre o Pix? 🍓",
  "hashtags": [
    "#pix",
    "#brasil",
    "#politica"
  ]
}
```

O CLI associa automaticamente o `.json` ao arquivo de vídeo homônimo. Caso um vídeo não possua arquivo `.json` acompanhante (como `moranguinha-03.mp4`), o sistema o processa normalmente com legenda vazia ou configurada via CLI.

---

## 📋 Guia de Comandos da CLI

| Comando | Descrição |
| :--- | :--- |
| `videopost init` | Inicializa a estrutura `.videopost/`, banco SQLite e diretório de vídeos |
| `videopost scan` | Detecta vídeos e `.json` na pasta e insere os novos na fila |
| `videopost queue` | Exibe a fila com horário agendado e status de cada vídeo |
| `videopost status` | Exibe resumo do perfil ativo, status de autenticação e métricas |
| `videopost start` | Inicia o scheduler em loop contínuo de publicação |
| `videopost start --once` | Executa apenas um ciclo de verificação/publicação e encerra |
| `videopost stop` | Envia sinal gracioso de parada ao processo do scheduler |
| `videopost publish <arquivo>` | Publica imediatamente um vídeo específico |
| `videopost publish <arquivo> --dry-run` | Simula a publicação exibindo metadados e plataformas sem enviar nada |
| `videopost retry` | Lista falhas e pergunta interativamente se deseja reprocessar |
| `videopost retry --all` | Reprocessa todas as publicações com falha automaticamente |
| `videopost logs [-n 50]` | Exibe as últimas linhas do arquivo de log |
| `videopost profile create <nome>` | Cria um novo perfil |
| `videopost profile list` | Lista todos os perfis |
| `videopost profile use <nome>` | Alterna o perfil ativo |
| `videopost auth <plataforma>` | Configura credenciais oficiais da plataforma |

---

## ⏱ Ciclo de Publicação e Scheduler

### Iniciar o Scheduler
```bash
./videopost start
```

Saída no terminal:
```text
🎬 VideoPost

Profile: frutinhas
Interval: 30m

✓ 3 videos found
✓ TikTok authenticated
✓ Facebook authenticated
✓ Kwai authenticated

[08:00] Publishing moranguinha-01.mp4
✓ TikTok published
✓ Facebook published
✓ Kwai published

Next video: moranguinha-02.mp4
Next publication: 08:30
```

Para parar o scheduler em execução:
- Pressione `Ctrl+C` no terminal (o shutdown hook salva o estado e limpa o PID).
- Ou, em outro terminal, execute `./videopost stop`.

---

## 🛡 Isolamento de Falhas, Idempotência e Retry

### Isolamento de Falhas
Se uma publicação falhar no Kwai por quota ou timeout, mas for bem-sucedida no TikTok e Facebook:
1. TikTok torna-se `PUBLISHED` e registra o `external_id`.
2. Facebook torna-se `PUBLISHED` e registra o `external_id`.
3. Kwai torna-se `FAILED` com mensagem de erro e contador de tentativas incrementado.
4. O status global do vídeo no banco passa para:
   ```text
   PARTIALLY_COMPLETED
   ```

### Idempotência Estrita
- O banco SQLite possui constraint única em `(video_id, platform)`.
- O coordenador de publicação consulta o banco antes de qualquer chamada HTTP. Se uma plataforma já estiver como `PUBLISHED`, a chamada externa é **pulada imediatamente**, garantindo que nenhum vídeo seja postado em duplicidade na sua rede social.

### Executando o Retry
Execute:
```bash
./videopost retry
```
Saída:
```text
Failed publications:

1. moranguinha-01.mp4
   Platform: KWAI
   Attempts: 1/3
   Error: Kwai API error (HTTP 429): Quota exceeded

Retry? [y/N]: y

Executando retry...

Resultado do Retry:
 ✓ 1 publicações recuperadas com sucesso.
```
Após o sucesso do retry no Kwai, o status do vídeo transita automaticamente para `COMPLETED`.

---

## 🌐 Status das APIs Oficiais e Limitações Conhecidas

O VideoPost **rejeita categoricamente scraping, roubo de cookies de navegadores e contorno de CAPTCHAs**. Apenas APIs oficiais são integradas:

### 1. TikTok (TikTok Content Posting API v2)
* **Status:** Totalmente implementado via endpoint oficial Direct Post (`POST https://open.tiktokapis.com/v2/post/publish/video/init/`).
* **Limitações:**
  - Contas individuais não auditadas só podem publicar em modo privado (`SELF_ONLY`).
  - Para publicações públicas diretas (`PUBLIC_TO_EVERYONE`), o app deve ser aprovado no processo de App Review da TikTok.
  - Vídeos devem estar no formato MP4/MOV e respeitar proporções verticais (9:16 recomendado).

### 2. Facebook (Meta Graph API v20.0)
* **Status:** Totalmente implementado via endpoint oficial de vídeos em páginas (`POST https://graph-video.facebook.com/v20.0/{page-id}/videos`).
* **Limitações:**
  - Requer que o perfil que gerencia o App tenha papel de Administrador na Página do Facebook.
  - Requer as permissões `pages_manage_posts` e `pages_read_engagement` no Meta App Review para publicação em produção fora do modo de desenvolvimento.

### 3. Kwai (Kuaishou Open Platform)
* **Status:** Adapter implementado conforme a especificação oficial de upload da Kuaishou Open Platform (`POST https://open.kuaishou.com/openapi/photo/start_upload`).
* **Limitações da Plataforma:**
  - A Kwai/Kuaishou não disponibiliza um portal self-service aberto para pessoas físicas criarem tokens de publicação direta no Brasil/América Latina.
  - O acesso à API de publicação (`user_video_publish`) exige contrato comercial de parceria empresarial e aprovação manual da equipe de parcerias da Kwai.
  - Caso você não possua credenciais de parceiro empresarial, o VideoPost relata claramente a falta de autorização oficial sem tentar contornar ou violar os termos de serviço.

---

## 🧩 Como Adicionar uma Nova Plataforma

A arquitetura orientada a interfaces torna a adição de novas plataformas trivial:

### Passo 1: Adicionar o Enum
No enum [Platform.java](file:///Users/ludmilamoreira/videopost/src/main/java/com/videopost/domain/model/Platform.java):
```java
public enum Platform {
    TIKTOK("TikTok"),
    FACEBOOK("Facebook"),
    KWAI("Kwai"),
    YOUTUBE_SHORTS("YouTube Shorts"); // Adicionado
}
```

### Passo 2: Implementar a interface `VideoPublisher`
Crie um adapter em `com.videopost.publisher.youtube.YouTubeShortsPublisher`:
```java
public class YouTubeShortsPublisher implements VideoPublisher {

    @Override
    public Platform platform() {
        return Platform.YOUTUBE_SHORTS;
    }

    @Override
    public boolean isAuthenticated() {
        // Valida credenciais OAuth 2.0 do Google Cloud
        return config != null && config.isValid();
    }

    @Override
    public PublishResult publish(Video video) {
        // Invoca a YouTube Data API v3 (videos.insert)
        return PublishResult.success(uploadedVideoId);
    }
}
```

### Passo 3: Registrar no `AppContext`
Em [AppContext.java](file:///Users/ludmilamoreira/videopost/src/main/java/com/videopost/application/AppContext.java):
```java
publisherRegistry.register(new YouTubeShortsPublisher(ytConfig, httpClient, objectMapper));
```

O `PublishingCoordinator`, o `ScanService`, o `RetryService`, o SQLite e o banco de dados já suportarão a nova plataforma imediatamente, sem alterações em seus códigos-fonte!

---

## 🩺 Troubleshooting e Diagnóstico

### 1. `Erro: SQLite database is locked`
O VideoPost utiliza SQLite no modo `WAL` (`Write-Ahead Logging`). Se houver múltiplos processos concorrentes tentando gravar no banco, finalize instâncias antigas com `./videopost stop`.

### 2. `Invalid OAuth access token`
O token de acesso da rede social expirou. Gere um novo token no portal de desenvolvedores da plataforma e atualize via:
```bash
./videopost auth tiktok --token NOVO_TOKEN
```

### 3. Verificar Histórico de Execuções e Erros
Consulte o arquivo de log:
```bash
./videopost logs -n 100
```
Ou inspecione o banco SQLite local com seu cliente SQL preferido:
```bash
sqlite3 .videopost/videopost.db "SELECT * FROM publications WHERE status = 'FAILED';"
```

---

## 👩‍💻 Qualidade do Código

- **Linguagem:** Java 21 LTS
- **Build Tool:** Maven 3.9+ com Maven Wrapper (`./mvnw`)
- **CLI Framework:** Picocli 4.7.6
- **Testes:** 32 testes cobrindo Domínio, Coordenador, Idempotência, Mocks de HTTP e SQLite com JUnit 5, AssertJ e Mockito.
- **Licença:** MIT
