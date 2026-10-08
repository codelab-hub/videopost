#!/usr/bin/env python3
"""
cloud/drive_sync.py - Sincronizador Automático de Vídeos do Google Drive para o VideoPost.

Funcionalidades:
1. Baixa novos vídeos (.mp4, .mov, etc.) e metadados (.json) de uma pasta compartilhada do Google Drive.
2. Suporta link público/aberto via gdown (sem precisar de API Keys do Google Cloud).
3. Suporta Service Account via Google Drive API v3 (para pastas corporativas privadas).
4. Auto-gera arquivos .json com hashtags virais caso o vídeo seja enviado sem metadados.
5. Evita re-downloads registrando o histórico em .drive_sync_state.json.
6. Notifica e atualiza automaticamente as filas do YouTube e do TikTok.
"""

import os
import sys
import re
import json
import time
import shutil
import logging
from pathlib import Path
from typing import List, Dict, Optional

# Logging setup
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] [drive_sync] %(message)s"
)
logger = logging.getLogger("drive_sync")

BASE_DIR = Path(__file__).resolve().parent.parent
VIDEOS_DEFAULT_DIR = BASE_DIR / "videos"
STATE_FILE = BASE_DIR / ".drive_sync_state.json"

DEFAULT_HASHTAGS = [
    "#flaviobolsonaro",
    "#bolsonaro",
    "#direitabrasil",
    "#politica",
    "#noticiabrasil",
    "#congresso",
    "#conservador",
    "#brasil",
    "#opiniao",
    "#fatos",
    "#urgente"
]

VIDEO_EXTENSIONS = {".mp4", ".mov", ".mkv", ".m4v", ".avi", ".webm"}

