import os
import re
import json
import sqlite3
from datetime import datetime, timedelta
from typing import Optional, List, Dict, Any

class TikTokDB:
    def __init__(self, db_path: str):
        self.db_path = db_path
        os.makedirs(os.path.dirname(db_path), exist_ok=True)
        self.init_db()

    def _get_conn(self) -> sqlite3.Connection:
        conn = sqlite3.connect(self.db_path)
        conn.row_factory = sqlite3.Row
        return conn

    def init_db(self):
        with self._get_conn() as conn:
            conn.execute("""
                CREATE TABLE IF NOT EXISTS queue (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    filename TEXT NOT NULL UNIQUE,
                    filepath TEXT NOT NULL,
                    caption TEXT,
                    hashtags TEXT,
                    status TEXT NOT NULL DEFAULT 'PENDING',
                    attempts INTEGER NOT NULL DEFAULT 0,
                    error_message TEXT,
                    scheduled_at TEXT,
                    published_at TEXT,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                )
            """)
            conn.execute("CREATE INDEX IF NOT EXISTS idx_queue_status ON queue(status)")
            conn.execute("CREATE INDEX IF NOT EXISTS idx_queue_scheduled_at ON queue(scheduled_at)")
            conn.commit()

    @staticmethod
    def parse_interval(interval_str: str) -> timedelta:
        match = re.match(r"^(\d+)([hmsd])$", interval_str.strip().lower())
        if not match:
            return timedelta(hours=3)
        val, unit = int(match.group(1)), match.group(2)
        if unit == "h":
            return timedelta(hours=val)
        elif unit == "m":
            return timedelta(minutes=val)
        elif unit == "s":
            return timedelta(seconds=val)
        elif unit == "d":
            return timedelta(days=val)
        return timedelta(hours=3)

    def sync_videos(self, video_dir: str, interval_str: str = "3h") -> int:
        if not os.path.exists(video_dir):
            return 0

        interval = self.parse_interval(interval_str)
        files = [
            f for f in os.listdir(video_dir)
            if not f.startswith(".") and f.lower().endswith((".mp4", ".mov"))
        ]
        files.sort()

        now = datetime.now()
        added_count = 0

        with self._get_conn() as conn:
            # Find current max scheduled_at
            cur = conn.execute("SELECT MAX(scheduled_at) as max_sched FROM queue")
            row = cur.fetchone()
            last_scheduled = None
            if row and row["max_sched"]:
                try:
                    last_scheduled = datetime.fromisoformat(row["max_sched"])
                except Exception:
                    last_scheduled = None

            base_time = last_scheduled if (last_scheduled and last_scheduled > now) else now

            for f in files:
                filepath = os.path.join(video_dir, f)
                cur = conn.execute("SELECT id FROM queue WHERE filename = ?", (f,))
                if cur.fetchone():
                    continue

                # Load metadata json if present
                stem = os.path.splitext(f)[0]
                json_path = os.path.join(video_dir, f"{stem}.json")
                caption = stem
                hashtags = ["#tiktok", "#fyp", "#brasil", "#noticias"]

                if os.path.exists(json_path):
                    try:
                        with open(json_path, "r", encoding="utf-8") as jf:
                            meta = json.load(jf)
                            caption = meta.get("caption", caption)
                            raw_tags = meta.get("hashtags", [])
                            if raw_tags:
                                # Adapt hashtags for TikTok
                                clean_tags = []
                                for tag in raw_tags:
                                    if tag.lower() not in ["#shorts", "#short"]:
                                        clean_tags.append(tag)
                                if "#tiktok" not in [t.lower() for t in clean_tags]:
                                    clean_tags.insert(0, "#tiktok")
                                if "#fyp" not in [t.lower() for t in clean_tags]:
                                    clean_tags.insert(1, "#fyp")
                                hashtags = clean_tags
                    except Exception:
                        pass

                scheduled_at = base_time + (interval * added_count)
                iso_sched = scheduled_at.isoformat(timespec="seconds")
                iso_now = now.isoformat(timespec="seconds")

                conn.execute("""
                    INSERT INTO queue (filename, filepath, caption, hashtags, status, scheduled_at, created_at, updated_at)
                    VALUES (?, ?, ?, ?, 'PENDING', ?, ?, ?)
                """, (f, filepath, caption, json.dumps(hashtags, ensure_ascii=False), iso_sched, iso_now, iso_now))
                added_count += 1

            conn.commit()
        return added_count

    def get_next_pending(self) -> Optional[Dict[str, Any]]:
        with self._get_conn() as conn:
            cur = conn.execute("""
                SELECT * FROM queue
                WHERE (status = 'PENDING' OR (status = 'FAILED' AND attempts < 3))
                ORDER BY scheduled_at ASC
                LIMIT 1
            """)
            row = cur.fetchone()
            if row:
                return dict(row)
            return None

    def reset_failed(self):
        now = datetime.now().isoformat(timespec="seconds")
        with self._get_conn() as conn:
            conn.execute("""
                UPDATE queue
                SET status = 'PENDING', attempts = 0, error_message = NULL, updated_at = ?
                WHERE status = 'FAILED'
            """, (now,))
            conn.commit()

    def mark_processing(self, item_id: int):
        now = datetime.now().isoformat(timespec="seconds")
        with self._get_conn() as conn:
            conn.execute("""
                UPDATE queue
                SET status = 'PROCESSING', attempts = attempts + 1, updated_at = ?
                WHERE id = ?
            """, (now, item_id))
            conn.commit()

    def mark_published(self, item_id: int):
        now = datetime.now().isoformat(timespec="seconds")
        with self._get_conn() as conn:
            conn.execute("""
                UPDATE queue
                SET status = 'PUBLISHED', published_at = ?, error_message = NULL, updated_at = ?
                WHERE id = ?
            """, (now, now, item_id))
            conn.commit()

    def mark_failed(self, item_id: int, error_message: str):
        now = datetime.now().isoformat(timespec="seconds")
        with self._get_conn() as conn:
            conn.execute("""
                UPDATE queue
                SET status = 'FAILED', error_message = ?, updated_at = ?
                WHERE id = ?
            """, (error_message, now, item_id))
            conn.commit()

    def reset_processing_to_pending(self):
        now = datetime.now().isoformat(timespec="seconds")
        with self._get_conn() as conn:
            conn.execute("""
                UPDATE queue
                SET status = 'PENDING', updated_at = ?
                WHERE status = 'PROCESSING'
            """, (now,))
            conn.commit()

    def get_summary(self) -> Dict[str, int]:
        with self._get_conn() as conn:
            cur = conn.execute("""
                SELECT status, count(*) as count
                FROM queue
                GROUP BY status
            """)
            counts = {row["status"]: row["count"] for row in cur.fetchall()}
            return {
                "pending": counts.get("PENDING", 0),
                "processing": counts.get("PROCESSING", 0),
                "published": counts.get("PUBLISHED", 0),
                "failed": counts.get("FAILED", 0),
                "total": sum(counts.values())
            }

    def list_queue(self, limit: int = 25) -> List[Dict[str, Any]]:
        with self._get_conn() as conn:
            cur = conn.execute("""
                SELECT id, filename, status, scheduled_at, published_at, attempts, error_message
                FROM queue
                ORDER BY
                    CASE status
                        WHEN 'PROCESSING' THEN 1
                        WHEN 'PENDING' THEN 2
                        WHEN 'FAILED' THEN 3
                        WHEN 'PUBLISHED' THEN 4
                    END,
                    scheduled_at ASC
                LIMIT ?
            """, (limit,))
            return [dict(r) for r in cur.fetchall()]
