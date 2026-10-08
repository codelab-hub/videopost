#!/usr/bin/env bash
# ==============================================================================
# oracle_setup.sh - Script de Instalação Automatizada do VideoPost no Oracle Cloud
# Compatível com Ubuntu 22.04 / 24.04 (ARM Ampere A1 e x86_64)
# ==============================================================================

set -e

echo "============================================================"
echo "🚀 INSTALANDO VIDEOPOST NO ORACLE CLOUD (ALWAYS FREE TIER)"
echo "============================================================"

# 1. Atualizar pacotes do sistema
echo "📦 Atualizando pacotes do sistema..."
sudo apt-get update && sudo apt-get upgrade -y
sudo apt-get install -y curl git ufw iptables-persistent

# 2. Instalar Docker e Docker Compose
if ! command -v docker &> /dev/null; then
    echo "🐳 Instalando Docker oficial..."
    curl -fsSL https://get.docker.com -o get-docker.sh
    sudo sh get-docker.sh
    sudo usermod -aG docker "$USER"
    rm get-docker.sh
    echo "  ✓ Docker instalado com sucesso."
else
    echo "  ✓ Docker já instalado."
fi

# 3. Liberar portas no Firewall do Ubuntu (Oracle Cloud Iptables Fix)
echo "🛡️ Configurando regras de firewall local para a porta 8000..."
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 8000 -j ACCEPT 2>/dev/null || sudo iptables -I INPUT 1 -p tcp --dport 8000 -j ACCEPT
sudo iptables -I INPUT 1 -p tcp --dport 80 -j ACCEPT 2>/dev/null || true
sudo iptables -I INPUT 1 -p tcp --dport 443 -j ACCEPT 2>/dev/null || true
sudo netfilter-persistent save 2>/dev/null || true

# 4. Clonar ou atualizar o repositório VideoPost
REPO_DIR="$HOME/videopost"
if [ ! -d "$REPO_DIR" ]; then
    echo "📥 Clonando repositório codelab-hub/videopost..."
    git clone https://github.com/codelab-hub/videopost.git "$REPO_DIR"
else
    echo "🔄 Atualizando repositório existente..."
    cd "$REPO_DIR" && git pull origin main
fi

cd "$REPO_DIR"

echo "============================================================"
echo "✅ Instalação concluída!"
echo ""
echo "👉 Próximo passo:"
echo "1. Crie o arquivo .env com suas credenciais:"
echo "   nano .env"
echo "2. Suba a aplicação com Docker Compose:"
echo "   docker compose up -d --build"
echo "3. Acesse o painel pelo navegador em: http://<IP-PUBLICO-DA-VM>:8000"
echo "============================================================"
