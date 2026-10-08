import os
import sys
import time
import signal
import json
import logging
from datetime import datetime, timedelta
from typing import Optional
import yaml

from db import TikTokDB
from uploader import TikTokUploader

logger = logging.getLogger("tiktok.scheduler")

class TikTokScheduler:
    def __init__(self, config_path: str):
        self.config_path = config_path
        self.config = self._load_config()
        self.running = False

        # Paths
        base_dir = os.path.dirname(os.path.abspath(config_path))
        self.video_dir = self.config["videos"]["directory"]
        self.interval_str = self.config["schedule"].get("interval", "3h")
        self.interval = TikTokDB.parse_interval(self.interval_str)

        db_path = self.config.get("database", {}).get("path", os.path.join(base_dir, "tiktok_service.db"))
        self.db = TikTokDB(db_path)

        profile_dir = self.config.get("browser", {}).get("profile_dir", os.path.join(base_dir, "browser_profile"))
        headless = self.config.get("browser", {}).get("headless", False)
        timeout_ms = self.config.get("browser", {}).get("timeout_ms", 60000)
        self.uploader = TikTokUploader(profile_dir=profile_dir, headless=headless, timeout_ms=timeout_ms)

        self.pid_file = self.config.get("logging", {}).get("pid_file", os.path.join(base_dir, "tiktok.pid"))
        self.log_file = self.config.get("logging", {}).get("file", os.path.join(base_dir, "logs", "tiktok.log"))
        self._setup_logging()

    def _load_config(self) -> dict:
        with open(self.config_path, "r", encoding="utf-8") as f:
            return yaml.safe_load(f)

    def _setup_logging(self):
        os.makedirs(os.path.dirname(self.log_file), exist_ok=True)
        logging.basicConfig(
            level=logging.INFO,
            format="%(asctime)s [%(levelname)s] [%(name)s] %(message)s",
            handlers=[
                logging.FileHandler(self.log_file, encoding="utf-8"),
                logging.StreamHandler(sys.stdout)
            ]
        )

    def _write_pid(self):
        os.makedirs(os.path.dirname(self.pid_file), exist_ok=True)
        with open(self.pid_file, "w") as f:
            f.write(str(os.getpid()))

    def _remove_pid(self):
        if os.path.exists(self.pid_file):
            try:
                os.remove(self.pid_file)
            except Exception:
                pass

    def _handle_signals(self, signum, frame):
        logger.info(f"Sinal recebido ({signum}). Encerrando TikTok Scheduler...")
        self.running = False

    def run_once_immediate(self, item_id: Optional[int] = None) -> bool:
        """Executa a publicação imediata de um vídeo (ou o próximo pendente)."""
        if item_id:
            with self.db._get_conn() as conn:
                cur = conn.execute("SELECT * FROM queue WHERE id = ?", (item_id,))
                item = cur.fetchone()
                item = dict(item) if item else None
        else:
            item = self.db.get_next_pending()

        if not item:
            logger.info("Nenhum vídeo pendente na fila para publicação.")
            return False

        logger.info(f"Processando vídeo imediato: {item['filename']} (ID: {item['id']})")
        self.db.mark_processing(item["id"])

        try:
            hashtags = json.loads(item["hashtags"]) if item["hashtags"] else []
        except Exception:
            hashtags = []

        try:
            self.uploader.upload_video(
                video_path=item["filepath"],
                caption=item["caption"] or item["filename"],
                hashtags=hashtags
            )
            self.db.mark_published(item["id"])
            logger.info(f"✅ Vídeo {item['filename']} publicado com sucesso!")
            return True
        except Exception as e:
            logger.error(f"❌ Falha ao publicar {item['filename']}: {e}")
            self.db.mark_failed(item["id"], str(e))
            return False

    def start(self):
        signal.signal(signal.SIGINT, self._handle_signals)
        signal.signal(signal.SIGTERM, self._handle_signals)

        self._write_pid()
        self.running = True
        logger.info("==================================================")
        logger.info("🤖 TIKTOK AUTONOMOUS SERVICE INICIADO")
        logger.info(f"Diretório de vídeos: {self.video_dir}")
        logger.info(f"Intervalo programado: {self.interval_str}")
        logger.info("==================================================")

        # Reset any stuck processing items back to pending
        self.db.reset_processing_to_pending()

        # Sync videos folder
        added = self.db.sync_videos(self.video_dir, self.interval_str)
        if added > 0:
            logger.info(f"{added} novos vídeos adicionados à fila do TikTok.")

        while self.running:
            try:
                # Sync folder periodically
                self.db.sync_videos(self.video_dir, self.interval_str)

                next_item = self.db.get_next_pending()
                if not next_item:
                    logger.info("Fila vazia ou todos os vídeos publicados. Aguardando 60s...")
                    time.sleep(60)
                    continue

                scheduled_at = datetime.fromisoformat(next_item["scheduled_at"])
                now = datetime.now()

                if now < scheduled_at:
                    wait_seconds = (scheduled_at - now).total_seconds()
                    logger.info(f"Próximo vídeo: '{next_item['filename']}' agendado para {scheduled_at.strftime('%Y-%m-%d %H:%M:%S')} (faltam {int(wait_seconds)}s).")
                    sleep_chunk = min(30, max(5, int(wait_seconds)))
                    time.sleep(sleep_chunk)
                    continue

                # Horário atingido! Publicar o vídeo
                logger.info(f"⏰ Horário atingido para '{next_item['filename']}'. Iniciando publicação autônoma...")
                self.db.mark_processing(next_item["id"])

                try:
                    hashtags = json.loads(next_item["hashtags"]) if next_item["hashtags"] else []
                except Exception:
                    hashtags = []

                try:
                    self.uploader.upload_video(
                        video_path=next_item["filepath"],
                        caption=next_item["caption"] or next_item["filename"],
                        hashtags=hashtags
                    )
                    self.db.mark_published(next_item["id"])
                    logger.info(f"🎉 SUCESSO! Vídeo '{next_item['filename']}' postado no TikTok.")
                except Exception as e:
                    logger.error(f"❌ Erro ao postar '{next_item['filename']}': {e}")
                    self.db.mark_failed(next_item["id"], str(e))

                # Pequena pausa antes de checar a próxima tarefa
                time.sleep(10)

            except Exception as e:
                logger.error(f"Erro inesperado no loop do scheduler: {e}", exc_info=True)
                time.sleep(15)

        self._remove_pid()
        logger.info("TikTok Scheduler finalizado.")
