#!/usr/bin/env python3
"""
cloud/status_api.py - API REST & Web Dashboard para VideoPost (Cloud & Lovable Integration).

Exposições:
1. GET / -> Painel Web visual interativo (Tailwind CSS, responsivo, dark mode).
2. GET /api/health -> Healthcheck para Railway, Render e Docker.
3. GET /api/status -> Status completo (YouTube, TikTok, Google Drive, Processos).
4. GET /api/queue -> Detalhes da fila de agendamento e histórico de publicações.
5. POST /api/sync-drive -> Dispara sincronização imediata com o Google Drive.
6. GET /api/logs -> Últimos logs de execução em tempo real.
7. GET /openapi.json -> Esquema OpenAPI para integração com Lovable.dev e frontends externos.
"""

import os
import sys
import json
import sqlite3
import subprocess
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Optional

from fastapi import FastAPI, BackgroundTasks, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import HTMLResponse, JSONResponse
from pydantic import BaseModel

BASE_DIR = Path(__file__).resolve().parent.parent
DOT_VIDEOPOST = BASE_DIR / ".videopost"
TIKTOK_DIR = BASE_DIR / "tiktok-service"

# Adicionar cloud ao path para importar drive_sync
sys.path.insert(0, str(BASE_DIR / "cloud"))
try:
    from drive_sync import GoogleDriveSync
except ImportError:
    GoogleDriveSync = None

app = FastAPI(
    title="VideoPost Cloud API",
    description="API de monitoramento, controle de filas e sincronização do Google Drive para VideoPost (YouTube Shorts & TikTok)",
    version="1.0.0"
)

# Permitir CORS total para Lovable, Vercel, localhost e qualquer origem
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

def get_active_profile() -> str:
    active_profile_file = DOT_VIDEOPOST / "active_profile"
    if active_profile_file.exists():
        p = active_profile_file.read_text(encoding="utf-8").strip()
        if p:
            return p
    return os.getenv("VIDEOPOST_PROFILE", "noticiabrasil")

def get_yt_db_path() -> Path:
    profile = get_active_profile()
    p_db = DOT_VIDEOPOST / "profiles" / profile / "videopost.db"
    if p_db.exists():
        return p_db
    root_db = DOT_VIDEOPOST / "videopost.db"
    return root_db

def is_pid_running(pid_file: Path) -> bool:
    if not pid_file.exists():
        return False
    try:
        pid = int(pid_file.read_text().strip())
        # Check if process is alive
        os.kill(pid, 0)
        return True
    except (ValueError, OSError):
        return False

def get_youtube_status() -> dict:
    yt_db = get_yt_db_path()
    yt_pid_file = DOT_VIDEOPOST / "videopost.pid"
    is_running = is_pid_running(yt_pid_file)

    if not yt_db.exists():
        return {
            "enabled": True,
            "running": is_running,
            "total_videos": 0,
            "pending": 0,
            "published": 0,
            "failed": 0,
            "last_published": None,
            "next_scheduled": None
        }

    try:
        conn = sqlite3.connect(str(yt_db))
        conn.row_factory = sqlite3.Row
        cur = conn.cursor()

        # Total
        cur.execute("SELECT COUNT(*) FROM videos")
        total = cur.fetchone()[0]

        # Publicados
        cur.execute("SELECT COUNT(*) FROM publications WHERE UPPER(platform)='YOUTUBE_SHORTS' AND status='PUBLISHED'")
        published = cur.fetchone()[0]

        # Falhas
        cur.execute("SELECT COUNT(*) FROM publications WHERE UPPER(platform)='YOUTUBE_SHORTS' AND status='FAILED'")
        failed = cur.fetchone()[0]

        # Pendentes / Agendados
        cur.execute("SELECT COUNT(*) FROM videos WHERE status IN ('PENDING', 'SCHEDULED')")
        pending = cur.fetchone()[0]

        # Último publicado
        cur.execute("""
            SELECT videos.filename, publications.external_id, publications.published_at, videos.caption
            FROM publications
            JOIN videos ON videos.id = publications.video_id
            WHERE UPPER(publications.platform)='YOUTUBE_SHORTS' AND publications.status='PUBLISHED'
            ORDER BY publications.published_at DESC LIMIT 1
        """)
        last_row = cur.fetchone()
        last_pub = None
        if last_row:
            last_pub = {
                "filename": last_row["filename"],
                "external_id": last_row["external_id"],
                "url": f"https://www.youtube.com/shorts/{last_row['external_id']}" if last_row["external_id"] else None,
                "published_at": last_row["published_at"],
                "caption": last_row["caption"]
            }

        # Próximo agendado
        cur.execute("""
            SELECT filename, scheduled_at, caption
            FROM videos
            WHERE status IN ('PENDING', 'SCHEDULED') AND scheduled_at IS NOT NULL
            ORDER BY scheduled_at ASC LIMIT 1
        """)
        next_row = cur.fetchone()
        next_sched = dict(next_row) if next_row else None

        conn.close()
        return {
            "enabled": True,
            "running": is_running,
            "total_videos": total,
            "pending": pending,
            "published": published,
            "failed": failed,
            "last_published": last_pub,
            "next_scheduled": next_sched
        }
    except Exception as e:
        return {"enabled": True, "running": is_running, "error": str(e)}

