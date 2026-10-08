#!/usr/bin/env python3
"""
cloud/bundle.py - Exportador e Hidratador de Credenciais e Configurações para Nuvem (Railway / Render / Docker).

Permite transferir de forma 100% segura as sessões ativas do YouTube e TikTok da máquina local
para o servidor na nuvem sem precisar refazer login ou abrir navegador remoto.
"""

import os
import sys
import base64
import json
import tarfile
import io
import shutil
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent.parent
DOT_VIDEOPOST = BASE_DIR / ".videopost"
TIKTOK_DIR = BASE_DIR / "tiktok-service"

def get_secret_key() -> str:
    env_key = os.getenv("VIDEOPOST_SECRET_KEY")
    if env_key:
        return env_key.strip()
    key_file = Path.home() / ".videopost_key"
    if key_file.exists():
        return key_file.read_text(encoding="utf-8").strip()
    return ""

def export_cloud():
    print("=" * 60)
    print("📦 EXPORTANDO CONFIGURAÇÕES E CREDENCIAIS PARA NUVEM")
    print("=" * 60)

    secret_key = get_secret_key()
    if not secret_key:
        print("⚠️ Chave mestre não encontrada. Gerando nova...")
        import secrets
        secret_key = secrets.token_hex(32)

    active_profile = "noticiabrasil"
    active_profile_file = DOT_VIDEOPOST / "active_profile"
    if active_profile_file.exists():
        p = active_profile_file.read_text(encoding="utf-8").strip()
        if p:
            active_profile = p

    # 1. Config.yml
    config_file = DOT_VIDEOPOST / "config.yml"
    config_b64 = ""
    if config_file.exists():
        content = config_file.read_text(encoding="utf-8")
        # In cloud, we point videos.directory to /app/videos
        config_b64 = base64.b64encode(content.encode("utf-8")).decode("utf-8")

    # 2. YouTube Encrypted Credentials
    yt_enc_file = DOT_VIDEOPOST / "credentials" / active_profile / "youtube_shorts.enc"
    yt_b64 = ""
    if yt_enc_file.exists():
        yt_b64 = base64.b64encode(yt_enc_file.read_bytes()).decode("utf-8")

    # 3. TikTok Session / Browser Profile (exclui caches pesados do Chromium)
    tt_profile_dir = TIKTOK_DIR / "browser_profile"
    tt_b64 = ""
    EXCLUDE_DIRS = {
        "Cache", "Code Cache", "GPUCache", "DawnWebGPUCache", 
        "DawnGraphiteCache", "optimization_guide_model_store", 
        "WasmTtsEngine", "Safe Browsing", "IndexedDB", "component_crx_cache"
    }
    if tt_profile_dir.exists() and any(tt_profile_dir.iterdir()):
        buf = io.BytesIO()
        with tarfile.open(fileobj=buf, mode="w:gz") as tar:
            for item in tt_profile_dir.rglob("*"):
                if item.is_file():
                    parts = set(item.parts)
                    if parts.isdisjoint(EXCLUDE_DIRS):
                        rel_path = item.relative_to(tt_profile_dir)
                        tar.add(item, arcname=str(rel_path))
        buf.seek(0)
        archive_bytes = buf.read()
        # Salva o arquivo comprimido para caso precise enviar diretamente
        session_tar_path = BASE_DIR / "cloud" / "tiktok_session.tar.gz"
        session_tar_path.write_bytes(archive_bytes)
        print(f"  ✓ Sessão do TikTok comprimida salva em {session_tar_path} ({len(archive_bytes) // 1024} KB)")
        if len(archive_bytes) < 64 * 1024:  # apenas inclui em base64 se menor que 64KB
            tt_b64 = base64.b64encode(archive_bytes).decode("utf-8")
        else:
            print("  ℹ️ Sessão do TikTok preservada em arquivo local (maior que 64KB).")

    env_lines = [
        f"VIDEOPOST_SECRET_KEY={secret_key}",
        f"VIDEOPOST_PROFILE={active_profile}",
        f"VIDEOS_DIR=/app/videos",
        f"GDRIVE_FOLDER_ID=",  # User fills this
        f"DRIVE_SYNC_INTERVAL_MINUTES=15",
    ]
    if config_b64:
        env_lines.append(f"VIDEOPOST_CONFIG_B64={config_b64}")
    if yt_b64:
        env_lines.append(f"YOUTUBE_ENC_B64={yt_b64}")
    if tt_b64:
        env_lines.append(f"TIKTOK_PROFILE_B64={tt_b64}")

    env_output_path = BASE_DIR / ".env.cloud"
    env_output_path.write_text("\n".join(env_lines) + "\n", encoding="utf-8")

    print(f"✅ Arquivo gerado com sucesso: {env_output_path}")
    print("\n📋 Copie e cole essas variáveis no painel da Railway / Render / VPS:\n")
    print("-" * 60)
    for line in env_lines:
        if "B64=" in line and len(line) > 80:
            print(line[:60] + f"... [tam: {len(line)} chars]")
        else:
            print(line)
    print("-" * 60)
    print("\n💡 Instruções completas: consulte LOVABLE_GUIDE.md")

