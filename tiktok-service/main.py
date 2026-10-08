import os
import sys
import subprocess
import signal
import time
import argparse
import yaml
from datetime import datetime

from db import TikTokDB
from uploader import TikTokUploader
from scheduler import TikTokScheduler

CONFIG_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "config.yml")

def load_config():
    with open(CONFIG_PATH, "r", encoding="utf-8") as f:
        return yaml.safe_load(f)

def get_pid(pid_file: str) -> int:
    if os.path.exists(pid_file):
        try:
            with open(pid_file, "r") as f:
                pid = int(f.read().strip())
                # Check if process is actually alive
                os.kill(pid, 0)
                return pid
        except (ValueError, OSError):
            return 0
    return 0

def cmd_login(args):
    config = load_config()
    profile_dir = config.get("browser", {}).get("profile_dir", os.path.join(os.path.dirname(CONFIG_PATH), "browser_profile"))
    uploader = TikTokUploader(profile_dir=profile_dir, headless=False)
    success = uploader.interactive_login(max_wait_seconds=args.timeout)
    if success:
        print("\n✅ Sessão do TikTok configurada com sucesso!")
        if getattr(args, "post", False):
            print("\n🚀 Publicando o primeiro vídeo imediatamente...")
            scheduler = TikTokScheduler(CONFIG_PATH)
            scheduler.run_once_immediate()
        if getattr(args, "start", False):
            print("\n🚀 Iniciando o agendador autônomo em segundo plano...")
            cmd_start(args)
        if not getattr(args, "post", False) and not getattr(args, "start", False):
            print("Você já pode iniciar o agendador autônomo com: ./tiktok start")
    else:
        print("\n❌ Não foi possível completar o login. Tente novamente.")
        sys.exit(1)

def cmd_check_auth(args):
    config = load_config()
    profile_dir = config.get("browser", {}).get("profile_dir", os.path.join(os.path.dirname(CONFIG_PATH), "browser_profile"))
    uploader = TikTokUploader(profile_dir=profile_dir, headless=True)
    is_auth, msg = uploader.check_auth()
    if is_auth:
        print(f"✅ Autenticação OK: {msg}")
    else:
        print(f"❌ Não autenticado: {msg}")
        print("Execute './tiktok login' para autenticar uma única vez.")
        sys.exit(1)

def cmd_sync(args):
    config = load_config()
    base_dir = os.path.dirname(CONFIG_PATH)
    db_path = config.get("database", {}).get("path", os.path.join(base_dir, "tiktok_service.db"))
    db = TikTokDB(db_path)
    video_dir = config["videos"]["directory"]
    interval = config["schedule"].get("interval", "3h")
    added = db.sync_videos(video_dir, interval)
    print(f"Vídeos sincronizados. Novos adicionados: {added}")
    summary = db.get_summary()
    print(f"Total na fila: {summary['total']} ({summary['pending']} pendentes, {summary['published']} publicados)")

def cmd_queue(args):
    config = load_config()
    base_dir = os.path.dirname(CONFIG_PATH)
    db_path = config.get("database", {}).get("path", os.path.join(base_dir, "tiktok_service.db"))
    db = TikTokDB(db_path)
    summary = db.get_summary()

    print("\n" + "=" * 60)
    print("📋 FILA DE PUBLICAÇÃO DO TIKTOK")
    print("=" * 60)
    print(f"Total: {summary['total']} | Pendentes: {summary['pending']} | Publicados: {summary['published']} | Falhas: {summary['failed']}")
    print("-" * 60)

    items = db.list_queue(limit=args.limit)
    if not items:
        print("Fila vazia.")
        return

    for item in items:
        status_icon = "⏳"
        if item["status"] == "PUBLISHED":
            status_icon = "✅"
        elif item["status"] == "FAILED":
            status_icon = "❌"
        elif item["status"] == "PROCESSING":
            status_icon = "🔄"

        sched = item["scheduled_at"] or "-"
        pub = item["published_at"] or "-"
        time_info = f"Agendado: {sched}" if item["status"] != "PUBLISHED" else f"Publicado: {pub}"
        print(f"[{item['id']:2d}] {status_icon} {item['status']:<10} | {item['filename']} | {time_info}")
        if item.get("error_message"):
            print(f"     ⚠️ Erro: {item['error_message']}")
    print("=" * 60 + "\n")

def cmd_post_next(args):
    scheduler = TikTokScheduler(CONFIG_PATH)
    success = scheduler.run_once_immediate()
    if not success:
        sys.exit(1)

def cmd_daemon(args):
    scheduler = TikTokScheduler(CONFIG_PATH)
    scheduler.start()