def get_tiktok_status() -> dict:
    tt_db = TIKTOK_DIR / "tiktok_service.db"
    tt_pid_file = TIKTOK_DIR / "tiktok.pid"
    is_running = is_pid_running(tt_pid_file)

    if not tt_db.exists():
        return {
            "enabled": True,
            "running": is_running,
            "total_videos": 0,
            "pending": 0,
            "published": 0,
            "failed": 0,
            "last_published": None,
            "next_scheduled": None
        }

    try:
        conn = sqlite3.connect(str(tt_db))
        conn.row_factory = sqlite3.Row
        cur = conn.cursor()

        cur.execute("SELECT COUNT(*) FROM queue")
        total = cur.fetchone()[0]

        cur.execute("SELECT COUNT(*) FROM queue WHERE status='PUBLISHED'")
        published = cur.fetchone()[0]

        cur.execute("SELECT COUNT(*) FROM queue WHERE status='FAILED'")
        failed = cur.fetchone()[0]

        cur.execute("SELECT COUNT(*) FROM queue WHERE status='PENDING'")
        pending = cur.fetchone()[0]

        cur.execute("SELECT filename, caption, published_at FROM queue WHERE status='PUBLISHED' ORDER BY published_at DESC LIMIT 1")
        last_row = cur.fetchone()
        last_pub = dict(last_row) if last_row else None

        cur.execute("SELECT filename, caption, scheduled_at FROM queue WHERE status='PENDING' ORDER BY scheduled_at ASC LIMIT 1")
        next_row = cur.fetchone()
        next_sched = dict(next_row) if next_row else None

        conn.close()
        return {
            "enabled": True,
            "running": is_running,
            "total_videos": total,
            "pending": pending,
            "published": published,
            "failed": failed,
            "last_published": last_pub,
            "next_scheduled": next_sched
        }
    except Exception as e:
        return {"enabled": True, "running": is_running, "error": str(e)}

def get_drive_status() -> dict:
    state_file = BASE_DIR / ".drive_sync_state.json"
    folder_id = os.getenv("GDRIVE_FOLDER_ID") or os.getenv("GDRIVE_FOLDER_URL") or ""
    state = {
        "folder_id": folder_id,
        "configured": bool(folder_id),
        "last_sync": None,
        "total_synced": 0,
        "last_status": "idle"
    }
    if state_file.exists():
        try:
            data = json.loads(state_file.read_text(encoding="utf-8"))
            state.update(data)
        except Exception:
            pass
    return state

# ==================== ENDPOINTS ====================

@app.get("/api/health")
@app.get("/health")
def health():
    return {
        "status": "healthy",
        "service": "VideoPost Cloud Engine",
        "timestamp": datetime.utcnow().isoformat() + "Z"
    }

@app.get("/api/status")
def status():
    return {
        "service": "VideoPost Cloud Engine",
        "profile": get_active_profile(),
        "timestamp": datetime.utcnow().isoformat() + "Z",
        "youtube": get_youtube_status(),
        "tiktok": get_tiktok_status(),
        "google_drive": get_drive_status()
    }