def hydrate_cloud():
    """Roda na inicialização do contêiner Docker para restaurar credenciais."""
    print("=" * 60)
    print("🚀 HIDRATANDO CREDENCIAIS NO AMBIENTE CLOUD...")
    print("=" * 60)

    # 1. Secret Key
    secret_key = os.getenv("VIDEOPOST_SECRET_KEY", "").strip()
    if secret_key:
        key_file = Path.home() / ".videopost_key"
        key_file.write_text(secret_key, encoding="utf-8")
        try:
            os.chmod(key_file, 0o600)
        except Exception:
            pass
        print("  ✓ VIDEOPOST_SECRET_KEY restaurada em ~/.videopost_key")

    profile = os.getenv("VIDEOPOST_PROFILE", "noticiabrasil").strip()
    DOT_VIDEOPOST.mkdir(parents=True, exist_ok=True)
    (DOT_VIDEOPOST / "active_profile").write_text(profile, encoding="utf-8")

    profile_cred_dir = DOT_VIDEOPOST / "credentials" / profile
    profile_cred_dir.mkdir(parents=True, exist_ok=True)

    # 2. Config YAML
    config_b64 = os.getenv("VIDEOPOST_CONFIG_B64", "").strip()
    config_path = DOT_VIDEOPOST / "config.yml"
    videos_dir = os.getenv("VIDEOS_DIR", "/app/videos")
    Path(videos_dir).mkdir(parents=True, exist_ok=True)

    if config_b64:
        try:
            raw_cfg = base64.b64decode(config_b64).decode("utf-8")
            # Replace local path with cloud path
            import re
            raw_cfg = re.sub(r'directory:\s*["\'].*?["\']', f'directory: "{videos_dir}"', raw_cfg)
            config_path.write_text(raw_cfg, encoding="utf-8")
            print("  ✓ config.yml restaurado e adaptado para diretório cloud")
        except Exception as e:
            print(f"  ⚠️ Falha ao decodificar VIDEOPOST_CONFIG_B64: {e}")
    elif not config_path.exists():
        # Default fallback config
        default_cfg = f"""videos:
  directory: "{videos_dir}"
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
captionFormat: "{{caption}}\\n\\n{{hashtags}}"
activeProfile: "{profile}"
"""
        config_path.write_text(default_cfg, encoding="utf-8")
        print("  ✓ config.yml padrão criado com sucesso")

    # 3. YouTube Encrypted Token
    yt_b64 = os.getenv("YOUTUBE_ENC_B64", "").strip()
    if yt_b64:
        try:
            raw_yt = base64.b64decode(yt_b64)
            (profile_cred_dir / "youtube_shorts.enc").write_bytes(raw_yt)
            print(f"  ✓ Credenciais do YouTube Shorts restauradas em {profile_cred_dir}/youtube_shorts.enc")
        except Exception as e:
            print(f"  ⚠️ Falha ao decodificar YOUTUBE_ENC_B64: {e}")

    # 4. TikTok Browser Profile
    tt_b64 = os.getenv("TIKTOK_PROFILE_B64", "").strip()
    if tt_b64:
        try:
            target_profile_dir = TIKTOK_DIR / "browser_profile"
            target_profile_dir.mkdir(parents=True, exist_ok=True)
            raw_tar = base64.b64decode(tt_b64)
            with tarfile.open(fileobj=io.BytesIO(raw_tar), mode="r:gz") as tar:
                tar.extractall(path=target_profile_dir)
            print(f"  ✓ Sessão TikTok restaurada em {target_profile_dir}")
        except Exception as e:
            print(f"  ⚠️ Falha ao decodificar TIKTOK_PROFILE_B64: {e}")

    print("✅ Hidratação concluída com sucesso.")

if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "hydrate":
        hydrate_cloud()
    else:
        export_cloud()
