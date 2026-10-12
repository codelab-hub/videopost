#!/usr/bin/env bash
set -e

echo "============================================================"
echo "🚀 INICIANDO VIDEOPOST CLOUD ENGINE (24/7 AUTONOMOUS)"
echo "============================================================"

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." >/dev/null 2>&1 && pwd)"
cd "$DIR"

# 1. Hidratar credenciais a partir das variáveis de ambiente
echo "🔑 Restaurando credenciais e configurações de ambiente..."
python3 cloud/bundle.py hydrate || true

# 2. Criar diretórios necessários
VIDEOS_DIR="${VIDEOS_DIR:-/app/videos}"
mkdir -p "$VIDEOS_DIR"
mkdir -p .videopost/logs
mkdir -p tiktok-service/logs

# Garantir config.yml na raiz de .videopost com YouTube Shorts ativo
if [ ! -f .videopost/config.yml ] || ! grep -q "youtube_shorts" .videopost/config.yml; then
    echo "⚙️ Gerando .videopost/config.yml otimizado para nuvem..."
    cat << 'EOF' > .videopost/config.yml
videos:
  directory: "/app/videos"
schedule:
  interval: "3h"
  workingHours:
    enabled: false
platforms:
  tiktok:
    enabled: false
  kwai:
    enabled: false
  facebook:
    enabled: false
  youtube_shorts:
    enabled: true
maxAttempts: 3
captionFormat: "{caption}\n\n{hashtags}"
activeProfile: "noticiabrasil"
EOF
fi

# Limpar vídeos dummy de exemplo
rm -f "$VIDEOS_DIR"/moranguinha* 2>/dev/null || true

# Limpar publicações espúrias de plataformas não autenticadas e re-enfileirar vídeos que falharam
echo "🧹 Restaurando fila e expurgando falhas espúrias..."
for DB_FILE in .videopost/profiles/${VIDEOPOST_PROFILE:-noticiabrasil}/videopost.db .videopost/videopost.db; do
    if [ -f "$DB_FILE" ]; then
        sqlite3 "$DB_FILE" "
            DELETE FROM publications WHERE video_id IN (SELECT id FROM videos WHERE filename LIKE 'moranguinha%');
            DELETE FROM videos WHERE filename LIKE 'moranguinha%';
            DELETE FROM publications WHERE platform IN ('TIKTOK', 'FACEBOOK', 'KWAI', 'INSTAGRAM');
            UPDATE videos SET status = 'SCHEDULED', scheduled_at = datetime('now', '-1 minute') WHERE status = 'FAILED';
        " 2>/dev/null || true
    fi
done

# Otimizar títulos e hashtags com estratégia de alto engajamento
python3 update_growth_metadata.py || true

# 3. Sincronização com o Google Drive em background
if [ -n "$GDRIVE_FOLDER_ID" ] || [ -n "$GDRIVE_FOLDER_URL" ]; then
    INTERVAL="${DRIVE_SYNC_INTERVAL_MINUTES:-15}"
    echo "⏱️ Iniciando Drive Sync em background (a cada ${INTERVAL}m)..."
    python3 cloud/drive_sync.py --watch --interval "$INTERVAL" &
    DRIVE_PID=$!
else
    echo "ℹ️ GDRIVE_FOLDER_ID não configurado. Vídeos locais de $VIDEOS_DIR serão processados."
fi

# 4. Iniciar daemon Java do VideoPost (YouTube Shorts) em background
JAR_FILE="$DIR/target/videopost-cli-1.0.0-SNAPSHOT.jar"
if [ -f "$JAR_FILE" ]; then
    echo "🎥 Iniciando daemon do VideoPost para YouTube Shorts em background..."
    PROFILE="${VIDEOPOST_PROFILE:-noticiabrasil}"
    java -jar "$JAR_FILE" start &
    JAVA_PID=$!
    echo "  ✓ Daemon VideoPost acionado (PID: $JAVA_PID)."
else
    echo "⚠️ JAR do VideoPost não encontrado em $JAR_FILE. O daemon não foi iniciado."
fi

# 5. Iniciar TikTok Service se configurado
if [ "${TIKTOK_ENABLED:-false}" = "true" ] || [ -d "tiktok-service/browser_profile" ]; then
    echo "📱 Iniciando serviço do TikTok..."
    python3 tiktok-service/main.py &
    TIKTOK_PID=$!
fi

# Trap para desligamento limpo
cleanup() {
    echo "🛑 Encerrando VideoPost Cloud Engine..."
    if [ -n "$DRIVE_PID" ]; then kill "$DRIVE_PID" 2>/dev/null || true; fi
    if [ -n "$TIKTOK_PID" ]; then kill "$TIKTOK_PID" 2>/dev/null || true; fi
    if [ -n "$JAVA_PID" ]; then kill "$JAVA_PID" 2>/dev/null || true; fi
    exit 0
}
trap cleanup SIGTERM SIGINT

PORT="${PORT:-8000}"
echo "🌐 Iniciando Web Dashboard & API REST na porta $PORT..."
echo "============================================================"

# 6. Iniciar Servidor FastAPI na porta configurada (mantém contêiner vivo)
exec uvicorn cloud.status_api:app --host 0.0.0.0 --port "$PORT"