@app.get("/api/queue")
def queue():
    yt_db = get_yt_db_path()
    items = []

    if yt_db.exists():
        try:
            conn = sqlite3.connect(str(yt_db))
            conn.row_factory = sqlite3.Row
            cur = conn.cursor()
            cur.execute("""
                SELECT 
                    videos.id,
                    videos.filename,
                    videos.status as video_status,
                    videos.scheduled_at,
                    videos.published_at,
                    videos.caption,
                    videos.hashtags,
                    publications.platform,
                    publications.status as pub_status,
                    publications.external_id,
                    publications.attempts,
                    publications.error_message
                FROM videos
                LEFT JOIN publications ON videos.id = publications.video_id
                ORDER BY 
                    CASE WHEN publications.status = 'PENDING' THEN 1
                         WHEN publications.status = 'FAILED' THEN 2
                         ELSE 3 END,
                    videos.scheduled_at ASC
            """)
            for row in cur.fetchall():
                d = dict(row)
                if d.get("external_id") and d.get("platform") == "youtube_shorts":
                    d["url"] = f"https://www.youtube.com/shorts/{d['external_id']}"
                else:
                    d["url"] = None
                items.append(d)
            conn.close()
        except Exception as e:
            return {"error": str(e), "items": []}

    return {"count": len(items), "items": items}

@app.post("/api/sync-drive")
def sync_drive(background_tasks: BackgroundTasks):
    folder_id = os.getenv("GDRIVE_FOLDER_ID") or os.getenv("GDRIVE_FOLDER_URL")
    if not folder_id:
        raise HTTPException(status_code=400, detail="GDRIVE_FOLDER_ID não está configurado.")

    def run_sync():
        if GoogleDriveSync:
            engine = GoogleDriveSync()
            engine.sync_once()

    background_tasks.add_task(run_sync)
    return {
        "status": "started",
        "message": "Sincronização com o Google Drive iniciada em segundo plano.",
        "timestamp": datetime.utcnow().isoformat() + "Z"
    }

@app.get("/api/logs")
def logs(lines: int = 50):
    yt_log_file = DOT_VIDEOPOST / "videopost.log"
    tt_log_file = TIKTOK_DIR / "logs" / "tiktok.log"

    def read_last_lines(file_path: Path, n: int) -> List[str]:
        if not file_path.exists():
            return []
        try:
            with open(file_path, "r", encoding="utf-8", errors="replace") as f:
                return f.readlines()[-n:]
        except Exception:
            return []

    return {
        "youtube_logs": read_last_lines(yt_log_file, lines),
        "tiktok_logs": read_last_lines(tt_log_file, lines)
    }

# ==================== EMBEDDED DASHBOARD ====================