def cmd_start(args):
    config = load_config()
    base_dir = os.path.dirname(CONFIG_PATH)
    pid_file = config.get("logging", {}).get("pid_file", os.path.join(base_dir, "tiktok.pid"))
    log_file = config.get("logging", {}).get("file", os.path.join(base_dir, "logs", "tiktok.log"))

    pid = get_pid(pid_file)
    if pid > 0:
        print(f"O serviço TikTok já está em execução (PID: {pid}).")
        return

    # Check auth before starting in background
    profile_dir = config.get("browser", {}).get("profile_dir", os.path.join(base_dir, "browser_profile"))
    uploader = TikTokUploader(profile_dir=profile_dir, headless=True)
    is_auth, _ = uploader.check_auth()
    if not is_auth:
        print("⚠️ Atenção: A sessão do TikTok ainda não foi configurada.")
        print("Execute primeiro: ./tiktok login")
        return

    os.makedirs(os.path.dirname(log_file), exist_ok=True)
    python_bin = sys.executable

    with open(log_file, "a") as out:
        proc = subprocess.Popen(
            [python_bin, os.path.abspath(__file__), "daemon"],
            stdout=out,
            stderr=out,
            stdin=subprocess.DEVNULL,
            start_new_session=True,
            cwd=base_dir
        )

    # Give it a second to initialize and write PID
    time.sleep(1.5)
    print(f"🚀 TikTok Daemon iniciado em segundo plano com PID {proc.pid}.")
    print(f"Logs em: {log_file}")

def cmd_stop(args):
    config = load_config()
    base_dir = os.path.dirname(CONFIG_PATH)
    pid_file = config.get("logging", {}).get("pid_file", os.path.join(base_dir, "tiktok.pid"))
    pid = get_pid(pid_file)

    if pid == 0:
        print("O serviço TikTok não está em execução.")
        return

    print(f"Encerrando TikTok Daemon (PID {pid})...")
    try:
        os.kill(pid, signal.SIGTERM)
        for _ in range(10):
            time.sleep(0.5)
            if get_pid(pid_file) == 0:
                break
        if get_pid(pid_file) != 0:
            os.kill(pid, signal.SIGKILL)
        print("✅ TikTok Daemon encerrado com sucesso.")
    except Exception as e:
        print(f"Erro ao encerrar processo {pid}: {e}")

def cmd_status(args):
    config = load_config()
    base_dir = os.path.dirname(CONFIG_PATH)
    pid_file = config.get("logging", {}).get("pid_file", os.path.join(base_dir, "tiktok.pid"))
    db_path = config.get("database", {}).get("path", os.path.join(base_dir, "tiktok_service.db"))
    db = TikTokDB(db_path)
    summary = db.get_summary()

    pid = get_pid(pid_file)
    print("\n" + "=" * 50)
    print("📊 STATUS DO SERVIÇO TIKTOK")
    print("=" * 50)
    if pid > 0:
        print(f"Status do Daemon: 🟢 ATIVO (PID: {pid})")
    else:
        print("Status do Daemon: 🔴 PARADO")

    print(f"Intervalo entre posts: {config['schedule'].get('interval', '3h')}")
    print(f"Diretório de vídeos: {config['videos']['directory']}")
    print("-" * 50)
    print(f"Fila: {summary['pending']} pendentes | {summary['published']} publicados | {summary['failed']} falhas")

    next_item = db.get_next_pending()
    if next_item:
        print(f"Próximo agendado: {next_item['filename']} às {next_item['scheduled_at']}")
    print("=" * 50 + "\n")

def main():
    parser = argparse.ArgumentParser(description="TikTok Autonomous Publisher Service")
    subparsers = parser.add_subparsers(dest="command")

    # login
    p_login = subparsers.add_parser("login", help="Abrir Chrome interativo para fazer login no TikTok")
    p_login.add_argument("--timeout", type=int, default=300, help="Tempo limite em segundos")
    p_login.add_argument("--post", action="store_true", help="Publicar o primeiro vídeo imediatamente após o login")
    p_login.add_argument("--start", action="store_true", help="Iniciar o serviço em segundo plano após o login")

    # check-auth
    subparsers.add_parser("check-auth", help="Verificar se a sessão salva é válida")

    # sync
    subparsers.add_parser("sync", help="Sincronizar pasta de vídeos com o banco SQLite")

    # queue
    p_queue = subparsers.add_parser("queue", help="Exibir fila de vídeos")
    p_queue.add_argument("--limit", type=int, default=25, help="Quantidade de vídeos a exibir")

    # post-next
    subparsers.add_parser("post-next", help="Publicar imediatamente o próximo vídeo pendente")

    # daemon
    subparsers.add_parser("daemon", help="Executar o scheduler em primeiro plano")

    # start
    subparsers.add_parser("start", help="Iniciar o serviço em segundo plano")

    # stop
    subparsers.add_parser("stop", help="Parar o serviço em segundo plano")

    # status
    subparsers.add_parser("status", help="Ver status do serviço e da fila")

    args = parser.parse_args()

    commands = {
        "login": cmd_login,
        "check-auth": cmd_check_auth,
        "sync": cmd_sync,
        "queue": cmd_queue,
        "post-next": cmd_post_next,
        "daemon": cmd_daemon,
        "start": cmd_start,
        "stop": cmd_stop,
        "status": cmd_status
    }

    if args.command in commands:
        commands[args.command](args)
    else:
        parser.print_help()

if __name__ == "__main__":
    main()
