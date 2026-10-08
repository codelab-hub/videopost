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

# 3. Sincronização inicial com o Google Drive
if [ -n "$GDRIVE_FOLDER_ID" ] || [ -n "$GDRIVE_FOLDER_URL" ]; then
    echo "🔄 Disparando sincronização inicial com Google Drive..."
    python3 cloud/drive_sync.py --once || echo "⚠️ Sincronização inicial avisou com retorno não-zero."
    
    # Inicia watcher em segundo plano
    INTERVAL="${DRIVE_SYNC_INTERVAL_MINUTES:-15}"
    echo "⏱️ Iniciando Drive Sync em background (a cada ${INTERVAL}m)..."
    python3 cloud/drive_sync.py --watch --interval "$INTERVAL" &
    DRIVE_PID=$!
else
    echo "ℹ️ GDRIVE_FOLDER_ID não configurado. Vídeos locais de $VIDEOS_DIR serão processados."
fi

# 4. Iniciar daemon Java do VideoPost (YouTube Shorts)
JAR_FILE="$DIR/target/videopost-cli-1.0.0-SNAPSHOT.jar"
if [ -f "$JAR_FILE" ]; then
    echo "🎥 Iniciando daemon do VideoPost para YouTube Shorts..."
    # Se o perfil foi especificado
    PROFILE="${VIDEOPOST_PROFILE:-noticiabrasil}"
    java -jar "$JAR_FILE" start || true
    echo "  ✓ Daemon VideoPost acionado."
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
    if [ -f "$JAR_FILE" ]; then java -jar "$JAR_FILE" stop 2>/dev/null || true; fi
    exit 0
}
trap cleanup SIGTERM SIGINT

PORT="${PORT:-8000}"
echo "🌐 Iniciando Web Dashboard & API REST na porta $PORT..."
echo "============================================================"

# 6. Iniciar Servidor FastAPI na porta configurada (mantém contêiner vivo)
exec uvicorn cloud.status_api:app --host 0.0.0.0 --port "$PORT"