@app.get("/", response_class=HTMLResponse)
def dashboard():
    html_content = """<!DOCTYPE html>
<html lang="pt-BR" class="dark">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>VideoPost Cloud Hub - Automação 24/7</title>
    <script src="https://cdn.tailwindcss.com"></script>
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <script>
        tailwind.config = {
            darkMode: 'class',
            theme: {
                extend: {
                    colors: {
                        brand: { 500: '#ef4444', 600: '#dc2626', 700: '#b91c1c' }
                    }
                }
            }
        }
    </script>
</head>
<body class="bg-slate-950 text-slate-100 min-h-screen font-sans antialiased">
    <!-- Navbar -->
    <header class="border-b border-slate-800 bg-slate-900/60 backdrop-blur sticky top-0 z-50">
        <div class="max-w-7xl mx-auto px-4 h-16 flex items-center justify-between">
            <div class="flex items-center space-x-3">
                <div class="w-10 h-10 rounded-xl bg-gradient-to-tr from-red-600 to-amber-500 flex items-center justify-center shadow-lg shadow-red-500/20">
                    <i class="fa-solid fa-play text-white text-lg"></i>
                </div>
                <div>
                    <h1 class="font-bold text-lg leading-tight flex items-center gap-2">
                        VideoPost <span class="text-xs px-2 py-0.5 rounded-full bg-red-500/20 text-red-400 border border-red-500/30">Cloud 24/7</span>
                    </h1>
                    <p class="text-xs text-slate-400">Perfil Ativo: <span id="profile-tag" class="text-slate-200 font-medium">Carregando...</span></p>
                </div>
            </div>

            <div class="flex items-center space-x-3">
                <button onclick="triggerSync()" id="btn-sync" class="px-4 py-2 bg-gradient-to-r from-red-600 to-red-700 hover:from-red-500 hover:to-red-600 text-white rounded-lg text-sm font-semibold flex items-center gap-2 transition shadow-lg shadow-red-600/20 disabled:opacity-50">
                    <i class="fa-solid fa-arrows-rotate" id="sync-icon"></i>
                    <span>Sincronizar Google Drive</span>
                </button>
                <a href="/docs" target="_blank" class="px-3 py-2 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-lg text-sm font-medium flex items-center gap-2 border border-slate-700 transition">
                    <i class="fa-solid fa-code"></i> API Docs
                </a>
            </div>
        </div>
    </header>

    <!-- Main Content -->
    <main class="max-w-7xl mx-auto px-4 py-8 space-y-8">
        
        <!-- Live Status Cards -->
        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
            
            <!-- Card 1: YouTube Shorts -->
            <div class="bg-slate-900 border border-slate-800 rounded-2xl p-5 shadow-sm hover:border-slate-700 transition">
                <div class="flex justify-between items-start">
                    <span class="text-sm font-medium text-slate-400">YouTube Shorts</span>
                    <span id="yt-badge" class="px-2 py-0.5 rounded-full text-xs font-semibold bg-emerald-500/20 text-emerald-400 border border-emerald-500/30">Ativo</span>
                </div>
                <div class="mt-3 flex items-baseline gap-2">
                    <span id="yt-published" class="text-3xl font-extrabold text-white">--</span>
                    <span class="text-xs text-slate-400">postados</span>
                </div>
                <div class="mt-3 pt-3 border-t border-slate-800 flex justify-between text-xs text-slate-400">
                    <span>Fila: <strong id="yt-pending" class="text-slate-200">--</strong></span>
                    <span>Falhas: <strong id="yt-failed" class="text-slate-200">0</strong></span>
                </div>
            </div>

            <!-- Card 2: TikTok -->
            <div class="bg-slate-900 border border-slate-800 rounded-2xl p-5 shadow-sm hover:border-slate-700 transition">
                <div class="flex justify-between items-start">
                    <span class="text-sm font-medium text-slate-400">TikTok</span>
                    <span id="tt-badge" class="px-2 py-0.5 rounded-full text-xs font-semibold bg-cyan-500/20 text-cyan-400 border border-cyan-500/30">Pronto</span>
                </div>
                <div class="mt-3 flex items-baseline gap-2">
                    <span id="tt-published" class="text-3xl font-extrabold text-white">--</span>
                    <span class="text-xs text-slate-400">postados</span>
                </div>
                <div class="mt-3 pt-3 border-t border-slate-800 flex justify-between text-xs text-slate-400">
                    <span>Fila: <strong id="tt-pending" class="text-slate-200">--</strong></span>
                    <span>Intervalo: <strong class="text-slate-200">3h</strong></span>
                </div>
            </div>

            <!-- Card 3: Google Drive -->
            <div class="bg-slate-900 border border-slate-800 rounded-2xl p-5 shadow-sm hover:border-slate-700 transition">
                <div class="flex justify-between items-start">
                    <span class="text-sm font-medium text-slate-400">Google Drive Sync</span>
                    <span id="drive-badge" class="px-2 py-0.5 rounded-full text-xs font-semibold bg-indigo-500/20 text-indigo-400 border border-indigo-500/30">Conectado</span>
                </div>
                <div class="mt-3 flex items-baseline gap-2">
                    <span id="drive-total" class="text-3xl font-extrabold text-white">--</span>
                    <span class="text-xs text-slate-400">vídeos baixados</span>
                </div>
                <div class="mt-3 pt-3 border-t border-slate-800 flex justify-between text-xs text-slate-400 truncate">
                    <span>Último sync: <strong id="drive-time" class="text-slate-200">--</strong></span>
                </div>
            </div>

            <!-- Card 4: Próxima Publicação -->
            <div class="bg-slate-900 border border-slate-800 rounded-2xl p-5 shadow-sm hover:border-slate-700 transition">
                <div class="flex justify-between items-start">
                    <span class="text-sm font-medium text-slate-400">Próximo Disparo</span>
                    <i class="fa-regular fa-clock text-slate-500"></i>
                </div>
                <div class="mt-3">
                    <div id="next-timer" class="text-xl font-bold text-amber-400">Aguardando...</div>
                    <div id="next-title" class="text-xs text-slate-400 truncate mt-1">--</div>
                </div>
                <div class="mt-3 pt-3 border-t border-slate-800 text-xs text-slate-400 flex items-center justify-between">
                    <span>Cadência:</span>
                    <span class="text-emerald-400 font-semibold">A cada 3 horas</span>
                </div>
            </div>

        </div>

        <!-- Vídeo Recente em Destaque -->
        <div id="last-published-box" class="bg-gradient-to-r from-red-950/40 via-slate-900 to-slate-900 border border-red-900/40 rounded-2xl p-6 flex flex-col md:flex-row items-center justify-between gap-4">
            <div class="flex items-center gap-4">
                <div class="w-12 h-12 rounded-xl bg-red-600/20 border border-red-500/30 flex items-center justify-center text-red-500 text-xl flex-shrink-0">
                    <i class="fa-brands fa-youtube"></i>
                </div>
                <div>
                    <span class="text-xs font-semibold uppercase tracking-wider text-red-400">Último Short Publicado</span>
                    <h3 id="last-pub-title" class="text-base font-semibold text-white line-clamp-1 mt-0.5">Carregando...</h3>
                    <p id="last-pub-date" class="text-xs text-slate-400">--</p>
                </div>
            </div>
            <a id="last-pub-link" href="#" target="_blank" class="px-5 py-2.5 bg-red-600 hover:bg-red-500 text-white rounded-xl text-sm font-bold flex items-center gap-2 transition flex-shrink-0 shadow-lg shadow-red-600/30">
                Assistir no YouTube <i class="fa-solid fa-arrow-up-right-from-square text-xs"></i>
            </a>
        </div>

        <!-- Fila de Vídeos Table -->
        <div class="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden shadow-sm">
            <div class="px-6 py-4 border-b border-slate-800 flex items-center justify-between">
                <div>
                    <h2 class="font-bold text-lg text-white">Fila de Publicações</h2>
                    <p class="text-xs text-slate-400">Vídeos sincronizados e programados no YouTube Shorts</p>
                </div>
                <span id="queue-badge" class="px-3 py-1 rounded-full text-xs font-semibold bg-slate-800 text-slate-300 border border-slate-700">-- vídeos</span>
            </div>
            
            <div class="overflow-x-auto">
                <table class="w-full text-left text-sm text-slate-300">
                    <thead class="bg-slate-950/50 text-slate-400 text-xs uppercase tracking-wider border-b border-slate-800">
                        <tr>
                            <th class="px-6 py-3">Arquivo / Título</th>
                            <th class="px-6 py-3">Status</th>
                            <th class="px-6 py-3">Agendado Para</th>
                            <th class="px-6 py-3 text-right">Ação</th>
                        </tr>
                    </thead>
                    <tbody id="queue-body" class="divide-y divide-slate-800/60">
                        <tr>
                            <td colspan="4" class="px-6 py-8 text-center text-slate-500">Carregando fila de vídeos...</td>
                        </tr>
                    </tbody>
                </table>
            </div>
        </div>

    </main>

    <!-- Toast Notification -->
    <div id="toast" class="fixed bottom-6 right-6 bg-slate-800 text-white border border-slate-700 px-5 py-3 rounded-xl shadow-2xl flex items-center gap-3 transition-opacity duration-300 opacity-0 pointer-events-none">
        <i class="fa-solid fa-circle-info text-red-400" id="toast-icon"></i>
        <span id="toast-message" class="text-sm font-medium">Aviso</span>
    </div>

    <script>
        function showToast(msg, icon = 'fa-circle-info') {
            const toast = document.getElementById('toast');
            document.getElementById('toast-message').innerText = msg;
            document.getElementById('toast-icon').className = `fa-solid ${icon} text-red-400`;
            toast.classList.remove('opacity-0', 'pointer-events-none');
            setTimeout(() => {
                toast.classList.add('opacity-0', 'pointer-events-none');
            }, 4000);
        }

        async function loadStatus() {
            try {
                const res = await fetch('/api/status');
                const data = await res.json();

                document.getElementById('profile-tag').innerText = data.profile || 'Padrão';

                // YouTube
                if (data.youtube) {
                    document.getElementById('yt-published').innerText = data.youtube.published || 0;
                    document.getElementById('yt-pending').innerText = data.youtube.pending || 0;
                    document.getElementById('yt-failed').innerText = data.youtube.failed || 0;
                    if (data.youtube.running) {
                        document.getElementById('yt-badge').className = 'px-2 py-0.5 rounded-full text-xs font-semibold bg-emerald-500/20 text-emerald-400 border border-emerald-500/30';
                        document.getElementById('yt-badge').innerText = 'Online 24/7';
                    }
                    if (data.youtube.last_published) {
                        document.getElementById('last-pub-title').innerText = data.youtube.last_published.caption || data.youtube.last_published.filename;
                        document.getElementById('last-pub-date').innerText = 'Publicado em ' + new Date(data.youtube.last_published.published_at).toLocaleString('pt-BR');
                        document.getElementById('last-pub-link').href = data.youtube.last_published.url || '#';
                        document.getElementById('last-published-box').classList.remove('hidden');
                    }
                    if (data.youtube.next_scheduled) {
                        const schedTime = new Date(data.youtube.next_scheduled.scheduled_at);
                        document.getElementById('next-timer').innerText = schedTime.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
                        document.getElementById('next-title').innerText = data.youtube.next_scheduled.filename;
                    }
                }

                // TikTok
                if (data.tiktok) {
                    document.getElementById('tt-published').innerText = data.tiktok.published || 0;
                    document.getElementById('tt-pending').innerText = data.tiktok.pending || 0;
                }

                // Drive
                if (data.google_drive) {
                    document.getElementById('drive-total').innerText = data.google_drive.total_synced || 0;
                    if (data.google_drive.last_sync) {
                        document.getElementById('drive-time').innerText = new Date(data.google_drive.last_sync).toLocaleTimeString('pt-BR');
                    }
                }

            } catch (err) {
                console.error("Erro ao carregar status:", err);
            }
        }

        async function loadQueue() {
            try {
                const res = await fetch('/api/queue');
                const data = await res.json();
                const tbody = document.getElementById('queue-body');
                document.getElementById('queue-badge').innerText = `${data.count} itens`;

                if (!data.items || data.items.length === 0) {
                    tbody.innerHTML = '<tr><td colspan="4" class="px-6 py-8 text-center text-slate-500">Nenhum vídeo na fila. Adicione novos vídeos na pasta do Drive!</td></tr>';
                    return;
                }

                let html = '';
                data.items.slice(0, 30).forEach(item => {
                    const isPub = item.pub_status === 'PUBLISHED';
                    const isFail = item.pub_status === 'FAILED';
                    const badgeClass = isPub 
                        ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30'
                        : isFail 
                        ? 'bg-rose-500/20 text-rose-400 border border-rose-500/30'
                        : 'bg-amber-500/20 text-amber-400 border border-amber-500/30';
                    const statusLabel = isPub ? 'Publicado' : isFail ? 'Falhou' : 'Na Fila';

                    const dateStr = item.scheduled_at ? new Date(item.scheduled_at).toLocaleString('pt-BR') : '--';

                    html += `
                        <tr class="hover:bg-slate-900/50 transition">
                            <td class="px-6 py-4">
                                <div class="font-medium text-white truncate max-w-md">${item.filename}</div>
                                <div class="text-xs text-slate-400 line-clamp-1">${item.caption || 'Sem descrição'}</div>
                            </td>
                            <td class="px-6 py-4">
                                <span class="px-2.5 py-1 rounded-full text-xs font-semibold ${badgeClass}">
                                    ${statusLabel}
                                </span>
                            </td>
                            <td class="px-6 py-4 text-xs text-slate-400">
                                ${dateStr}
                            </td>
                            <td class="px-6 py-4 text-right">
                                ${item.url ? `<a href="${item.url}" target="_blank" class="text-red-400 hover:text-red-300 text-xs font-semibold flex items-center justify-end gap-1"><i class="fa-solid fa-play"></i> Ver Short</a>` : '<span class="text-slate-600 text-xs">--</span>'}
                            </td>
                        </tr>
                    `;
                });
                tbody.innerHTML = html;
            } catch (err) {
                console.error("Erro ao carregar fila:", err);
            }
        }

        async function triggerSync() {
            const btn = document.getElementById('btn-sync');
            const icon = document.getElementById('sync-icon');
            btn.disabled = true;
            icon.classList.add('fa-spin');
            showToast('Disparando sincronização com Google Drive...', 'fa-arrows-rotate');

            try {
                const res = await fetch('/api/sync-drive', { method: 'POST' });
                const json = await res.json();
                if (res.ok) {
                    showToast('Sincronização iniciada com sucesso!', 'fa-check');
                    setTimeout(() => { loadStatus(); loadQueue(); }, 3000);
                } else {
                    showToast(json.detail || 'Falha ao sincronizar.', 'fa-circle-xmark');
                }
            } catch (err) {
                showToast('Erro de conexão ao sincronizar.', 'fa-circle-xmark');
            } finally {
                setTimeout(() => {
                    btn.disabled = false;
                    icon.classList.remove('fa-spin');
                }, 2000);
            }
        }

        // Init
        loadStatus();
        loadQueue();
        setInterval(() => {
            loadStatus();
            loadQueue();
        }, 15000);
    </script>
</body>
</html>"""
    return HTMLResponse(content=html_content)

if __name__ == "__main__":
    import uvicorn
    port = int(os.getenv("PORT", 8000))
    uvicorn.run("status_api:app", host="0.0.0.0", port=port, reload=False)