class GoogleDriveSync:
    def __init__(
        self,
        folder_id_or_url: Optional[str] = None,
        target_dir: Optional[Path] = None,
        service_account_json: Optional[str] = None,
        default_hashtags: Optional[List[str]] = None
    ):
        self.folder_id_or_url = folder_id_or_url or os.getenv("GDRIVE_FOLDER_ID") or os.getenv("GDRIVE_FOLDER_URL") or ""
        
        # Resolve target directory
        env_videos_dir = os.getenv("VIDEOS_DIR")
        if target_dir:
            self.target_dir = Path(target_dir)
        elif env_videos_dir:
            self.target_dir = Path(env_videos_dir)
        else:
            self.target_dir = VIDEOS_DEFAULT_DIR

        self.target_dir.mkdir(parents=True, exist_ok=True)
        self.service_account_json = service_account_json or os.getenv("GDRIVE_SERVICE_ACCOUNT_JSON")
        self.default_hashtags = default_hashtags or DEFAULT_HASHTAGS
        self.state = self._load_state()

    def _load_state(self) -> dict:
        if STATE_FILE.exists():
            try:
                return json.loads(STATE_FILE.read_text(encoding="utf-8"))
            except Exception as e:
                logger.warning(f"Erro ao ler state file: {e}. Criando novo.")
        return {
            "last_sync": None,
            "synced_files": [],
            "last_status": "initialized",
            "total_synced": 0
        }

    def _save_state(self):
        try:
            STATE_FILE.write_text(json.dumps(self.state, indent=2, ensure_ascii=False), encoding="utf-8")
        except Exception as e:
            logger.error(f"Erro ao salvar state file: {e}")

    def extract_folder_id(self, url_or_id: str) -> str:
        """Extrai o ID da pasta a partir de uma URL completa ou retorna o ID se já for um ID puro."""
        if not url_or_id:
            return ""
        url_or_id = url_or_id.strip()
        # Se for URL do Google Drive
        match = re.search(r"folders/([a-zA-Z0-9_-]+)", url_or_id)
        if match:
            return match.group(1)
        match_id = re.search(r"id=([a-zA-Z0-9_-]+)", url_or_id)
        if match_id:
            return match_id.group(1)
        # Se for apenas o hash/ID
        if "/" not in url_or_id and "?" not in url_or_id and len(url_or_id) > 10:
            return url_or_id
        return url_or_id

    def sync_via_gdown(self, folder_id: str) -> List[str]:
        """Sincroniza pasta pública ou compartilhada usando gdown."""
        import gdown

        logger.info(f"Iniciando download do Google Drive (Folder ID: {folder_id}) via gdown...")
        download_url = f"https://drive.google.com/drive/folders/{folder_id}"

        # Diretório temporário para download
        temp_dir = BASE_DIR / "temp_gdrive_sync"
        temp_dir.mkdir(parents=True, exist_ok=True)

        newly_synced = []

        try:
            downloaded = gdown.download_folder(
                url=download_url,
                output=str(temp_dir),
                quiet=False,
                use_cookies=False,
                remaining_ok=True
            )

            # Mover arquivos baixados para o diretório final
            for item in temp_dir.rglob("*"):
                if item.is_file():
                    dest_file = self.target_dir / item.name
                    if not dest_file.exists() or dest_file.stat().st_size != item.stat().st_size:
                        shutil.move(str(item), str(dest_file))
                        logger.info(f"📥 Novo arquivo copiado para videos/: {dest_file.name}")
                        newly_synced.append(dest_file.name)
                    else:
                        # Arquivo já existe identicamente
                        item.unlink()

            # Limpar diretório temporário
            shutil.rmtree(temp_dir, ignore_errors=True)

        except Exception as e:
            logger.error(f"Erro durante download via gdown: {e}")
            self.state["last_status"] = f"error: {str(e)}"
            self._save_state()
            raise e

        return newly_synced

    def sync_via_api(self, folder_id: str, sa_json: str) -> List[str]:
        """Sincroniza pasta usando Google Drive API v3 e Service Account."""
        from google.oauth2 import service_account
        from googleapiclient.discovery import build
        from googleapiclient.http import MediaIoBaseDownload
        import io

        logger.info(f"Autenticando via Service Account para pasta {folder_id}...")
        sa_info = json.loads(sa_json) if sa_json.startswith("{") else json.loads(Path(sa_json).read_text())
        creds = service_account.Credentials.from_service_account_info(
            sa_info,
            scopes=["https://www.googleapis.com/auth/drive.readonly"]
        )
        service = build("drive", "v3", credentials=creds)

        query = f"'{folder_id}' in parents and trashed = false"
        results = service.files().list(q=query, fields="files(id, name, mimeType, size)").execute()
        files = results.get("files", [])

        newly_synced = []
        for f in files:
            file_id = f["id"]
            file_name = f["name"]
            dest_file = self.target_dir / file_name

            # Pular se já tiver sido sincronizado
            if dest_file.exists() and str(dest_file.stat().st_size) == f.get("size", "0"):
                continue

            logger.info(f"📥 Baixando {file_name} via Drive API...")
            request = service.files().get_media(fileId=file_id)
            with io.FileIO(str(dest_file), "wb") as fh:
                downloader = MediaIoBaseDownload(fh, request)
                done = False
                while not done:
                    status, done = downloader.next_chunk()

            newly_synced.append(file_name)
            logger.info(f"✅ Concluído: {file_name}")

        return newly_synced

    def generate_missing_metadata(self):
        """Para qualquer vídeo na pasta que não tenha arquivo .json irmão, cria automaticamente."""
        for file in self.target_dir.iterdir():
            if file.is_file() and file.suffix.lower() in VIDEO_EXTENSIONS:
                json_sibling = file.with_suffix(".json")
                if not json_sibling.exists():
                    clean_title = file.stem.replace("_", " ").replace("-", " ").capitalize()
                    meta = {
                        "caption": f"🚨 {clean_title} | Notícias Urgentes e Fatos da Política",
                        "hashtags": self.default_hashtags
                    }
                    json_sibling.write_text(json.dumps(meta, indent=2, ensure_ascii=False), encoding="utf-8")
                    logger.info(f"✨ Metadados automáticos gerados para {file.name} -> {json_sibling.name}")

    def notify_platforms(self):
        """Atualiza a fila do TikTok e escaneia novos vídeos para o VideoPost."""
        # 1. TikTok Service DB
        tt_db_path = BASE_DIR / "tiktok-service" / "tiktok_service.db"
        if tt_db_path.exists():
            try:
                sys.path.insert(0, str(BASE_DIR / "tiktok-service"))
                from db import TikTokDB
                tt_db = TikTokDB(str(tt_db_path))
                added = tt_db.sync_videos(str(self.target_dir), "3h")
                if added > 0:
                    logger.info(f"📱 {added} novos vídeos adicionados à fila do TikTok.")
            except Exception as e:
                logger.warning(f"Não foi possível sincronizar com banco do TikTok: {e}")

        # 2. YouTube / VideoPost Scan (se o jar estiver compilado)
        jar_path = BASE_DIR / "target" / "videopost-cli-1.0.0-SNAPSHOT.jar"
        if jar_path.exists():
            try:
                import subprocess
                subprocess.run(
                    ["java", "-jar", str(jar_path), "scan"],
                    cwd=str(BASE_DIR),
                    capture_output=True,
                    timeout=30
                )
                logger.info("🎥 VideoPost scan disparado com sucesso.")
            except Exception as e:
                logger.debug(f"Scan automático do VideoPost: {e}")

    def sync_once(self) -> Dict:
        """Executa um ciclo completo de sincronização."""
        folder_id = self.extract_folder_id(self.folder_id_or_url)
        if not folder_id:
            msg = "Nenhum GDRIVE_FOLDER_ID ou link configurado."
            logger.warning(msg)
            return {
                "success": False,
                "message": msg,
                "synced_count": 0,
                "files": []
            }

        start_time = time.time()
        new_files = []

        try:
            if self.service_account_json:
                new_files = self.sync_via_api(folder_id, self.service_account_json)
            else:
                new_files = self.sync_via_gdown(folder_id)

            self.generate_missing_metadata()
            self.notify_platforms()

            elapsed = round(time.time() - start_time, 2)
            self.state["last_sync"] = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
            self.state["last_status"] = "success"
            self.state["total_synced"] = self.state.get("total_synced", 0) + len(new_files)
            for f in new_files:
                if f not in self.state["synced_files"]:
                    self.state["synced_files"].append(f)
            self._save_state()

            logger.info(f"🎉 Sincronização concluída em {elapsed}s. {len(new_files)} novos arquivos.")
            return {
                "success": True,
                "message": f"Sincronização concluída com sucesso ({len(new_files)} novos vídeos).",
                "synced_count": len(new_files),
                "files": new_files,
                "elapsed_seconds": elapsed
            }

        except Exception as e:
            logger.error(f"Falha na sincronização do Google Drive: {e}")
            self.state["last_status"] = f"error: {str(e)}"
            self._save_state()
            return {
                "success": False,
                "message": f"Erro ao sincronizar: {str(e)}",
                "synced_count": 0,
                "files": []
            }

    def run_daemon(self, interval_minutes: int = 15):
        """Roda a sincronização em loop contínuo."""
        logger.info(f"Iniciando Drive Sync Daemon (intervalo: {interval_minutes} minutos)...")
        while True:
            try:
                self.sync_once()
            except Exception as e:
                logger.error(f"Erro no loop de sincronização: {e}")
            time.sleep(interval_minutes * 60)

if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="Google Drive Sync para VideoPost")
    parser.add_argument("--once", action="store_true", help="Executa uma única sincronização e encerra")
    parser.add_argument("--watch", action="store_true", help="Executa em loop contínuo")
    parser.add_argument("--interval", type=int, default=15, help="Intervalo em minutos para o modo watch")
    parser.add_argument("--folder", type=str, default="", help="Link ou ID da pasta do Google Drive")

    args = parser.parse_args()

    sync_engine = GoogleDriveSync(folder_id_or_url=args.folder if args.folder else None)

    if args.watch:
        sync_engine.run_daemon(interval_minutes=args.interval)
    else:
        result = sync_engine.sync_once()
        print(json.dumps(result, indent=2, ensure_ascii=False))
